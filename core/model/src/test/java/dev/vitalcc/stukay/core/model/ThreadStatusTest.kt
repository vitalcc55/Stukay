package dev.vitalcc.stukay.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadStatusTest {
    @Test
    fun runningThreadCanBeCompletedButCannotStartAgain() {
        assertFalse(ThreadStatus.Running.canStartFakeTurn())
        assertTrue(ThreadStatus.Running.canCompleteFakeTurn())
    }

    @Test
    fun waitingForApprovalCanResolveApprovalButCannotStartTurn() {
        assertTrue(ThreadStatus.WaitingForApproval.canResolveApproval())
        assertFalse(ThreadStatus.WaitingForApproval.canStartFakeTurn())
    }

    @Test
    fun completedThreadCanStartNewFakeTurn() {
        assertTrue(ThreadStatus.Completed.canStartFakeTurn())
    }
}
