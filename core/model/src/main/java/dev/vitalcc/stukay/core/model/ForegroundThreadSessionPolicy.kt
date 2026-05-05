package dev.vitalcc.stukay.core.model

fun ForegroundThreadSessionState.shouldKeepOffscreen(): Boolean =
    pendingApprovals.isNotEmpty() ||
        activeTurnId != null ||
        streamState in setOf(
            ForegroundThreadStreamState.Hydrating,
            ForegroundThreadStreamState.Streaming,
            ForegroundThreadStreamState.Interrupting,
            ForegroundThreadStreamState.AwaitingReconnect,
            ForegroundThreadStreamState.Failed,
        )

fun ForegroundThreadSessionState.canSendPrompt(runtimePathAvailable: Boolean): Boolean =
    runtimePathAvailable &&
        composerDraft.trim().isNotEmpty() &&
        activeTurnId == null &&
        blockedReason == null &&
        streamState in setOf(
            ForegroundThreadStreamState.Idle,
            ForegroundThreadStreamState.Ready,
        )

fun ForegroundThreadSessionState.canStopTurn(runtimePathAvailable: Boolean): Boolean =
    runtimePathAvailable &&
        activeTurnId != null &&
        streamState in setOf(
            ForegroundThreadStreamState.Streaming,
            ForegroundThreadStreamState.Ready,
        )
