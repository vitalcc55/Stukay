package dev.vitalcc.stukay.runtime

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.vitalcc.stukay.core.logging.AndroidLogcatSink
import dev.vitalcc.stukay.core.logging.AppLogger
import dev.vitalcc.stukay.core.logging.CompositeLogSink
import dev.vitalcc.stukay.core.logging.DiagnosticsSummary
import dev.vitalcc.stukay.core.logging.DiagnosticsSummaryProvider
import dev.vitalcc.stukay.core.logging.InMemoryLogStore
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.LogSink
import dev.vitalcc.stukay.core.logging.RuntimeDiagnosticsSnapshot
import dev.vitalcc.stukay.core.logging.StructuredLogger
import dev.vitalcc.stukay.core.logging.LogEvents
import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.ForegroundThreadBlockedReason
import dev.vitalcc.stukay.core.model.ForegroundThreadSessionState
import dev.vitalcc.stukay.core.model.ForegroundThreadStreamState
import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.HostBridgeConnectionState
import dev.vitalcc.stukay.core.model.LocalNetworkAccessState
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.RouteContext
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.ThreadStatus
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.TurnId
import dev.vitalcc.stukay.core.model.canSendPrompt
import dev.vitalcc.stukay.core.model.canStopTurn
import dev.vitalcc.stukay.core.model.hostBridgeEndpointDisplayValue
import dev.vitalcc.stukay.core.model.runtimeSummaryScope
import dev.vitalcc.stukay.core.model.shouldKeepOffscreen
import dev.vitalcc.stukay.runtime.hostbridge.AndroidNetworkMonitor
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeClientException
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeEventStream
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeThreadEvent
import dev.vitalcc.stukay.runtime.hostbridge.endpointHostOrNull
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class StukayAppState(
    application: Application,
) {
    private val appContext = application.applicationContext
    private var localNetworkPermissionGranted by mutableStateOf(checkLocalNetworkPermissionGranted())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hostBridgeExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "stukay-hostbridge").apply {
            isDaemon = true
        }
    }
    private val foregroundRuntimeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "stukay-foreground-runtime").apply {
            isDaemon = true
        }
    }
    private val foregroundStreamExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "stukay-foreground-stream").apply {
            isDaemon = true
        }
    }
    private val logStore = InMemoryLogStore(capacity = 250)
    private var logRevisionState by mutableIntStateOf(0)
    private var domainRevisionState by mutableIntStateOf(0)
    private val runtimeGraph = createStukayRuntimeGraph(
        context = appContext,
        localNetworkPermissionGranted = localNetworkPermissionGranted,
    )
    private val projectsRepository = runtimeGraph.projectsRepository
    private val threadRepository = runtimeGraph.threadRepository
    private val hostBridgeRepository = runtimeGraph.hostBridgeRepository
    private val networkMonitor = AndroidNetworkMonitor(appContext) {
        mainHandler.post {
            onAndroidNetworkChanged()
        }
    }

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

    var currentRouteContext by mutableStateOf(RouteContext(routePattern = "projects"))
        private set
    var lastInspectedRouteContext by mutableStateOf(RouteContext(routePattern = "projects"))
        private set
    var pairingInput by mutableStateOf("")
        private set
    var hostBridgeState by mutableStateOf(hostBridgeRepository.currentState())
        private set
    var foregroundThreadSession by mutableStateOf(ForegroundThreadSessionState())
        private set

    @Volatile
    private var hostBridgeGeneration: Long = 0L

    @Volatile
    private var foregroundStreamGeneration: Long = 0L

    @Volatile
    private var foregroundEventStream: HostBridgeEventStream? = null

    @Volatile
    private var runtimeIndexRefreshInFlight: Boolean = false

    @Volatile
    private var runtimeIndexRefreshPendingReason: String? = null

    private var hostBridgeProbeFuture: ScheduledFuture<*>? = null
    private val hostBridgeProbeBarrier = HostBridgeProbeBarrier()
    private val hostBridgeImmediateProbeCoordinator = HostBridgeImmediateProbeCoordinator()

    init {
        networkMonitor.start()
        logger.info(
            LogEvents.info(
                area = LogArea.App,
                eventName = "app_started",
                messageHuman = "Stukay app shell started",
            ),
        )
        if (hostBridgeState.pairedHost != null) {
            refreshRuntimeIndexAsync("app_started_with_pairing")
        }
    }

    fun updateCurrentRouteContext(routeContext: RouteContext) {
        currentRouteContext = routeContext
        if (routeContext.projectId != null || routeContext.threadId != null) {
            lastInspectedRouteContext = routeContext
        }
        val nextThreadId = routeContext.threadId
        val activeThreadId = foregroundThreadSession.activeThreadId?.value
        val shouldKeepOffscreenSession = activeThreadId != null &&
            nextThreadId == null &&
            foregroundThreadSession.shouldKeepOffscreen()
        if (activeThreadId != null && nextThreadId != null && nextThreadId != activeThreadId) {
            closeForegroundThreadSession()
        } else if (activeThreadId != null && nextThreadId == null && !shouldKeepOffscreenSession) {
            closeForegroundThreadSession()
        }
        if (routeContext.routePattern == "projects" || routeContext.projectId != null || routeContext.threadId != null) {
            refreshRuntimeIndexAsync("route_changed")
        }
    }

    fun diagnosticsSummary(): DiagnosticsSummary {
        val ignored = logRevisionState
        return diagnosticsSummaryProvider.snapshot(
            runtimeSnapshot = runtimeDiagnosticsSnapshot(),
        )
    }

    fun projects(): List<CodexProject> {
        val ignored = domainRevisionState
        return projectsRepository.loadProjects()
    }

    fun project(projectId: String): CodexProject? {
        val ignored = domainRevisionState
        return projectsRepository.loadProject(ProjectId(projectId))
    }

    fun threads(projectId: String): List<CodexThread> {
        val ignored = domainRevisionState
        return threadRepository.loadThreads(ProjectId(projectId))
    }

    fun thread(threadId: String): CodexThread? {
        val ignored = domainRevisionState
        return threadRepository.loadThread(ThreadId(threadId))
    }

    fun timeline(threadId: String): List<TimelineItem> {
        val ignored = domainRevisionState
        return threadRepository.loadTimeline(ThreadId(threadId))
    }

    fun openThreadSession(threadId: String) {
        val targetThreadId = ThreadId(threadId)
        if (!canUseRuntimePath()) {
            foregroundThreadSession = failedForegroundSession(
                threadId = targetThreadId,
                message = "Host Bridge runtime path доступен только после connect/reconnect.",
            )
            return
        }
        if (foregroundThreadSession.activeThreadId == targetThreadId &&
            foregroundThreadSession.streamState !in setOf(
                ForegroundThreadStreamState.Idle,
                ForegroundThreadStreamState.Failed,
                ForegroundThreadStreamState.AwaitingReconnect,
            )
        ) {
            return
        }
        foregroundThreadSession = foregroundThreadSession.copy(
            activeThreadId = targetThreadId,
            streamState = ForegroundThreadStreamState.Hydrating,
            lastError = null,
        )
        recoverForegroundThreadSession(targetThreadId, "thread_opened")
    }

    fun loadOlderHistory(threadId: String) {
        val targetThreadId = ThreadId(threadId)
        val currentHistoryState = threadRepository.historyState(targetThreadId)
        if (!currentHistoryState.hasOlderHistory || currentHistoryState.isLoadingOlderHistory) {
            return
        }
        val expectedGeneration = foregroundStreamGeneration
        foregroundThreadSession = foregroundThreadSession.copy(
            historyState = currentHistoryState.copy(isLoadingOlderHistory = true),
            lastError = null,
        )
        foregroundRuntimeExecutor.execute {
            val result = runCatching {
                threadRepository.loadOlderHistory(targetThreadId)
            }
            mainHandler.post {
                if (expectedGeneration != foregroundStreamGeneration ||
                    foregroundThreadSession.activeThreadId != targetThreadId
                ) {
                    return@post
                }
                result.onSuccess {
                    domainRevisionState += 1
                    syncForegroundSession(threadId = targetThreadId)
                }.onFailure { error ->
                    foregroundThreadSession = foregroundThreadSession.copy(
                        historyState = threadRepository.historyState(targetThreadId),
                        lastError = error.message ?: "Не удалось догрузить более старую историю.",
                    )
                    logger.warn(
                        LogEvents.info(
                            area = LogArea.Thread,
                            eventName = "thread_history_load_failed",
                            messageHuman = foregroundThreadSession.lastError
                                ?: "Не удалось догрузить более старую историю.",
                            fields = mapOf("threadId" to targetThreadId.value),
                        ),
                    )
                }
            }
        }
    }

    fun updateComposerDraft(value: String) {
        foregroundThreadSession = foregroundThreadSession.copy(composerDraft = value)
    }

    fun sendPrompt(threadId: String) {
        val targetThreadId = ThreadId(threadId)
        if (!foregroundThreadSession.canSendPrompt(runtimePathAvailable = canUseRuntimePath())) {
            return
        }
        val text = foregroundThreadSession.composerDraft.trim()
        val previousDraft = foregroundThreadSession.composerDraft
        foregroundThreadSession = foregroundThreadSession.copy(
            composerDraft = "",
            streamState = ForegroundThreadStreamState.Streaming,
            lastError = null,
        )
        foregroundRuntimeExecutor.execute {
            val result = runCatching {
                threadRepository.startTurn(targetThreadId, text)
            }
            mainHandler.post {
                result.onSuccess { turnId ->
                    domainRevisionState += 1
                    syncForegroundSession(
                        threadId = targetThreadId,
                        streamState = ForegroundThreadStreamState.Streaming,
                        activeTurnId = turnId,
                        lastTurnId = turnId,
                    )
                    logger.info(
                        LogEvents.info(
                            area = LogArea.Turn,
                            eventName = "turn_start_requested",
                            messageHuman = "Foreground turn started from Android composer.",
                            fields = mapOf(
                                "threadId" to threadId,
                                "turnId" to turnId.value,
                            ),
                        ),
                    )
                }.onFailure { error ->
                    val pendingApprovals = threadRepository.unresolvedApprovals(targetThreadId)
                    val thread = threadRepository.loadThread(targetThreadId)
                    foregroundThreadSession = foregroundThreadSession.copy(
                        composerDraft = previousDraft,
                        activeTurnId = null,
                        streamState = if (canUseRuntimePath()) {
                            ForegroundThreadStreamState.Ready
                        } else {
                            ForegroundThreadStreamState.Failed
                        },
                        blockedReason = blockedReasonFor(thread, pendingApprovals),
                        pendingApprovals = pendingApprovals,
                        lastError = error.message ?: "Не удалось отправить turn/start.",
                    )
                    logger.error(
                        LogEvents.info(
                            area = LogArea.Turn,
                            eventName = "turn_start_failed",
                            messageHuman = foregroundThreadSession.lastError ?: "Не удалось отправить turn/start.",
                            fields = mapOf("threadId" to threadId),
                        ),
                    )
                }
            }
        }
    }

    fun interruptTurn(threadId: String) {
        val targetThreadId = ThreadId(threadId)
        if (!foregroundThreadSession.canStopTurn(runtimePathAvailable = canUseRuntimePath())) {
            return
        }
        val activeTurnId = foregroundThreadSession.activeTurnId ?: return
        foregroundThreadSession = foregroundThreadSession.copy(
            streamState = ForegroundThreadStreamState.Interrupting,
            lastTurnId = activeTurnId,
        )
        foregroundRuntimeExecutor.execute {
            val result = runCatching {
                threadRepository.interruptTurn(targetThreadId, activeTurnId)
            }
            mainHandler.post {
                result.onSuccess {
                    logger.info(
                        LogEvents.info(
                            area = LogArea.Turn,
                            eventName = "turn_interrupt_requested",
                            messageHuman = "Interrupt requested for active turn.",
                            fields = mapOf(
                                "threadId" to threadId,
                                "turnId" to activeTurnId.value,
                            ),
                        ),
                    )
                }.onFailure { error ->
                    foregroundThreadSession = foregroundThreadSession.copy(
                        streamState = ForegroundThreadStreamState.Failed,
                        lastError = error.message ?: "Не удалось отправить turn/interrupt.",
                    )
                    logger.error(
                        LogEvents.info(
                            area = LogArea.Turn,
                            eventName = "turn_interrupt_failed",
                            messageHuman = foregroundThreadSession.lastError
                                ?: "Не удалось отправить turn/interrupt.",
                            fields = mapOf(
                                "threadId" to threadId,
                                "turnId" to activeTurnId.value,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun resolveApproval(
        requestId: String,
        decision: ApprovalDecision,
    ) {
        val activeThreadId = foregroundThreadSession.activeThreadId ?: return
        if (!canUseRuntimePath()) {
            foregroundThreadSession = failedForegroundSession(
                threadId = activeThreadId,
                message = "Host Bridge runtime path доступен только после connect/reconnect.",
            )
            return
        }
        foregroundThreadSession = foregroundThreadSession.copy(
            approvalActionInFlightRequestId = requestId,
            lastRequestId = requestId,
            lastError = null,
        )
        foregroundRuntimeExecutor.execute {
            val result = runCatching {
                threadRepository.respondToApproval(requestId, decision)
            }
            mainHandler.post {
                result.onSuccess {
                    logger.info(
                        LogEvents.info(
                            area = LogArea.Approval,
                            eventName = "approval_response_sent",
                            messageHuman = "Approval response sent through Host Bridge.",
                            fields = mapOf(
                                "threadId" to activeThreadId.value,
                                "requestId" to requestId,
                                "decision" to decision.name,
                            ),
                        ),
                    )
                }.onFailure { error ->
                    foregroundThreadSession = failedForegroundSession(
                        threadId = activeThreadId,
                        message = error.message ?: "Не удалось отправить approval response.",
                    )
                    logger.error(
                        LogEvents.info(
                            area = LogArea.Approval,
                            eventName = "approval_response_failed",
                            messageHuman = foregroundThreadSession.lastError
                                ?: "Не удалось отправить approval response.",
                            fields = mapOf(
                                "threadId" to activeThreadId.value,
                                "requestId" to requestId,
                                "decision" to decision.name,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun threadSessionState(threadId: String): ForegroundThreadSessionState =
        if (foregroundThreadSession.activeThreadId?.value == threadId) {
            foregroundThreadSession
        } else {
            ForegroundThreadSessionState()
        }

    fun runtimePathAvailable(): Boolean = canUseRuntimePath()

    fun updatePairingInput(value: String) {
        pairingInput = value
    }

    fun savePairingPayload() {
        hostBridgeGeneration += 1
        cancelHostBridgeProbeLoop()
        val generation = hostBridgeGeneration
        val rawPayload = pairingInput
        hostBridgeExecutor.execute {
            if (generation != hostBridgeGeneration) {
                return@execute
            }
            val result = runCatching {
                hostBridgeRepository.savePairingPayload(
                    rawPayload = rawPayload,
                    localNetworkPermissionGranted = localNetworkPermissionGranted,
                )
            }
            mainHandler.post {
                if (generation != hostBridgeGeneration) {
                    return@post
                }
                result.onSuccess { nextState ->
                    applyHostBridgeState(nextState)
                    refreshRuntimeIndexAsync("pairing_saved")
                    logger.info(
                        LogEvents.info(
                            area = LogArea.HostBridge,
                            eventName = "pairing_saved",
                            messageHuman = "Сохранен pairing payload host bridge.",
                            fields = buildMap {
                                nextState.pairedHost?.hostId?.value?.let { put("hostId", it) }
                                nextState.pairedHost?.transport?.name?.let { put("transport", it) }
                                endpointHostOrNull(nextState.pairedHost?.endpoint.orEmpty())?.let { put("endpointHost", it) }
                            },
                        ),
                    )
                }.onFailure { error ->
                    hostBridgeState = failedHostBridgeState(error.message ?: "Не удалось сохранить pairing payload.")
                    logger.error(
                        LogEvents.info(
                            area = LogArea.Security,
                            eventName = "pairing_save_failed",
                            messageHuman = hostBridgeState.lastError ?: "Не удалось сохранить pairing payload.",
                        ),
                    )
                }
            }
        }
    }

    fun connectHostBridge() {
        if (!hasSavedPairing()) {
            hostBridgeState = failedHostBridgeState("Сначала сохраните pairing payload.")
            return
        }
        logger.info(
            LogEvents.info(
                area = LogArea.Connection,
                eventName = "host_bridge_connect_started",
                messageHuman = "Запущена попытка подключения к host bridge.",
                fields = hostBridgeFields(),
            ),
        )
        val generation = beginHostBridgeConnectTransition()
        hostBridgeExecutor.execute {
            if (generation != hostBridgeGeneration) {
                return@execute
            }
            val result = runCatching {
                hostBridgeRepository.connect(localNetworkPermissionGranted)
            }
            mainHandler.post {
                if (generation != hostBridgeGeneration) {
                    return@post
                }
                applyHostBridgeState(
                    result.getOrElse { error ->
                        failedHostBridgeState(error.message ?: "Не удалось подключиться к host bridge.")
                    },
                )
            }
        }
    }

    fun reconnectHostBridge() {
        if (!hasSavedPairing()) {
            hostBridgeState = failedHostBridgeState("Сначала сохраните pairing payload.")
            return
        }
        logger.info(
            LogEvents.info(
                area = LogArea.Connection,
                eventName = "host_bridge_reconnect_started",
                messageHuman = "Запущена попытка повторного подключения к host bridge.",
                fields = hostBridgeFields(),
            ),
        )
        val generation = beginHostBridgeConnectTransition()
        hostBridgeExecutor.execute {
            if (generation != hostBridgeGeneration) {
                return@execute
            }
            val result = runCatching {
                hostBridgeRepository.reconnect(localNetworkPermissionGranted)
            }
            mainHandler.post {
                if (generation != hostBridgeGeneration) {
                    return@post
                }
                applyHostBridgeState(
                    result.getOrElse { error ->
                        failedHostBridgeState(error.message ?: "Не удалось повторно подключиться к host bridge.")
                    },
                )
            }
        }
    }

    fun disconnectHostBridge(clearPairing: Boolean = false) {
        hostBridgeGeneration += 1
        hostBridgeProbeBarrier.disable()
        hostBridgeImmediateProbeCoordinator.disable()
        cancelHostBridgeProbeLoop()
        closeForegroundThreadSession()
        if (!hasSavedPairing()) {
            if (clearPairing) {
                pairingInput = ""
                applyHostBridgeState(hostBridgeRepository.currentState(), shouldLogTransition = false)
            } else {
                hostBridgeState = failedHostBridgeState("Сохраненного pairing payload нет.")
            }
            return
        }
        val generation = hostBridgeGeneration
        hostBridgeExecutor.execute {
            if (generation != hostBridgeGeneration) {
                return@execute
            }
            val result = runCatching {
                hostBridgeRepository.disconnect(clearPairing = clearPairing)
            }
            mainHandler.post {
                if (generation != hostBridgeGeneration) {
                    return@post
                }
                val nextState = result.getOrElse { error ->
                    failedHostBridgeState(error.message ?: "Не удалось завершить host bridge action.")
                }
                applyHostBridgeState(nextState, shouldLogTransition = false)
                if (clearPairing) {
                    pairingInput = ""
                }
                logger.info(
                    LogEvents.info(
                        area = LogArea.Connection,
                        eventName = if (clearPairing) {
                            "pairing_cleared"
                        } else {
                            "host_bridge_disconnected"
                        },
                        messageHuman = if (clearPairing) {
                            "Сохраненный pairing payload удален."
                        } else {
                            "Подключение к host bridge остановлено."
                        },
                        fields = hostBridgeFields(),
                    ),
                )
            }
        }
    }

    fun requestLocalNetworkPermission() {
        logger.info(
            LogEvents.info(
                area = LogArea.Connection,
                eventName = "local_network_permission_requested",
                messageHuman = "Запрошено разрешение Nearby devices для Android 16 local network path.",
            ),
        )
    }

    fun onLocalNetworkPermissionResult(granted: Boolean) {
        localNetworkPermissionGranted = granted
        logger.info(
            LogEvents.info(
                area = LogArea.Connection,
                eventName = "local_network_permission_result",
                messageHuman = if (granted) {
                    "Разрешение Nearby devices получено."
                } else {
                    "Разрешение Nearby devices не выдано."
                },
                fields = mapOf("granted" to granted.toString()),
            ),
        )
        hostBridgeGeneration += 1
        hostBridgeProbeBarrier.disable()
        hostBridgeImmediateProbeCoordinator.disable()
        cancelHostBridgeProbeLoop()
        val generation = hostBridgeGeneration
        hostBridgeExecutor.execute {
            if (generation != hostBridgeGeneration) {
                return@execute
            }
            val nextState = hostBridgeRepository.refreshPermissionState(granted)
            mainHandler.post {
                if (generation != hostBridgeGeneration) {
                    return@post
                }
                applyHostBridgeState(nextState)
            }
        }
    }

    fun canAttemptHostBridgeConnect(): Boolean = hasSavedPairing()

    fun canDisconnectHostBridge(): Boolean = hasSavedPairing()

    fun shouldOfferNearbyDevicesPermission(): Boolean =
        hasSavedPairing() &&
            !hostBridgeState.nearbyWifiDevicesGranted &&
            hostBridgeState.localNetworkAccessState != LocalNetworkAccessState.UnsupportedForSlice

    private fun failedHostBridgeState(message: String): HostBridgeConnectionState = HostBridgeConnectionState(
        phase = HostBridgeConnectionPhase.Failed,
        pairedHost = hostBridgeState.pairedHost,
        runtimeSummary = hostBridgeState.runtimeSummary,
        localNetworkAccessState = hostBridgeState.localNetworkAccessState,
        nearbyWifiDevicesGranted = hostBridgeState.nearbyWifiDevicesGranted,
        lastError = message,
        lastTransitionAtEpochMs = System.currentTimeMillis(),
        lastConnectedAtEpochMs = hostBridgeState.lastConnectedAtEpochMs,
    )

    private fun beginHostBridgeConnectTransition(): Long {
        hostBridgeGeneration += 1
        hostBridgeProbeBarrier.disable()
        hostBridgeImmediateProbeCoordinator.disable()
        cancelHostBridgeProbeLoop()
        hostBridgeState = hostBridgeState.copy(
            phase = HostBridgeConnectionPhase.Connecting,
            lastError = null,
            lastTransitionAtEpochMs = System.currentTimeMillis(),
        )
        return hostBridgeGeneration
    }

    private fun applyHostBridgeState(
        nextState: HostBridgeConnectionState,
        shouldLogTransition: Boolean = true,
    ) {
        val previousState = hostBridgeState
        hostBridgeState = nextState
        if (shouldLogTransition && didHostBridgeStateMeaningfullyChange(previousState, nextState)) {
            logHostBridgeTransition()
        }
        when (nextState.phase) {
            HostBridgeConnectionPhase.Connected -> {
                hostBridgeProbeBarrier.enableForGeneration(hostBridgeGeneration)
                hostBridgeImmediateProbeCoordinator.enableForGeneration(hostBridgeGeneration)
                ensureHostBridgeProbeLoop()
                refreshRuntimeIndexAsync("host_bridge_connected")
                val activeThreadId = foregroundThreadSession.activeThreadId
                if (activeThreadId != null &&
                    foregroundThreadSession.streamState in setOf(
                        ForegroundThreadStreamState.AwaitingReconnect,
                        ForegroundThreadStreamState.Failed,
                    )
                ) {
                        recoverForegroundThreadSession(activeThreadId, "host_bridge_recovered")
                }
            }

            HostBridgeConnectionPhase.Degraded -> {
                hostBridgeProbeBarrier.enableForGeneration(hostBridgeGeneration)
                hostBridgeImmediateProbeCoordinator.enableForGeneration(hostBridgeGeneration)
                ensureHostBridgeProbeLoop()
            }

            else -> {
                hostBridgeProbeBarrier.disable()
                hostBridgeImmediateProbeCoordinator.disable()
                cancelHostBridgeProbeLoop()
                if (foregroundThreadSession.activeThreadId != null) {
                    foregroundThreadSession = foregroundThreadSession.copy(
                        streamState = ForegroundThreadStreamState.AwaitingReconnect,
                        lastError = nextState.lastError ?: "Host Bridge is not currently connected.",
                    )
                }
            }
        }
    }

    private fun ensureHostBridgeProbeLoop() {
        val currentFuture = hostBridgeProbeFuture
        if (currentFuture != null && !currentFuture.isDone && !currentFuture.isCancelled) {
            return
        }
        val generation = hostBridgeGeneration
        val ticket = hostBridgeProbeBarrier.capture(generation) ?: return
        hostBridgeProbeFuture = hostBridgeExecutor.scheduleWithFixedDelay(
            {
                if (generation != hostBridgeGeneration || !hostBridgeProbeBarrier.allows(ticket, hostBridgeGeneration)) {
                    return@scheduleWithFixedDelay
                }
                val result = runCatching {
                    hostBridgeRepository.probe(localNetworkPermissionGranted)
                }
                mainHandler.post {
                    if (generation != hostBridgeGeneration || !hostBridgeProbeBarrier.allows(ticket, hostBridgeGeneration)) {
                        return@post
                    }
                    val nextState = result.getOrElse { error ->
                        failedHostBridgeState(error.message ?: "Не удалось выполнить probe к host bridge.")
                    }
                    val shouldLog = didHostBridgeStateMeaningfullyChange(hostBridgeState, nextState)
                    applyHostBridgeState(nextState, shouldLogTransition = shouldLog)
                }
            },
            HOST_BRIDGE_PROBE_INTERVAL_MS,
            HOST_BRIDGE_PROBE_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun cancelHostBridgeProbeLoop() {
        hostBridgeProbeFuture?.cancel(false)
        hostBridgeProbeFuture = null
    }

    private fun onAndroidNetworkChanged() {
        logger.info(
            LogEvents.info(
                area = LogArea.Connection,
                eventName = "android_network_changed",
                messageHuman = "Android network path изменился.",
                fields = hostBridgeFields(),
            ),
        )
        if (shouldAutoProbeAfterNetworkChange()) {
            submitImmediateHostBridgeProbe()
        }
    }

    private fun submitImmediateHostBridgeProbe() {
        val generation = hostBridgeGeneration
        val ticket = hostBridgeProbeBarrier.capture(generation) ?: return
        if (!hostBridgeImmediateProbeCoordinator.request(generation)) {
            return
        }
        hostBridgeExecutor.execute {
            while (true) {
                if (generation != hostBridgeGeneration ||
                    !hostBridgeProbeBarrier.allows(ticket, hostBridgeGeneration) ||
                    !hostBridgeImmediateProbeCoordinator.canRun(generation)
                ) {
                    hostBridgeImmediateProbeCoordinator.finishRun(generation)
                    return@execute
                }
                val result = runCatching {
                    hostBridgeRepository.probe(localNetworkPermissionGranted)
                }
                mainHandler.post {
                    if (generation != hostBridgeGeneration || !hostBridgeProbeBarrier.allows(ticket, hostBridgeGeneration)) {
                        return@post
                    }
                    val nextState = result.getOrElse { error ->
                        failedHostBridgeState(error.message ?: "Не удалось восстановить probe после возврата сети.")
                    }
                    val shouldLog = didHostBridgeStateMeaningfullyChange(hostBridgeState, nextState)
                    applyHostBridgeState(nextState, shouldLogTransition = shouldLog)
                }
                when (hostBridgeImmediateProbeCoordinator.finishRun(generation)) {
                    HostBridgeImmediateProbeNextAction.Rerun -> continue
                    HostBridgeImmediateProbeNextAction.Idle,
                    HostBridgeImmediateProbeNextAction.Disabled,
                    -> return@execute
                }
            }
        }
    }

    private fun shouldAutoProbeAfterNetworkChange(): Boolean =
        hasSavedPairing() &&
            hostBridgeProbeBarrier.isEnabledForGeneration(hostBridgeGeneration) &&
            hostBridgeImmediateProbeCoordinator.isEnabledForGeneration(hostBridgeGeneration) &&
            hostBridgeState.localNetworkAccessState == LocalNetworkAccessState.Ready &&
            hostBridgeState.phase in setOf(
                HostBridgeConnectionPhase.Connected,
                HostBridgeConnectionPhase.Degraded,
            )

    private fun refreshRuntimeIndexAsync(reason: String) {
        if (!canUseRuntimePath()) {
            return
        }
        synchronized(this) {
            if (runtimeIndexRefreshInFlight) {
                runtimeIndexRefreshPendingReason = reason
                return
            }
            runtimeIndexRefreshInFlight = true
        }
        hostBridgeExecutor.execute {
            val result = runCatching {
                threadRepository.refreshIndex()
            }
            mainHandler.post {
                val nextReason = synchronized(this) {
                    runtimeIndexRefreshInFlight = false
                    val rerunReason = runtimeIndexRefreshPendingReason
                    runtimeIndexRefreshPendingReason = null
                    rerunReason
                }
                result.onSuccess {
                    domainRevisionState += 1
                    logger.info(
                        LogEvents.info(
                            area = LogArea.Thread,
                            eventName = "thread_index_refreshed",
                            messageHuman = "Runtime-backed thread index refreshed.",
                            fields = mapOf("reason" to reason),
                        ),
                    )
                }.onFailure { error ->
                    logger.warn(
                        LogEvents.info(
                            area = LogArea.Thread,
                            eventName = "thread_index_refresh_failed",
                            messageHuman = error.message ?: "Не удалось обновить thread index.",
                            fields = mapOf("reason" to reason),
                        ),
                    )
                }
                if (nextReason != null && canUseRuntimePath()) {
                    refreshRuntimeIndexAsync(nextReason)
                }
            }
        }
    }

    private fun recoverForegroundThreadSession(
        threadId: ThreadId,
        reason: String,
    ) {
        closeForegroundEventStream()
        val generation = ++foregroundStreamGeneration
        foregroundThreadSession = foregroundThreadSession.copy(
            activeThreadId = threadId,
            streamState = ForegroundThreadStreamState.Hydrating,
            reconnectGeneration = foregroundThreadSession.reconnectGeneration + 1,
            lastRecoverAttemptAtEpochMs = System.currentTimeMillis(),
            lastError = null,
        )
        foregroundRuntimeExecutor.execute {
            var attachedStream: HostBridgeEventStream? = null
            val result = runCatching {
                threadRepository.readThreadSummary(threadId)
                attachedStream = threadRepository.openEventStream(threadId)
                val resumedThread = threadRepository.resumeThread(threadId)
                var initialHistoryError: Throwable? = null
                if (resumedThread?.ephemeral != true) {
                    runCatching {
                        threadRepository.loadInitialHistory(threadId)
                    }.onFailure { error ->
                        initialHistoryError = error
                    }
                }
                RecoveryResult(
                    stream = requireNotNull(attachedStream) {
                        "Foreground event stream did not attach."
                    },
                    initialHistoryError = initialHistoryError,
                )
            }
            mainHandler.post {
                if (generation != foregroundStreamGeneration || foregroundThreadSession.activeThreadId != threadId) {
                    result.getOrNull()?.stream?.close()
                    return@post
                }
                result.onSuccess { recovery ->
                    domainRevisionState += 1
                    syncForegroundSession(
                        threadId = threadId,
                        streamState = ForegroundThreadStreamState.Ready,
                    )
                    if (recovery.initialHistoryError != null) {
                        foregroundThreadSession = foregroundThreadSession.copy(
                            lastError = recovery.initialHistoryError.message
                                ?: "Не удалось загрузить начальную страницу истории.",
                        )
                        logger.warn(
                            LogEvents.info(
                                area = LogArea.Thread,
                                eventName = "foreground_thread_initial_history_failed",
                                messageHuman = foregroundThreadSession.lastError
                                    ?: "Не удалось загрузить начальную страницу истории.",
                                fields = mapOf(
                                    "threadId" to threadId.value,
                                    "reason" to reason,
                                ),
                            ),
                        )
                    }
                    logger.info(
                        LogEvents.info(
                            area = LogArea.Thread,
                            eventName = "foreground_thread_rehydrated",
                            messageHuman = "Foreground thread rehydrated from runtime.",
                            fields = mapOf(
                                "threadId" to threadId.value,
                                "reason" to reason,
                            ),
                        ),
                    )
                    startForegroundEventLoop(threadId, generation, recovery.stream)
                }.onFailure { error ->
                    attachedStream?.close()
                    foregroundThreadSession = failedForegroundSession(
                        threadId = threadId,
                        message = error.message ?: "Не удалось восстановить foreground thread.",
                    )
                    logger.error(
                        LogEvents.info(
                            area = LogArea.Thread,
                            eventName = "foreground_thread_rehydrate_failed",
                            messageHuman = foregroundThreadSession.lastError
                                ?: "Не удалось восстановить foreground thread.",
                            fields = mapOf(
                                "threadId" to threadId.value,
                                "reason" to reason,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun startForegroundEventLoop(
        threadId: ThreadId,
        generation: Long,
        stream: HostBridgeEventStream,
    ) {
        foregroundStreamExecutor.execute {
            foregroundEventStream = stream
            try {
                while (generation == foregroundStreamGeneration && foregroundThreadSession.activeThreadId == threadId) {
                    val event = stream.nextEvent() ?: break
                    mainHandler.post {
                        if (generation != foregroundStreamGeneration || foregroundThreadSession.activeThreadId != threadId) {
                            return@post
                        }
                        handleForegroundEvent(threadId, event)
                    }
                }
            } catch (error: HostBridgeClientException) {
                mainHandler.post {
                    if (generation == foregroundStreamGeneration && foregroundThreadSession.activeThreadId == threadId) {
                        foregroundThreadSession = foregroundThreadSession.copy(
                            streamState = ForegroundThreadStreamState.AwaitingReconnect,
                            lastError = error.message ?: "Foreground event stream disconnected.",
                        )
                    }
                }
            } finally {
                stream.close()
                if (foregroundEventStream === stream) {
                    foregroundEventStream = null
                }
                mainHandler.post {
                    if (generation == foregroundStreamGeneration && foregroundThreadSession.activeThreadId == threadId &&
                        foregroundThreadSession.streamState !in setOf(
                            ForegroundThreadStreamState.Idle,
                            ForegroundThreadStreamState.Failed,
                        )
                    ) {
                        foregroundThreadSession = foregroundThreadSession.copy(
                            streamState = ForegroundThreadStreamState.AwaitingReconnect,
                            lastError = foregroundThreadSession.lastError ?: "Foreground event stream disconnected.",
                        )
                    }
                }
            }
        }
    }

    private data class RecoveryResult(
        val stream: HostBridgeEventStream,
        val initialHistoryError: Throwable?,
    )

    private fun handleForegroundEvent(
        threadId: ThreadId,
        event: HostBridgeThreadEvent,
    ) {
        threadRepository.applyEvent(event)
        domainRevisionState += 1
        when (event.method) {
            "turn/started" -> syncForegroundSession(
                threadId = threadId,
                streamState = ForegroundThreadStreamState.Streaming,
                activeTurnId = event.turn?.id?.let(::TurnId),
                lastTurnId = event.turn?.id?.let(::TurnId),
            )

            "turn/completed" -> syncForegroundSession(
                threadId = threadId,
                streamState = ForegroundThreadStreamState.Ready,
                activeTurnId = null,
                lastTurnId = event.turn?.id?.let(::TurnId) ?: foregroundThreadSession.lastTurnId,
            )

            "item/agentMessage/delta" -> syncForegroundSession(
                threadId = threadId,
                streamState = ForegroundThreadStreamState.Streaming,
                activeTurnId = event.turnId?.let(::TurnId) ?: foregroundThreadSession.activeTurnId,
                lastTurnId = event.turnId?.let(::TurnId) ?: foregroundThreadSession.lastTurnId,
                lastItemId = event.itemId ?: foregroundThreadSession.lastItemId,
            )

            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            -> syncForegroundSession(
                threadId = threadId,
                streamState = ForegroundThreadStreamState.Ready,
                lastTurnId = event.approval?.turnId?.let(::TurnId) ?: foregroundThreadSession.lastTurnId,
                lastRequestId = event.requestId ?: foregroundThreadSession.lastRequestId,
                lastItemId = event.approval?.itemId ?: foregroundThreadSession.lastItemId,
            )

            "serverRequest/resolved" -> syncForegroundSession(
                threadId = threadId,
                streamState = ForegroundThreadStreamState.Ready,
                lastRequestId = event.requestId ?: foregroundThreadSession.lastRequestId,
            )

            "item/tool/requestUserInput" -> syncForegroundSession(
                threadId = threadId,
                streamState = ForegroundThreadStreamState.Ready,
                lastTurnId = event.turnId?.let(::TurnId) ?: foregroundThreadSession.lastTurnId,
                lastItemId = event.itemId ?: foregroundThreadSession.lastItemId,
            )

            "error" -> foregroundThreadSession = foregroundThreadSession.copy(
                activeThreadId = threadId,
                activeTurnId = null,
                streamState = ForegroundThreadStreamState.Failed,
                blockedReason = null,
                pendingApprovals = threadRepository.unresolvedApprovals(threadId),
                lastTurnId = event.turnId?.let(::TurnId) ?: foregroundThreadSession.lastTurnId,
                lastItemId = event.itemId ?: foregroundThreadSession.lastItemId,
                lastError = event.message ?: "Foreground runtime error.",
            )

            else -> syncForegroundSession(threadId = threadId)
        }
    }

    private fun syncForegroundSession(
        threadId: ThreadId,
        streamState: ForegroundThreadStreamState = foregroundThreadSession.streamState,
        activeTurnId: TurnId? = foregroundThreadSession.activeTurnId,
        lastTurnId: TurnId? = foregroundThreadSession.lastTurnId,
        lastRequestId: String? = foregroundThreadSession.lastRequestId,
        lastItemId: String? = foregroundThreadSession.lastItemId,
    ) {
        val thread = threadRepository.loadThread(threadId)
        val pendingApprovals = threadRepository.unresolvedApprovals(threadId)
        val historyState = threadRepository.historyState(threadId)
        val approvalActionInFlightRequestId =
            foregroundThreadSession.approvalActionInFlightRequestId?.takeIf { inFlightRequestId ->
                pendingApprovals.any { approval -> approval.requestId == inFlightRequestId }
            }
        foregroundThreadSession = foregroundThreadSession.copy(
            activeThreadId = threadId,
            activeTurnId = activeTurnId,
            streamState = streamState,
            blockedReason = blockedReasonFor(thread, pendingApprovals),
            pendingApprovals = pendingApprovals,
            historyState = historyState,
            approvalActionInFlightRequestId = approvalActionInFlightRequestId,
            lastTurnId = lastTurnId,
            lastRequestId = lastRequestId,
            lastItemId = lastItemId,
            lastError = null,
        )
    }

    private fun failedForegroundSession(
        threadId: ThreadId,
        message: String,
    ): ForegroundThreadSessionState {
        val thread = threadRepository.loadThread(threadId)
        val pendingApprovals = threadRepository.unresolvedApprovals(threadId)
        return foregroundThreadSession.copy(
            activeThreadId = threadId,
            activeTurnId = null,
            streamState = ForegroundThreadStreamState.Failed,
            blockedReason = blockedReasonFor(thread, pendingApprovals),
            pendingApprovals = pendingApprovals,
            historyState = threadRepository.historyState(threadId),
            approvalActionInFlightRequestId = null,
            lastError = message,
        )
    }

    private fun blockedReasonFor(
        thread: CodexThread?,
        pendingApprovals: List<TimelineItem.ApprovalRequest>,
    ): ForegroundThreadBlockedReason? = when {
        pendingApprovals.isNotEmpty() -> ForegroundThreadBlockedReason.WaitingOnApproval
        thread?.status == ThreadStatus.WaitingForUserInput -> ForegroundThreadBlockedReason.WaitingOnUserInput
        else -> null
    }

    private fun closeForegroundThreadSession() {
        closeForegroundEventStream()
        foregroundThreadSession = ForegroundThreadSessionState()
    }

    private fun closeForegroundEventStream() {
        foregroundStreamGeneration += 1
        foregroundEventStream?.close()
        foregroundEventStream = null
    }

    private fun runtimeDiagnosticsSnapshot(): RuntimeDiagnosticsSnapshot {
        val activeThread = foregroundThreadSession.activeThreadId?.let { threadId ->
            threadRepository.loadThread(threadId)
        }
        return RuntimeDiagnosticsSnapshot(
            activeThreadId = foregroundThreadSession.activeThreadId?.value,
            activeSessionId = activeThread?.sessionId,
            activeTurnId = foregroundThreadSession.activeTurnId?.value,
            streamState = foregroundThreadSession.streamState.name,
            blockedReason = foregroundThreadSession.blockedReason?.name,
            pendingApprovalSummary = foregroundThreadSession.pendingApprovals.joinToString { approval ->
                approval.requestId ?: approval.approvalId.value
            }.ifBlank { null },
            historyNextCursor = foregroundThreadSession.historyState.nextCursor,
            hasOlderHistory = foregroundThreadSession.historyState.hasOlderHistory,
            isLoadingOlderHistory = foregroundThreadSession.historyState.isLoadingOlderHistory,
            reconnectGeneration = foregroundThreadSession.reconnectGeneration,
            lastRecoverAttemptAtEpochMs = foregroundThreadSession.lastRecoverAttemptAtEpochMs,
            approvalActionInFlightRequestId = foregroundThreadSession.approvalActionInFlightRequestId,
            lastTurnId = foregroundThreadSession.lastTurnId?.value,
            lastRequestId = foregroundThreadSession.lastRequestId,
            lastItemId = foregroundThreadSession.lastItemId,
            lastError = foregroundThreadSession.lastError,
        )
    }

    private fun didHostBridgeStateMeaningfullyChange(
        previousState: HostBridgeConnectionState,
        nextState: HostBridgeConnectionState,
    ): Boolean = previousState.phase != nextState.phase ||
        previousState.lastError != nextState.lastError ||
        previousState.localNetworkAccessState != nextState.localNetworkAccessState ||
        previousState.runtimeSummary.hostStatus != nextState.runtimeSummary.hostStatus ||
        previousState.runtimeSummary.appListCount != nextState.runtimeSummary.appListCount ||
        previousState.runtimeSummary.retryAttempt != nextState.runtimeSummary.retryAttempt

    private fun hostBridgeFields(): Map<String, String> = buildMap {
        hostBridgeState.pairedHost?.hostId?.value?.let { put("hostId", it) }
        hostBridgeState.pairedHost?.transport?.name?.let { put("transport", it) }
        endpointHostOrNull(hostBridgeState.pairedHost?.endpoint.orEmpty())?.let { put("endpointHost", it) }
        hostBridgeState.pairedHost?.endpoint?.let { put("endpointDisplay", hostBridgeEndpointDisplayValue(it)) }
        put("phase", hostBridgeState.phase.name)
        put("nearbyGranted", hostBridgeState.nearbyWifiDevicesGranted.toString())
        put("runtimeSnapshotScope", hostBridgeState.runtimeSummaryScope().name)
        put("runtimeStatus", hostBridgeState.runtimeSummary.hostStatus.name)
        put("runtimeReady", hostBridgeState.runtimeSummary.runtimeReady.toString())
        hostBridgeState.runtimeSummary.appListCount?.let { put("appListCount", it.toString()) }
        hostBridgeState.runtimeSummary.lastRoundTripMs?.let { put("lastRoundTripMs", it.toString()) }
        hostBridgeState.runtimeSummary.lastProbeAtEpochMs?.let { put("lastProbeAtEpochMs", it.toString()) }
        if (hostBridgeState.runtimeSummary.retryAttempt > 0) {
            put("retryAttempt", hostBridgeState.runtimeSummary.retryAttempt.toString())
        }
        hostBridgeState.runtimeSummary.degradedReason?.let { put("degradedReason", it) }
        hostBridgeState.runtimeSummary.lastTransportError?.let { put("lastTransportError", it) }
        foregroundThreadSession.activeThreadId?.value?.let { put("activeThreadId", it) }
        foregroundThreadSession.activeTurnId?.value?.let { put("activeTurnId", it) }
        foregroundThreadSession.historyState.nextCursor?.let { put("historyNextCursor", it) }
        put("hasOlderHistory", foregroundThreadSession.historyState.hasOlderHistory.toString())
        put("isLoadingOlderHistory", foregroundThreadSession.historyState.isLoadingOlderHistory.toString())
        put("foregroundStreamState", foregroundThreadSession.streamState.name)
        foregroundThreadSession.lastRequestId?.let { put("lastRequestId", it) }
        foregroundThreadSession.lastItemId?.let { put("lastItemId", it) }
    }

    fun dispose() {
        hostBridgeGeneration += 1
        hostBridgeProbeBarrier.disable()
        hostBridgeImmediateProbeCoordinator.disable()
        cancelHostBridgeProbeLoop()
        closeForegroundEventStream()
        networkMonitor.stop()
        hostBridgeExecutor.shutdownNow()
        foregroundRuntimeExecutor.shutdownNow()
        foregroundStreamExecutor.shutdownNow()
    }

    private fun logHostBridgeTransition() {
        when (hostBridgeState.phase) {
            HostBridgeConnectionPhase.Connected -> logger.info(
                LogEvents.info(
                    area = LogArea.Connection,
                    eventName = "host_bridge_connect_succeeded",
                    messageHuman = "Подключение к host bridge помечено как готовое.",
                    fields = hostBridgeFields(),
                ),
            )

            HostBridgeConnectionPhase.Degraded -> logger.warn(
                LogEvents.info(
                    area = LogArea.Connection,
                    eventName = "host_bridge_degraded",
                    messageHuman = hostBridgeState.lastError ?: "Host bridge перешел в degraded state.",
                    fields = hostBridgeFields(),
                ),
            )

            HostBridgeConnectionPhase.Failed -> logger.error(
                LogEvents.info(
                    area = LogArea.HostBridge,
                    eventName = "host_bridge_connect_failed",
                    messageHuman = hostBridgeState.lastError ?: "Подключение к host bridge завершилось ошибкой.",
                    fields = hostBridgeFields(),
                ),
            )

            HostBridgeConnectionPhase.Paired ->
                if (!hostBridgeState.nearbyWifiDevicesGranted &&
                    hostBridgeState.localNetworkAccessState == LocalNetworkAccessState.Ready
                ) {
                    logger.warn(
                        LogEvents.info(
                            area = LogArea.Connection,
                            eventName = "host_bridge_permission_advisory",
                            messageHuman = "Nearby devices остается manual opt-in advisory для Android 16 local-network path.",
                            fields = hostBridgeFields(),
                        ),
                    )
                } else {
                    logger.info(
                        LogEvents.info(
                            area = LogArea.Connection,
                            eventName = "host_bridge_state_updated",
                            messageHuman = "Состояние host bridge обновлено.",
                            fields = hostBridgeFields(),
                        ),
                    )
                }

            else -> logger.info(
                LogEvents.info(
                    area = LogArea.Connection,
                    eventName = "host_bridge_state_updated",
                    messageHuman = "Состояние host bridge обновлено.",
                    fields = hostBridgeFields(),
                ),
            )
        }
    }

    private fun checkLocalNetworkPermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.NEARBY_WIFI_DEVICES,
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasSavedPairing(): Boolean = hostBridgeState.pairedHost != null

    private fun canUseRuntimePath(): Boolean = hasSavedPairing() &&
        hostBridgeState.phase in setOf(
            HostBridgeConnectionPhase.Connected,
            HostBridgeConnectionPhase.Degraded,
        )

    private companion object {
        const val HOST_BRIDGE_PROBE_INTERVAL_MS = 5_000L
    }
}

