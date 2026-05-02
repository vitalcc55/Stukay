package dev.vitalcc.stukay.feature.thread.data

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.TimelineItem

interface ThreadRepository {
    fun loadThreads(projectId: ProjectId): List<CodexThread>

    fun loadThread(threadId: ThreadId): CodexThread?

    fun loadTimeline(threadId: ThreadId): List<TimelineItem>

    fun startFakeTurn(threadId: ThreadId): CodexThread

    fun completeFakeTurn(threadId: ThreadId): CodexThread

    fun resolveApproval(
        threadId: ThreadId,
        approvalId: ApprovalId,
        decision: ApprovalDecision,
    ): CodexThread
}
