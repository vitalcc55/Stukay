package dev.vitalcc.stukay.core.logging

import java.time.Clock

class DiagnosticsSummaryProvider(
    private val store: InMemoryLogStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val sessionStartedAt = clock.instant()

    fun snapshot(
        recentLimit: Int = 25,
        runtimeSnapshot: RuntimeDiagnosticsSnapshot? = null,
    ): DiagnosticsSummary = DiagnosticsSummary(
        sessionStartedAt = sessionStartedAt,
        totalLogs = store.totalAcceptedEvents(),
        latestWarningOrError = store.latestWarningOrError(),
        recentHostConnectionLogs = store.recentMatching(limit = recentLimit) { event ->
            event.area == LogArea.Connection ||
                event.area == LogArea.HostBridge ||
                event.area == LogArea.Security
        },
        recentLogs = store.recent(limit = recentLimit),
        runtimeSnapshot = runtimeSnapshot,
    )
}
