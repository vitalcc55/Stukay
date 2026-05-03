package dev.vitalcc.stukay.runtime

import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.feature.projects.data.ProjectsRepository

/**
 * Runtime adapter seam for future Host Bridge backed project data.
 * The current slice still delegates to fake data, but app state no longer owns
 * the raw fake repository directly.
 */
class RuntimeProjectsRepository(
    private val delegate: ProjectsRepository,
) : ProjectsRepository {
    override fun loadProjects(): List<CodexProject> = delegate.loadProjects()

    override fun loadProject(projectId: ProjectId): CodexProject? = delegate.loadProject(projectId)
}
