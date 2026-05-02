package dev.vitalcc.stukay.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard

private val placeholderThreads = listOf(
    "thread-active-shell" to "Active fake shell thread with timeline and approvals",
    "thread-review-shell" to "Upcoming review-focused thread surface",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectRoute(
    logger: AppLogger,
    projectId: String,
    onNavigateBack: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
    LaunchedEffect(projectId) {
        logger.info(
            logEvent(
                area = LogArea.Ui,
                eventName = "screen_opened",
                messageHuman = "Project screen opened",
                fields = mapOf("screen" to "project", "projectId" to projectId),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = projectId)
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
                Text(
                    text = "Project shell",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Здесь появится список тредов, active/recent sections и быстрый вход в новый run.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            items(placeholderThreads) { (threadId, description) ->
                ExpressiveCard(
                    title = threadId,
                    subtitle = description,
                ) {
                    Column {
                        Text(
                            text = "Переход уже маршрутизируется через root navigation scaffold.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { onOpenThread(threadId) },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text(text = "Open thread")
                        }
                    }
                }
            }
        }
    }
}
