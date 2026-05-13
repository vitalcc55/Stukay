from __future__ import annotations

import argparse
import http.server
import ipaddress
import json
import re
import time
import threading
from typing import Callable
from urllib.parse import parse_qs, unquote, urlparse

from tools.hostbridge.auth import extract_bearer_token, is_authorized, resolve_session_token
from tools.hostbridge.models import (
    RuntimeSummary,
    normalize_stream_message,
    normalize_thread_list,
    normalize_thread_response,
    normalize_thread_turns_page,
    normalize_turn_response,
)
from tools.hostbridge.runtime_client import CodexRuntimeClient, RuntimeClientError

THREAD_LIST_SOURCE_KINDS = ["cli", "vscode", "appServer"]
_THREAD_PATH_RE = re.compile(r"^/v1/threads/(?P<thread_id>[^/]+)$")
_THREAD_HISTORY_RE = re.compile(r"^/v1/threads/(?P<thread_id>[^/]+)/history$")
_THREAD_RESUME_RE = re.compile(r"^/v1/threads/(?P<thread_id>[^/]+)/resume$")
_THREAD_TURNS_RE = re.compile(r"^/v1/threads/(?P<thread_id>[^/]+)/turns$")
_THREAD_INTERRUPT_RE = re.compile(r"^/v1/threads/(?P<thread_id>[^/]+)/turns/(?P<turn_id>[^/]+)/interrupt$")
_THREAD_EVENTS_RE = re.compile(r"^/v1/threads/(?P<thread_id>[^/]+)/events$")
_APPROVAL_RESPOND_RE = re.compile(r"^/v1/approvals/(?P<request_id>[^/]+)/respond$")


class HostBridgeService:
    def __init__(
        self,
        *,
        session_token: str,
        runtime_client_factory: Callable[[], object] | None = None,
    ) -> None:
        self._session_token = session_token
        self._runtime_client_factory = runtime_client_factory or (lambda: CodexRuntimeClient())
        self._runtime_client = None
        self._last_summary = RuntimeSummary()
        self._retry_attempt = 0
        self._lock = threading.Lock()

    def is_authorized(self, provided_token: str | None) -> bool:
        return is_authorized(self._session_token, provided_token)

    def get_runtime_summary(self) -> RuntimeSummary:
        with self._lock:
            started_at = time.perf_counter()
            try:
                client = self._get_runtime_client()
                apps = client.fetch_app_list()
                elapsed_ms = int((time.perf_counter() - started_at) * 1000)
                summary = RuntimeSummary.ready(
                    app_list_count=len(apps),
                    last_round_trip_ms=elapsed_ms,
                )
                self._retry_attempt = 0
                self._last_summary = summary
                return summary
            except Exception as error:
                self._retry_attempt += 1
                if self._runtime_client is not None:
                    try:
                        self._runtime_client.close()
                    finally:
                        self._runtime_client = None
                error_code, error_message = _sanitize_runtime_error(error)
                degraded = self._last_summary.degraded(
                    retry_attempt=self._retry_attempt,
                    degraded_reason="probe_failed",
                    error_code=error_code,
                    error_message=error_message,
                )
                self._last_summary = degraded
                return degraded

    def list_threads(self) -> dict[str, object]:
        with self._lock:
            client = self._get_runtime_client()
            threads = client.list_threads(
                source_kinds=THREAD_LIST_SOURCE_KINDS,
                archived=False,
                sort_key="updated_at",
                sort_direction="desc",
            )
        return normalize_thread_list(threads)

    def read_thread(self, thread_id: str) -> dict[str, object]:
        with self._lock:
            client = self._get_runtime_client()
            response = client.read_thread(thread_id, include_turns=False)
            pending_requests = client.pending_server_requests_for_thread(thread_id)
        thread = response.get("thread")
        if not isinstance(thread, dict):
            raise RuntimeClientError(
                code="runtime_protocol_error",
                public_message="Host Bridge received an invalid thread/read response.",
            )
        return normalize_thread_response(thread, pending_server_requests=pending_requests)

    def resume_thread(self, thread_id: str) -> dict[str, object]:
        with self._lock:
            client = self._get_runtime_client()
            response = client.resume_thread(thread_id, exclude_turns=True)
            pending_requests = client.pending_server_requests_for_thread(thread_id)
        thread = response.get("thread")
        if not isinstance(thread, dict):
            raise RuntimeClientError(
                code="runtime_protocol_error",
                public_message="Host Bridge received an invalid thread/resume response.",
            )
        return normalize_thread_response(thread, pending_server_requests=pending_requests)

    def list_thread_history_page(
        self,
        thread_id: str,
        *,
        cursor: str | None,
        limit: int,
        sort_direction: str,
    ) -> dict[str, object]:
        with self._lock:
            client = self._get_runtime_client()
            response = client.list_thread_turns_page(
                thread_id,
                cursor=cursor,
                limit=limit,
                sort_direction=sort_direction,
                items_view="full",
            )
        return normalize_thread_turns_page(thread_id=thread_id, result=response)

    def start_turn(self, thread_id: str, text: str) -> dict[str, object]:
        with self._lock:
            client = self._get_runtime_client()
            response = client.start_turn(thread_id, text)
        turn = response.get("turn")
        if not isinstance(turn, dict):
            raise RuntimeClientError(
                code="runtime_protocol_error",
                public_message="Host Bridge received an invalid turn/start response.",
            )
        return normalize_turn_response(turn)

    def interrupt_turn(self, thread_id: str, turn_id: str) -> dict[str, object]:
        with self._lock:
            client = self._get_runtime_client()
            client.interrupt_turn(thread_id, turn_id)
        return {}

    def respond_to_request(self, request_id: str, decision: str) -> dict[str, object]:
        with self._lock:
            client = self._get_runtime_client()
            client.respond_server_request(request_id, {"decision": decision})
        return {}

    def subscribe_thread_events(self, thread_id: str):
        with self._lock:
            client = self._get_runtime_client()
            return client.subscribe_thread(thread_id)

    def close(self) -> None:
        with self._lock:
            if self._runtime_client is not None:
                self._runtime_client.close()
                self._runtime_client = None

    def _get_runtime_client(self):
        if self._runtime_client is None:
            self._runtime_client = self._runtime_client_factory()
        return self._runtime_client


