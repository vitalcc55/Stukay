package dev.vitalcc.stukay.runtime

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository

/**
 * Runtime adapter seam for future Host Bridge backed thread/timeline transport.
 * This slice still delegates to fake thread data, but the app shell now depends
 * on a runtime-owned adapter instead of the fake repository instance directly.
 */
class RuntimeThreadRepository(
    private val delegate: ThreadRepository,
) : ThreadRepository {
    override fun loadThreads(projectId: ProjectId): List<CodexThread> = delegate.loadThreads(projectId)

    override fun loadThread(threadId: ThreadId): CodexThread? = delegate.loadThread(threadId)

    override fun loadTimeline(threadId: ThreadId): List<TimelineItem> = delegate.loadTimeline(threadId)

    override fun startFakeTurn(threadId: ThreadId): CodexThread = delegate.startFakeTurn(threadId)

    override fun completeFakeTurn(threadId: ThreadId): CodexThread = delegate.completeFakeTurn(threadId)

    override fun resolveApproval(
        threadId: ThreadId,
        approvalId: ApprovalId,
        decision: ApprovalDecision,
    ): CodexThread = delegate.resolveApproval(threadId, approvalId, decision)
}
