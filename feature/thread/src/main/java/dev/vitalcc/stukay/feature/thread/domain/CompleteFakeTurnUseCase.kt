package dev.vitalcc.stukay.feature.thread.domain

import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository

class CompleteFakeTurnUseCase(
    private val repository: ThreadRepository,
) {
    operator fun invoke(threadId: ThreadId): CodexThread = repository.completeFakeTurn(threadId)
}
