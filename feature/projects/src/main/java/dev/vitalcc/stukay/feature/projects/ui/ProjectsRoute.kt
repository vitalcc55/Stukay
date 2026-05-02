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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard

private val placeholderProjects = listOf(
    "main" to "Локальный Codex runtime на Windows и Pixel-first shell",
    "diagnostics" to "Отдельный поток по логированию и evidence surfaces",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsRoute(
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
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
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.padding(innerPadding),
        ) {
            item {
                Text(
                    text = "Pixel 9 Pro XL shell",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Пока это foundation shell. Следующим шагом сюда лягут typed timeline, approvals и diagnostics.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            items(placeholderProjects) { (projectId, description) ->
                ExpressiveCard(
                    title = projectId,
                    subtitle = description,
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    Column {
                        Text(
                            text = "Локальный Android shell уже готов к реальной форме проекта.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { onOpenProject(projectId) },
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
