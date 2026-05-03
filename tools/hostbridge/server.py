from __future__ import annotations

import argparse
import http.server
import json
import time
import threading
from typing import Callable

from tools.hostbridge.auth import extract_bearer_token, is_authorized, resolve_session_token
from tools.hostbridge.models import RuntimeSummary
from tools.hostbridge.runtime_client import CodexRuntimeClient, RuntimeClientError


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
        def do_GET(self) -> None:  # noqa: N802
            provided_token = extract_bearer_token(self.headers)
            if not service.is_authorized(provided_token):
                self._write_json(
                    401,
                    {
                        "errorCode": "unauthorized",
                        "errorMessage": "Unauthorized",
                    },
                )
                return

            if self.path == "/v1/runtime/summary":
                self._write_json(200, service.get_runtime_summary().to_wire_dict())
                return

            self._write_json(
                404,
                {
                    "errorCode": "not_found",
                    "errorMessage": "Unknown route",
                },
            )

        def log_message(self, format: str, *args) -> None:  # noqa: A003
            return None

        def _write_json(self, status_code: int, payload: dict[str, object | None]) -> None:
            body = json.dumps(payload).encode("utf-8")
            self.send_response(status_code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

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

    def runtime_client_factory():
        return CodexRuntimeClient(
            cwd=args.cwd,
            codex_bin=args.codex_bin,
        )

    service = HostBridgeService(
        session_token=session_token,
        runtime_client_factory=runtime_client_factory,
    )
    server = HostBridgeHttpServer((args.host, args.port), service)
    try:
        server.serve_forever()
    finally:
        service.close()


if __name__ == "__main__":
    main()


def _sanitize_runtime_error(error: Exception) -> tuple[str, str]:
    if isinstance(error, RuntimeClientError):
        return error.code, error.public_message
    return "runtime_unavailable", "Host Bridge runtime is unavailable."
