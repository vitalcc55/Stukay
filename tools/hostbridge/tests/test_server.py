from __future__ import annotations

import http.client
import json
import os
import socket
import subprocess
import sys
import threading
import time
import unittest
from pathlib import Path

from tools.hostbridge.server import HostBridgeHttpServer, HostBridgeService, validate_bind_host


class FakeRuntimeClient:
    def __init__(self, responses):
        self._responses = list(responses)
        self.calls = 0
        self.recorded_interrupts = []
        self.recorded_approval_responses = []

    def fetch_app_list(self):
        self.calls += 1
        response = self._responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    def list_threads(self, *, source_kinds, archived, sort_key, sort_direction, limit=50):
        self.calls += 1
        response = self._responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    def read_thread(self, thread_id, *, include_turns):
        self.calls += 1
        return self._responses.pop(0)

    def resume_thread(self, thread_id):
        self.calls += 1
        return self._responses.pop(0)

    def start_turn(self, thread_id, text):
        self.calls += 1
        return self._responses.pop(0)

    def interrupt_turn(self, thread_id, turn_id):
        self.recorded_interrupts.append((thread_id, turn_id))
        return {}

    def respond_server_request(self, request_id, result):
        self.recorded_approval_responses.append((request_id, result))

    def subscribe_thread(self, thread_id):
        response = self._responses.pop(0)
        return FakeSubscription(response)

    def close(self):
        return None


class FakeSubscription:
    def __init__(self, events):
        self._events = list(events)

    def next_event(self, timeout_s=None):
        if not self._events:
            return None
        return self._events.pop(0)

    def close(self):
        return None


