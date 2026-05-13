package dev.vitalcc.stukay.core.logging

data class RuntimeDiagnosticsSnapshot(
    val activeThreadId: String? = null,
    val activeSessionId: String? = null,
    val activeTurnId: String? = null,
    val streamState: String = "idle",
    val blockedReason: String? = null,
    val pendingApprovalSummary: String? = null,
    val historyNextCursor: String? = null,
    val hasOlderHistory: Boolean = false,
    val isLoadingOlderHistory: Boolean = false,
    val reconnectGeneration: Int = 0,
    val lastRecoverAttemptAtEpochMs: Long? = null,
    val lastTurnId: String? = null,
    val lastRequestId: String? = null,
    val lastItemId: String? = null,
)
