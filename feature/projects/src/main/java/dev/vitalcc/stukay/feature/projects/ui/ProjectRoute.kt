package dev.vitalcc.stukay.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusPill
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusTone
import dev.vitalcc.stukay.core.design.layout.ScreenFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectRoute(
    project: CodexProject?,
    threads: List<CodexThread>,
    logger: AppLogger,
    onNavigateBack: () -> Unit,
    onOpenThread: (ThreadId) -> Unit,
) {
    LaunchedEffect(project?.id) {
        logger.info(
            logEvent(
                area = LogArea.Ui,
                eventName = "screen_opened",
                messageHuman = "Project screen opened",
                fields = mapOf("screen" to "project", "projectId" to (project?.id?.value ?: "missing")),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = project?.name ?: "Missing project")
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
                    Text(
                        text = "Project shell",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = project?.summary ?: "Project record is missing.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                items(threads) { thread ->
                    ExpressiveCard(
                        title = thread.title,
                        subtitle = thread.preview,
                    ) {
                        Column {
                            ExpressiveStatusPill(
                                label = thread.status.name,
                                tone = when (thread.status) {
                                    ThreadStatus.Running -> ExpressiveStatusTone.Positive
                                    ThreadStatus.WaitingForApproval -> ExpressiveStatusTone.Warning
                                    ThreadStatus.WaitingForUserInput -> ExpressiveStatusTone.Warning
                                    ThreadStatus.Failed -> ExpressiveStatusTone.Critical
                                    ThreadStatus.SystemError -> ExpressiveStatusTone.Critical
                                    else -> ExpressiveStatusTone.Neutral
                                },
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            Text(
                                text = "Updated at ${thread.lastUpdatedAtEpochMs}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = { onOpenThread(thread.id) },
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .testTag("project.thread_open.${threadTag(thread.id.value)}"),
                            ) {
                                Text(text = "Open thread")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun threadTag(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
