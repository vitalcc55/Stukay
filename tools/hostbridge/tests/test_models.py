from __future__ import annotations

import unittest

from tools.hostbridge.models import (
    normalize_server_request,
    normalize_thread_response,
    normalize_thread_turns_page,
)


class ModelsTest(unittest.TestCase):
    def test_normalize_thread_response_keeps_session_id_without_timeline_hydration(self):
        payload = normalize_thread_response(
            {
                "id": "thread-1",
                "sessionId": "session-1",
                "cwd": "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                "preview": "Preview",
                "source": "appServer",
                "status": {"type": "idle"},
                "turns": [],
                "createdAt": 10,
                "updatedAt": 11,
            },
        )

        self.assertEqual("thread-1", payload["thread"]["id"])
        self.assertEqual("session-1", payload["thread"]["sessionId"])
        self.assertEqual([], payload["thread"]["timeline"])

    def test_normalize_server_request_carries_started_at_timestamp(self):
        payload = normalize_server_request(
            method="item/commandExecution/requestApproval",
            request_id="request-1",
            params={
                "threadId": "thread-1",
                "turnId": "turn-1",
                "itemId": "item-1",
                "approvalId": "approval-1",
                "command": "dir",
                "startedAtMs": 123456789,
                "availableDecisions": ["accept", "decline"],
            },
        )

        self.assertEqual("request-1", payload["requestId"])
        self.assertEqual(123456789, payload["approval"]["startedAtEpochMs"])

    def test_normalize_thread_turns_page_returns_items_view_and_cursors(self):
        payload = normalize_thread_turns_page(
            thread_id="thread-1",
            result={
                "data": [
                    {
                        "id": "turn-1",
                        "status": "completed",
                        "itemsView": "full",
                        "startedAt": 15,
                        "completedAt": 16,
                        "durationMs": 1500,
                        "items": [
                            {
                                "type": "userMessage",
                                "id": "user-1",
                                "content": [{"type": "text", "text": "Hello"}],
                            },
                            {
                                "type": "agentMessage",
                                "id": "assistant-1",
                                "text": "World",
                            },
                        ],
                    },
                ],
                "nextCursor": "cursor-2",
                "backwardsCursor": "cursor-0",
            },
        )

        self.assertEqual("thread-1", payload["threadId"])
        self.assertEqual("turn-1", payload["data"][0]["id"])
        self.assertEqual("full", payload["data"][0]["itemsView"])
        self.assertEqual("assistantMessage", payload["data"][0]["items"][1]["type"])
        self.assertEqual("cursor-2", payload["nextCursor"])
        self.assertEqual("cursor-0", payload["backwardsCursor"])


if __name__ == "__main__":
    unittest.main()
