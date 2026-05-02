package dev.vitalcc.stukay.feature.thread.domain

import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.feature.thread.data.FakeThreadRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadUseCasesTest {
    @Test
    fun observeThreadTimelineReturnsSeededTypedTimeline() {
        val repository = FakeThreadRepository()
        val useCase = ObserveThreadTimelineUseCase(repository)

        val timeline = useCase(ThreadId("thread-active-shell"))

        assertTrue(timeline.isNotEmpty())
        assertTrue(timeline.first().threadId.value == "thread-active-shell")
    }

    @Test
    fun startFakeTurnUseCaseDelegatesToRepository() {
        val repository = FakeThreadRepository()
        val useCase = StartFakeTurnUseCase(repository)

        val thread = useCase(ThreadId("thread-review-shell"))

        assertEquals(ThreadStatus.Running, thread.status)
    }
}
