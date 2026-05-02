package dev.vitalcc.stukay.feature.thread.data

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.ApprovalKind
import dev.vitalcc.stukay.core.model.ApprovalRisk
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.CommandRunStatus
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.canCompleteFakeTurn
import dev.vitalcc.stukay.core.model.canResolveApproval
import dev.vitalcc.stukay.core.model.canStartFakeTurn

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
                ),
            ),
        ),
        ThreadId("thread-review-shell") to ThreadScenario(
            thread = CodexThread(
                id = ThreadId("thread-review-shell"),
                projectId = ProjectId("diagnostics"),
                title = "Diagnostics follow-up",
                preview = "Готов к запуску fake run",
                status = ThreadStatus.Completed,
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

    override fun startFakeTurn(threadId: ThreadId): CodexThread {
        val scenario = requireNotNull(scenarios[threadId]) { "Unknown thread: ${threadId.value}" }
        check(scenario.thread.status.canStartFakeTurn()) { "Cannot start fake turn from ${scenario.thread.status}" }

        scenario.thread = scenario.thread.copy(
            status = ThreadStatus.Running,
            preview = "Fake run is currently active",
            lastUpdatedAtEpochMs = scenario.thread.lastUpdatedAtEpochMs + 1_000,
        )
        scenario.timeline += TimelineItem.StatusEvent(
            id = "${threadId.value}-started-${scenario.sequence++}",
            threadId = threadId,
            title = "Fake run started",
            detail = "The shell entered a running state.",
        )

        return scenario.thread
    }

    override fun completeFakeTurn(threadId: ThreadId): CodexThread {
        val scenario = requireNotNull(scenarios[threadId]) { "Unknown thread: ${threadId.value}" }
        check(scenario.thread.status.canCompleteFakeTurn()) { "Cannot complete fake turn from ${scenario.thread.status}" }

        scenario.thread = scenario.thread.copy(
            status = ThreadStatus.Completed,
            preview = "Fake run completed",
            lastUpdatedAtEpochMs = scenario.thread.lastUpdatedAtEpochMs + 1_000,
        )
        scenario.timeline += TimelineItem.StatusEvent(
            id = "${threadId.value}-completed-${scenario.sequence++}",
            threadId = threadId,
            title = "Fake run completed",
            detail = "The shell left the running state successfully.",
        )

        return scenario.thread
    }

    override fun resolveApproval(
        threadId: ThreadId,
        approvalId: ApprovalId,
        decision: ApprovalDecision,
    ): CodexThread {
        val scenario = requireNotNull(scenarios[threadId]) { "Unknown thread: ${threadId.value}" }
        check(scenario.thread.status.canResolveApproval()) { "Cannot resolve approval from ${scenario.thread.status}" }

        val approvalIndex = scenario.timeline.indexOfFirst { item ->
            item is TimelineItem.ApprovalRequest && item.approvalId == approvalId
        }
        check(approvalIndex >= 0) { "Unknown approval: ${approvalId.value}" }

        val currentApproval = scenario.timeline[approvalIndex] as TimelineItem.ApprovalRequest
        scenario.timeline[approvalIndex] = currentApproval.copy(
            resolved = true,
            decision = decision,
        )

        val isAccepted = decision == ApprovalDecision.AcceptOnce || decision == ApprovalDecision.AcceptSession
        val resolvedStatus = if (isAccepted) {
            ThreadStatus.Completed
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

        return scenario.thread
    }

    private class ThreadScenario(
        var thread: CodexThread,
        val timeline: MutableList<TimelineItem>,
        var sequence: Int = 1,
    )
}
