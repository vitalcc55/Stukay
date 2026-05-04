from __future__ import annotations

from dataclasses import dataclass, replace
import os
import time
from typing import Any


JsonDict = dict[str, Any]


@dataclass(frozen=True)
class RuntimeSummary:
    host_status: str = "unknown"
    runtime_ready: bool = False
    app_list_count: int | None = None
    last_round_trip_ms: int | None = None
    probe_at_epoch_ms: int | None = None
    retry_attempt: int = 0
    degraded_reason: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    last_transport_error: str | None = None

    def to_wire_dict(self) -> dict[str, object | None]:
        return {
            "hostStatus": self.host_status,
            "runtimeReady": self.runtime_ready,
            "appListCount": self.app_list_count,
            "lastRoundTripMs": self.last_round_trip_ms,
            "probeAtEpochMs": self.probe_at_epoch_ms,
            "retryAttempt": self.retry_attempt,
            "degradedReason": self.degraded_reason,
            "errorCode": self.error_code,
            "errorMessage": self.error_message,
            "lastTransportError": self.last_transport_error,
        }

    @classmethod
    def ready(cls, *, app_list_count: int, last_round_trip_ms: int) -> "RuntimeSummary":
        return cls(
            host_status="ready",
            runtime_ready=True,
            app_list_count=app_list_count,
            last_round_trip_ms=last_round_trip_ms,
            probe_at_epoch_ms=_now_epoch_ms(),
            retry_attempt=0,
        )

    def degraded(self, *, retry_attempt: int, degraded_reason: str, error_code: str, error_message: str) -> "RuntimeSummary":
        return replace(
            self,
            host_status="degraded",
            runtime_ready=False,
            probe_at_epoch_ms=_now_epoch_ms(),
            retry_attempt=retry_attempt,
            degraded_reason=degraded_reason,
            error_code=error_code,
            error_message=error_message,
            last_transport_error=error_message,
        )


def normalize_thread_list(threads: list[JsonDict]) -> JsonDict:
    return {
        "data": [normalize_thread(thread, include_timeline=False) for thread in threads],
    }


def normalize_thread_response(thread: JsonDict) -> JsonDict:
    return {"thread": normalize_thread(thread, include_timeline=True)}


def normalize_turn_response(turn: JsonDict) -> JsonDict:
    return {"turn": normalize_turn(turn)}


def normalize_stream_message(message: JsonDict) -> JsonDict | None:
    method = message.get("method")
    params = message.get("params")
    if not isinstance(method, str):
        return None
    if not isinstance(params, dict):
        params = {}
    if method == "item/agentMessage/delta":
        return {
            "method": method,
            "threadId": params.get("threadId"),
            "turnId": params.get("turnId"),
            "itemId": params.get("itemId"),
            "delta": params.get("delta"),
        }
    if method in {"item/started", "item/completed"}:
        item = params.get("item")
        if not isinstance(item, dict):
            return None
        return {
            "method": method,
            "threadId": params.get("threadId"),
            "turnId": params.get("turnId"),
            "item": normalize_thread_item(
                thread_id=_string_or_none(params.get("threadId")) or "",
                turn_id=_string_or_none(params.get("turnId")),
                item=item,
                is_streaming=False,
            ),
        }
    if method in {"turn/started", "turn/completed"}:
        turn = params.get("turn")
        if not isinstance(turn, dict):
            return None
        return {
            "method": method,
            "threadId": params.get("threadId"),
            "turn": normalize_turn(turn),
        }
    if method == "thread/status/changed":
        return {
            "method": method,
            "threadId": params.get("threadId"),
            "status": normalize_thread_status(params.get("status")),
        }
    if method == "serverRequest/resolved":
        return {
            "method": method,
            "threadId": params.get("threadId"),
            "requestId": params.get("requestId"),
        }
    if method in {
        "item/commandExecution/requestApproval",
        "item/fileChange/requestApproval",
        "item/tool/requestUserInput",
    }:
        request_id = message.get("id")
        if request_id is None:
            return None
        return normalize_server_request(method=method, request_id=str(request_id), params=params)
    if method == "error":
        return {
            "method": method,
            "threadId": params.get("threadId"),
            "turnId": params.get("turnId"),
            "message": _extract_turn_error_message(params.get("error")),
        }
    return None


