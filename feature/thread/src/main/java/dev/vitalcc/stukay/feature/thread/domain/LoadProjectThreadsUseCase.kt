package dev.vitalcc.stukay.feature.thread.domain

import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository

class LoadProjectThreadsUseCase(
    private val repository: ThreadRepository,
) {
    operator fun invoke(projectId: ProjectId): List<CodexThread> = repository.loadThreads(projectId)
}
