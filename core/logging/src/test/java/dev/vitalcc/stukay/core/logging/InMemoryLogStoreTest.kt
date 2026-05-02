package dev.vitalcc.stukay.core.logging

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryLogStoreTest {
    @Test
    fun recentReturnsNewestFirstAndDropsOverflow() {
        val store = InMemoryLogStore(capacity = 3)

        store.log(event(name = "one"))
        store.log(event(name = "two"))
        store.log(event(name = "three"))
        store.log(event(name = "four"))

        assertEquals(
            listOf("four", "three", "two"),
            store.recent(limit = 10).map { it.eventName },
        )
    }

    @Test
    fun recentRespectsRequestedLimit() {
        val store = InMemoryLogStore(capacity = 5)

        store.log(event(name = "one"))
        store.log(event(name = "two"))
        store.log(event(name = "three"))

        assertEquals(
            listOf("three", "two"),
            store.recent(limit = 2).map { it.eventName },
        )
    }

    @Test
    fun latestWarningOrErrorReturnsMostRecentProblemEvent() {
        val store = InMemoryLogStore(capacity = 5)

        store.log(event(name = "info-1", level = LogLevel.Info))
        store.log(event(name = "warn-1", level = LogLevel.Warn))
        store.log(event(name = "error-1", level = LogLevel.Error))

        assertEquals("error-1", store.latestWarningOrError()?.eventName)
    }

    @Test
    fun latestWarningOrErrorReturnsNullWhenNoProblemEventsExist() {
        val store = InMemoryLogStore(capacity = 5)

        store.log(event(name = "debug-1", level = LogLevel.Debug))
        store.log(event(name = "info-1", level = LogLevel.Info))

        assertNull(store.latestWarningOrError())
    }

    private fun event(
        name: String,
        level: LogLevel = LogLevel.Info,
    ): LogEvent = LogEvent(
        timestampUtc = Instant.parse("2026-05-02T12:00:00Z"),
        level = level,
        area = LogArea.App,
        eventName = name,
        messageHuman = "message-$name",
        fields = mapOf("screen" to "test"),
    )
}
