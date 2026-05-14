package dev.vitalcc.stukay.feature.thread.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ForegroundThreadBlockedReason
import dev.vitalcc.stukay.core.model.ForegroundThreadSessionState
import dev.vitalcc.stukay.core.model.ForegroundThreadStreamState
import dev.vitalcc.stukay.core.model.ThreadHistoryState
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.canResolveApproval
import dev.vitalcc.stukay.core.model.canSendPrompt
import dev.vitalcc.stukay.core.model.canStopTurn
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusPill
import dev.vitalcc.stukay.core.design.expressive.ExpressiveStatusTone
import dev.vitalcc.stukay.core.design.layout.ScreenFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadRoute(
    thread: CodexThread?,
    timeline: List<TimelineItem>,
    sessionState: ForegroundThreadSessionState,
    runtimePathAvailable: Boolean,
    logger: AppLogger,
    onComposerChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onLoadOlderHistory: () -> Unit,
    onResolveApproval: (String, ApprovalDecision) -> Unit,
    onNavigateBack: () -> Unit,
) {
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
        ScreenFrame(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 20.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        text = "Runtime thread",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    ExpressiveStatusPill(
                        label = threadLabel(thread, sessionState),
                        tone = threadTone(thread, sessionState),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                item {
                    ExpressiveCard(
                        title = "Runtime status",
                        subtitle = thread?.preview ?: "No thread data",
                        modifier = Modifier
                            .testTag("thread.status.banner")
                            .semantics {
                                stateDescription = statusBannerText(thread, sessionState)
                            },
                    ) {
                        Text(text = statusBannerText(thread, sessionState))
                    }
                }

                if (sessionState.historyState.hasOlderHistory || sessionState.historyState.isLoadingOlderHistory) {
                    item {
                        ExpressiveCard(
                            title = "History",
                            subtitle = historyBannerText(sessionState.historyState),
                            modifier = Modifier
                                .testTag("thread.history.status")
                                .semantics {
                                    stateDescription = historyBannerText(sessionState.historyState)
                                },
                        ) {
                            Button(
                                onClick = onLoadOlderHistory,
                                enabled = !sessionState.historyState.isLoadingOlderHistory,
                                modifier = Modifier.testTag("thread.history.loadOlder"),
                            ) {
                                Text(
                                    text = if (sessionState.historyState.isLoadingOlderHistory) {
                                        "Загрузка старой истории..."
                                    } else {
                                        "Загрузить старое"
                                    },
                                )
                            }
                        }
                    }
                }

                if (sessionState.pendingApprovals.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sessionState.pendingApprovals.forEach { approval ->
                                val canResolveApproval = sessionState.canResolveApproval(
                                    requestId = approval.requestId,
                                    runtimePathAvailable = runtimePathAvailable,
                                )
                                ExpressiveCard(
                                    title = approval.title,
                                    subtitle = approval.description,
                                ) {
                                    Text(
                                        text = approvalMetadata(approval),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(top = 12.dp),
                                    ) {
                                        if (ApprovalDecision.AcceptOnce in approval.availableDecisions || approval.availableDecisions.isEmpty()) {
                                            Button(
                                                onClick = {
                                                    approval.requestId?.let { requestId ->
                                                        onResolveApproval(requestId, ApprovalDecision.AcceptOnce)
                                                    }
                                                },
                                                enabled = canResolveApproval,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .semantics {
                                                        contentDescription = approvalActionDescription(
                                                            actionLabel = "Approve once",
                                                            approval = approval,
                                                        )
                                                    }
                                                    .testTag("thread.approval.once.${approvalTag(approval)}"),
                                            ) {
                                                Text(text = "Approve once")
                                            }
                                        }
                                        if (ApprovalDecision.AcceptSession in approval.availableDecisions || approval.availableDecisions.isEmpty()) {
                                            Button(
                                                onClick = {
                                                    approval.requestId?.let { requestId ->
                                                        onResolveApproval(requestId, ApprovalDecision.AcceptSession)
                                                    }
                                                },
                                                enabled = canResolveApproval,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .semantics {
                                                        contentDescription = approvalActionDescription(
                                                            actionLabel = "Approve session",
                                                            approval = approval,
                                                        )
                                                    }
                                                    .testTag("thread.approval.session.${approvalTag(approval)}"),
                                            ) {
                                                Text(text = "Approve session")
                                            }
                                        }
                                        if (ApprovalDecision.Decline in approval.availableDecisions || approval.availableDecisions.isEmpty()) {
                                            TextButton(
                                                onClick = {
                                                    approval.requestId?.let { requestId ->
                                                        onResolveApproval(requestId, ApprovalDecision.Decline)
                                                    }
                                                },
                                                enabled = canResolveApproval,
                                                modifier = Modifier
                                                    .semantics {
                                                        contentDescription = approvalActionDescription(
                                                            actionLabel = "Decline",
                                                            approval = approval,
                                                        )
                                                    }
                                                    .testTag("thread.approval.decline.${approvalTag(approval)}"),
                                            ) {
                                                Text(text = "Decline")
                                            }
                                        }
                                        if (ApprovalDecision.Cancel in approval.availableDecisions || approval.availableDecisions.isEmpty()) {
                                            TextButton(
                                                onClick = {
                                                    approval.requestId?.let { requestId ->
                                                        onResolveApproval(requestId, ApprovalDecision.Cancel)
                                                    }
                                                },
                                                enabled = canResolveApproval,
                                                modifier = Modifier
                                                    .semantics {
                                                        contentDescription = approvalActionDescription(
                                                            actionLabel = "Cancel",
                                                            approval = approval,
                                                        )
                                                    }
                                                    .testTag("thread.approval.cancel.${approvalTag(approval)}"),
                                            ) {
                                                Text(text = "Cancel")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    ExpressiveCard(
                        title = "Composer",
                        subtitle = "Continue the resumed foreground thread.",
                    ) {
                        OutlinedTextField(
                            value = sessionState.composerDraft,
                            onValueChange = onComposerChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("thread.composer.input"),
                            label = {
                                Text(text = "Prompt")
                            },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Button(
                                onClick = onSend,
                                enabled = sessionState.canSendPrompt(runtimePathAvailable = runtimePathAvailable),
                                modifier = Modifier.testTag("thread.turn.send"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send prompt",
                                )
                                Text(
                                    text = "Send",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Button(
                                onClick = onStop,
                                enabled = sessionState.canStopTurn(runtimePathAvailable = runtimePathAvailable),
                                modifier = Modifier.testTag("thread.turn.stop"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop active turn",
                                )
                                Text(
                                    text = "Stop",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }

                items(timeline.filterNot { item ->
                    item is TimelineItem.ApprovalRequest && !item.resolved
                }) { item ->
                    TimelineCard(item = item)
                }
            }
        }
    }
}

private fun historyBannerText(state: ThreadHistoryState): String = when {
    state.isLoadingOlderHistory -> "Старая история треда загружается из runtime."
    state.hasOlderHistory -> "Доступна более старая история треда."
    else -> "Вся доступная история уже загружена."
}

private fun approvalActionDescription(
    actionLabel: String,
    approval: TimelineItem.ApprovalRequest,
): String = "$actionLabel: ${approval.title}"

@Composable
private fun TimelineCard(
    item: TimelineItem,
) {
    when (item) {
        is TimelineItem.UserMessage -> ExpressiveCard(
            title = "User prompt",
            subtitle = item.text,
        ) {
            Text(text = "User message")
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
                    "Resolved: ${item.decision?.name ?: if (item.stale) "stale" else "done"}"
                } else {
                    "Pending approval"
                },
            )
        }

        is TimelineItem.StatusEvent -> ExpressiveCard(
            title = item.title,
            subtitle = item.detail,
        ) {
            Text(text = "Status event")
        }
    }
}

private fun threadLabel(
    thread: CodexThread?,
    sessionState: ForegroundThreadSessionState,
): String = when (sessionState.streamState) {
    ForegroundThreadStreamState.Hydrating -> "Hydrating"
    ForegroundThreadStreamState.Streaming -> "Streaming"
    ForegroundThreadStreamState.Interrupting -> "Interrupting"
    ForegroundThreadStreamState.AwaitingReconnect -> "Awaiting reconnect"
    ForegroundThreadStreamState.Failed -> "Failed"
    else -> thread?.status?.name ?: "Missing"
}

private fun threadTone(
    thread: CodexThread?,
    sessionState: ForegroundThreadSessionState,
): ExpressiveStatusTone = when {
    sessionState.streamState == ForegroundThreadStreamState.Failed -> ExpressiveStatusTone.Critical
    sessionState.streamState == ForegroundThreadStreamState.AwaitingReconnect -> ExpressiveStatusTone.Warning
    sessionState.blockedReason == ForegroundThreadBlockedReason.WaitingOnApproval -> ExpressiveStatusTone.Warning
    sessionState.blockedReason == ForegroundThreadBlockedReason.WaitingOnUserInput -> ExpressiveStatusTone.Warning
    thread?.status == ThreadStatus.Running || sessionState.streamState == ForegroundThreadStreamState.Streaming -> ExpressiveStatusTone.Positive
    thread?.status == ThreadStatus.Failed || thread?.status == ThreadStatus.SystemError -> ExpressiveStatusTone.Critical
    else -> ExpressiveStatusTone.Neutral
}

private fun statusBannerText(
    thread: CodexThread?,
    sessionState: ForegroundThreadSessionState,
): String = when {
    sessionState.streamState == ForegroundThreadStreamState.Hydrating -> "Hydrating thread state from runtime and opening live subscription."
    sessionState.streamState == ForegroundThreadStreamState.Streaming -> "Assistant output is streaming from the active turn."
    sessionState.streamState == ForegroundThreadStreamState.Interrupting -> "Interrupt was requested; waiting for terminal turn/completed."
    sessionState.streamState == ForegroundThreadStreamState.AwaitingReconnect -> "Runtime stream is waiting for reconnect recovery."
    sessionState.streamState == ForegroundThreadStreamState.Failed -> sessionState.lastError ?: "Foreground thread runtime failed."
    sessionState.lastError != null -> sessionState.lastError.orEmpty()
    sessionState.blockedReason == ForegroundThreadBlockedReason.WaitingOnApproval -> "Thread is waiting on an approval decision."
    sessionState.blockedReason == ForegroundThreadBlockedReason.WaitingOnUserInput -> "Thread is waiting on user input that is out of scope for this slice."
    thread?.status == ThreadStatus.Interrupted -> "Last active turn was interrupted."
    thread?.status == ThreadStatus.SystemError -> "Runtime reported a system error for this thread."
    else -> thread?.preview ?: "Runtime thread is ready."
}

private fun approvalMetadata(item: TimelineItem.ApprovalRequest): String = buildString {
    append("kind=${item.kind.name}")
    append(" · risk=${item.risk.name}")
    item.commandPreview?.takeIf { it.isNotBlank() }?.let { append(" · command=$it") }
    item.networkHost?.takeIf { it.isNotBlank() }?.let { host ->
        append(" · network=$host")
        item.networkProtocol?.takeIf { it.isNotBlank() }?.let { protocol ->
            append(" ($protocol)")
        }
    }
    item.cwd?.takeIf { it.isNotBlank() }?.let { append(" · cwd=$it") }
}

private fun approvalTag(item: TimelineItem.ApprovalRequest): String =
    (item.requestId ?: item.approvalId.value).replace(Regex("[^A-Za-z0-9._-]"), "_")
