from __future__ import annotations

import json
import os
import queue
import shutil
import subprocess
import threading
import uuid
from dataclasses import dataclass
from typing import Any, Callable

JsonDict = dict[str, Any]

_THREAD_BOUND_SERVER_REQUESTS = {
    "item/commandExecution/requestApproval",
    "item/fileChange/requestApproval",
    "item/tool/requestUserInput",
}


class RuntimeClientError(RuntimeError):
    def __init__(self, code: str, public_message: str, detail: str | None = None) -> None:
        super().__init__(public_message)
        self.code = code
        self.public_message = public_message
        self.detail = detail


@dataclass
class RuntimeEventSubscription:
    _queue: queue.Queue[JsonDict | None]
    _cleanup: Callable[[], None]

    def next_event(self, timeout_s: float | None = None) -> JsonDict | None:
        try:
            return self._queue.get(timeout=timeout_s)
        except queue.Empty:
            return {}

    def close(self) -> None:
        self._cleanup()


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
        self._stderr_lines: list[str] = []
        self._stdout_thread: threading.Thread | None = None
        self._stderr_thread: threading.Thread | None = None
        self._initialized = False
        self._write_lock = threading.Lock()
        self._lifecycle_lock = threading.RLock()
        self._response_waiters: dict[str, queue.Queue[JsonDict | None]] = {}
        self._pending_server_requests: dict[str, JsonDict] = {}
        self._subscriptions: dict[str, list[queue.Queue[JsonDict | None]]] = {}

    def close(self) -> None:
        with self._lifecycle_lock:
            proc = self._proc
            self._proc = None
            self._initialized = False
            pending_waiters = list(self._response_waiters.values())
            self._response_waiters.clear()
            pending_subscriptions = list(self._subscriptions.values())
            self._subscriptions.clear()
            self._pending_server_requests.clear()
        for waiter in pending_waiters:
            waiter.put(None)
        for subscription_group in pending_subscriptions:
            for subscriber in subscription_group:
                subscriber.put(None)
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
        if self._stdout_thread and self._stdout_thread.is_alive():
            self._stdout_thread.join(timeout=0.5)
        if self._stderr_thread and self._stderr_thread.is_alive():
            self._stderr_thread.join(timeout=0.5)

    def fetch_app_list(self) -> list[JsonDict]:
        apps: list[JsonDict] = []
        cursor: str | None = None
        while True:
            result = self.request(
                "app/list",
                {"cursor": cursor, "limit": 50, "forceRefetch": False},
            )
            data = result.get("data")
            if not isinstance(data, list):
                raise RuntimeClientError(
                    code="runtime_protocol_error",
                    public_message="Host Bridge received an invalid app/list response.",
                    detail=self._failure_detail("app/list response is missing data array"),
                )
            apps.extend(item for item in data if isinstance(item, dict))
            next_cursor = result.get("nextCursor")
            if next_cursor is None:
                return apps
            if not isinstance(next_cursor, str) or not next_cursor:
                raise RuntimeClientError(
                    code="runtime_protocol_error",
                    public_message="Host Bridge received an invalid pagination cursor from app/list.",
                    detail=self._failure_detail("app/list nextCursor must be string or null"),
                )
            cursor = next_cursor

    def list_threads(
        self,
        *,
        source_kinds: list[str],
        archived: bool,
        sort_key: str,
        sort_direction: str,
        limit: int = 50,
    ) -> list[JsonDict]:
        threads: list[JsonDict] = []
        cursor: str | None = None
        while True:
            result = self.request(
                "thread/list",
                {
                    "cursor": cursor,
                    "limit": limit,
                    "archived": archived,
                    "sortKey": sort_key,
                    "sortDirection": sort_direction,
                    "sourceKinds": source_kinds,
                },
            )
            data = result.get("data")
            if not isinstance(data, list):
                raise RuntimeClientError(
                    code="runtime_protocol_error",
                    public_message="Host Bridge received an invalid thread/list response.",
                    detail=self._failure_detail("thread/list response is missing data array"),
                )
            threads.extend(item for item in data if isinstance(item, dict))
            next_cursor = result.get("nextCursor")
            if next_cursor is None:
                return threads
            if not isinstance(next_cursor, str) or not next_cursor:
                raise RuntimeClientError(
                    code="runtime_protocol_error",
                    public_message="Host Bridge received an invalid pagination cursor from thread/list.",
                    detail=self._failure_detail("thread/list nextCursor must be string or null"),
                )
            cursor = next_cursor

    def read_thread(self, thread_id: str, *, include_turns: bool) -> JsonDict:
        return self.request(
            "thread/read",
            {"threadId": thread_id, "includeTurns": include_turns},
        )

    def resume_thread(self, thread_id: str) -> JsonDict:
        return self.request(
            "thread/resume",
            {"threadId": thread_id},
        )

    def start_turn(self, thread_id: str, text: str) -> JsonDict:
        return self.request(
            "turn/start",
            {
                "threadId": thread_id,
                "input": [{"type": "text", "text": text}],
            },
        )

    def interrupt_turn(self, thread_id: str, turn_id: str) -> JsonDict:
        return self.request(
            "turn/interrupt",
            {"threadId": thread_id, "turnId": turn_id},
        )

    def subscribe_thread(self, thread_id: str) -> RuntimeEventSubscription:
        self._initialize_if_needed()
        with self._lifecycle_lock:
            event_queue: queue.Queue[JsonDict | None] = queue.Queue()
            self._subscriptions.setdefault(thread_id, []).append(event_queue)

        def cleanup() -> None:
            with self._lifecycle_lock:
                subscribers = self._subscriptions.get(thread_id)
                if subscribers is None:
                    return
                if event_queue in subscribers:
                    subscribers.remove(event_queue)
                if not subscribers:
                    self._subscriptions.pop(thread_id, None)
            event_queue.put(None)

        return RuntimeEventSubscription(_queue=event_queue, _cleanup=cleanup)

    def respond_server_request(self, request_id: str, result: JsonDict) -> None:
        with self._lifecycle_lock:
            self._ensure_started_locked()
            if request_id not in self._pending_server_requests:
                raise RuntimeClientError(
                    code="runtime_request_missing",
                    public_message="Host Bridge no longer has the requested approval prompt in memory.",
                )
        self._write_message({"id": request_id, "result": result})

    def request(self, method: str, params: JsonDict | None = None) -> JsonDict:
        with self._lifecycle_lock:
            self._ensure_started_locked()
            should_initialize = not self._initialized and method != "initialize"
        if should_initialize:
            self._initialize_if_needed()
        return self._request_without_bootstrap(method, params or {})

    def _initialize_if_needed(self) -> None:
        with self._lifecycle_lock:
            self._ensure_started_locked()
            if self._initialized:
                return
        self._initialize_locked()

    def _request_without_bootstrap(self, method: str, params: JsonDict) -> JsonDict:
        request_id = str(uuid.uuid4())
        waiter: queue.Queue[JsonDict | None] = queue.Queue(maxsize=1)
        with self._lifecycle_lock:
            self._response_waiters[request_id] = waiter
        try:
            self._write_message(
                {"id": request_id, "method": method, "params": params},
            )
            try:
                message = waiter.get(timeout=self._request_timeout_s)
            except queue.Empty as error:
                raise RuntimeClientError(
                    code="runtime_timeout",
                    public_message="Host Bridge runtime timed out while waiting for app-server response.",
                    detail=self._failure_detail(f"Timed out waiting for response to {method}"),
                ) from error
            if message is None:
                raise RuntimeClientError(
                    code="runtime_unavailable",
                    public_message="Host Bridge runtime closed the connection unexpectedly.",
                    detail=self._failure_detail("Transport closed before response"),
                )
            if "error" in message:
                error_payload = message.get("error")
                if isinstance(error_payload, dict):
                    error_message = error_payload.get("message")
                    if isinstance(error_message, str) and error_message.strip():
                        public_message = error_message
                    else:
                        public_message = f"Local app-server request failed: {method}."
                else:
                    public_message = f"Local app-server request failed: {method}."
                raise RuntimeClientError(
                    code="runtime_request_failed",
                    public_message=public_message,
                    detail=self._failure_detail(f"{method} returned JSON-RPC error: {error_payload!r}"),
                )
            result = message.get("result")
            if not isinstance(result, dict):
                raise RuntimeClientError(
                    code="runtime_protocol_error",
                    public_message="Host Bridge received an invalid app-server response envelope.",
                    detail=self._failure_detail(f"{method} result envelope is not an object"),
                )
            return result
        finally:
            with self._lifecycle_lock:
                self._response_waiters.pop(request_id, None)

    def _ensure_started_locked(self) -> None:
        if self._proc is not None and self._proc.poll() is None:
            return
        self.close()
        self._proc = self._spawn_process()
        self._stderr_lines = []
        self._stdout_thread = threading.Thread(target=self._read_stdout, daemon=True)
        self._stderr_thread = threading.Thread(target=self._read_stderr, daemon=True)
        self._stdout_thread.start()
        self._stderr_thread.start()

    def _initialize_locked(self) -> None:
        if self._initialized:
            return
        self._request_without_bootstrap(
            "initialize",
            {
                "clientInfo": {
                    "name": self._client_name,
                    "title": self._client_title,
                    "version": self._client_version,
                },
                "capabilities": {"experimentalApi": True},
            },
        )
        self._write_message({"method": "initialized", "params": {}})
        self._initialized = True

    def _spawn_process(self) -> subprocess.Popen[str]:
        if self._process_factory is not None:
            return self._process_factory()
        codex_command = resolve_codex_command(self._codex_bin)
        return subprocess.Popen(
            [codex_command, "app-server", "--listen", "stdio://"],
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
        proc = self._proc
        if proc is None or proc.stdout is None:
            return
        try:
            for line in proc.stdout:
                raw = line.rstrip("\n")
                if not raw.strip():
                    continue
                try:
                    message = json.loads(raw)
                except json.JSONDecodeError:
                    self._fail_all(RuntimeClientError(
                        code="runtime_protocol_error",
                        public_message="Host Bridge received malformed JSON from local app-server.",
                        detail=self._failure_detail(f"Malformed JSON-RPC line: {raw!r}"),
                    ))
                    return
                if not isinstance(message, dict):
                    self._fail_all(RuntimeClientError(
                        code="runtime_protocol_error",
                        public_message="Host Bridge received invalid JSON-RPC payload from local app-server.",
                        detail=self._failure_detail("JSON-RPC line must decode to an object"),
                    ))
                    return
                self._dispatch_message(message)
        finally:
            self._fail_all(
                RuntimeClientError(
                    code="runtime_unavailable",
                    public_message="Host Bridge runtime closed the connection unexpectedly.",
                    detail=self._failure_detail("app-server stdout closed"),
                ),
            )

    def _read_stderr(self) -> None:
        proc = self._proc
        if proc is None or proc.stderr is None:
            return
        for line in proc.stderr:
            self._stderr_lines.append(line.rstrip("\n"))
            if len(self._stderr_lines) > 100:
                self._stderr_lines = self._stderr_lines[-100:]

    def _dispatch_message(self, message: JsonDict) -> None:
        if "id" in message and "method" not in message:
            self._dispatch_response(message)
            return
        if "method" in message and "id" in message:
            self._dispatch_server_request(message)
            return
        if "method" in message:
            self._dispatch_notification(message)

    def _dispatch_response(self, message: JsonDict) -> None:
        request_id = str(message.get("id"))
        with self._lifecycle_lock:
            waiter = self._response_waiters.get(request_id)
        if waiter is not None:
            waiter.put(message)

    def _dispatch_server_request(self, message: JsonDict) -> None:
        request_id = str(message.get("id"))
        method = message.get("method")
        params = message.get("params")
        if not isinstance(method, str):
            return
        if not isinstance(params, dict):
            params = {}
        thread_id = self._extract_thread_id(method, params)
        if method in _THREAD_BOUND_SERVER_REQUESTS and thread_id is not None:
            with self._lifecycle_lock:
                self._pending_server_requests[request_id] = {
                    "id": request_id,
                    "method": method,
                    "params": params,
                }
            self._broadcast_thread_message(thread_id, {"id": request_id, "method": method, "params": params})
            return
        # Keep the stdio session responsive for server requests outside the bounded slice.
        self._write_message({"id": message["id"], "result": {}})

    def _dispatch_notification(self, message: JsonDict) -> None:
        method = message.get("method")
        params = message.get("params")
        if not isinstance(method, str):
            return
        if not isinstance(params, dict):
            params = {}
        thread_id = self._extract_thread_id(method, params)
        if method == "serverRequest/resolved":
            request_id = params.get("requestId")
            if request_id is not None:
                with self._lifecycle_lock:
                    self._pending_server_requests.pop(str(request_id), None)
        if thread_id is not None:
            self._broadcast_thread_message(thread_id, {"method": method, "params": params})

    def _broadcast_thread_message(self, thread_id: str, message: JsonDict) -> None:
        with self._lifecycle_lock:
            subscribers = list(self._subscriptions.get(thread_id, []))
        for subscriber in subscribers:
            subscriber.put(message)

    def _fail_all(self, error: RuntimeClientError) -> None:
        with self._lifecycle_lock:
            pending_waiters = list(self._response_waiters.values())
            self._response_waiters.clear()
            pending_subscriptions = list(self._subscriptions.values())
            self._subscriptions.clear()
            self._pending_server_requests.clear()
            self._initialized = False
        for waiter in pending_waiters:
            waiter.put(None)
        for group in pending_subscriptions:
            for subscriber in group:
                subscriber.put(None)

    def _extract_thread_id(self, method: str, params: JsonDict) -> str | None:
        direct_thread_id = params.get("threadId")
        if isinstance(direct_thread_id, str) and direct_thread_id:
            return direct_thread_id
        if method == "thread/started":
            thread = params.get("thread")
            if isinstance(thread, dict):
                thread_id = thread.get("id")
                if isinstance(thread_id, str) and thread_id:
                    return thread_id
        return None

    def _write_message(self, payload: JsonDict) -> None:
        proc = self._proc
        if proc is None or proc.stdin is None:
            raise RuntimeClientError(
                code="runtime_unavailable",
                public_message="Host Bridge runtime is unavailable.",
            )
        with self._write_lock:
            proc.stdin.write(json.dumps(payload) + "\n")
            proc.stdin.flush()

    def _failure_detail(self, prefix: str) -> str:
        stderr_tail = "\n".join(self._stderr_lines[-10:])
        if not stderr_tail:
            return prefix
        return f"{prefix}. stderr: {stderr_tail}"


def resolve_codex_command(
    codex_bin: str,
    *,
    os_name: str | None = None,
    which: Callable[[str], str | None] = shutil.which,
) -> str:
    resolved_os_name = os_name or os.name
    if resolved_os_name != "nt":
        return codex_bin

    lowered = codex_bin.lower()
    has_explicit_suffix = lowered.endswith((".cmd", ".bat", ".exe", ".ps1"))
    has_path_separator = any(separator in codex_bin for separator in ("\\", "/"))
    if has_explicit_suffix or has_path_separator:
        return codex_bin

    for candidate in (f"{codex_bin}.cmd", f"{codex_bin}.exe", codex_bin):
        resolved = which(candidate)
        if resolved is not None:
            return resolved
    return codex_bin
