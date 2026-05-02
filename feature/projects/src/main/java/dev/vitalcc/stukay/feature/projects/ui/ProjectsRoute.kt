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
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ProjectStatus
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusPill
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusTone
import dev.vitalcc.stukay.core.design.layout.ScreenFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsRoute(
    projects: List<CodexProject>,
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
                        text = "Shell уже typed и observability-ready. Следующим шагом сюда лягут pairing и Host Bridge state.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
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
