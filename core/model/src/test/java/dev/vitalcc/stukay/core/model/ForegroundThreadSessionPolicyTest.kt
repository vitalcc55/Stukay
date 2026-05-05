package dev.vitalcc.stukay.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundThreadSessionPolicyTest {
    @Test
    fun failedSessionStaysRetainedOffscreenForRecovery() {
        val session = ForegroundThreadSessionState(
            activeThreadId = ThreadId("thread-1"),
            streamState = ForegroundThreadStreamState.Failed,
        )

        assertTrue(session.shouldKeepOffscreen())
    }

    @Test
    fun sendPromptRequiresReadyOrIdleStateAndRuntimeAvailability() {
        val ready = ForegroundThreadSessionState(
            composerDraft = "hello",
            streamState = ForegroundThreadStreamState.Ready,
        )
        val failed = ready.copy(streamState = ForegroundThreadStreamState.Failed)
        val streaming = ready.copy(streamState = ForegroundThreadStreamState.Streaming)

        assertTrue(ready.canSendPrompt(runtimePathAvailable = true))
        assertFalse(ready.canSendPrompt(runtimePathAvailable = false))
        assertFalse(failed.canSendPrompt(runtimePathAvailable = true))
        assertFalse(streaming.canSendPrompt(runtimePathAvailable = true))
    }

    @Test
    fun sendPromptBlocksWhenTurnIsActiveOrSessionIsBlocked() {
        val activeTurn = ForegroundThreadSessionState(
            composerDraft = "hello",
            streamState = ForegroundThreadStreamState.Ready,
            activeTurnId = TurnId("turn-1"),
        )
        val blocked = ForegroundThreadSessionState(
            composerDraft = "hello",
            streamState = ForegroundThreadStreamState.Ready,
            blockedReason = ForegroundThreadBlockedReason.WaitingOnApproval,
        )

        assertFalse(activeTurn.canSendPrompt(runtimePathAvailable = true))
        assertFalse(blocked.canSendPrompt(runtimePathAvailable = true))
    }

    @Test
    fun stopTurnRequiresStreamingStateActiveTurnAndRuntimeAvailability() {
        val streaming = ForegroundThreadSessionState(
            activeTurnId = TurnId("turn-1"),
            streamState = ForegroundThreadStreamState.Streaming,
        )
        val interrupting = streaming.copy(streamState = ForegroundThreadStreamState.Interrupting)
        val ready = streaming.copy(streamState = ForegroundThreadStreamState.Ready)

        assertTrue(streaming.canStopTurn(runtimePathAvailable = true))
        assertTrue(ready.canStopTurn(runtimePathAvailable = true))
        assertFalse(streaming.canStopTurn(runtimePathAvailable = false))
        assertFalse(interrupting.canStopTurn(runtimePathAvailable = true))
    }
}
