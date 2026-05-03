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

from tools.hostbridge.server import HostBridgeHttpServer, HostBridgeService


class FakeRuntimeClient:
    def __init__(self, responses):
        self._responses = list(responses)
        self.calls = 0

    def fetch_app_list(self):
        self.calls += 1
        response = self._responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    def close(self):
        return None


class ServerTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
