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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.canCompleteFakeTurn
import dev.vitalcc.stukay.core.model.canStartFakeTurn
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusPill
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadRoute(
    thread: CodexThread?,
    timeline: List<TimelineItem>,
    logger: AppLogger,
    onStartFakeTurn: () -> Unit,
    onCompleteFakeTurn: () -> Unit,
    onResolveApproval: (ApprovalId, ApprovalDecision) -> Unit,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(thread?.id) {
        logger.info(
            logEvent(
                area = LogArea.Thread,
                eventName = "thread_opened",
                messageHuman = "Thread shell opened",
                fields = mapOf("screen" to "thread", "threadId" to (thread?.id?.value ?: "missing")),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = thread?.title ?: "Missing thread")
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
                    label = thread?.status?.name ?: "Missing",
                    tone = when (thread?.status) {
                        ThreadStatus.Running -> ExpressiveStatusTone.Positive
                        ThreadStatus.WaitingForApproval -> ExpressiveStatusTone.Warning
                        ThreadStatus.Failed -> ExpressiveStatusTone.Critical
                        else -> ExpressiveStatusTone.Neutral
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            item {
                ExpressiveCard(
                    title = "Run controls",
                    subtitle = thread?.preview ?: "No thread data",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (thread != null && thread.status.canStartFakeTurn()) {
                            Button(
                                onClick = onStartFakeTurn,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = "Start fake run")
                            }
                        }
                        if (thread != null && thread.status.canCompleteFakeTurn()) {
                            Button(
                                onClick = onCompleteFakeTurn,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = "Complete fake run")
                            }
                        }
                    }
                }
            }

            items(timeline) { item ->
                TimelineCard(
                    item = item,
                    onResolveApproval = onResolveApproval,
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(
    item: TimelineItem,
    onResolveApproval: (ApprovalId, ApprovalDecision) -> Unit,
) {
    when (item) {
        is TimelineItem.UserMessage -> ExpressiveCard(
            title = "User prompt",
            subtitle = item.text,
        ) {
            Text(text = "Typed user message item")
        }

        is TimelineItem.AssistantMessage -> ExpressiveCard(
            title = "Assistant response",
            subtitle = item.text,
        ) {
            Text(text = if (item.streaming) "Streaming" else "Completed")
        }

        is TimelineItem.CommandRun -> ExpressiveCard(
            title = "Command execution",
            subtitle = item.commandPreview,
        ) {
            Text(text = item.status.name)
        }

        is TimelineItem.FileChange -> ExpressiveCard(
            title = "File change",
            subtitle = item.path,
        ) {
            Text(text = item.changeKind.name)
        }

        is TimelineItem.ApprovalRequest -> ExpressiveCard(
            title = item.title,
            subtitle = item.description,
        ) {
            Text(
                text = if (item.resolved) {
                    "Resolved: ${item.decision?.name}"
                } else {
                    "Pending approval (${item.kind.name}, ${item.risk.name})"
                },
            )
            if (!item.resolved) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Button(
                        onClick = { onResolveApproval(item.approvalId, ApprovalDecision.AcceptOnce) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Approve once")
                    }
                    TextButton(
                        onClick = { onResolveApproval(item.approvalId, ApprovalDecision.Decline) },
                    ) {
                        Text(text = "Decline")
                    }
                }
            }
        }

        is TimelineItem.StatusEvent -> ExpressiveCard(
            title = item.title,
            subtitle = item.detail,
        ) {
            Text(text = "Status event")
        }
    }
}
