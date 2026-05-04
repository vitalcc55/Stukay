package dev.vitalcc.stukay.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadStatusTest {
    @Test
    fun runningThreadStatusRemainsActiveUntilTerminalTurnEvent() {
        assertTrue(ThreadStatus.Running == ThreadStatus.Running)
        assertFalse(ThreadStatus.Running == ThreadStatus.Idle)
    }

    @Test
    fun waitingStatesStayDistinctForApprovalAndUserInput() {
        assertFalse(ThreadStatus.WaitingForApproval == ThreadStatus.WaitingForUserInput)
        assertTrue(ThreadStatus.WaitingForApproval != ThreadStatus.Running)
    }

    @Test
    fun interruptedThreadStaysDistinctFromIdleAndFailure() {
        assertFalse(ThreadStatus.Interrupted == ThreadStatus.Idle)
        assertFalse(ThreadStatus.Interrupted == ThreadStatus.Failed)
    }
}
