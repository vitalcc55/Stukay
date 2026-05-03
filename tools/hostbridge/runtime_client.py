from __future__ import annotations

import json
import queue
import subprocess
import threading
import uuid
from typing import Callable


class RuntimeClientError(RuntimeError):
    def __init__(self, code: str, public_message: str, detail: str | None = None) -> None:
        super().__init__(public_message)
        self.code = code
        self.public_message = public_message
        self.detail = detail


class CodexRuntimeClient:
    def __init__(
        self,
        *,
        process_factory: Callable[[], subprocess.Popen[str]] | None = None,
        codex_bin: str = "codex",
        cwd: str | None = None,
        env: dict[str, str] | None = None,
        request_timeout_s: float = 10.0,
        client_name: str = "stukay-hostbridge",
        client_title: str = "Stukay Host Bridge",
        client_version: str = "0.1.0",
    ) -> None:
        self._process_factory = process_factory
        self._codex_bin = codex_bin
        self._cwd = cwd
        self._env = env
        self._request_timeout_s = request_timeout_s
        self._client_name = client_name
        self._client_title = client_title
        self._client_version = client_version
        self._proc: subprocess.Popen[str] | None = None
        self._stdout_queue: queue.Queue[str | None] = queue.Queue()
        self._stderr_lines: list[str] = []
        self._stdout_thread: threading.Thread | None = None
        self._stderr_thread: threading.Thread | None = None
        self._initialized = False
        self._io_lock = threading.Lock()

    def close(self) -> None:
        proc = self._proc
        self._proc = None
        self._initialized = False
        if proc is None:
            return
        try:
            if proc.stdin:
                proc.stdin.close()
        except OSError:
            pass
        try:
            proc.terminate()
            proc.wait(timeout=2)
        except Exception:
            proc.kill()
        try:
            if proc.stdout:
                proc.stdout.close()
        except OSError:
            pass
        try:
            if proc.stderr:
                proc.stderr.close()
        except OSError:
            pass
        self._stdout_queue.put(None)
        if self._stdout_thread and self._stdout_thread.is_alive():
            self._stdout_thread.join(timeout=0.5)
        if self._stderr_thread and self._stderr_thread.is_alive():
            self._stderr_thread.join(timeout=0.5)

    def fetch_app_list(self) -> list[dict[str, object]]:
        with self._io_lock:
            self._ensure_started()
            self._ensure_initialized()
            result = self._request("app/list", {"cursor": None, "limit": 50, "forceRefetch": False})
            data = result.get("data")
            if not isinstance(data, list):
                raise RuntimeError("Invalid app/list response: missing data array")
            return data

    def _ensure_started(self) -> None:
        if self._proc is not None and self._proc.poll() is None:
            return
        self.close()
        self._proc = self._spawn_process()
        self._stdout_queue = queue.Queue()
        self._stderr_lines = []
        self._stdout_thread = threading.Thread(target=self._read_stdout, daemon=True)
        self._stderr_thread = threading.Thread(target=self._read_stderr, daemon=True)
        self._stdout_thread.start()
        self._stderr_thread.start()

    def _ensure_initialized(self) -> None:
        if self._initialized:
            return
        self._request(
            "initialize",
            {
                "clientInfo": {
                    "name": self._client_name,
                    "title": self._client_title,
                    "version": self._client_version,
                },
                "capabilities": {
                    "experimentalApi": True,
                },
            },
        )
        self._write_message({"method": "initialized", "params": {}})
        self._initialized = True

    def _spawn_process(self) -> subprocess.Popen[str]:
        if self._process_factory is not None:
            return self._process_factory()
        return subprocess.Popen(
            [self._codex_bin, "app-server", "--listen", "stdio://"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            cwd=self._cwd,
            env=self._env,
            bufsize=1,
        )

    def _read_stdout(self) -> None:
        assert self._proc is not None
        stdout = self._proc.stdout
        if stdout is None:
            return
        for line in stdout:
            self._stdout_queue.put(line.rstrip("\n"))
        self._stdout_queue.put(None)

    def _read_stderr(self) -> None:
        assert self._proc is not None
        stderr = self._proc.stderr
        if stderr is None:
            return
        for line in stderr:
            self._stderr_lines.append(line.rstrip("\n"))
            if len(self._stderr_lines) > 100:
                self._stderr_lines = self._stderr_lines[-100:]

    def _request(self, method: str, params: dict[str, object]) -> dict[str, object]:
        request_id = str(uuid.uuid4())
        self._write_message({"id": request_id, "method": method, "params": params})
        while True:
            message = self._read_message()
            if message is None:
                raise RuntimeClientError(
                    code="runtime_unavailable",
                    public_message="Host Bridge runtime closed the connection unexpectedly.",
                    detail=self._failure_detail("Transport closed before response"),
                )
            if "method" in message and "id" in message:
                self._write_message({"id": message["id"], "result": {}})
                continue
            if "method" in message:
                continue
            if message.get("id") != request_id:
                continue
            if "error" in message:
                raise RuntimeClientError(
                    code="runtime_request_failed",
                    public_message="Host Bridge request to local app-server failed.",
                    detail=self._failure_detail(str(message["error"])),
                )
            result = message.get("result")
            if not isinstance(result, dict):
                raise RuntimeClientError(
                    code="runtime_protocol_error",
                    public_message="Host Bridge received an invalid app-server response envelope.",
                    detail=self._failure_detail("Invalid JSON-RPC result envelope"),
                )
            return result

    def _read_message(self) -> dict[str, object] | None:
        try:
            line = self._stdout_queue.get(timeout=self._request_timeout_s)
        except queue.Empty as error:
            raise RuntimeClientError(
                code="runtime_timeout",
                public_message="Host Bridge runtime timed out while waiting for app-server response.",
                detail=self._failure_detail("Timed out waiting for app-server response"),
            ) from error
        if line is None:
            return None
        try:
            payload = json.loads(line)
        except json.JSONDecodeError as error:
            raise RuntimeClientError(
                code="runtime_protocol_error",
                public_message="Host Bridge received malformed JSON from local app-server.",
                detail=self._failure_detail(f"Malformed JSON-RPC line: {line!r}"),
            ) from error
        if not isinstance(payload, dict):
            raise RuntimeClientError(
                code="runtime_protocol_error",
                public_message="Host Bridge received invalid JSON-RPC payload from local app-server.",
                detail=self._failure_detail("Invalid JSON-RPC payload"),
            )
        return payload

    def _write_message(self, payload: dict[str, object]) -> None:
        if self._proc is None or self._proc.stdin is None:
            raise RuntimeError("app-server is not running")
        self._proc.stdin.write(json.dumps(payload) + "\n")
        self._proc.stdin.flush()

    def _failure_detail(self, prefix: str) -> str:
        stderr_tail = "\n".join(self._stderr_lines[-10:])
        if not stderr_tail:
            return prefix
        return f"{prefix}. stderr: {stderr_tail}"
