package dev.vitalcc.stukay.core.logging

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticsSummaryProviderTest {
    @Test
    fun snapshotIncludesRecentLogsAndLatestProblem() {
        val store = InMemoryLogStore(capacity = 5)
        val provider = DiagnosticsSummaryProvider(
            store = store,
            clock = fixedClock,
        )

        store.log(event(name = "info-1", level = LogLevel.Info))
        store.log(event(name = "warn-1", level = LogLevel.Warn))
        store.log(event(name = "info-2", level = LogLevel.Info))

        val summary = provider.snapshot(recentLimit = 2)

        assertEquals(3, summary.totalLogs)
        assertEquals("warn-1", summary.latestWarningOrError?.eventName)
        assertEquals(listOf("info-2", "warn-1"), summary.recentLogs.map { it.eventName })
        assertEquals(Instant.parse("2026-05-02T12:00:00Z"), summary.sessionStartedAt)
    }

    @Test
    fun snapshotHasNullProblemWhenWarningsAndErrorsAreAbsent() {
        val store = InMemoryLogStore(capacity = 5)
        val provider = DiagnosticsSummaryProvider(
            store = store,
            clock = fixedClock,
        )

        store.log(event(name = "debug-1", level = LogLevel.Debug))

        val summary = provider.snapshot(recentLimit = 10)

        assertNull(summary.latestWarningOrError)
    }

    @Test
    fun snapshotReportsTotalAcceptedEventsNotOnlyCurrentBufferSize() {
        val store = InMemoryLogStore(capacity = 2)
        val provider = DiagnosticsSummaryProvider(
            store = store,
            clock = fixedClock,
        )

        store.log(event(name = "one", level = LogLevel.Info))
        store.log(event(name = "two", level = LogLevel.Info))
        store.log(event(name = "three", level = LogLevel.Info))

        val summary = provider.snapshot(recentLimit = 10)

        assertEquals(3, summary.totalLogs)
        assertEquals(listOf("three", "two"), summary.recentLogs.map { it.eventName })
    }

    private fun event(
        name: String,
        level: LogLevel,
    ): LogEvent = LogEvent(
        timestampUtc = Instant.parse("2026-05-02T12:00:00Z"),
        level = level,
        area = LogArea.Diagnostics,
        eventName = name,
        messageHuman = "message-$name",
        fields = emptyMap(),
    )

    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-05-02T12:00:00Z"),
        ZoneOffset.UTC,
    )
}
