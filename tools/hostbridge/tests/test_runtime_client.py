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


if __name__ == "__main__":
    unittest.main()
