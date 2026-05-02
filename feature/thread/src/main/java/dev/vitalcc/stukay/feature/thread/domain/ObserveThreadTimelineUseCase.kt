package dev.vitalcc.stukay.feature.thread.domain

import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository

class ObserveThreadTimelineUseCase(
    private val repository: ThreadRepository,
) {
    operator fun invoke(threadId: ThreadId): List<TimelineItem> = repository.loadTimeline(threadId)
}