def normalize_server_request(*, method: str, request_id: str, params: JsonDict) -> JsonDict:
    if method == "item/tool/requestUserInput":
        questions = params.get("questions")
        question_count = len(questions) if isinstance(questions, list) else 0
        return {
            "method": method,
            "requestId": request_id,
            "threadId": params.get("threadId"),
            "turnId": params.get("turnId"),
            "itemId": params.get("itemId"),
            "questionCount": question_count,
        }
    approval_kind = "command" if method == "item/commandExecution/requestApproval" else "fileChange"
    available_decisions = params.get("availableDecisions")
    scalar_decisions = [
        decision
        for decision in available_decisions
        if isinstance(decision, str)
    ] if isinstance(available_decisions, list) else []
    network_context = params.get("networkApprovalContext")
    network_host = None
    network_protocol = None
    if isinstance(network_context, dict):
        network_host = _string_or_none(network_context.get("host"))
        network_protocol = _string_or_none(network_context.get("protocol"))
    return {
        "method": method,
        "requestId": request_id,
        "threadId": params.get("threadId"),
        "turnId": params.get("turnId"),
        "itemId": params.get("itemId"),
        "approval": {
            "id": _string_or_none(params.get("approvalId")) or request_id,
            "requestId": request_id,
            "itemId": params.get("itemId"),
            "threadId": params.get("threadId"),
            "turnId": params.get("turnId"),
            "kind": approval_kind,
            "title": _approval_title(params, approval_kind, network_host),
            "description": _approval_description(params, approval_kind, network_host, network_protocol),
            "availableDecisions": scalar_decisions,
            "command": _string_or_none(params.get("command")),
            "cwd": _string_or_none(params.get("cwd")),
            "grantRoot": _string_or_none(params.get("grantRoot")),
            "networkHost": network_host,
            "networkProtocol": network_protocol,
            "reason": _string_or_none(params.get("reason")),
        },
    }


def normalize_thread(thread: JsonDict, *, include_timeline: bool) -> JsonDict:
    cwd = _string_or_none(thread.get("cwd")) or ""
    normalized: JsonDict = {
        "id": thread.get("id"),
        "cwd": cwd,
        "title": _thread_title(thread, cwd),
        "preview": _string_or_none(thread.get("preview")) or "",
        "sourceKind": _source_kind(thread.get("source")),
        "updatedAtEpochMs": _seconds_to_epoch_ms(thread.get("updatedAt")),
        "createdAtEpochMs": _seconds_to_epoch_ms(thread.get("createdAt")),
        "status": normalize_thread_status(thread.get("status")),
        "turnCount": len(thread.get("turns")) if isinstance(thread.get("turns"), list) else 0,
    }
    if include_timeline:
        normalized["timeline"] = normalize_timeline(
            thread_id=_string_or_none(thread.get("id")) or "",
            turns=thread.get("turns"),
        )
    return normalized


def normalize_thread_status(status: Any) -> JsonDict:
    if isinstance(status, dict):
        status_type = _string_or_none(status.get("type")) or "notLoaded"
        active_flags = status.get("activeFlags")
        if status_type == "active" and isinstance(active_flags, list):
            return {
                "type": status_type,
                "activeFlags": [flag for flag in active_flags if isinstance(flag, str)],
            }
        return {"type": status_type, "activeFlags": []}
    return {"type": "notLoaded", "activeFlags": []}


def normalize_turn(turn: JsonDict) -> JsonDict:
    return {
        "id": turn.get("id"),
        "status": _string_or_none(turn.get("status")) or "inProgress",
        "startedAtEpochMs": _seconds_to_epoch_ms(turn.get("startedAt")),
        "completedAtEpochMs": _seconds_to_epoch_ms(turn.get("completedAt")),
        "durationMs": _int_or_none(turn.get("durationMs")),
        "errorMessage": _extract_turn_error_message(turn.get("error")),
    }


def normalize_timeline(*, thread_id: str, turns: Any) -> list[JsonDict]:
    if not isinstance(turns, list):
        return []
    timeline: list[JsonDict] = []
    for turn in turns:
        if not isinstance(turn, dict):
            continue
        turn_id = _string_or_none(turn.get("id"))
        items = turn.get("items")
        if isinstance(items, list):
            for item in items:
                if not isinstance(item, dict):
                    continue
                normalized_item = normalize_thread_item(
                    thread_id=thread_id,
                    turn_id=turn_id,
                    item=item,
                    is_streaming=False,
                )
                if normalized_item is not None:
                    timeline.append(normalized_item)
        if _string_or_none(turn.get("status")) == "interrupted":
            timeline.append(
                {
                    "type": "statusEvent",
                    "id": f"{turn_id}-interrupted",
                    "threadId": thread_id,
                    "title": "Turn interrupted",
                    "detail": "Последний turn был прерван пользователем.",
                    "turnId": turn_id,
                },
            )
        error_message = _extract_turn_error_message(turn.get("error"))
        if error_message:
            timeline.append(
                {
                    "type": "statusEvent",
                    "id": f"{turn_id}-error",
                    "threadId": thread_id,
                    "title": "Turn failed",
                    "detail": error_message,
                    "turnId": turn_id,
                },
            )
    return timeline


