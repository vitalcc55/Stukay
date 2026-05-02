package dev.vitalcc.stukay.feature.thread.domain

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository

class ResolveFakeApprovalUseCase(
    private val repository: ThreadRepository,
) {
    operator fun invoke(
        threadId: ThreadId,
        approvalId: ApprovalId,
        decision: ApprovalDecision,
    ): CodexThread = repository.resolveApproval(threadId, approvalId, decision)
}