class HostBridgeHttpServer(http.server.ThreadingHTTPServer):
    def __init__(self, server_address, service: HostBridgeService):
        super().__init__(server_address, _build_handler(service))
        self.service = service

    def server_close(self) -> None:
        try:
            self.service.close()
        finally:
            super().server_close()


def _build_handler(service: HostBridgeService):
    class Handler(http.server.BaseHTTPRequestHandler):
        server_version = "StukayHostBridge/0.2"

        def do_GET(self) -> None:  # noqa: N802
            if not self._authorize():
                return
            parsed = urlparse(self.path)
            path = parsed.path
            if path == "/v1/runtime/summary":
                self._write_json(200, service.get_runtime_summary().to_wire_dict())
                return
            thread_match = _THREAD_PATH_RE.match(path)
            if thread_match is not None:
                thread_id = unquote(thread_match.group("thread_id"))
                self._run_runtime_json(service.read_thread, thread_id)
                return
            history_match = _THREAD_HISTORY_RE.match(path)
            if history_match is not None:
                thread_id = unquote(history_match.group("thread_id"))
                query = parse_qs(parsed.query)
                cursor = _single_query_value(query, "cursor")
                limit = _int_query_value(query, "limit", default=50)
                sort_direction = _single_query_value(query, "sortDirection") or "desc"
                self._run_runtime_json(
                    service.list_thread_history_page,
                    thread_id,
                    cursor=cursor,
                    limit=limit,
                    sort_direction=sort_direction,
                )
                return
            stream_match = _THREAD_EVENTS_RE.match(path)
            if stream_match is not None:
                thread_id = unquote(stream_match.group("thread_id"))
                self._stream_thread_events(thread_id)
                return
            self._write_error(404, "not_found", "Unknown route")

        def do_POST(self) -> None:  # noqa: N802
            if not self._authorize():
                return
            parsed = urlparse(self.path)
            path = parsed.path
            if path == "/v1/threads/list":
                self._run_runtime_json(service.list_threads)
                return
            resume_match = _THREAD_RESUME_RE.match(path)
            if resume_match is not None:
                thread_id = unquote(resume_match.group("thread_id"))
                self._run_runtime_json(service.resume_thread, thread_id)
                return
            turns_match = _THREAD_TURNS_RE.match(path)
            if turns_match is not None:
                thread_id = unquote(turns_match.group("thread_id"))
                body = self._read_json_body()
                text = body.get("text")
                if not isinstance(text, str) or not text.strip():
                    self._write_error(400, "bad_request", "Turn text is required.")
                    return
                self._run_runtime_json(service.start_turn, thread_id, text)
                return
            interrupt_match = _THREAD_INTERRUPT_RE.match(path)
            if interrupt_match is not None:
                thread_id = unquote(interrupt_match.group("thread_id"))
                turn_id = unquote(interrupt_match.group("turn_id"))
                self._run_runtime_json(service.interrupt_turn, thread_id, turn_id)
                return
            approval_match = _APPROVAL_RESPOND_RE.match(path)
            if approval_match is not None:
                request_id = unquote(approval_match.group("request_id"))
                body = self._read_json_body()
                decision = body.get("decision")
                if not isinstance(decision, str) or not decision.strip():
                    self._write_error(400, "bad_request", "Approval decision is required.")
                    return
                self._run_runtime_json(service.respond_to_request, request_id, decision)
                return
            self._write_error(404, "not_found", "Unknown route")

        def log_message(self, format: str, *args) -> None:  # noqa: A003
            return None

        def _authorize(self) -> bool:
            provided_token = extract_bearer_token(self.headers)
            if service.is_authorized(provided_token):
                return True
            self._write_error(401, "unauthorized", "Unauthorized")
            return False

        def _read_json_body(self) -> dict[str, object]:
            content_length = self.headers.get("Content-Length")
            if content_length is None:
                return {}
            try:
                body_size = int(content_length)
            except ValueError:
                return {}
            raw_body = self.rfile.read(body_size)
            if not raw_body:
                return {}
            try:
                payload = json.loads(raw_body.decode("utf-8"))
            except json.JSONDecodeError:
                self._write_error(400, "bad_request", "Invalid JSON body.")
                return {}
            if not isinstance(payload, dict):
                self._write_error(400, "bad_request", "JSON body must be an object.")
                return {}
            return payload

        def _run_runtime_json(self, callback: Callable, *args, **kwargs) -> None:
            try:
                payload = callback(*args, **kwargs)
            except RuntimeClientError as error:
                self._write_runtime_error(error)
                return
            except Exception as error:  # noqa: BLE001
                runtime_error = RuntimeClientError(
                    code="runtime_unavailable",
                    public_message="Host Bridge runtime is unavailable.",
                    detail=str(error),
                )
                self._write_runtime_error(runtime_error)
                return
            self._write_json(200, payload)

        def _write_runtime_error(self, error: RuntimeClientError) -> None:
            status_code = 503 if error.code in {"runtime_unavailable", "runtime_timeout"} else 500
            if error.code == "runtime_request_missing":
                status_code = 409
            self._write_error(status_code, error.code, error.public_message)

        def _write_error(self, status_code: int, error_code: str, error_message: str) -> None:
            self._write_json(
                status_code,
                {"errorCode": error_code, "errorMessage": error_message},
            )

        def _write_json(self, status_code: int, payload: dict[str, object | None]) -> None:
            body = json.dumps(payload).encode("utf-8")
            self.send_response(status_code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _stream_thread_events(self, thread_id: str) -> None:
            subscription = service.subscribe_thread_events(thread_id)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            try:
                while True:
                    event = subscription.next_event(timeout_s=15.0)
                    if event is None:
                        return
                    if event == {}:
                        self.wfile.write(b": keep-alive\n\n")
                        self.wfile.flush()
                        continue
                    normalized = normalize_stream_message(event)
                    if normalized is None:
                        continue
                    payload = json.dumps(normalized).encode("utf-8")
                    self.wfile.write(b"data: ")
                    self.wfile.write(payload)
                    self.wfile.write(b"\n\n")
                    self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                return
            finally:
                subscription.close()

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8421)
    parser.add_argument("--session-token-env", default="STUKAY_HOSTBRIDGE_SESSION_TOKEN")
    parser.add_argument("--session-token-file", default=None)
    parser.add_argument("--cwd", default=None)
    parser.add_argument("--codex-bin", default="codex")
    args = parser.parse_args()
    session_token = resolve_session_token(
        token_env_var=args.session_token_env,
        token_file=args.session_token_file,
    )
    bind_host = validate_bind_host(args.host)

    def runtime_client_factory():
        return CodexRuntimeClient(
            cwd=args.cwd,
            codex_bin=args.codex_bin,
        )

    service = HostBridgeService(
        session_token=session_token,
        runtime_client_factory=runtime_client_factory,
    )
    server = HostBridgeHttpServer((bind_host, args.port), service)
    try:
        server.serve_forever()
    finally:
        service.close()


