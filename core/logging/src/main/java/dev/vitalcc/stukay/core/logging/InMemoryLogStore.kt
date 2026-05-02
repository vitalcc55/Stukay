package dev.vitalcc.stukay.core.logging

class InMemoryLogStore(
    private val capacity: Int = 200,
) : LogSink {
    private val entries = ArrayDeque<LogEvent>()
    private var totalAcceptedEvents: Int = 0

    override fun log(event: LogEvent) {
        entries.addFirst(event)
        totalAcceptedEvents += 1
        while (entries.size > capacity) {
            entries.removeLast()
        }
    }

    fun recent(limit: Int = capacity): List<LogEvent> {
        if (limit <= 0) {
            return emptyList()
        }
        return entries.take(limit)
    }

    fun latestWarningOrError(): LogEvent? = entries.firstOrNull { event ->
        event.level == LogLevel.Warn || event.level == LogLevel.Error
    }

    fun size(): Int = entries.size

    fun totalAcceptedEvents(): Int = totalAcceptedEvents
}
