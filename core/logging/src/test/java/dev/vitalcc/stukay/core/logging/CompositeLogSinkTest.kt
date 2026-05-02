package dev.vitalcc.stukay.core.logging

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeLogSinkTest {
    @Test
    fun logWritesEventToEveryChildSink() {
        val first = RecordingSink()
        val second = RecordingSink()
        val sink = CompositeLogSink(listOf(first, second))
        val event = LogEvent(
            timestampUtc = Instant.parse("2026-05-02T12:00:00Z"),
            level = LogLevel.Info,
            area = LogArea.App,
            eventName = "fan-out",
            messageHuman = "fan-out message",
            fields = emptyMap(),
        )

        sink.log(event)

        assertEquals(listOf(event), first.events)
        assertEquals(listOf(event), second.events)
    }

    private class RecordingSink : LogSink {
        val events = mutableListOf<LogEvent>()

        override fun log(event: LogEvent) {
            events += event
        }
    }
}
