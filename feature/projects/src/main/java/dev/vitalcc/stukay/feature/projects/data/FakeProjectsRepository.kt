package dev.vitalcc.stukay.feature.projects.data

import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ProjectStatus

class FakeProjectsRepository : ProjectsRepository {
    private val projects = listOf(
        CodexProject(
            id = ProjectId("main"),
            name = "main",
            cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
            status = ProjectStatus.Active,
            summary = "Основной Pixel-first shell для локального Codex runtime.",
        ),
        CodexProject(
            id = ProjectId("diagnostics"),
            name = "diagnostics",
            cwd = "C:\\Users\\v.vlasov\\Desktop\\Stukay",
            status = ProjectStatus.Idle,
            summary = "Поток по logging, diagnostics и evidence surfaces.",
        ),
    )

    override fun loadProjects(): List<CodexProject> = projects

    override fun loadProject(projectId: ProjectId): CodexProject? = projects.firstOrNull { project ->
        project.id == projectId
    }
}
