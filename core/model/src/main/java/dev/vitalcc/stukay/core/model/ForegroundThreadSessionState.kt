package dev.vitalcc.stukay.core.model

enum class ForegroundThreadStreamState {
    Idle,
    Hydrating,
    Ready,
    Streaming,
    Interrupting,
    AwaitingReconnect,
    Failed,
}

enum class ForegroundThreadBlockedReason {
    WaitingOnApproval,
    WaitingOnUserInput,
}

data class ForegroundThreadSessionState(
    val activeThreadId: ThreadId? = null,
    val activeTurnId: TurnId? = null,
    val composerDraft: String = "",
    val streamState: ForegroundThreadStreamState = ForegroundThreadStreamState.Idle,
    val blockedReason: ForegroundThreadBlockedReason? = null,
    val pendingApprovals: List<TimelineItem.ApprovalRequest> = emptyList(),
    val historyState: ThreadHistoryState = ThreadHistoryState(),
    val reconnectGeneration: Int = 0,
    val lastRecoverAttemptAtEpochMs: Long? = null,
    val lastTurnId: TurnId? = null,
    val lastRequestId: String? = null,
    val lastItemId: String? = null,
    val lastError: String? = null,
)
