package dev.vitalcc.stukay.feature.thread.data

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.ApprovalKind
import dev.vitalcc.stukay.core.model.ApprovalRisk
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.CommandRunStatus
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ThreadHistoryState
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.TurnId

class FakeThreadRepository : ThreadRepository {
    private val scenarios = mutableMapOf(
        ThreadId("thread-active-shell") to ThreadScenario(
            thread = CodexThread(
                id = ThreadId("thread-active-shell"),
                projectId = ProjectId("main"),
                title = "Foundation approval flow",
                preview = "Ожидает подтверждение shell-изменений",
                status = ThreadStatus.WaitingForApproval,
                lastUpdatedAtEpochMs = 1_746_187_200_000,
            ),
            timeline = mutableListOf(
                TimelineItem.UserMessage(
                    id = "user-1",
                    threadId = ThreadId("thread-active-shell"),
                    text = "Собери foundation shell под Pixel-first UX.",
                ),
                TimelineItem.AssistantMessage(
                    id = "assistant-1",
                    threadId = ThreadId("thread-active-shell"),
                    text = "Foundation shell готов к утверждению и следующему milestone.",
                    streaming = false,
                ),
                TimelineItem.CommandRun(
                    id = "command-1",
                    threadId = ThreadId("thread-active-shell"),
                    commandPreview = "./gradlew :app:assembleDebug",
                    status = CommandRunStatus.Succeeded,
                ),
                TimelineItem.ApprovalRequest(
                    id = "approval-shell-1",
                    threadId = ThreadId("thread-active-shell"),
                    approvalId = ApprovalId("approval-shell-1"),
                    kind = ApprovalKind.Command,
                    risk = ApprovalRisk.Medium,
                    title = "Approve shell promotion",
                    description = "Подтвердить переход shell-изменений к следующему этапу.",
                    resolved = false,
                    requestId = "approval-shell-1",
                    itemId = "approval-shell-1",
                ),
            ),
        ),
        ThreadId("thread-review-shell") to ThreadScenario(
            thread = CodexThread(
                id = ThreadId("thread-review-shell"),
                projectId = ProjectId("diagnostics"),
                title = "Diagnostics follow-up",
                preview = "Готов к runtime turn",
                status = ThreadStatus.Idle,
                lastUpdatedAtEpochMs = 1_746_187_260_000,
            ),
            timeline = mutableListOf(
                TimelineItem.UserMessage(
                    id = "user-2",
                    threadId = ThreadId("thread-review-shell"),
                    text = "Проверь diagnostics и подготовь следующий run.",
                ),
                TimelineItem.AssistantMessage(
                    id = "assistant-2",
                    threadId = ThreadId("thread-review-shell"),
                    text = "Diagnostics foundation уже готова к следующему fake run.",
                    streaming = false,
                ),
                TimelineItem.StatusEvent(
                    id = "status-completed-1",
                    threadId = ThreadId("thread-review-shell"),
                    title = "Run completed",
                    detail = "Foundation and logging stages were completed successfully.",
                ),
            ),
        ),
    )

    override fun loadThreads(projectId: ProjectId): List<CodexThread> = scenarios.values
        .map { it.thread }
        .filter { thread -> thread.projectId == projectId }
        .sortedByDescending { thread -> thread.lastUpdatedAtEpochMs }

    override fun loadThread(threadId: ThreadId): CodexThread? = scenarios[threadId]?.thread

    override fun loadTimeline(threadId: ThreadId): List<TimelineItem> = scenarios[threadId]?.timeline?.toList().orEmpty()

    override fun refreshIndex(): List<CodexThread> = scenarios.values
        .map { it.thread }
        .sortedByDescending { thread -> thread.lastUpdatedAtEpochMs }

    override fun readThreadSummary(threadId: ThreadId): CodexThread? = loadThread(threadId)

    override fun historyState(threadId: ThreadId): ThreadHistoryState = ThreadHistoryState()

    override fun loadInitialHistory(threadId: ThreadId): ThreadHistoryState = ThreadHistoryState()

