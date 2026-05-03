package dev.vitalcc.stukay.feature.projects.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
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
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.HostBridgeConnectionState
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ProjectStatus
import dev.vitalcc.stukay.core.model.hostBridgeEndpointDisplayValue
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusPill
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusTone
import dev.vitalcc.stukay.core.design.layout.ScreenFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsRoute(
    projects: List<CodexProject>,
    hostBridgeState: HostBridgeConnectionState,
    logger: AppLogger,
    onOpenProject: (ProjectId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LaunchedEffect(Unit) {
        logger.info(
            logEvent(
                area = LogArea.Ui,
                eventName = "screen_opened",
                messageHuman = "Projects screen opened",
                fields = mapOf("screen" to "projects"),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Stukay")
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Open settings",
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
            ) {
                item {
                    Text(
                        text = "Pixel 9 Pro XL shell",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = "Shell уже typed, observability-ready и получил первый Host Bridge contract slice. Следующий шаг отсюда — реальный Host Bridge MVP.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    ExpressiveCard(
                        title = "Host Bridge",
                        subtitle = hostBridgeTitle(hostBridgeState),
                        modifier = Modifier.padding(bottom = 14.dp),
                    ) {
                        ExpressiveStatusPill(
                            label = hostBridgeLabel(hostBridgeState),
                            tone = when (hostBridgeState.phase) {
                                HostBridgeConnectionPhase.Connected -> ExpressiveStatusTone.Positive
                                HostBridgeConnectionPhase.Degraded -> ExpressiveStatusTone.Warning
                                HostBridgeConnectionPhase.Failed -> ExpressiveStatusTone.Critical
                                else -> ExpressiveStatusTone.Neutral
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Text(
                            text = hostBridgeDetail(hostBridgeState),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                items(projects) { project ->
                    ExpressiveCard(
                        title = project.name,
                        subtitle = project.summary,
                        modifier = Modifier.padding(bottom = 14.dp),
                    ) {
                        Column {
                            ExpressiveStatusPill(
                                label = when (project.status) {
                                    ProjectStatus.Active -> "Active"
                                    ProjectStatus.Idle -> "Idle"
                                    ProjectStatus.Archived -> "Archived"
                                },
                                tone = when (project.status) {
                                    ProjectStatus.Active -> ExpressiveStatusTone.Positive
                                    ProjectStatus.Idle -> ExpressiveStatusTone.Neutral
                                    ProjectStatus.Archived -> ExpressiveStatusTone.Warning
                                },
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            Text(
                                text = project.cwd,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = { onOpenProject(project.id) },
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(text = "Open project")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hostBridgeLabel(state: HostBridgeConnectionState): String = when (state.phase) {
    HostBridgeConnectionPhase.NotPaired -> "Не настроен"
    HostBridgeConnectionPhase.Paired ->
        if (state.localNetworkAccessState == dev.vitalcc.stukay.core.model.LocalNetworkAccessState.PermissionRequired) {
            "Нужно разрешение"
        } else {
            "Сохранен"
        }
    HostBridgeConnectionPhase.Connecting -> "Подключение"
    HostBridgeConnectionPhase.Connected -> "Подключен"
    HostBridgeConnectionPhase.Degraded -> "Degraded"
    HostBridgeConnectionPhase.Disconnected -> "Отключен"
    HostBridgeConnectionPhase.Failed -> "Ошибка"
}

private fun hostBridgeTitle(state: HostBridgeConnectionState): String = when (state.phase) {
    HostBridgeConnectionPhase.NotPaired -> "Pairing payload еще не добавлен."
    HostBridgeConnectionPhase.Paired ->
        if (state.localNetworkAccessState == dev.vitalcc.stukay.core.model.LocalNetworkAccessState.PermissionRequired) {
            "Host сохранен, но nearby devices access еще не выдан."
        } else {
            "Host сохранен и готов к подключению."
        }
    HostBridgeConnectionPhase.Connecting -> "Подготовка к подключению к локальному host."
    HostBridgeConnectionPhase.Connected -> "Локальный host bridge помечен как доступный."
    HostBridgeConnectionPhase.Degraded -> state.lastError ?: "Локальный host bridge отвечает нестабильно."
    HostBridgeConnectionPhase.Disconnected -> "Pairing сохранен, подключение можно восстановить."
    HostBridgeConnectionPhase.Failed -> state.lastError ?: "Host bridge вернул ошибочное состояние."
}

private fun hostBridgeDetail(state: HostBridgeConnectionState): String {
    val pairedHost = state.pairedHost ?: return "Откройте Settings и сохраните pairing payload для Windows host bridge."
    val errorPart = state.lastError?.let { " Ошибка: $it" }.orEmpty()
    return "${pairedHost.hostLabel} · ${pairedHost.transport.name} · ${hostBridgeEndpointDisplayValue(pairedHost.endpoint)}.$errorPart"
}
