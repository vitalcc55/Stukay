package dev.vitalcc.stukay.core.logging

import java.time.Instant

data class DiagnosticsSummary(
    val sessionStartedAt: Instant,
    val totalLogs: Int,
    val latestWarningOrError: LogEvent?,
    val recentLogs: List<LogEvent>,
)