    override fun loadOlderHistory(threadId: ThreadId): ThreadHistoryState = ThreadHistoryState()

    override fun resumeThread(threadId: ThreadId): CodexThread? = loadThread(threadId)

    override fun startTurn(threadId: ThreadId, text: String): TurnId {
        val scenario = requireNotNull(scenarios[threadId]) { "Unknown thread: ${threadId.value}" }

        scenario.thread = scenario.thread.copy(
            status = ThreadStatus.Running,
            preview = "Runtime turn is currently active",
            lastUpdatedAtEpochMs = scenario.thread.lastUpdatedAtEpochMs + 1_000,
        )
        val turnId = TurnId("${threadId.value}-turn-${scenario.sequence++}")
        scenario.timeline += TimelineItem.UserMessage(
            id = "${turnId.value}-user",
            threadId = threadId,
            text = text,
            turnId = turnId,
        )
        scenario.timeline += TimelineItem.StatusEvent(
            id = "${threadId.value}-started-${scenario.sequence++}",
            threadId = threadId,
            title = "Runtime turn started",
            detail = "The shell entered a running state.",
            turnId = turnId,
        )

        return turnId
    }

    override fun interruptTurn(threadId: ThreadId, turnId: TurnId) {
        val scenario = requireNotNull(scenarios[threadId]) { "Unknown thread: ${threadId.value}" }

        scenario.thread = scenario.thread.copy(
            status = ThreadStatus.Interrupted,
            preview = "Runtime turn interrupted",
            lastUpdatedAtEpochMs = scenario.thread.lastUpdatedAtEpochMs + 1_000,
        )
        scenario.timeline += TimelineItem.StatusEvent(
            id = "${threadId.value}-completed-${scenario.sequence++}",
            threadId = threadId,
            title = "Runtime turn interrupted",
            detail = "The shell left the running state by interrupt.",
            turnId = turnId,
        )
    }

    override fun respondToApproval(requestId: String, decision: ApprovalDecision) {
        val ownerScenario = scenarios.values.firstOrNull { scenario ->
            scenario.timeline.any { item ->
                item is TimelineItem.ApprovalRequest && item.requestId == requestId
            }
        } ?: error("Unknown approval request: $requestId")
        val threadId = ownerScenario.thread.id
        val scenario = requireNotNull(scenarios[threadId]) { "Unknown thread: ${threadId.value}" }

        val approvalIndex = scenario.timeline.indexOfFirst { item ->
            item is TimelineItem.ApprovalRequest && item.requestId == requestId
        }
        check(approvalIndex >= 0) { "Unknown approval: $requestId" }

        val currentApproval = scenario.timeline[approvalIndex] as TimelineItem.ApprovalRequest
        scenario.timeline[approvalIndex] = currentApproval.copy(
            resolved = true,
            decision = decision,
        )

        val isAccepted = decision == ApprovalDecision.AcceptOnce || decision == ApprovalDecision.AcceptSession
        val resolvedStatus = if (isAccepted) {
            ThreadStatus.Idle
        } else {
            ThreadStatus.Failed
        }
        val resolvedPreview = when (decision) {
            ApprovalDecision.AcceptOnce -> "Approval accepted for one action"
            ApprovalDecision.AcceptSession -> "Approval accepted for session"
            ApprovalDecision.Decline -> "Approval declined"
            ApprovalDecision.Cancel -> "Approval cancelled"
        }

        scenario.thread = scenario.thread.copy(
            status = resolvedStatus,
            preview = resolvedPreview,
            lastUpdatedAtEpochMs = scenario.thread.lastUpdatedAtEpochMs + 1_000,
        )
        scenario.timeline += TimelineItem.StatusEvent(
            id = "${threadId.value}-approval-${scenario.sequence++}",
            threadId = threadId,
            title = "Approval resolved",
            detail = "Decision: ${decision.name}; status=${resolvedStatus.name}",
        )
    }

    private class ThreadScenario(
        var thread: CodexThread,
        val timeline: MutableList<TimelineItem>,
        var sequence: Int = 1,
    )
}
