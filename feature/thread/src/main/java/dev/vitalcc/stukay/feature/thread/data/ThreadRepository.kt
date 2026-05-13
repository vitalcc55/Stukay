package dev.vitalcc.stukay.feature.thread.data

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ThreadHistoryState
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.TurnId

interface ThreadRepository {
    fun loadThreads(projectId: ProjectId): List<CodexThread>

    fun loadThread(threadId: ThreadId): CodexThread?

    fun loadTimeline(threadId: ThreadId): List<TimelineItem>

    fun refreshIndex(): List<CodexThread>

    fun readThreadSummary(threadId: ThreadId): CodexThread?

    fun historyState(threadId: ThreadId): ThreadHistoryState

    fun loadInitialHistory(threadId: ThreadId): ThreadHistoryState

    fun loadOlderHistory(threadId: ThreadId): ThreadHistoryState

    fun resumeThread(threadId: ThreadId): CodexThread?

    fun startTurn(threadId: ThreadId, text: String): TurnId

    fun interruptTurn(threadId: ThreadId, turnId: TurnId)

    fun respondToApproval(requestId: String, decision: ApprovalDecision)
}
