package dev.vitalcc.stukay.feature.thread.data

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.model.TurnId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeThreadRepositoryTest {
    @Test
    fun startTurnMovesThreadIntoRunningState() {
        val repository = FakeThreadRepository()

        repository.startTurn(ThreadId("thread-review-shell"), "hello")
        val thread = repository.loadThread(ThreadId("thread-review-shell"))

        assertEquals(ThreadStatus.Running, thread?.status)
    }

    @Test
    fun interruptTurnMovesThreadIntoInterruptedStateAndAppendsStatusEvent() {
        val repository = FakeThreadRepository()

        val turnId = repository.startTurn(ThreadId("thread-review-shell"), "hello")
        repository.interruptTurn(ThreadId("thread-review-shell"), turnId)
        val thread = repository.loadThread(ThreadId("thread-review-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-review-shell"))

        assertEquals(ThreadStatus.Interrupted, thread?.status)
        assertTrue(timeline.last().id.contains("completed"))
    }

    @Test
    fun respondToApprovalMarksApprovalAsResolvedAndReturnsIdleThread() {
        val repository = FakeThreadRepository()

        repository.respondToApproval("approval-shell-1", ApprovalDecision.AcceptOnce)

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))

        assertEquals(ThreadStatus.Idle, thread?.status)
        assertTrue(timeline.any { it.id == "approval-shell-1" && it is dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest && it.resolved })
    }

    @Test
    fun respondToApprovalDeclineMarksThreadFailedAndKeepsDecision() {
        val repository = FakeThreadRepository()

        repository.respondToApproval("approval-shell-1", ApprovalDecision.Decline)

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))
        val approval = timeline.first { it.id == "approval-shell-1" } as dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest

        assertEquals(ThreadStatus.Failed, thread?.status)
        assertEquals(ApprovalDecision.Decline, approval.decision)
        assertTrue(approval.resolved)
    }

    @Test
    fun respondToApprovalCancelMarksThreadFailedAndKeepsDecision() {
        val repository = FakeThreadRepository()

        repository.respondToApproval("approval-shell-1", ApprovalDecision.Cancel)

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))
        val approval = timeline.first { it.id == "approval-shell-1" } as dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest

        assertEquals(ThreadStatus.Failed, thread?.status)
        assertEquals(ApprovalDecision.Cancel, approval.decision)
        assertTrue(approval.resolved)
    }

    @Test
    fun respondToApprovalAcceptSessionReturnsIdleThreadAndKeepsDecision() {
        val repository = FakeThreadRepository()

        repository.respondToApproval("approval-shell-1", ApprovalDecision.AcceptSession)

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))
        val approval = timeline.first { it.id == "approval-shell-1" } as dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest

        assertEquals(ThreadStatus.Idle, thread?.status)
        assertEquals(ApprovalDecision.AcceptSession, approval.decision)
        assertTrue(approval.resolved)
    }
}
