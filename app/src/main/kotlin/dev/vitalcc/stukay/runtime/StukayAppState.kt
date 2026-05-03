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
import dev.vitalcc.stukay.core.logging.StructuredLogger
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.core.model.ApprovalId
import dev.vitalcc.stukay.core.model.CodexProject
import dev.vitalcc.stukay.core.model.CodexThread
import dev.vitalcc.stukay.core.model.HostBridgeConnectionPhase
import dev.vitalcc.stukay.core.model.HostBridgeConnectionState
import dev.vitalcc.stukay.core.model.LocalNetworkAccessState
import dev.vitalcc.stukay.core.model.ProjectId
import dev.vitalcc.stukay.core.model.RouteContext
import dev.vitalcc.stukay.core.model.ThreadId
import dev.vitalcc.stukay.core.model.TimelineItem
import dev.vitalcc.stukay.core.model.hostBridgeEndpointDisplayValue
import dev.vitalcc.stukay.feature.projects.domain.LoadProjectsUseCase
import dev.vitalcc.stukay.feature.projects.domain.OpenProjectUseCase
import dev.vitalcc.stukay.feature.thread.domain.CompleteFakeTurnUseCase
import dev.vitalcc.stukay.feature.thread.domain.LoadProjectThreadsUseCase
import dev.vitalcc.stukay.feature.thread.domain.ObserveThreadTimelineUseCase
import dev.vitalcc.stukay.feature.thread.domain.OpenThreadUseCase
import dev.vitalcc.stukay.feature.thread.domain.ResolveFakeApprovalUseCase
import dev.vitalcc.stukay.feature.thread.domain.StartFakeTurnUseCase
import dev.vitalcc.stukay.runtime.hostbridge.AndroidNetworkMonitor
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
    private val loadProjectsUseCase = LoadProjectsUseCase(projectsRepository)
    private val openProjectUseCase = OpenProjectUseCase(projectsRepository)
    private val loadProjectThreadsUseCase = LoadProjectThreadsUseCase(threadRepository)
    private val openThreadUseCase = OpenThreadUseCase(threadRepository)
    private val observeThreadTimelineUseCase = ObserveThreadTimelineUseCase(threadRepository)
    private val startFakeTurnUseCase = StartFakeTurnUseCase(threadRepository)
    private val completeFakeTurnUseCase = CompleteFakeTurnUseCase(threadRepository)
    private val resolveFakeApprovalUseCase = ResolveFakeApprovalUseCase(threadRepository)
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

    @Volatile
    private var hostBridgeGeneration: Long = 0L
    private var hostBridgeProbeFuture: ScheduledFuture<*>? = null

    init {
        networkMonitor.start()
        logger.info(
            logEvent(
                area = LogArea.App,
                eventName = "app_started",
                messageHuman = "Stukay app shell started",
            ),
        )
    }

    fun updateCurrentRouteContext(routeContext: RouteContext) {
        currentRouteContext = routeContext
        if (routeContext.projectId != null || routeContext.threadId != null) {
            lastInspectedRouteContext = routeContext
        }
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
                    logger.info(
                        logEvent(
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
                        logEvent(
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
            logEvent(
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
            logEvent(
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
        cancelHostBridgeProbeLoop()
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
                    logEvent(
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
            logEvent(
                area = LogArea.Connection,
                eventName = "local_network_permission_requested",
                messageHuman = "Запрошено разрешение Nearby devices для Android 16 local network path.",
            ),
        )
    }

    fun onLocalNetworkPermissionResult(granted: Boolean) {
        localNetworkPermissionGranted = granted
        logger.info(
            logEvent(
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
            HostBridgeConnectionPhase.Connected,
            HostBridgeConnectionPhase.Degraded,
            -> ensureHostBridgeProbeLoop()

            else -> cancelHostBridgeProbeLoop()
        }
    }

    private fun ensureHostBridgeProbeLoop() {
        val currentFuture = hostBridgeProbeFuture
        if (currentFuture != null && !currentFuture.isDone && !currentFuture.isCancelled) {
            return
        }
        val generation = hostBridgeGeneration
        hostBridgeProbeFuture = hostBridgeExecutor.scheduleWithFixedDelay(
            {
                if (generation != hostBridgeGeneration) {
                    return@scheduleWithFixedDelay
                }
                val result = runCatching {
                    hostBridgeRepository.probe(localNetworkPermissionGranted)
                }
                mainHandler.post {
                    if (generation != hostBridgeGeneration) {
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
            logEvent(
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
        hostBridgeExecutor.execute {
            if (generation != hostBridgeGeneration) {
                return@execute
            }
            val result = runCatching {
                hostBridgeRepository.probe(localNetworkPermissionGranted)
            }
            mainHandler.post {
                if (generation != hostBridgeGeneration) {
                    return@post
                }
                val nextState = result.getOrElse { error ->
                    failedHostBridgeState(error.message ?: "Не удалось восстановить probe после возврата сети.")
                }
                val shouldLog = didHostBridgeStateMeaningfullyChange(hostBridgeState, nextState)
                applyHostBridgeState(nextState, shouldLogTransition = shouldLog)
            }
        }
    }

    private fun shouldAutoProbeAfterNetworkChange(): Boolean =
        hasSavedPairing() &&
            hostBridgeState.localNetworkAccessState == LocalNetworkAccessState.Ready &&
            hostBridgeState.phase in setOf(
                HostBridgeConnectionPhase.Connected,
                HostBridgeConnectionPhase.Degraded,
            )

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
    }

    fun dispose() {
        hostBridgeGeneration += 1
        cancelHostBridgeProbeLoop()
        networkMonitor.stop()
        hostBridgeExecutor.shutdownNow()
    }

    private fun logHostBridgeTransition() {
        when (hostBridgeState.phase) {
            HostBridgeConnectionPhase.Connected -> logger.info(
                logEvent(
                    area = LogArea.Connection,
                    eventName = "host_bridge_connect_succeeded",
                    messageHuman = "Подключение к host bridge помечено как готовое.",
                    fields = hostBridgeFields(),
                ),
            )

            HostBridgeConnectionPhase.Degraded -> logger.warn(
                logEvent(
                    area = LogArea.Connection,
                    eventName = "host_bridge_degraded",
                    messageHuman = hostBridgeState.lastError ?: "Host bridge перешел в degraded state.",
                    fields = hostBridgeFields(),
                ),
            )

            HostBridgeConnectionPhase.Failed -> logger.error(
                logEvent(
                    area = LogArea.HostBridge,
                    eventName = "host_bridge_connect_failed",
                    messageHuman = hostBridgeState.lastError ?: "Подключение к host bridge завершилось ошибкой.",
                    fields = hostBridgeFields(),
                ),
            )

            HostBridgeConnectionPhase.Paired ->
                if (hostBridgeState.localNetworkAccessState == LocalNetworkAccessState.PermissionRequired) {
                    logger.warn(
                        logEvent(
                            area = LogArea.Connection,
                            eventName = "host_bridge_permission_required",
                            messageHuman = "Для local network path требуется Nearby devices permission.",
                            fields = hostBridgeFields(),
                        ),
                    )
                } else {
                    logger.info(
                        logEvent(
                            area = LogArea.Connection,
                            eventName = "host_bridge_state_updated",
                            messageHuman = "Состояние host bridge обновлено.",
                            fields = hostBridgeFields(),
                        ),
                    )
                }

            else -> logger.info(
                logEvent(
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

    private companion object {
        const val HOST_BRIDGE_PROBE_INTERVAL_MS = 5_000L
    }
}
