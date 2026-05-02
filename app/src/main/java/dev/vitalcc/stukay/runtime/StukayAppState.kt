package dev.vitalcc.stukay.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.vitalcc.stukay.core.logging.AndroidLogcatSink
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.CompositeLogSink
import dev.vitalcc.stukay.core.logging.DiagnosticsSummary
import dev.vitalcc.stukay.core.logging.DiagnosticsSummaryProvider
import dev.vitalcc.stukay.core.logging.InMemoryLogStore
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.LogSink
import dev.vitalcc.stukay.core.logging.StructuredLogger
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.feature.projects.data.FakeProjectsRepository
import dev.vitalcc.stukay.feature.projects.domain.LoadProjectsUseCase
import dev.vitalcc.stukay.feature.projects.domain.OpenProjectUseCase
import dev.vitalcc.stukay.feature.thread.data.FakeThreadRepository
import dev.vitalcc.stukay.feature.thread.domain.CompleteFakeTurnUseCase
import dev.vitalcc.stukay.feature.thread.domain.LoadProjectThreadsUseCase
import dev.vitalcc.stukay.feature.thread.domain.ObserveThreadTimelineUseCase
import dev.vitalcc.stukay.feature.thread.domain.OpenThreadUseCase
import dev.vitalcc.stukay.feature.thread.domain.ResolveFakeApprovalUseCase
import dev.vitalcc.stukay.feature.thread.domain.StartFakeTurnUseCase

class StukayAppState {
    private val logStore = InMemoryLogStore(capacity = 250)
    private var logRevisionState by mutableIntStateOf(0)
    private var domainRevisionState by mutableIntStateOf(0)
    private val projectsRepository = FakeProjectsRepository()
    private val threadRepository = FakeThreadRepository()
    private val loadProjectsUseCase = LoadProjectsUseCase(projectsRepository)
    private val openProjectUseCase = OpenProjectUseCase(projectsRepository)
    private val loadProjectThreadsUseCase = LoadProjectThreadsUseCase(threadRepository)
    private val openThreadUseCase = OpenThreadUseCase(threadRepository)
    private val observeThreadTimelineUseCase = ObserveThreadTimelineUseCase(threadRepository)
    private val startFakeTurnUseCase = StartFakeTurnUseCase(threadRepository)
    private val completeFakeTurnUseCase = CompleteFakeTurnUseCase(threadRepository)
    private val resolveFakeApprovalUseCase = ResolveFakeApprovalUseCase(threadRepository)

    private val liveSink = LogSink { event ->
        compositeSink.log(event)
        logRevisionState += 1
    }

    private val compositeSink = CompositeLogSink(
        sinks = listOf(
            AndroidLogcatSink(),
            logStore,
        ),
    )

    val logger: AppLogger = StructuredLogger(liveSink)
    private val diagnosticsSummaryProvider = DiagnosticsSummaryProvider(store = logStore)

    var currentScreenRoute by mutableStateOf("projects")
        private set

    fun updateCurrentScreenRoute(route: String) {
        currentScreenRoute = route
    }

    fun diagnosticsSummary(): DiagnosticsSummary {
        val ignored = logRevisionState
        return diagnosticsSummaryProvider.snapshot()
    }

    fun projects(): List<CodexProject> {
        val ignored = domainRevisionState
        return loadProjectsUseCase()
    }

    fun project(projectId: String): CodexProject? {
        val ignored = domainRevisionState
        return openProjectUseCase(ProjectId(projectId))
    }

    fun threads(projectId: String): List<CodexThread> {
        val ignored = domainRevisionState
        return loadProjectThreadsUseCase(ProjectId(projectId))
    }

    fun thread(threadId: String): CodexThread? {
        val ignored = domainRevisionState
        return openThreadUseCase(ThreadId(threadId))
    }

    fun timeline(threadId: String): List<TimelineItem> {
        val ignored = domainRevisionState
        return observeThreadTimelineUseCase(ThreadId(threadId))
    }

    fun startFakeTurn(threadId: String) {
        val updatedThread = startFakeTurnUseCase(ThreadId(threadId))
        domainRevisionState += 1
        logger.info(
            logEvent(
                area = LogArea.Turn,
                eventName = "fake_turn_started",
                messageHuman = "Started fake turn from shell action",
                fields = mapOf(
                    "threadId" to updatedThread.id.value,
                    "status" to updatedThread.status.name,
                ),
            ),
        )
    }

    fun completeFakeTurn(threadId: String) {
        val updatedThread = completeFakeTurnUseCase(ThreadId(threadId))
        domainRevisionState += 1
        logger.info(
            logEvent(
                area = LogArea.Turn,
                eventName = "fake_turn_completed",
                messageHuman = "Completed fake turn from shell action",
                fields = mapOf(
                    "threadId" to updatedThread.id.value,
                    "status" to updatedThread.status.name,
                ),
            ),
        )
    }

    fun resolveApproval(
        threadId: String,
        approvalId: String,
        decision: ApprovalDecision,
    ) {
        val updatedThread = resolveFakeApprovalUseCase(
            threadId = ThreadId(threadId),
            approvalId = ApprovalId(approvalId),
            decision = decision,
        )
        domainRevisionState += 1
        logger.info(
            logEvent(
                area = LogArea.Approval,
                eventName = "approval_clicked",
                messageHuman = "Resolved fake approval from shell action",
                fields = mapOf(
                    "threadId" to updatedThread.id.value,
                    "approvalId" to approvalId,
                    "decision" to decision.name,
                ),
            ),
        )
    }
}
