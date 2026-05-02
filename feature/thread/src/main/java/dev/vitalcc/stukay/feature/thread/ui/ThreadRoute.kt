package dev.vitalcc.stukay.feature.thread.ui

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
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusPill
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusTone

private val placeholderTimeline = listOf(
    "User prompt card",
    "Assistant response card",
    "Command execution card",
    "Approval request card",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadRoute(
    logger: AppLogger,
    threadId: String,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(threadId) {
        logger.info(
            logEvent(
                area = LogArea.Thread,
                eventName = "thread_opened",
                messageHuman = "Thread shell opened",
                fields = mapOf("screen" to "thread", "threadId" to threadId),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = threadId)
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
                    text = "Thread shell",
                    style = MaterialTheme.typography.headlineSmall,
                )
                ExpressiveStatusPill(
                    label = "Foundation ready",
                    tone = ExpressiveStatusTone.Positive,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            items(placeholderTimeline) { itemTitle ->
                ExpressiveCard(
                    title = itemTitle,
                    subtitle = "Typed timeline surface will land in the next commit of this milestone.",
                ) {
                    Text(
                        text = "Сейчас здесь только shell-контур: top app bar, vertical rhythm и место под fake run controls.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                ExpressiveCard(
                    title = "Composer and action area",
                    subtitle = "Plan / Review / Stop / Desktop handoff",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Следующим шагом сюда лягут fake composer, run state и approval interaction.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Shell action placeholder")
                        }
                    }
                }
            }
        }
    }
}
