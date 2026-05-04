package dev.vitalcc.stukay.feature.thread.domain

import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.feature.thread.data.FakeThreadRepository
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
    fun openThreadUseCaseReadsFakeRepositorySnapshot() {
        val repository = FakeThreadRepository()
        val useCase = OpenThreadUseCase(repository)

        val thread = useCase(ThreadId("thread-review-shell"))

        assertTrue(thread != null)
        assertTrue(thread?.id?.value == "thread-review-shell")
    }
}
