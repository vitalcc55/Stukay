package dev.vitalcc.stukay.core.model

enum class ApprovalKind {
    Command,
    FileChange,
}

enum class ApprovalRisk {
    Low,
    Medium,
    High,
}

enum class ApprovalDecision {
    AcceptOnce,
    AcceptSession,
    Decline,
    Cancel,
}

enum class CommandRunStatus {
    Running,
    Succeeded,
    Failed,
    Declined,
}

enum class FileChangeKind {
    Created,
    Modified,
    Deleted,
}

sealed interface TimelineItem {
    val id: String
    val threadId: ThreadId

    data class UserMessage(
        override val id: String,
        override val threadId: ThreadId,
        val text: String,
        val turnId: TurnId? = null,
    ) : TimelineItem

    data class AssistantMessage(
        override val id: String,
        override val threadId: ThreadId,
        val text: String,
        val streaming: Boolean,
        val turnId: TurnId? = null,
        val itemId: String? = null,
        val phase: String? = null,
    ) : TimelineItem

    data class CommandRun(
        override val id: String,
        override val threadId: ThreadId,
        val commandPreview: String,
        val status: CommandRunStatus,
        val turnId: TurnId? = null,
        val cwd: String? = null,
        val aggregatedOutput: String? = null,
        val exitCode: Int? = null,
    ) : TimelineItem

    data class FileChange(
        override val id: String,
        override val threadId: ThreadId,
        val path: String,
        val changeKind: FileChangeKind,
        val turnId: TurnId? = null,
        val status: String? = null,
    ) : TimelineItem

    data class ApprovalRequest(
        override val id: String,
        override val threadId: ThreadId,
        val approvalId: ApprovalId,
        val kind: ApprovalKind,
        val risk: ApprovalRisk,
        val title: String,
        val description: String,
        val resolved: Boolean,
        val decision: ApprovalDecision? = null,
        val turnId: TurnId? = null,
        val requestId: String? = null,
        val itemId: String? = null,
        val availableDecisions: List<ApprovalDecision> = emptyList(),
        val commandPreview: String? = null,
        val cwd: String? = null,
        val grantRoot: String? = null,
        val networkHost: String? = null,
        val networkProtocol: String? = null,
        val stale: Boolean = false,
    ) : TimelineItem

    data class StatusEvent(
        override val id: String,
        override val threadId: ThreadId,
        val title: String,
        val detail: String,
        val turnId: TurnId? = null,
    ) : TimelineItem
}
