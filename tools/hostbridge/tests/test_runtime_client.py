from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.hostbridge.runtime_client import CodexRuntimeClient, resolve_codex_command


class RuntimeClientTest(unittest.TestCase):
    def test_fetch_app_list_initializes_once_and_reuses_process(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            counter_path = tmp_path / "counter.json"
            script_path = _write_fake_app_server(tmp_path, counter_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                first = client.fetch_app_list()
                second = client.fetch_app_list()
            finally:
                client.close()

            self.assertEqual(2, len(first))
            self.assertEqual(2, len(second))

            counters = json.loads(counter_path.read_text(encoding="utf-8"))
            self.assertEqual(1, counters["initialize"])
            self.assertEqual(2, counters["app_list"])

    def test_fetch_app_list_drains_all_pages_before_returning(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            script_path = _write_paginated_app_server(tmp_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                apps = client.fetch_app_list()
            finally:
                client.close()

            self.assertEqual(["a", "b", "c"], [app["id"] for app in apps])

    def test_fetch_app_list_uses_large_page_size_for_runtime_probe(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            params_path = tmp_path / "params.json"
            script_path = _write_app_list_probe_app_server(tmp_path, params_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                apps = client.fetch_app_list()
            finally:
                client.close()

            self.assertEqual(["a"], [app["id"] for app in apps])
            recorded_params = json.loads(params_path.read_text(encoding="utf-8"))
            self.assertEqual({"cursor": None, "limit": 500, "forceRefetch": False}, recorded_params)

    def test_fetch_app_list_raises_on_transport_error(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            script_path = _write_broken_app_server(tmp_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                with self.assertRaises(RuntimeError):
                    client.fetch_app_list()
            finally:
                client.close()

    def test_read_thread_can_skip_turns_for_summary_hydration(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            params_path = tmp_path / "params.json"
            script_path = _write_read_thread_probe_app_server(tmp_path, params_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                payload = client.read_thread("thread-1", include_turns=False)
            finally:
                client.close()

            self.assertEqual("thread-1", payload["thread"]["id"])
            recorded_params = json.loads(params_path.read_text(encoding="utf-8"))
            self.assertEqual({"threadId": "thread-1", "includeTurns": False}, recorded_params)

    def test_resume_thread_requests_metadata_only_when_history_is_paged_separately(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            params_path = tmp_path / "params.json"
            script_path = _write_resume_thread_probe_app_server(tmp_path, params_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                payload = client.resume_thread("thread-1", exclude_turns=True)
            finally:
                client.close()

            self.assertEqual("thread-1", payload["thread"]["id"])
            recorded_params = json.loads(params_path.read_text(encoding="utf-8"))
            self.assertEqual({"threadId": "thread-1", "excludeTurns": True}, recorded_params)

    def test_list_thread_turns_returns_single_page_with_full_items_view(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            params_path = tmp_path / "params.json"
            script_path = _write_thread_turns_probe_app_server(tmp_path, params_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                payload = client.list_thread_turns_page(
                    "thread-1",
                    cursor="cursor-2",
                    limit=25,
                    sort_direction="asc",
                    items_view="full",
                )
            finally:
                client.close()

            self.assertEqual("turn-1", payload["data"][0]["id"])
            recorded_params = json.loads(params_path.read_text(encoding="utf-8"))
            self.assertEqual(
                {
                    "threadId": "thread-1",
                    "cursor": "cursor-2",
                    "limit": 25,
                    "sortDirection": "asc",
                    "itemsView": "full",
                },
                recorded_params,
            )

    def test_list_threads_uses_larger_default_page_size(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            params_path = tmp_path / "params.json"
            script_path = _write_thread_list_probe_app_server(tmp_path, params_path)

            def process_factory() -> subprocess.Popen[str]:
                return subprocess.Popen(
                    [sys.executable, "-u", str(script_path)],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                )

            client = CodexRuntimeClient(process_factory=process_factory)
            try:
                payload = client.list_threads(
                    source_kinds=["cli", "vscode", "appServer"],
                    archived=False,
                    sort_key="updated_at",
                    sort_direction="desc",
                )
            finally:
                client.close()

            self.assertEqual(["thread-1"], [item["id"] for item in payload])
            recorded_params = json.loads(params_path.read_text(encoding="utf-8"))
            self.assertEqual(
                {
                    "cursor": None,
                    "limit": 200,
                    "archived": False,
                    "sortKey": "updated_at",
                    "sortDirection": "desc",
                    "sourceKinds": ["cli", "vscode", "appServer"],
                },
                recorded_params,
            )

    def test_resolve_codex_command_prefers_windows_cmd_shim(self):
        command = resolve_codex_command(
            "codex",
            os_name="nt",
            which=lambda value: {
                "codex.cmd": r"C:\tools\codex.cmd",
                "codex.exe": r"C:\tools\codex.exe",
                "codex": r"C:\tools\codex",
            }.get(value),
        )

        self.assertEqual(r"C:\tools\codex.cmd", command)

    def test_resolve_codex_command_keeps_explicit_binary_name(self):
        command = resolve_codex_command(
            "C:\\custom\\codex.exe",
            os_name="nt",
            which=lambda value: None,
        )

        self.assertEqual("C:\\custom\\codex.exe", command)


def _write_fake_app_server(tmp_path: Path, counter_path: Path) -> Path:
    script_path = tmp_path / "fake_app_server.py"
    script_path.write_text(
        f"""
import json
from pathlib import Path

counter_path = Path(r"{counter_path}")
counters = {{"initialize": 0, "app_list": 0}}

def flush_counters():
    counter_path.write_text(json.dumps(counters), encoding="utf-8")

flush_counters()

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        counters["initialize"] += 1
        flush_counters()
        print(json.dumps({{"id": message["id"], "result": {{"userAgent": "fake-agent"}}}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "app/list":
        counters["app_list"] += 1
        flush_counters()
        print(json.dumps({{"id": message["id"], "result": {{"data": [{{"id": "a"}}, {{"id": "b"}}], "nextCursor": None}}}}), flush=True)
    else:
        print(json.dumps({{"id": message["id"], "error": {{"code": -32601, "message": "unknown"}}}}), flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


def _write_broken_app_server(tmp_path: Path) -> Path:
    script_path = tmp_path / "broken_app_server.py"
    script_path.write_text(
        """
import json

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        print(json.dumps({"id": message["id"], "result": {"userAgent": "fake-agent"}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "app/list":
        print("not-json", flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


def _write_paginated_app_server(tmp_path: Path) -> Path:
    script_path = tmp_path / "paginated_app_server.py"
    script_path.write_text(
        """
import json

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        print(json.dumps({"id": message["id"], "result": {"userAgent": "fake-agent"}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "app/list":
        cursor = message.get("params", {}).get("cursor")
        if cursor is None:
            result = {"data": [{"id": "a"}, {"id": "b"}], "nextCursor": "cursor-2"}
        elif cursor == "cursor-2":
            result = {"data": [{"id": "c"}], "nextCursor": None}
        else:
            result = {"data": [], "nextCursor": None}
        print(json.dumps({"id": message["id"], "result": result}), flush=True)
    else:
        print(json.dumps({"id": message["id"], "error": {"code": -32601, "message": "unknown"}}), flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


def _write_app_list_probe_app_server(tmp_path: Path, params_path: Path) -> Path:
    script_path = tmp_path / "app_list_probe_app_server.py"
    script_path.write_text(
        f"""
import json
from pathlib import Path

params_path = Path(r"{params_path}")

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        print(json.dumps({{"id": message["id"], "result": {{"userAgent": "fake-agent"}}}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "app/list":
        params_path.write_text(json.dumps(message.get("params")), encoding="utf-8")
        print(json.dumps({{"id": message["id"], "result": {{"data": [{{"id": "a"}}], "nextCursor": None}}}}), flush=True)
    else:
        print(json.dumps({{"id": message["id"], "error": {{"code": -32601, "message": "unknown"}}}}), flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


def _write_read_thread_probe_app_server(tmp_path: Path, params_path: Path) -> Path:
    script_path = tmp_path / "read_thread_probe_app_server.py"
    script_path.write_text(
        f"""
import json
from pathlib import Path

params_path = Path(r"{params_path}")

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        print(json.dumps({{"id": message["id"], "result": {{"userAgent": "fake-agent"}}}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "thread/read":
        params_path.write_text(json.dumps(message.get("params")), encoding="utf-8")
        print(json.dumps({{"id": message["id"], "result": {{"thread": {{"id": "thread-1"}}}}}}), flush=True)
    else:
        print(json.dumps({{"id": message["id"], "error": {{"code": -32601, "message": "unknown"}}}}), flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


def _write_resume_thread_probe_app_server(tmp_path: Path, params_path: Path) -> Path:
    script_path = tmp_path / "resume_thread_probe_app_server.py"
    script_path.write_text(
        f"""
import json
from pathlib import Path

params_path = Path(r"{params_path}")

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        print(json.dumps({{"id": message["id"], "result": {{"userAgent": "fake-agent"}}}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "thread/resume":
        params_path.write_text(json.dumps(message.get("params")), encoding="utf-8")
        print(json.dumps({{"id": message["id"], "result": {{"thread": {{"id": "thread-1"}}}}}}), flush=True)
    else:
        print(json.dumps({{"id": message["id"], "error": {{"code": -32601, "message": "unknown"}}}}), flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


def _write_thread_turns_probe_app_server(tmp_path: Path, params_path: Path) -> Path:
    script_path = tmp_path / "thread_turns_probe_app_server.py"
    script_path.write_text(
        f"""
import json
from pathlib import Path

params_path = Path(r"{params_path}")

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        print(json.dumps({{"id": message["id"], "result": {{"userAgent": "fake-agent"}}}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "thread/turns/list":
        params_path.write_text(json.dumps(message.get("params")), encoding="utf-8")
        print(json.dumps({{"id": message["id"], "result": {{"data": [{{"id": "turn-1"}}], "nextCursor": "cursor-3", "backwardsCursor": "cursor-1"}}}}), flush=True)
    else:
        print(json.dumps({{"id": message["id"], "error": {{"code": -32601, "message": "unknown"}}}}), flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


def _write_thread_list_probe_app_server(tmp_path: Path, params_path: Path) -> Path:
    script_path = tmp_path / "thread_list_probe_app_server.py"
    script_path.write_text(
        f"""
import json
from pathlib import Path

params_path = Path(r"{params_path}")

while True:
    line = input()
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        print(json.dumps({{"id": message["id"], "result": {{"userAgent": "fake-agent"}}}}), flush=True)
    elif method == "initialized":
        continue
    elif method == "thread/list":
        params_path.write_text(json.dumps(message.get("params")), encoding="utf-8")
        print(json.dumps({{"id": message["id"], "result": {{"data": [{{"id": "thread-1"}}], "nextCursor": None}}}}), flush=True)
    else:
        print(json.dumps({{"id": message["id"], "error": {{"code": -32601, "message": "unknown"}}}}), flush=True)
""".strip(),
        encoding="utf-8",
    )
    return script_path


if __name__ == "__main__":
    unittest.main()
