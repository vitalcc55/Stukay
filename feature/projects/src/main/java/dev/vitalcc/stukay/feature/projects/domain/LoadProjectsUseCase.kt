package dev.vitalcc.stukay.feature.projects.domain

import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.feature.projects.data.ProjectsRepository

class LoadProjectsUseCase(
    private val repository: ProjectsRepository,
) {
    operator fun invoke(): List<CodexProject> = repository.loadProjects()
}
