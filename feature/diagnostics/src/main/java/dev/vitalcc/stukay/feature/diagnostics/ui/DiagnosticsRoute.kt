package dev.vitalcc.stukay.feature.diagnostics.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.vitalcc.stukay.core.model.HostBridgeConnectionState
import dev.vitalcc.stukay.core.model.RouteContext
import dev.vitalcc.stukay.core.model.hostBridgeEndpointDisplayValue
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.layout.ScreenFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsRoute(
    logger: AppLogger,
    currentRouteContext: RouteContext,
    inspectedRouteContext: RouteContext,
    diagnosticsSummary: DiagnosticsSummary,
    hostBridgeState: HostBridgeConnectionState,
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
        ScreenFrame(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 20.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ExpressiveCard(
                        title = "Runtime summary",
                        subtitle = "Live shell diagnostics snapshot.",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                            Text(text = "Current route: ${currentRouteContext.routePattern}")
                            inspectedRouteContext.projectId?.let { projectId ->
                                Text(text = "Inspected projectId: $projectId")
                            }
                            inspectedRouteContext.threadId?.let { threadId ->
                                Text(text = "Inspected threadId: $threadId")
                            }
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

                item {
                    ExpressiveCard(
                        title = "Host Bridge summary",
                        subtitle = hostBridgeState.phase.name,
                    ) {
                        val pairedHost = hostBridgeState.pairedHost
                        if (pairedHost == null) {
                            Text(text = "Pairing payload еще не сохранен.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "Host: ${pairedHost.hostLabel}")
                                Text(text = "Endpoint: ${hostBridgeEndpointDisplayValue(pairedHost.endpoint)}")
                                Text(text = "Transport: ${pairedHost.transport.name}")
                                Text(text = "Local network: ${hostBridgeState.localNetworkAccessState.name}")
                                Text(text = "Nearby devices granted: ${hostBridgeState.nearbyWifiDevicesGranted}")
                                hostBridgeState.lastConnectedAtEpochMs?.let { lastConnectedAt ->
                                    Text(text = "Последнее готовое подключение: $lastConnectedAt")
                                }
                                hostBridgeState.lastError?.let { errorText ->
                                    Text(
                                        text = "Последняя ошибка: $errorText",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    ExpressiveCard(
                        title = "Recent host and connection events",
                        subtitle = if (diagnosticsSummary.recentHostConnectionLogs.isEmpty()) {
                            "Host/connection events пока не попадали в лог."
                        } else {
                            "Последние события по Host Bridge и connection state."
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            diagnosticsSummary.recentHostConnectionLogs.forEach { event ->
                                Text(
                                    text = "${event.level.name} · ${event.eventName} · ${event.messageHuman}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
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
}
