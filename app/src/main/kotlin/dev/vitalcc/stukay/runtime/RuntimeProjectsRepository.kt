package dev.vitalcc.stukay.runtime

import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.feature.projects.data.ProjectsRepository

class RuntimeProjectsRepository(
    private val store: RuntimeThreadStore,
) : ProjectsRepository {
    override fun loadProjects(): List<CodexProject> = store.loadProjects()

    override fun loadProject(projectId: ProjectId): CodexProject? = store.loadProject(projectId)
}
