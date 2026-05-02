package dev.vitalcc.stukay.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.vitalcc.stukay.core.logging.AndroidLogcatSink
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.CompositeLogSink
import dev.vitalcc.stukay.core.logging.DiagnosticsSummary
import dev.vitalcc.stukay.core.logging.DiagnosticsSummaryProvider
import dev.vitalcc.stukay.core.logging.InMemoryLogStore
import dev.vitalcc.stukay.core.logging.LogSink
import dev.vitalcc.stukay.core.logging.StructuredLogger

class StukayAppState {
    private val logStore = InMemoryLogStore(capacity = 250)
    private var logRevisionState by mutableIntStateOf(0)

    private val liveSink = LogSink { event ->
        compositeSink.log(event)
        logRevisionState += 1
    }

    private val compositeSink = CompositeLogSink(
        sinks = listOf(
            AndroidLogcatSink(),
            logStore,
        ),
    )

    val logger: AppLogger = StructuredLogger(liveSink)
    private val diagnosticsSummaryProvider = DiagnosticsSummaryProvider(store = logStore)

    var currentScreenRoute by mutableStateOf("projects")
        private set

    fun updateCurrentScreenRoute(route: String) {
        currentScreenRoute = route
    }

    fun diagnosticsSummary(): DiagnosticsSummary {
        val ignored = logRevisionState
        return diagnosticsSummaryProvider.snapshot().copy(
            recentLogs = diagnosticsSummaryProvider.snapshot().recentLogs,
            totalLogs = diagnosticsSummaryProvider.snapshot().totalLogs,
            latestWarningOrError = diagnosticsSummaryProvider.snapshot().latestWarningOrError,
            sessionStartedAt = diagnosticsSummaryProvider.snapshot().sessionStartedAt,
        )
    }
}
