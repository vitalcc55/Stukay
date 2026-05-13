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
    val sessionId: String? = null,
    val ephemeral: Boolean = false,
    val cwd: String? = null,
    val sourceKind: String? = null,
    val threadSource: String? = null,
)

data class ThreadHistoryState(
    val nextCursor: String? = null,
    val backwardsCursor: String? = null,
    val hasOlderHistory: Boolean = false,
    val isLoadingOlderHistory: Boolean = false,
)
