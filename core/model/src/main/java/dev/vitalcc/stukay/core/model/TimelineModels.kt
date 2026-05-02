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
    ) : TimelineItem

    data class AssistantMessage(
        override val id: String,
        override val threadId: ThreadId,
        val text: String,
        val streaming: Boolean,
    ) : TimelineItem

    data class CommandRun(
        override val id: String,
        override val threadId: ThreadId,
        val commandPreview: String,
        val status: CommandRunStatus,
    ) : TimelineItem

    data class FileChange(
        override val id: String,
        override val threadId: ThreadId,
        val path: String,
        val changeKind: FileChangeKind,
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
    ) : TimelineItem

    data class StatusEvent(
        override val id: String,
        override val threadId: ThreadId,
        val title: String,
        val detail: String,
    ) : TimelineItem
}