class ServerTest(unittest.TestCase):
    def test_validate_bind_host_accepts_loopback_and_private_ranges(self):
        self.assertEqual("127.0.0.1", validate_bind_host("127.0.0.1"))
        self.assertEqual("10.0.0.8", validate_bind_host("10.0.0.8"))
        self.assertEqual("100.64.10.20", validate_bind_host("100.64.10.20"))
        self.assertEqual("localhost", validate_bind_host("localhost"))

    def test_validate_bind_host_rejects_wildcard_and_public_hosts(self):
        with self.assertRaises(ValueError):
            validate_bind_host("0.0.0.0")
        with self.assertRaises(ValueError):
            validate_bind_host("8.8.8.8")
        with self.assertRaises(ValueError):
            validate_bind_host("example.com")

    def test_runtime_summary_requires_bearer_auth(self):
        factory_calls = []

        def client_factory():
            client = FakeRuntimeClient([[{"id": "a"}]])
            factory_calls.append(client)
            return client

        server, thread = _start_server(client_factory, "secret-token")
        try:
            connection = http.client.HTTPConnection(server.server_address[0], server.server_address[1], timeout=2)
            connection.request("GET", "/v1/runtime/summary")
            response = connection.getresponse()
            payload = json.loads(response.read().decode("utf-8"))
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

        self.assertEqual(401, response.status)
        self.assertEqual("unauthorized", payload["errorCode"])
        self.assertEqual(0, len(factory_calls))

    def test_runtime_summary_returns_ready_state_for_authorized_request(self):
        server, thread = _start_server(lambda: FakeRuntimeClient([[{"id": "a"}, {"id": "b"}]]), "secret-token")
        try:
            connection = http.client.HTTPConnection(server.server_address[0], server.server_address[1], timeout=2)
            connection.request(
                "GET",
                "/v1/runtime/summary",
                headers={"Authorization": "Bearer secret-token"},
            )
            response = connection.getresponse()
            payload = json.loads(response.read().decode("utf-8"))
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

        self.assertEqual(200, response.status)
        self.assertEqual("ready", payload["hostStatus"])
        self.assertTrue(payload["runtimeReady"])
        self.assertEqual(2, payload["appListCount"])

    def test_runtime_summary_preserves_last_good_snapshot_when_probe_fails(self):
        factory_calls = []

        def client_factory():
            client = FakeRuntimeClient([
                [{"id": "a"}, {"id": "b"}, {"id": "c"}],
                TimeoutError("probe timeout"),
            ])
            factory_calls.append(client)
            return client

        service = HostBridgeService(session_token="secret-token", runtime_client_factory=client_factory)

        first = service.get_runtime_summary()
        second = service.get_runtime_summary()

        self.assertEqual(1, len(factory_calls))
        self.assertEqual("ready", first.host_status)
        self.assertEqual(3, first.app_list_count)
        self.assertEqual("degraded", second.host_status)
        self.assertFalse(second.runtime_ready)
        self.assertEqual(3, second.app_list_count)
        self.assertEqual(1, second.retry_attempt)
        self.assertEqual("probe_failed", second.degraded_reason)

    def test_runtime_summary_does_not_leak_raw_transport_error(self):
        service = HostBridgeService(
            session_token="secret-token",
            runtime_client_factory=lambda: FakeRuntimeClient([RuntimeError("not-json stderr: raw-secret")]),
        )

        summary = service.get_runtime_summary()

        self.assertEqual("runtime_unavailable", summary.error_code)
        self.assertEqual("Host Bridge runtime is unavailable.", summary.error_message)
        self.assertEqual("Host Bridge runtime is unavailable.", summary.last_transport_error)

    def test_module_entrypoint_returns_degraded_json_when_runtime_spawn_fails(self):
        port = _pick_free_port()
        env = os.environ.copy()
        env["STUKAY_HOSTBRIDGE_SESSION_TOKEN"] = "secret-token"
        repo_root = Path(__file__).resolve().parents[3]
        proc = subprocess.Popen(
            [
                sys.executable,
                "-m",
                "tools.hostbridge.server",
                "--host",
                "127.0.0.1",
                "--port",
                str(port),
                "--cwd",
                str(repo_root),
                "--codex-bin",
                "definitely-missing-codex",
            ],
            cwd=repo_root,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
        )
        connection = None
        try:
            _wait_for_http_server(port)
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            connection.request(
                "GET",
                "/v1/runtime/summary",
                headers={"Authorization": "Bearer secret-token"},
            )
            response = connection.getresponse()
            payload = json.loads(response.read().decode("utf-8"))
        finally:
            if connection is not None:
                connection.close()
            proc.terminate()
            proc.wait(timeout=5)
            if proc.stdout is not None:
                proc.stdout.close()
            if proc.stderr is not None:
                proc.stderr.close()

        self.assertEqual(200, response.status)
        self.assertEqual("degraded", payload["hostStatus"])
        self.assertEqual("runtime_unavailable", payload["errorCode"])
        self.assertEqual("Host Bridge runtime is unavailable.", payload["errorMessage"])

    def test_thread_routes_return_normalized_runtime_payloads(self):
        raw_thread = {
            "id": "thread-1",
            "cwd": "C:\\Users\\v.vlasov\\Desktop\\Stukay",
            "name": "Runtime thread",
            "preview": "First preview",
            "source": "appServer",
            "status": {"type": "active", "activeFlags": ["waitingOnApproval"]},
            "turns": [
                {
                    "id": "turn-1",
                    "status": "completed",
                    "items": [
                        {"type": "userMessage", "id": "user-1", "content": [{"type": "text", "text": "Hello"}]},
                        {"type": "agentMessage", "id": "assistant-1", "text": "World"},
                    ],
                },
            ],
            "createdAt": 10,
            "updatedAt": 20,
        }
        client = FakeRuntimeClient(
            [
                [raw_thread],
                {"thread": raw_thread},
                {"thread": raw_thread},
                {"turn": {"id": "turn-2", "status": "inProgress"}},
            ],
        )
        server, thread = _start_server(lambda: client, "secret-token")
        try:
            list_payload = _json_request(server, "POST", "/v1/threads/list")
            read_payload = _json_request(server, "GET", "/v1/threads/thread-1")
            resume_payload = _json_request(server, "POST", "/v1/threads/thread-1/resume")
            start_payload = _json_request(server, "POST", "/v1/threads/thread-1/turns", body={"text": "Continue"})
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

        self.assertEqual("thread-1", list_payload["data"][0]["id"])
        self.assertEqual("active", list_payload["data"][0]["status"]["type"])
        self.assertEqual("assistantMessage", read_payload["thread"]["timeline"][1]["type"])
        self.assertEqual("Runtime thread", resume_payload["thread"]["title"])
        self.assertEqual("turn-2", start_payload["turn"]["id"])

    def test_thread_events_stream_and_approval_response_are_exposed_over_http(self):
        client = FakeRuntimeClient(
            [[
                {
                    "id": "request-1",
                    "method": "item/commandExecution/requestApproval",
                    "params": {
                        "threadId": "thread-1",
                        "turnId": "turn-1",
                        "itemId": "item-1",
                        "command": "dir",
                        "cwd": "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                        "availableDecisions": ["accept", "decline", "cancel"],
                    },
                },
                None,
            ]],
        )
        server, thread = _start_server(lambda: client, "secret-token")
        try:
            connection = http.client.HTTPConnection(server.server_address[0], server.server_address[1], timeout=5)
            connection.request(
                "GET",
                "/v1/threads/thread-1/events",
                headers={"Authorization": "Bearer secret-token"},
            )
            response = connection.getresponse()
            body = response.fp.readline().decode("utf-8")
            approval_response = _json_request(
                server,
                "POST",
                "/v1/approvals/request-1/respond",
                body={"decision": "accept"},
            )
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

        self.assertEqual(200, response.status)
        self.assertIn("item/commandExecution/requestApproval", body)
        self.assertEqual({}, approval_response)
        self.assertEqual([("request-1", {"decision": "accept"})], client.recorded_approval_responses)


def _start_server(runtime_client_factory, token: str):
    service = HostBridgeService(session_token=token, runtime_client_factory=runtime_client_factory)
    server = HostBridgeHttpServer(("127.0.0.1", 0), service)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, thread


def _pick_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def _wait_for_http_server(port: int) -> None:
    deadline = time.monotonic() + 5
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=1)
            connection.connect()
            connection.close()
            return
        except Exception as error:  # noqa: BLE001
            last_error = error
            time.sleep(0.1)
    raise RuntimeError(f"Timed out waiting for host bridge server on port {port}") from last_error


def _json_request(server, method: str, path: str, body: dict | None = None):
    connection = http.client.HTTPConnection(server.server_address[0], server.server_address[1], timeout=5)
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Authorization": "Bearer secret-token"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    try:
        connection.request(method, path, body=payload, headers=headers)
        response = connection.getresponse()
        return json.loads(response.read().decode("utf-8"))
    finally:
        connection.close()


if __name__ == "__main__":
    unittest.main()