def normalize_thread_item(
    *,
    thread_id: str,
    turn_id: str | None,
    item: JsonDict,
    is_streaming: bool,
) -> JsonDict | None:
    item_type = _string_or_none(item.get("type"))
    item_id = _string_or_none(item.get("id")) or f"{thread_id}-{turn_id or 'unknown'}-{item_type or 'item'}"
    if item_type == "userMessage":
        return {
            "type": "userMessage",
            "id": item_id,
            "threadId": thread_id,
            "turnId": turn_id,
            "text": _join_user_content(item.get("content")),
        }
    if item_type == "agentMessage":
        return {
            "type": "assistantMessage",
            "id": item_id,
            "threadId": thread_id,
            "turnId": turn_id,
            "itemId": item_id,
            "text": _string_or_none(item.get("text")) or "",
            "streaming": is_streaming,
            "phase": _string_or_none(item.get("phase")),
        }
    if item_type == "commandExecution":
        return {
            "type": "commandRun",
            "id": item_id,
            "threadId": thread_id,
            "turnId": turn_id,
            "commandPreview": _string_or_none(item.get("command")) or "",
            "status": _string_or_none(item.get("status")) or "inProgress",
            "cwd": _string_or_none(item.get("cwd")),
            "aggregatedOutput": _string_or_none(item.get("aggregatedOutput")),
            "exitCode": _int_or_none(item.get("exitCode")),
        }
    if item_type == "fileChange":
        path, change_kind = _extract_file_change(item.get("changes"))
        return {
            "type": "fileChange",
            "id": item_id,
            "threadId": thread_id,
            "turnId": turn_id,
            "path": path,
            "changeKind": change_kind,
            "status": _string_or_none(item.get("status")) or "pending",
        }
    if item_type == "reasoning":
        summary = item.get("summary")
        detail = ", ".join(part for part in summary if isinstance(part, str)) if isinstance(summary, list) else ""
        return {
            "type": "statusEvent",
            "id": item_id,
            "threadId": thread_id,
            "turnId": turn_id,
            "title": "Reasoning",
            "detail": detail or "Agent reasoning snapshot",
        }
    if item_type == "plan":
        return {
            "type": "statusEvent",
            "id": item_id,
            "threadId": thread_id,
            "turnId": turn_id,
            "title": "Plan",
            "detail": _string_or_none(item.get("text")) or "",
        }
    return None


def _thread_title(thread: JsonDict, cwd: str) -> str:
    name = _string_or_none(thread.get("name"))
    if name:
        return name
    preview = _string_or_none(thread.get("preview"))
    if preview:
        return preview[:80]
    if cwd:
        basename = os.path.basename(cwd.rstrip("\\/"))
        if basename:
            return basename
    return _string_or_none(thread.get("id")) or "Unknown thread"


def _source_kind(source: Any) -> str:
    if isinstance(source, str):
        return source
    if isinstance(source, dict):
        if isinstance(source.get("custom"), str):
            return "custom"
        sub_agent = source.get("subAgent")
        if isinstance(sub_agent, dict):
            return "subAgent"
    return "unknown"


def _join_user_content(content: Any) -> str:
    if not isinstance(content, list):
        return ""
    parts: list[str] = []
    for item in content:
        if not isinstance(item, dict):
            continue
        text = _string_or_none(item.get("text"))
        if text:
            parts.append(text)
    return "\n".join(parts)


def _extract_file_change(changes: Any) -> tuple[str, str]:
    if not isinstance(changes, list) or not changes:
        return ("Unknown path", "modified")
    first = changes[0]
    if not isinstance(first, dict):
        return ("Unknown path", "modified")
    return (
        _string_or_none(first.get("path"))
        or _string_or_none(first.get("newPath"))
        or _string_or_none(first.get("oldPath"))
        or "Unknown path",
        _string_or_none(first.get("type")) or "modified",
    )


def _approval_title(params: JsonDict, approval_kind: str, network_host: str | None) -> str:
    if approval_kind == "fileChange":
        return "Approve file change"
    if network_host:
        return f"Approve network access to {network_host}"
    return "Approve command execution"


def _approval_description(
    params: JsonDict,
    approval_kind: str,
    network_host: str | None,
    network_protocol: str | None,
) -> str:
    reason = _string_or_none(params.get("reason"))
    if reason:
        return reason
    if approval_kind == "fileChange":
        grant_root = _string_or_none(params.get("grantRoot"))
        if grant_root:
            return f"Request to allow writes under {grant_root}."
        return "The agent requested permission to write files."
    command = _string_or_none(params.get("command"))
    if network_host and network_protocol:
        return f"Command needs {network_protocol.upper()} access to {network_host}: {command or 'command preview unavailable'}"
    if command:
        return command
    return "The agent requested permission to run a command."


def _extract_turn_error_message(error: Any) -> str | None:
    if isinstance(error, str):
        return error
    if isinstance(error, dict):
        message = _string_or_none(error.get("message"))
        if message:
            return message
        codex_error_info = error.get("codexErrorInfo")
        if isinstance(codex_error_info, str):
            return codex_error_info
    return None


def _string_or_none(value: Any) -> str | None:
    return value if isinstance(value, str) and value != "" else None


def _int_or_none(value: Any) -> int | None:
    return value if isinstance(value, int) else None


def _seconds_to_epoch_ms(value: Any) -> int | None:
    if not isinstance(value, int):
        return None
    return value * 1000


def _now_epoch_ms() -> int:
    return int(time.time() * 1000)
