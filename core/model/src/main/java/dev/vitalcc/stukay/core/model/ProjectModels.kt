package dev.vitalcc.stukay.core.model

enum class ProjectStatus {
    Active,
    Idle,
    Archived,
}

data class CodexProject(
    val id: ProjectId,
    val name: String,
    val cwd: String,
    val status: ProjectStatus,
    val summary: String,
)
