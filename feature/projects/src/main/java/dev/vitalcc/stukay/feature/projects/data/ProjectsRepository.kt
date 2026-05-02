package dev.vitalcc.stukay.feature.projects.data

import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.ProjectId

interface ProjectsRepository {
    fun loadProjects(): List<CodexProject>

    fun loadProject(projectId: ProjectId): CodexProject?
}
