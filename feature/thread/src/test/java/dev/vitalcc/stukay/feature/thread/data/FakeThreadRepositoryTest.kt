package dev.vitalcc.stukay.feature.thread.data

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeThreadRepositoryTest {
    @Test
    fun startFakeTurnMovesThreadIntoRunningState() {
        val repository = FakeThreadRepository()

        repository.startFakeTurn(ThreadId("thread-review-shell"))
        val thread = repository.loadThread(ThreadId("thread-review-shell"))

        assertEquals(ThreadStatus.Running, thread?.status)
    }

    @Test
    fun completeFakeTurnMovesThreadIntoCompletedStateAndAppendsStatusEvent() {
        val repository = FakeThreadRepository()

        repository.startFakeTurn(ThreadId("thread-review-shell"))
        repository.completeFakeTurn(ThreadId("thread-review-shell"))
        val thread = repository.loadThread(ThreadId("thread-review-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-review-shell"))

        assertEquals(ThreadStatus.Completed, thread?.status)
        assertTrue(timeline.last().id.contains("completed"))
    }

    @Test
    fun resolveApprovalMarksApprovalAsResolvedAndCompletesThread() {
        val repository = FakeThreadRepository()

        repository.resolveApproval(
            threadId = ThreadId("thread-active-shell"),
            approvalId = ApprovalId("approval-shell-1"),
            decision = ApprovalDecision.AcceptOnce,
        )

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))

        assertEquals(ThreadStatus.Completed, thread?.status)
        assertTrue(timeline.any { it.id == "approval-shell-1" && it is dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest && it.resolved })
    }

    @Test
    fun resolveApprovalDeclineMarksThreadFailedAndKeepsDecision() {
        val repository = FakeThreadRepository()

        repository.resolveApproval(
            threadId = ThreadId("thread-active-shell"),
            approvalId = ApprovalId("approval-shell-1"),
            decision = ApprovalDecision.Decline,
        )

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))
        val approval = timeline.first { it.id == "approval-shell-1" } as dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest

        assertEquals(ThreadStatus.Failed, thread?.status)
        assertEquals(ApprovalDecision.Decline, approval.decision)
        assertTrue(approval.resolved)
    }

    @Test
    fun resolveApprovalCancelMarksThreadFailedAndKeepsDecision() {
        val repository = FakeThreadRepository()

        repository.resolveApproval(
            threadId = ThreadId("thread-active-shell"),
            approvalId = ApprovalId("approval-shell-1"),
            decision = ApprovalDecision.Cancel,
        )

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))
        val approval = timeline.first { it.id == "approval-shell-1" } as dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest

        assertEquals(ThreadStatus.Failed, thread?.status)
        assertEquals(ApprovalDecision.Cancel, approval.decision)
        assertTrue(approval.resolved)
    }

    @Test
    fun resolveApprovalAcceptSessionCompletesThreadAndKeepsDecision() {
        val repository = FakeThreadRepository()

        repository.resolveApproval(
            threadId = ThreadId("thread-active-shell"),
            approvalId = ApprovalId("approval-shell-1"),
            decision = ApprovalDecision.AcceptSession,
        )

        val thread = repository.loadThread(ThreadId("thread-active-shell"))
        val timeline = repository.loadTimeline(ThreadId("thread-active-shell"))
        val approval = timeline.first { it.id == "approval-shell-1" } as dev.vitalcc.stukay.core.model.TimelineItem.ApprovalRequest

        assertEquals(ThreadStatus.Completed, thread?.status)
        assertEquals(ApprovalDecision.AcceptSession, approval.decision)
        assertTrue(approval.resolved)
    }
}
