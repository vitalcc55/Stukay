package dev.vitalcc.stukay.runtime

import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeApprovalPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeHistoryTurnPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadEvent
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadHistoryPagePayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadStatusPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeTimelineItemPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeTurnPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeThreadStoreTest {
    @Test
    fun applyFirstHistoryPageStoresChronologicalTimelineAndOlderCursor() {
        val store = RuntimeThreadStore(nowProvider = { 100L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )

        store.applyHistoryPage(
            threadId = dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"),
            page = HostBridgeThreadHistoryPagePayload(
                threadId = "thread-main-1",
                data = listOf(
                    historyTurn(
                        id = "turn-2",
                        userText = "Newest user",
                        assistantText = "Newest answer",
                    ),
                    historyTurn(
                        id = "turn-1",
                        userText = "Older user",
                        assistantText = "Older answer",
                    ),
                ),
                nextCursor = "cursor-older",
                backwardsCursor = "cursor-newer",
            ),
        )

        val timeline = store.loadTimeline(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        val historyState = store.historyState(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))

        assertEquals(
            listOf("Older user", "Older answer", "Newest user", "Newest answer"),
            timeline.mapNotNull { item ->
                when (item) {
                    is dev.vitalcc.stukay.core.model.TimelineItem.UserMessage -> item.text
                    is dev.vitalcc.stukay.core.model.TimelineItem.AssistantMessage -> item.text
                    else -> null
                }
            },
        )
        assertEquals("cursor-older", historyState.nextCursor)
        assertTrue(historyState.hasOlderHistory)
    }

    @Test
    fun applyOlderHistoryPrependsWithoutDuplicatingExistingTurnItems() {
        val store = RuntimeThreadStore(nowProvider = { 120L })
        val threadId = dev.vitalcc.stukay.core.model.ThreadId("thread-main-1")
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )
        store.applyHistoryPage(
            threadId = threadId,
            page = HostBridgeThreadHistoryPagePayload(
                threadId = "thread-main-1",
                data = listOf(
                    historyTurn(id = "turn-2", userText = "Turn 2", assistantText = "Answer 2"),
                    historyTurn(id = "turn-1", userText = "Turn 1", assistantText = "Answer 1"),
                ),
                nextCursor = "cursor-older",
                backwardsCursor = "cursor-newer",
            ),
        )

        store.applyHistoryPage(
            threadId = threadId,
            page = HostBridgeThreadHistoryPagePayload(
                threadId = "thread-main-1",
                data = listOf(
                    historyTurn(id = "turn-1", userText = "Turn 1", assistantText = "Answer 1"),
                    historyTurn(id = "turn-0", userText = "Turn 0", assistantText = "Answer 0"),
                ),
                nextCursor = null,
                backwardsCursor = "cursor-newer-2",
            ),
        )

        val timeline = store.loadTimeline(threadId)
        val historyState = store.historyState(threadId)

        assertEquals(
            listOf("Turn 0", "Answer 0", "Turn 1", "Answer 1", "Turn 2", "Answer 2"),
            timeline.mapNotNull { item ->
                when (item) {
                    is dev.vitalcc.stukay.core.model.TimelineItem.UserMessage -> item.text
                    is dev.vitalcc.stukay.core.model.TimelineItem.AssistantMessage -> item.text
                    else -> null
                }
            },
        )
        assertEquals(null, historyState.nextCursor)
        assertEquals(false, historyState.hasOlderHistory)
    }

    @Test
    fun applyInitialHistoryPagePreservesAlreadyLoadedOlderPrefixDuringReconnect() {
        val store = RuntimeThreadStore(nowProvider = { 125L })
        val threadId = dev.vitalcc.stukay.core.model.ThreadId("thread-main-1")
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )
        store.applyHistoryPage(
            threadId = threadId,
            page = HostBridgeThreadHistoryPagePayload(
                threadId = "thread-main-1",
                data = listOf(
                    historyTurn(id = "turn-2", userText = "Turn 2", assistantText = "Answer 2"),
                    historyTurn(id = "turn-1", userText = "Turn 1", assistantText = "Answer 1"),
                ),
                nextCursor = "cursor-older",
                backwardsCursor = "cursor-newer",
            ),
            isInitialPage = true,
        )
        store.applyHistoryPage(
            threadId = threadId,
            page = HostBridgeThreadHistoryPagePayload(
                threadId = "thread-main-1",
                data = listOf(
                    historyTurn(id = "turn-0", userText = "Turn 0", assistantText = "Answer 0"),
                ),
                nextCursor = null,
                backwardsCursor = "cursor-newer-2",
            ),
        )

        store.applyHistoryPage(
            threadId = threadId,
            page = HostBridgeThreadHistoryPagePayload(
                threadId = "thread-main-1",
                data = listOf(
                    historyTurn(id = "turn-3", userText = "Turn 3", assistantText = "Answer 3"),
                    historyTurn(id = "turn-2", userText = "Turn 2", assistantText = "Answer 2"),
                    historyTurn(id = "turn-1", userText = "Turn 1", assistantText = "Answer 1"),
                ),
                nextCursor = "cursor-older-2",
                backwardsCursor = "cursor-newer-3",
            ),
            isInitialPage = true,
        )

        val timeline = store.loadTimeline(threadId)
        assertEquals(
            listOf("Turn 0", "Answer 0", "Turn 1", "Answer 1", "Turn 2", "Answer 2", "Turn 3", "Answer 3"),
            timeline.mapNotNull { item ->
                when (item) {
                    is dev.vitalcc.stukay.core.model.TimelineItem.UserMessage -> item.text
                    is dev.vitalcc.stukay.core.model.TimelineItem.AssistantMessage -> item.text
                    else -> null
                }
            },
        )
    }

    @Test
    fun mergeResumedThreadKeepsOverlayPendingApprovalsOutsideHistoryTimeline() {
        val store = RuntimeThreadStore(nowProvider = { 130L })
        val threadId = dev.vitalcc.stukay.core.model.ThreadId("thread-main-1")
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )
        store.applyEvent(approvalEvent("request-2", "approval-2"))

        store.mergeResumedThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread resumed",
                preview = "Resume preview",
                updatedAtEpochMs = 2_000L,
            ).copy(
                status = HostBridgeThreadStatusPayload(
                    type = "active",
                    activeFlags = listOf("waitingOnApproval"),
                ),
                pendingApprovals = listOf(
                    HostBridgeApprovalPayload(
                        id = "approval-1",
                        requestId = "request-1",
                        itemId = "item-1",
                        threadId = "thread-main-1",
                        turnId = "turn-1",
                        kind = "command",
                        title = "Approve command",
                        description = "Need to run command",
                        availableDecisions = listOf("accept", "decline", "cancel"),
                        startedAtEpochMs = 123456789L,
                        command = "dir",
                    ),
                ),
            ),
        )

        val pending = store.unresolvedApprovals(threadId)
        val timeline = store.loadTimeline(threadId)

        assertEquals(2, pending.size)
        assertTrue(pending.any { approval -> approval.requestId == "request-1" })
        assertTrue(pending.any { approval -> approval.requestId == "request-2" })
        assertEquals(123456789L, pending.first { approval -> approval.requestId == "request-1" }.startedAtEpochMs)
        assertTrue(timeline.none { item ->
            item is dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest && !item.resolved
        })
    }

    @Test
    fun replaceIndexGroupsThreadsByCwdAndNoProjectBucket() {
        val store = RuntimeThreadStore(nowProvider = { 100L })

        store.replaceIndex(
            listOf(
                threadPayload(
                    id = "thread-main-1",
                    cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                    title = "Main thread",
                    preview = "Latest main preview",
                    updatedAtEpochMs = 2_000L,
                ),
                threadPayload(
                    id = "thread-no-project",
                    cwd = "",
                    title = "Detached thread",
                    preview = "Detached preview",
                    updatedAtEpochMs = 1_500L,
                ),
                threadPayload(
                    id = "thread-main-2",
                    cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                    title = "Older main thread",
                    preview = "Older main preview",
                    updatedAtEpochMs = 1_000L,
                ),
            ),
        )

        val projects = store.loadProjects()

        assertEquals(2, projects.size)
        assertTrue(projects.any { project -> project.id.value == "C:\\Users\\v.vlasov\\Desktop\\Stukay" })
        assertTrue(projects.any { project -> project.id.value == "no-project" && project.name == "No Project" })
    }

    @Test
    fun approvalLifecycleTracksPendingAndClearsOnResolvedEvent() {
        val store = RuntimeThreadStore(nowProvider = { 200L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )

        store.applyEvent(
            HostBridgeThreadEvent(
                method = "item/commandExecution/requestApproval",
                threadId = "thread-main-1",
                requestId = "request-1",
                approval = HostBridgeApprovalPayload(
                    id = "approval-1",
                    requestId = "request-1",
                    itemId = "item-1",
                    threadId = "thread-main-1",
                    turnId = "turn-1",
                    kind = "command",
                    title = "Approve command",
                    description = "Need to run command",
                    availableDecisions = listOf("accept", "decline", "cancel"),
                    command = "dir",
                    cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                ),
            ),
        )

        val pendingBefore = store.unresolvedApprovals(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertEquals(1, pendingBefore.size)
        assertEquals(ThreadStatus.WaitingForApproval, store.loadThread(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))?.status)

        store.applyEvent(
            HostBridgeThreadEvent(
                method = "serverRequest/resolved",
                threadId = "thread-main-1",
                requestId = "request-1",
            ),
        )

        val pendingAfter = store.unresolvedApprovals(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertTrue(pendingAfter.isEmpty())
    }

    @Test
    fun resolvingOneApprovalDoesNotDropWaitingStateWhileAnotherApprovalRemains() {
        val store = RuntimeThreadStore(nowProvider = { 220L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )

        store.applyEvent(approvalEvent("request-1", "approval-1"))
        store.applyEvent(approvalEvent("request-2", "approval-2"))
        store.applyEvent(
            HostBridgeThreadEvent(
                method = "serverRequest/resolved",
                threadId = "thread-main-1",
                requestId = "request-1",
            ),
        )

        val thread = store.loadThread(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        val pending = store.unresolvedApprovals(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertEquals(ThreadStatus.WaitingForApproval, thread?.status)
        assertEquals(1, pending.size)
        assertEquals("request-2", pending.single().requestId)
    }

    @Test
    fun assistantDeltaAndInterruptedTurnUpdateThreadState() {
        val store = RuntimeThreadStore(nowProvider = { 300L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )

        store.applyEvent(
            HostBridgeThreadEvent(
                method = "item/agentMessage/delta",
                threadId = "thread-main-1",
                turnId = "turn-1",
                itemId = "assistant-1",
                delta = "Hello",
            ),
        )

        val runningThread = store.loadThread(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertEquals(ThreadStatus.Running, runningThread?.status)

        store.applyEvent(
            HostBridgeThreadEvent(
                method = "turn/completed",
                threadId = "thread-main-1",
                turn = HostBridgeTurnPayload(
                    id = "turn-1",
                    status = "interrupted",
                    startedAtEpochMs = 100L,
                    completedAtEpochMs = 200L,
                    durationMs = 100L,
                    errorMessage = null,
                ),
            ),
        )

        val interruptedThread = store.loadThread(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertEquals(ThreadStatus.Interrupted, interruptedThread?.status)
    }

    @Test
    fun mergeResumedThreadPreservesPreviouslyHydratedTimeline() {
        val store = RuntimeThreadStore(nowProvider = { 400L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ).copy(
                timeline = listOf(
                    HostBridgeTimelineItemPayload(
                        type = "commandRun",
                        id = "cmd-1",
                        threadId = "thread-main-1",
                        turnId = "turn-1",
                        commandPreview = "dir",
                        status = "completed",
                    ),
                ),
            ),
        )

        store.mergeResumedThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread resumed",
                preview = "Resume preview",
                updatedAtEpochMs = 2_000L,
            ),
        )

        val timeline = store.loadTimeline(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertTrue(timeline.any { item -> item is dev.vitalcc.stukay.core.model.TimelineItem.CommandRun })
    }

    @Test
    fun mergeResumedThreadReplacesExistingItemWithFresherResumePayload() {
        val store = RuntimeThreadStore(nowProvider = { 450L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ).copy(
                timeline = listOf(
                    HostBridgeTimelineItemPayload(
                        type = "assistantMessage",
                        id = "assistant-1",
                        threadId = "thread-main-1",
                        turnId = "turn-1",
                        itemId = "assistant-1",
                        text = "Partial",
                        streaming = true,
                    ),
                ),
            ),
        )

        store.mergeResumedThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread resumed",
                preview = "Resume preview",
                updatedAtEpochMs = 2_000L,
            ).copy(
                timeline = listOf(
                    HostBridgeTimelineItemPayload(
                        type = "assistantMessage",
                        id = "assistant-1",
                        threadId = "thread-main-1",
                        turnId = "turn-1",
                        itemId = "assistant-1",
                        text = "Complete",
                        streaming = false,
                    ),
                ),
            ),
        )

        val message = store.loadTimeline(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
            .single { item -> item.id == "assistant-1" } as dev.vitalcc.stukay.core.model.TimelineItem.AssistantMessage
        assertEquals("Complete", message.text)
        assertEquals(false, message.streaming)
    }

    @Test
    fun mergeResumedThreadDropsMissingApprovalWhenRuntimeIsNoLongerWaitingOnApproval() {
        val store = RuntimeThreadStore(nowProvider = { 470L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )
        store.applyEvent(approvalEvent("request-1", "approval-1"))

        store.mergeResumedThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread resumed",
                preview = "Resume preview",
                updatedAtEpochMs = 2_000L,
            ),
        )

        val pending = store.unresolvedApprovals(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertTrue(pending.isEmpty())
    }

    @Test
    fun replaceThreadPreservesUnresolvedApprovalsNotPersistedInThreadReadSnapshot() {
        val store = RuntimeThreadStore(nowProvider = { 500L })
        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread",
                preview = "Preview",
                updatedAtEpochMs = 1_000L,
            ),
        )
        store.applyEvent(approvalEvent("request-1", "approval-1"))

        store.replaceThread(
            threadPayload(
                id = "thread-main-1",
                cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
                title = "Main thread rehydrated",
                preview = "Rehydrated preview",
                updatedAtEpochMs = 2_000L,
            ).copy(
                timeline = listOf(
                    HostBridgeTimelineItemPayload(
                        type = "userMessage",
                        id = "user-1",
                        threadId = "thread-main-1",
                        turnId = "turn-1",
                        text = "Hello",
                    ),
                ),
            ),
        )

        val pending = store.unresolvedApprovals(dev.vitalcc.stukay.core.model.ThreadId("thread-main-1"))
        assertEquals(1, pending.size)
        assertEquals("request-1", pending.single().requestId)
    }

    private fun threadPayload(
        id: String,
        cwd: String,
        title: String,
        preview: String,
        updatedAtEpochMs: Long,
    ): HostBridgeThreadPayload = HostBridgeThreadPayload(
        id = id,
        cwd = cwd,
        title = title,
        preview = preview,
        sourceKind = "appServer",
        updatedAtEpochMs = updatedAtEpochMs,
        createdAtEpochMs = updatedAtEpochMs,
        turnCount = 0,
        status = HostBridgeThreadStatusPayload(
            type = "idle",
            activeFlags = emptyList(),
        ),
    )

    private fun approvalEvent(requestId: String, approvalId: String): HostBridgeThreadEvent = HostBridgeThreadEvent(
        method = "item/commandExecution/requestApproval",
        threadId = "thread-main-1",
        requestId = requestId,
        approval = HostBridgeApprovalPayload(
            id = approvalId,
            requestId = requestId,
            itemId = approvalId,
            threadId = "thread-main-1",
            turnId = "turn-1",
            kind = "command",
            title = "Approve command",
            description = "Need to run command",
            availableDecisions = listOf("accept", "decline", "cancel"),
            command = "dir",
            cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
        ),
    )

    private fun historyTurn(
        id: String,
        userText: String,
        assistantText: String,
    ): HostBridgeHistoryTurnPayload = HostBridgeHistoryTurnPayload(
        id = id,
        status = "completed",
        itemsView = "full",
        startedAtEpochMs = 100L,
        completedAtEpochMs = 200L,
        durationMs = 100L,
        errorMessage = null,
        items = listOf(
            HostBridgeTimelineItemPayload(
                type = "userMessage",
                id = "$id-user",
                threadId = "thread-main-1",
                turnId = id,
                text = userText,
            ),
            HostBridgeTimelineItemPayload(
                type = "assistantMessage",
                id = "$id-assistant",
                threadId = "thread-main-1",
                turnId = id,
                itemId = "$id-assistant",
                text = assistantText,
                streaming = false,
            ),
        ),
    )
}
