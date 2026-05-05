package dev.vitalcc.stukay.runtime

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.ApprovalKind
import dev.vitalcc.stukay.core.model.ApprovalRisk
import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.CommandRunStatus
import dev.vitalcc.stukay.core.model.FileChangeKind
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ProjectStatus
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.TurnId
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeApprovalPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadEvent
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadStatusPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeTimelineItemPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeTurnPayload
import java.io.File

class RuntimeThreadStore(
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    private val threadsById = linkedMapOf<String, CachedRuntimeThread>()

    @Synchronized
    fun replaceIndex(payloads: List<HostBridgeThreadPayload>) {
        val nextThreads = linkedMapOf<String, CachedRuntimeThread>()
        payloads.forEach { payload ->
            val existing = threadsById[payload.id]
            val timeline = if (payload.timeline.isNotEmpty()) {
                payload.timeline.map(::toTimelineItem).toMutableList()
            } else {
                existing?.timeline ?: mutableListOf()
            }
            nextThreads[payload.id] = CachedRuntimeThread(
                thread = toCodexThread(payload),
                timeline = timeline,
            )
        }
        threadsById.clear()
        threadsById.putAll(nextThreads)
    }

    @Synchronized
    fun replaceThread(payload: HostBridgeThreadPayload) {
        val existing = threadsById[payload.id]
        val timeline = if (payload.timeline.isNotEmpty()) {
            mergeEphemeralTimeline(
                baseTimeline = payload.timeline.map(::toTimelineItem),
                existingTimeline = existing?.timeline.orEmpty(),
            )
        } else {
            existing?.timeline ?: mutableListOf()
        }
        threadsById[payload.id] = CachedRuntimeThread(
            thread = toCodexThread(payload),
            timeline = timeline,
        )
    }

    @Synchronized
    fun mergeResumedThread(payload: HostBridgeThreadPayload) {
        val existing = threadsById[payload.id]
        val timeline = when {
            existing == null -> payload.timeline.map(::toTimelineItem).toMutableList()
            existing.timeline.isNotEmpty() && payload.timeline.isNotEmpty() -> mergeTimelineById(
                existing.timeline,
                payload.timeline.map(::toTimelineItem),
            )
            existing.timeline.isNotEmpty() -> existing.timeline.toMutableList()
            payload.timeline.isNotEmpty() -> payload.timeline.map(::toTimelineItem).toMutableList()
            else -> mutableListOf()
        }
        threadsById[payload.id] = CachedRuntimeThread(
            thread = toCodexThread(payload),
            timeline = timeline,
        )
    }

    @Synchronized
    fun loadProjects(): List<CodexProject> = threadsById.values
        .map { it.thread }
        .groupBy { it.projectId }
        .values
        .map { group -> toProject(group) }
        .sortedByDescending { it.summary }

    @Synchronized
    fun loadProject(projectId: ProjectId): CodexProject? = loadProjects().firstOrNull { it.id == projectId }

    @Synchronized
    fun loadThreads(projectId: ProjectId): List<CodexThread> = threadsById.values
        .map { it.thread }
        .filter { it.projectId == projectId }
        .sortedByDescending { it.lastUpdatedAtEpochMs }

    @Synchronized
    fun loadAllThreads(): List<CodexThread> = threadsById.values
        .map { it.thread }
        .sortedByDescending { it.lastUpdatedAtEpochMs }

    @Synchronized
    fun loadThread(threadId: ThreadId): CodexThread? = threadsById[threadId.value]?.thread

    @Synchronized
    fun loadTimeline(threadId: ThreadId): List<TimelineItem> = threadsById[threadId.value]?.timeline?.toList().orEmpty()

    @Synchronized
    fun unresolvedApprovals(threadId: ThreadId): List<TimelineItem.ApprovalRequest> = threadsById[threadId.value]
        ?.timeline
        ?.mapNotNull { item -> item as? TimelineItem.ApprovalRequest }
        ?.filter { approval -> !approval.resolved }
        .orEmpty()

    @Synchronized
    fun clearActiveApprovals(threadId: ThreadId, reason: String) {
        val cached = threadsById[threadId.value] ?: return
        var changed = false
        val updated = cached.timeline.map { item ->
            if (item is TimelineItem.ApprovalRequest && !item.resolved) {
                changed = true
                item.copy(
                    resolved = true,
                    stale = true,
                )
            } else {
                item
            }
        }.toMutableList()
        if (changed) {
            updated += TimelineItem.StatusEvent(
                id = "${threadId.value}-approval-cleared-${updated.size}",
                threadId = threadId,
                title = "Approval cleared",
                detail = reason,
            )
            cached.timeline.clear()
            cached.timeline.addAll(updated)
            cached.thread = cached.thread.copy(
                preview = reason,
                lastUpdatedAtEpochMs = nowProvider(),
            )
        }
    }

    @Synchronized
    fun applyEvent(event: HostBridgeThreadEvent) {
        ensureThreadExists(event.threadId)
        when (event.method) {
            "thread/status/changed" -> event.status?.let { status ->
                updateThreadStatus(
                    threadId = ThreadId(event.threadId),
                    status = status,
                )
            }

            "turn/started" -> event.turn?.let { turn ->
                applyTurnStarted(
                    threadId = ThreadId(event.threadId),
                    turn = turn,
                )
            }

            "turn/completed" -> event.turn?.let { turn ->
                applyTurnCompleted(
                    threadId = ThreadId(event.threadId),
                    turn = turn,
                )
            }

            "item/started",
            "item/completed",
            -> event.item?.let { item ->
                upsertTimelineItem(item)
            }

            "item/agentMessage/delta" -> applyAssistantDelta(
                threadId = ThreadId(event.threadId),
                turnId = event.turnId?.let(::TurnId),
                itemId = event.itemId,
                delta = event.delta.orEmpty(),
            )

            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            -> event.approval?.let(::upsertApproval)

            "serverRequest/resolved" -> {
                if (event.requestId != null) {
                    clearApprovalByRequestId(
                        threadId = ThreadId(event.threadId),
                        requestId = event.requestId,
                        reason = "Approval request resolved by runtime.",
                    )
                }
            }

            "item/tool/requestUserInput" -> {
                val cached = threadsById[event.threadId] ?: return
                cached.thread = cached.thread.copy(
                    status = ThreadStatus.WaitingForUserInput,
                    preview = "Thread is waiting for user input.",
                    lastUpdatedAtEpochMs = nowProvider(),
                )
                cached.timeline += TimelineItem.StatusEvent(
                    id = "${event.threadId}-waiting-user-input-${cached.timeline.size}",
                    threadId = ThreadId(event.threadId),
                    title = "Waiting for user input",
                    detail = "Runtime is blocked on an out-of-scope user input request.",
                    turnId = event.turnId?.let(::TurnId),
                )
            }

            "error" -> {
                val cached = threadsById[event.threadId] ?: return
                cached.thread = cached.thread.copy(
                    status = ThreadStatus.Failed,
                    preview = event.message ?: "Thread runtime error.",
                    lastUpdatedAtEpochMs = nowProvider(),
                )
                cached.timeline += TimelineItem.StatusEvent(
                    id = "${event.threadId}-runtime-error-${cached.timeline.size}",
                    threadId = ThreadId(event.threadId),
                    title = "Runtime error",
                    detail = event.message ?: "Thread runtime error.",
                    turnId = event.turnId?.let(::TurnId),
                )
            }
        }
    }

    private fun applyAssistantDelta(
        threadId: ThreadId,
        turnId: TurnId?,
        itemId: String?,
        delta: String,
    ) {
        if (delta.isBlank()) {
            return
        }
        val cached = threadsById[threadId.value] ?: return
        val existingIndex = cached.timeline.indexOfFirst { item ->
            item is TimelineItem.AssistantMessage && item.itemId == itemId
        }
        if (existingIndex >= 0) {
            val existing = cached.timeline[existingIndex] as TimelineItem.AssistantMessage
            cached.timeline[existingIndex] = existing.copy(
                text = existing.text + delta,
                streaming = true,
                turnId = turnId ?: existing.turnId,
            )
        } else {
            cached.timeline += TimelineItem.AssistantMessage(
                id = itemId ?: "${threadId.value}-assistant-${cached.timeline.size}",
                threadId = threadId,
                text = delta,
                streaming = true,
                turnId = turnId,
                itemId = itemId,
            )
        }
        cached.thread = cached.thread.copy(
            status = ThreadStatus.Running,
            preview = "Streaming response in progress",
            lastUpdatedAtEpochMs = nowProvider(),
        )
    }

    @Synchronized
    fun applyTurnStarted(threadId: ThreadId, turn: HostBridgeTurnPayload) {
        val cached = threadsById[threadId.value] ?: return
        cached.thread = cached.thread.copy(
            status = ThreadStatus.Running,
            preview = "Running turn ${turn.id}",
            lastUpdatedAtEpochMs = nowProvider(),
        )
    }

    private fun applyTurnCompleted(threadId: ThreadId, turn: HostBridgeTurnPayload) {
        val cached = threadsById[threadId.value] ?: return
        val nextStatus = when (turn.status.lowercase()) {
            "interrupted" -> ThreadStatus.Interrupted
            "failed" -> ThreadStatus.Failed
            else -> ThreadStatus.Idle
        }
        cached.thread = cached.thread.copy(
            status = nextStatus,
            preview = when (nextStatus) {
                ThreadStatus.Interrupted -> "Turn interrupted"
                ThreadStatus.Failed -> turn.errorMessage ?: "Turn failed"
                else -> "Thread idle"
            },
            lastUpdatedAtEpochMs = turn.completedAtEpochMs ?: nowProvider(),
        )
        cached.timeline.replaceAll { item ->
            if (item is TimelineItem.AssistantMessage && item.turnId?.value == turn.id) {
                item.copy(streaming = false)
            } else {
                item
            }
        }
        if (nextStatus == ThreadStatus.Interrupted || nextStatus == ThreadStatus.Failed) {
            cached.timeline += TimelineItem.StatusEvent(
                id = "${threadId.value}-${turn.id}-${turn.status}",
                threadId = threadId,
                title = if (nextStatus == ThreadStatus.Interrupted) {
                    "Turn interrupted"
                } else {
                    "Turn failed"
                },
                detail = turn.errorMessage ?: "Terminal turn status: ${turn.status}",
                turnId = TurnId(turn.id),
            )
        }
    }

    private fun updateThreadStatus(
        threadId: ThreadId,
        status: HostBridgeThreadStatusPayload,
    ) {
        val cached = threadsById[threadId.value] ?: return
        val nextStatus = toThreadStatus(status)
        cached.thread = cached.thread.copy(
            status = nextStatus,
            preview = when (nextStatus) {
                ThreadStatus.WaitingForApproval -> "Waiting for approval"
                ThreadStatus.WaitingForUserInput -> "Waiting for user input"
                ThreadStatus.Running -> "Thread is active"
                ThreadStatus.SystemError -> "Runtime reported system error"
                else -> cached.thread.preview
            },
            lastUpdatedAtEpochMs = nowProvider(),
        )
    }

    private fun upsertTimelineItem(item: HostBridgeTimelineItemPayload) {
        val cached = threadsById[item.threadId] ?: return
        val mapped = toTimelineItem(item)
        val existingIndex = cached.timeline.indexOfFirst { timelineItem -> timelineItem.id == mapped.id }
        if (existingIndex >= 0) {
            cached.timeline[existingIndex] = mapped
        } else {
            cached.timeline += mapped
        }
        if (mapped is TimelineItem.CommandRun && mapped.status == CommandRunStatus.Running) {
            cached.thread = cached.thread.copy(
                status = ThreadStatus.Running,
                preview = mapped.commandPreview,
                lastUpdatedAtEpochMs = nowProvider(),
            )
        }
    }

    private fun upsertApproval(payload: HostBridgeApprovalPayload) {
        val cached = threadsById[payload.threadId] ?: return
        val approval = payload.toApprovalRequest()
        val existingIndex = cached.timeline.indexOfFirst { item ->
            item is TimelineItem.ApprovalRequest && item.requestId == payload.requestId
        }
        if (existingIndex >= 0) {
            cached.timeline[existingIndex] = approval
        } else {
            cached.timeline += approval
        }
        cached.thread = cached.thread.copy(
            status = ThreadStatus.WaitingForApproval,
            preview = payload.title,
            lastUpdatedAtEpochMs = nowProvider(),
        )
    }

    private fun clearApprovalByRequestId(
        threadId: ThreadId,
        requestId: String,
        reason: String,
    ) {
        val cached = threadsById[threadId.value] ?: return
        val updated = cached.timeline.map { item ->
            if (item is TimelineItem.ApprovalRequest && item.requestId == requestId && !item.resolved) {
                item.copy(
                    resolved = true,
                    stale = false,
                )
            } else {
                item
            }
        }.toMutableList()
        cached.timeline.clear()
        cached.timeline.addAll(updated)
        val remainingApprovals = updated.count { item ->
            item is TimelineItem.ApprovalRequest && !item.resolved
        }
        cached.timeline += TimelineItem.StatusEvent(
            id = "${threadId.value}-approval-resolved-$requestId",
            threadId = threadId,
            title = "Approval resolved",
            detail = reason,
        )
        if (cached.thread.status == ThreadStatus.WaitingForApproval) {
            cached.thread = cached.thread.copy(
                status = if (remainingApprovals > 0) {
                    ThreadStatus.WaitingForApproval
                } else {
                    ThreadStatus.Running
                },
                preview = if (remainingApprovals > 0) {
                    cached.thread.preview
                } else {
                    "Approval resolved; awaiting runtime status."
                },
                lastUpdatedAtEpochMs = nowProvider(),
            )
        }
    }

    private fun ensureThreadExists(threadId: String) {
        if (threadsById.containsKey(threadId)) {
            return
        }
        threadsById[threadId] = CachedRuntimeThread(
            thread = CodexThread(
                id = ThreadId(threadId),
                projectId = noProjectId(),
                title = threadId,
                preview = "",
                status = ThreadStatus.Idle,
                lastUpdatedAtEpochMs = nowProvider(),
            ),
            timeline = mutableListOf(),
        )
    }

    private fun toProject(group: List<CodexThread>): CodexProject {
        val newest = group.maxByOrNull { it.lastUpdatedAtEpochMs } ?: error("group must not be empty")
        return CodexProject(
            id = newest.projectId,
            name = projectName(newest),
            cwd = newest.cwd.orEmpty(),
            status = when {
                group.any { thread ->
                    thread.status in setOf(
                        ThreadStatus.Running,
                        ThreadStatus.WaitingForApproval,
                        ThreadStatus.WaitingForUserInput,
                    )
                } -> ProjectStatus.Active
                group.all { it.status == ThreadStatus.Archived } -> ProjectStatus.Archived
                else -> ProjectStatus.Idle
            },
            summary = newest.preview,
        )
    }

    private fun projectName(thread: CodexThread): String {
        val cwd = thread.cwd.orEmpty()
        if (cwd.isBlank()) {
            return "No Project"
        }
        return File(cwd).name.ifBlank { cwd }
    }

    private fun noProjectId(): ProjectId = ProjectId("no-project")

    private fun projectIdForCwd(cwd: String?): ProjectId = cwd?.takeIf { it.isNotBlank() }?.let(::ProjectId) ?: noProjectId()

    private fun toCodexThread(payload: HostBridgeThreadPayload): CodexThread = CodexThread(
        id = ThreadId(payload.id),
        projectId = projectIdForCwd(payload.cwd),
        title = payload.title.ifBlank { payload.id },
        preview = payload.preview,
        status = toThreadStatus(payload.status),
        lastUpdatedAtEpochMs = payload.updatedAtEpochMs ?: nowProvider(),
        cwd = payload.cwd.takeIf { it.isNotBlank() },
        sourceKind = payload.sourceKind,
    )

    private fun toThreadStatus(status: HostBridgeThreadStatusPayload): ThreadStatus {
        if (status.type == "active") {
            return when {
                "waitingOnApproval" in status.activeFlags -> ThreadStatus.WaitingForApproval
                "waitingOnUserInput" in status.activeFlags -> ThreadStatus.WaitingForUserInput
                else -> ThreadStatus.Running
            }
        }
        return when (status.type) {
            "systemError" -> ThreadStatus.SystemError
            "idle", "notLoaded" -> ThreadStatus.Idle
            else -> ThreadStatus.Idle
        }
    }

    private fun toTimelineItem(payload: HostBridgeTimelineItemPayload): TimelineItem = when (payload.type) {
        "userMessage" -> TimelineItem.UserMessage(
            id = payload.id,
            threadId = ThreadId(payload.threadId),
            text = payload.text.orEmpty(),
            turnId = payload.turnId?.let(::TurnId),
        )

        "assistantMessage" -> TimelineItem.AssistantMessage(
            id = payload.id,
            threadId = ThreadId(payload.threadId),
            text = payload.text.orEmpty(),
            streaming = payload.streaming,
            turnId = payload.turnId?.let(::TurnId),
            itemId = payload.itemId,
            phase = payload.phase,
        )

        "commandRun" -> TimelineItem.CommandRun(
            id = payload.id,
            threadId = ThreadId(payload.threadId),
            commandPreview = payload.commandPreview.orEmpty(),
            status = when (payload.status?.lowercase()) {
                "completed" -> CommandRunStatus.Succeeded
                "failed" -> CommandRunStatus.Failed
                "declined" -> CommandRunStatus.Declined
                else -> CommandRunStatus.Running
            },
            turnId = payload.turnId?.let(::TurnId),
            cwd = payload.cwd,
            aggregatedOutput = payload.aggregatedOutput,
            exitCode = payload.exitCode,
        )

        "fileChange" -> TimelineItem.FileChange(
            id = payload.id,
            threadId = ThreadId(payload.threadId),
            path = payload.path.orEmpty(),
            changeKind = when (payload.changeKind?.lowercase()) {
                "created", "add" -> FileChangeKind.Created
                "deleted", "remove" -> FileChangeKind.Deleted
                else -> FileChangeKind.Modified
            },
            turnId = payload.turnId?.let(::TurnId),
            status = payload.status,
        )

        else -> TimelineItem.StatusEvent(
            id = payload.id,
            threadId = ThreadId(payload.threadId),
            title = payload.title ?: "Status event",
            detail = payload.detail ?: payload.text ?: "",
            turnId = payload.turnId?.let(::TurnId),
        )
    }

    private fun HostBridgeApprovalPayload.toApprovalRequest(): TimelineItem.ApprovalRequest = TimelineItem.ApprovalRequest(
        id = itemId,
        threadId = ThreadId(threadId),
        approvalId = ApprovalId(id),
        kind = if (kind == "fileChange") ApprovalKind.FileChange else ApprovalKind.Command,
        risk = when {
            networkHost != null -> ApprovalRisk.High
            kind == "fileChange" -> ApprovalRisk.Low
            else -> ApprovalRisk.Medium
        },
        title = title,
        description = description,
        resolved = false,
        turnId = TurnId(turnId),
        requestId = requestId,
        itemId = itemId,
        availableDecisions = availableDecisions.mapNotNull { decision ->
            when (decision) {
                "accept" -> ApprovalDecision.AcceptOnce
                "acceptForSession" -> ApprovalDecision.AcceptSession
                "decline" -> ApprovalDecision.Decline
                "cancel" -> ApprovalDecision.Cancel
                else -> null
            }
        },
        commandPreview = command,
        cwd = cwd,
        grantRoot = grantRoot,
        networkHost = networkHost,
        networkProtocol = networkProtocol,
    )

    private fun mergeEphemeralTimeline(
        baseTimeline: List<TimelineItem>,
        existingTimeline: List<TimelineItem>,
    ): MutableList<TimelineItem> {
        val merged = baseTimeline.toMutableList()
        val knownIds = merged.mapTo(linkedSetOf()) { item -> item.id }
        existingTimeline.forEach { item ->
            if (item is TimelineItem.ApprovalRequest && !item.resolved && knownIds.add(item.id)) {
                merged += item
            }
        }
        return merged
    }

    private fun mergeTimelineById(
        existingTimeline: List<TimelineItem>,
        resumedTimeline: List<TimelineItem>,
    ): MutableList<TimelineItem> {
        val merged = existingTimeline.toMutableList()
        val knownIds = merged.mapTo(linkedSetOf()) { item -> item.id }
        resumedTimeline.forEach { item ->
            if (knownIds.add(item.id)) {
                merged += item
            }
        }
        return merged
    }

    private data class CachedRuntimeThread(
        var thread: CodexThread,
        val timeline: MutableList<TimelineItem>,
    )
}
