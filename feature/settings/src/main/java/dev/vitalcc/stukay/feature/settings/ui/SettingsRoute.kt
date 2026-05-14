package dev.vitalcc.stukay.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.LogEvents
import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.HostBridgeConnectionState
import dev.vitalcc.stukay.core.model.HostRuntimeSnapshotScope
import dev.vitalcc.stukay.core.model.LocalNetworkAccessState
import dev.vitalcc.stukay.core.model.hostBridgeEndpointDisplayValue
import dev.vitalcc.stukay.core.model.runtimeSummaryScope
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.layout.ScreenFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    logger: AppLogger,
    hostBridgeState: HostBridgeConnectionState,
    canAttemptHostBridgeConnect: Boolean,
    canDisconnectHostBridge: Boolean,
    shouldOfferNearbyDevicesPermission: Boolean,
    pairingInput: String,
    onUpdatePairingInput: (String) -> Unit,
    onSavePairingPayload: () -> Unit,
    onConnectHostBridge: () -> Unit,
    onReconnectHostBridge: () -> Unit,
    onDisconnectHostBridge: (Boolean) -> Unit,
    onRequestLocalNetworkPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LaunchedEffect(Unit) {
        logger.info(
            LogEvents.info(
                area = LogArea.Ui,
                eventName = "screen_opened",
                messageHuman = "Settings screen opened",
                fields = mapOf("screen" to "settings"),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Settings")
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
                        title = "Host Bridge pairing",
                        subtitle = pairingSubtitle(hostBridgeState),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = pairingInput,
                                onValueChange = onUpdatePairingInput,
                                label = {
                                    Text(text = "Pairing payload")
                                },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = onSavePairingPayload,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = "Сохранить payload")
                            }
                            Button(
                                onClick = onConnectHostBridge,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canAttemptHostBridgeConnect,
                            ) {
                                Text(text = "Подключиться к host bridge")
                            }
                            Button(
                                onClick = onReconnectHostBridge,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canAttemptHostBridgeConnect,
                            ) {
                                Text(text = "Повторить подключение")
                            }
                            if (shouldOfferNearbyDevicesPermission) {
                                Button(
                                    onClick = onRequestLocalNetworkPermission,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(text = "Выдать Nearby devices")
                                }
                            }
                            TextButton(
                                onClick = { onDisconnectHostBridge(false) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canDisconnectHostBridge,
                            ) {
                                Text(text = "Отключить")
                            }
                            TextButton(
                                onClick = { onDisconnectHostBridge(true) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canDisconnectHostBridge,
                            ) {
                                Text(text = "Забыть pairing")
                            }
                            hostBridgeState.pairedHost?.let { pairedHost ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Host: ${pairedHost.hostLabel} · ${hostBridgeEndpointDisplayValue(pairedHost.endpoint)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = runtimeSummaryText(hostBridgeState),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            hostBridgeState.lastError?.let { errorText ->
                                Text(
                                    text = errorText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                item {
                    ExpressiveCard(
                        title = "Local network policy",
                        subtitle = "Android 16 и Android 17 ведут себя по-разному.",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "На targetSdk 36 local network protections еще переходные: для opt-in path используется Nearby devices.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Blocked-by-default и ACCESS_LOCAL_NETWORK начинаются только с Android 17 / targetSdk 37+.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = localNetworkDetail(hostBridgeState),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    ExpressiveCard(
                        title = "Diagnostics",
                        subtitle = "First-class engineering surface reachable through Settings.",
                    ) {
                        Button(onClick = onOpenDiagnostics) {
                            Text(text = "Open diagnostics")
                        }
                    }
                }

                item {
                    ExpressiveCard(title = "Workflow surface") {
                        Text(
                            text = "JetBrains MCP и Android CLI остаются engineering surfaces. Этот экран отвечает за pairing, connect/reconnect/disconnect и live host/runtime health, а foreground thread lifecycle живет на Thread screen.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
private fun pairingSubtitle(state: HostBridgeConnectionState): String = when (state.phase) {
    HostBridgeConnectionPhase.NotPaired -> "Сначала сохраните pairing payload для одного Windows host."
    HostBridgeConnectionPhase.Paired ->
        if (!state.nearbyWifiDevicesGranted && state.localNetworkAccessState == LocalNetworkAccessState.Ready) {
            "Host сохранен; connect flow можно запускать уже сейчас, а Nearby devices нужен только для Android 16 opt-in proof path."
        } else {
            "Host сохранен, можно запускать локальный connect flow."
        }
    HostBridgeConnectionPhase.Connecting -> "Идет попытка локального подключения."
    HostBridgeConnectionPhase.Connected -> "Локальный host bridge помечен как доступный."
    HostBridgeConnectionPhase.Degraded -> state.lastError ?: "Host bridge доступен частично и требует повторной проверки."
    HostBridgeConnectionPhase.Disconnected -> "Pairing сохранен, подключение отключено вручную."
    HostBridgeConnectionPhase.Failed -> state.lastError ?: "Host bridge вернул ошибочное состояние."
}
private fun localNetworkDetail(state: HostBridgeConnectionState): String = when (state.localNetworkAccessState) {
    LocalNetworkAccessState.NotConfigured ->
        "Сначала добавьте pairing payload, чтобы оценить local-network path."

    LocalNetworkAccessState.Ready ->
        if (!state.nearbyWifiDevicesGranted) {
            "Private LAN endpoint разрешен для текущего slice. Android cleartext opt-in включен явно, а Nearby devices остается manual opt-in advisory для Android 16 proof path."
        } else {
            "Private LAN endpoint разрешен для текущего slice. Android cleartext opt-in включен явно, а точная private/local boundary должна удерживаться runtime-валидацией host endpoint."
        }

    LocalNetworkAccessState.UnsupportedForSlice ->
        "Текущий endpoint не похож на private LAN / .local path. Публичный tunnel или internet endpoint вынесен за пределы этого slice."
}

private fun runtimeSummaryText(state: HostBridgeConnectionState): String {
    val summary = state.runtimeSummary
    val scopePrefix = when (state.runtimeSummaryScope()) {
        HostRuntimeSnapshotScope.None -> "Runtime snapshot еще не получен"
        HostRuntimeSnapshotScope.Live -> "Live runtime"
        HostRuntimeSnapshotScope.LastKnown -> "Last known runtime"
    }
    val parts = buildList {
        add(scopePrefix)
        add(summary.hostStatus.name)
        add("ready=${summary.runtimeReady}")
        summary.appListCount?.let { add("apps=$it") }
    }
    return parts.joinToString(" · ")
}

