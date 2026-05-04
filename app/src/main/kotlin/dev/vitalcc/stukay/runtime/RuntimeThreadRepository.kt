package dev.vitalcc.stukay.runtime

import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.TurnId
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeEventStream
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeRepository
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadEvent
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadPayload
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeTurnPayload

class RuntimeThreadRepository(
    private val hostBridgeRepository: HostBridgeRepository,
    private val store: RuntimeThreadStore,
) : ThreadRepository {
    override fun loadThreads(projectId: ProjectId): List<CodexThread> = store.loadThreads(projectId)

    override fun loadThread(threadId: ThreadId): CodexThread? = store.loadThread(threadId)

    override fun loadTimeline(threadId: ThreadId): List<TimelineItem> = store.loadTimeline(threadId)

    override fun refreshIndex(): List<CodexThread> {
        val payload = hostBridgeRepository.listThreads()
        store.replaceIndex(payload.data)
        return store.loadAllThreads()
    }

    override fun readThread(
        threadId: ThreadId,
        includeTurns: Boolean,
    ): CodexThread? {
        val payload = hostBridgeRepository.readThread(threadId.value)
        store.replaceThread(payload)
        return store.loadThread(threadId)
    }

    override fun resumeThread(threadId: ThreadId): CodexThread? {
        val payload = hostBridgeRepository.resumeThread(threadId.value)
        store.replaceThread(payload)
        return store.loadThread(threadId)
    }

    override fun startTurn(threadId: ThreadId, text: String): TurnId {
        val payload = hostBridgeRepository.startTurn(threadId.value, text)
        store.applyTurnStarted(threadId = threadId, turn = payload)
        return TurnId(payload.id)
    }

    override fun interruptTurn(threadId: ThreadId, turnId: TurnId) {
        hostBridgeRepository.interruptTurn(threadId.value, turnId.value)
    }

    override fun respondToApproval(requestId: String, decision: ApprovalDecision) {
        hostBridgeRepository.respondToApproval(requestId, decision)
    }

    fun openEventStream(threadId: ThreadId): HostBridgeEventStream = hostBridgeRepository.openThreadEventStream(threadId.value)

    fun applyThreadReadPayload(payload: HostBridgeThreadPayload) {
        store.replaceThread(payload)
    }

    fun applyEvent(event: HostBridgeThreadEvent) {
        store.applyEvent(event)
    }

    fun clearActiveApprovals(threadId: ThreadId, reason: String) {
        store.clearActiveApprovals(threadId, reason)
    }

    fun unresolvedApprovals(threadId: ThreadId): List<TimelineItem.ApprovalRequest> = store.unresolvedApprovals(threadId)
}
