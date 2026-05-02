package dev.vitalcc.stukay.core.model

enum class ThreadStatus {
    Idle,
    Running,
    WaitingForApproval,
    Completed,
    Failed,
    Archived,
}

fun ThreadStatus.canStartFakeTurn(): Boolean = this == ThreadStatus.Idle || this == ThreadStatus.Completed

fun ThreadStatus.canCompleteFakeTurn(): Boolean = this == ThreadStatus.Running

fun ThreadStatus.canResolveApproval(): Boolean = this == ThreadStatus.WaitingForApproval

data class CodexThread(
    val id: ThreadId,
    val projectId: ProjectId,
    val title: String,
    val preview: String,
    val status: ThreadStatus,
    val lastUpdatedAtEpochMs: Long,
)
