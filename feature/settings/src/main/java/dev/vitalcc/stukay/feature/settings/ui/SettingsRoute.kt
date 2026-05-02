package dev.vitalcc.stukay.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
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
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard

private val placeholderSettings = listOf(
    "Dynamic color enabled",
    "Expressive design layer isolated",
    "JetBrains MCP + Android CLI workflow documented",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    logger: AppLogger,
    onNavigateBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LaunchedEffect(Unit) {
        logger.info(
            logEvent(
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
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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

            items(placeholderSettings) { line ->
                ExpressiveCard(title = line) {
                    Text(
                        text = "Это foundation-level настройка или decision, уже зафиксированный в repo-local docs.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
