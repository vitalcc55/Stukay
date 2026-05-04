package dev.vitalcc.stukay.core.model

enum class ThreadStatus {
    Idle,
    Running,
    WaitingForApproval,
    WaitingForUserInput,
    Interrupted,
    Failed,
    SystemError,
    Archived,
}

data class CodexThread(
    val id: ThreadId,
    val projectId: ProjectId,
    val title: String,
    val preview: String,
    val status: ThreadStatus,
    val lastUpdatedAtEpochMs: Long,
    val cwd: String? = null,
    val sourceKind: String? = null,
)