def validate_bind_host(host: str) -> str:
    normalized = host.strip().lower()
    if normalized == "localhost":
        return "localhost"

    try:
        address = ipaddress.ip_address(normalized)
    except ValueError as error:
        raise ValueError(
            "Host Bridge MVP принимает только loopback или private LAN bind host.",
        ) from error

    if address.version != 4:
        raise ValueError("Host Bridge MVP пока принимает только IPv4 bind host.")
    if _is_allowed_bind_address(address):
        return normalized
    raise ValueError("Host Bridge MVP принимает только loopback или private LAN bind host.")


def _is_allowed_bind_address(address: ipaddress.IPv4Address) -> bool:
    if address.is_loopback:
        return True

    first_octet = address.packed[0]
    second_octet = address.packed[1]
    return first_octet == 10 or (
        first_octet == 100 and second_octet in range(64, 128)
    ) or (
        first_octet == 172 and second_octet in range(16, 32)
    ) or (
        first_octet == 192 and second_octet == 168
    )


def _sanitize_runtime_error(error: Exception) -> tuple[str, str]:
    if isinstance(error, RuntimeClientError):
        return error.code, error.public_message
    return "runtime_unavailable", "Host Bridge runtime is unavailable."


def _single_query_value(query: dict[str, list[str]], key: str) -> str | None:
    values = query.get(key)
    if not values:
        return None
    value = values[0].strip()
    return value or None


def _int_query_value(query: dict[str, list[str]], key: str, *, default: int) -> int:
    value = _single_query_value(query, key)
    if value is None:
        return default
    try:
        parsed = int(value)
    except ValueError:
        return default
    return parsed if parsed > 0 else default


if __name__ == "__main__":
    main()
