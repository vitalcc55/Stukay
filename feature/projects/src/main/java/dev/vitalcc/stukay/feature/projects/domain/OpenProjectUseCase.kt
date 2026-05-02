package dev.vitalcc.stukay.feature.projects.domain

import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.feature.projects.data.ProjectsRepository

class OpenProjectUseCase(
    private val repository: ProjectsRepository,
) {
    operator fun invoke(projectId: ProjectId): CodexProject? = repository.loadProject(projectId)
}
