package dev.vitalcc.stukay.core.logging

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StructuredLoggerTest {
    @Test
    fun infoForcesInfoLevelAndRoutesToSink() {
        val sink = RecordingSink()
        val logger = StructuredLogger(sink)

        logger.info(baseEvent(level = LogLevel.Debug, name = "info-event"))

        assertEquals(1, sink.events.size)
        assertEquals(LogLevel.Info, sink.events.single().level)
        assertEquals("info-event", sink.events.single().eventName)
    }

    @Test
    fun errorForcesErrorLevelAndRoutesToSink() {
        val sink = RecordingSink()
        val logger = StructuredLogger(sink)

        logger.error(baseEvent(level = LogLevel.Info, name = "error-event"))

        assertEquals(1, sink.events.size)
        assertEquals(LogLevel.Error, sink.events.single().level)
        assertEquals("error-event", sink.events.single().eventName)
    }

    private fun baseEvent(
        level: LogLevel,
        name: String,
    ): LogEvent = LogEvent(
        timestampUtc = Instant.parse("2026-05-02T12:00:00Z"),
        level = level,
        area = LogArea.Navigation,
        eventName = name,
        messageHuman = "message-$name",
        fields = emptyMap(),
    )

    private class RecordingSink : LogSink {
        val events = mutableListOf<LogEvent>()

        override fun log(event: LogEvent) {
            events += event
        }
    }
}
