package dev.vitalcc.stukay.core.logging

import java.time.Clock

class DiagnosticsSummaryProvider(
    private val store: InMemoryLogStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val sessionStartedAt = clock.instant()

    fun snapshot(recentLimit: Int = 25): DiagnosticsSummary = DiagnosticsSummary(
        sessionStartedAt = sessionStartedAt,
        totalLogs = store.totalAcceptedEvents(),
        latestWarningOrError = store.latestWarningOrError(),
        recentLogs = store.recent(limit = recentLimit),
    )
}
