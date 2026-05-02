package dev.vitalcc.stukay.feature.diagnostics.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.DiagnosticsSummary
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsRoute(
    logger: AppLogger,
    currentScreenRoute: String,
    diagnosticsSummary: DiagnosticsSummary,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        logger.info(
            logEvent(
                area = LogArea.Diagnostics,
                eventName = "diagnostics_opened",
                messageHuman = "Diagnostics screen opened",
                fields = mapOf("screen" to "diagnostics"),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Diagnostics")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ExpressiveCard(
                    title = "Runtime summary",
                    subtitle = "Foundation placeholder before logging core and diagnostics provider.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                        Text(text = "Current route: $currentScreenRoute")
                        Text(text = "Session started: ${diagnosticsSummary.sessionStartedAt}")
                        Text(text = "Total logs: ${diagnosticsSummary.totalLogs}")
                        Text(text = "Diagnostics is intentionally routed through Settings.")
                    }
                }
            }

            item {
                ExpressiveCard(
                    title = "Latest warning or error",
                    subtitle = diagnosticsSummary.latestWarningOrError?.eventName ?: "No warning/error yet",
                ) {
                    val latest = diagnosticsSummary.latestWarningOrError
                    if (latest == null) {
                        Text(text = "Current shell has not emitted warning/error events yet.")
                    } else {
                        Text(
                            text = latest.messageHuman,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            items(diagnosticsSummary.recentLogs) { event ->
                ExpressiveCard(
                    title = "${event.level.name} · ${event.eventName}",
                    subtitle = event.area.name,
                ) {
                    Text(
                        text = event.messageHuman,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (event.fields.isNotEmpty()) {
                        Text(
                            text = event.fields.entries.joinToString { (key, value) -> "$key=$value" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
