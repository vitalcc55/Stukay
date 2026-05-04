package dev.vitalcc.stukay

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.vitalcc.stukay.core.logging.LogArea
import dev.vitalcc.stukay.core.logging.logEvent
import dev.vitalcc.stukay.core.model.RouteContext
import dev.vitalcc.stukay.core.design.theme.StukayTheme
import dev.vitalcc.stukay.core.model.ApprovalDecision
import dev.vitalcc.stukay.feature.diagnostics.ui.DiagnosticsRoute
import dev.vitalcc.stukay.feature.projects.ui.ProjectRoute
import dev.vitalcc.stukay.feature.projects.ui.ProjectsRoute
import dev.vitalcc.stukay.feature.settings.ui.SettingsRoute
import dev.vitalcc.stukay.feature.thread.ui.ThreadRoute
import dev.vitalcc.stukay.navigation.ProjectDetailsDestination
import dev.vitalcc.stukay.navigation.StukayDestination
import dev.vitalcc.stukay.navigation.ThreadDestination
import dev.vitalcc.stukay.runtime.StukayAppState

@Composable
fun StukayApp(
    appState: StukayAppState,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val routeContext = currentBackStackEntry.toRouteContext()
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        appState.onLocalNetworkPermissionResult(granted)
    }

    LaunchedEffect(routeContext) {
        appState.updateCurrentRouteContext(routeContext)
        appState.logger.info(
            logEvent(
                area = LogArea.Navigation,
                eventName = "navigation_changed",
                messageHuman = "Navigation route changed",
                fields = buildMap {
                    put("route", routeContext.routePattern)
                    routeContext.projectId?.let { projectId -> put("projectId", projectId) }
                    routeContext.threadId?.let { threadId -> put("threadId", threadId) }
                },
            ),
        )
    }

    StukayTheme {
        NavHost(
            navController = navController,
            startDestination = StukayDestination.Projects.route,
        ) {
            composable(route = StukayDestination.Projects.route) {
                ProjectsRoute(
                    projects = appState.projects(),
                    hostBridgeState = appState.hostBridgeState,
                    logger = appState.logger,
                    onOpenProject = { projectId ->
                        navController.navigate(ProjectDetailsDestination.route(projectId.value))
                    },
                    onOpenSettings = {
                        navController.navigate(StukayDestination.Settings.route)
                    },
                )
            }

            composable(
                route = ProjectDetailsDestination.routePattern,
                arguments = listOf(
                    navArgument(ProjectDetailsDestination.projectIdArg) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val projectId = ProjectDetailsDestination.decodeProjectId(
                    backStackEntry.arguments?.getString(ProjectDetailsDestination.projectIdArg).orEmpty(),
                )
                ProjectRoute(
                    project = appState.project(projectId),
                    threads = appState.threads(projectId),
                    logger = appState.logger,
                    onNavigateBack = navController::popBackStack,
                    onOpenThread = { threadId ->
                        navController.navigate(ThreadDestination.route(threadId.value))
                    },
                )
            }

            composable(
                route = ThreadDestination.routePattern,
                arguments = listOf(
                    navArgument(ThreadDestination.threadIdArg) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val threadId = ThreadDestination.decodeThreadId(
                    backStackEntry.arguments?.getString(ThreadDestination.threadIdArg).orEmpty(),
                )
                LaunchedEffect(threadId) {
                    appState.openThreadSession(threadId)
                }
                ThreadRoute(
                    thread = appState.thread(threadId),
                    timeline = appState.timeline(threadId),
                    sessionState = appState.threadSessionState(threadId),
                    logger = appState.logger,
                    onComposerChanged = appState::updateComposerDraft,
                    onSend = {
                        appState.sendPrompt(threadId)
                    },
                    onStop = {
                        appState.interruptTurn(threadId)
                    },
                    onResolveApproval = { requestId, decision ->
                        appState.resolveApproval(
                            requestId = requestId,
                            decision = decision,
                        )
                    },
                    onNavigateBack = navController::popBackStack,
                )
            }

            composable(route = StukayDestination.Settings.route) {
                SettingsRoute(
                    logger = appState.logger,
                    hostBridgeState = appState.hostBridgeState,
                    canAttemptHostBridgeConnect = appState.canAttemptHostBridgeConnect(),
                    canDisconnectHostBridge = appState.canDisconnectHostBridge(),
                    shouldOfferNearbyDevicesPermission = appState.shouldOfferNearbyDevicesPermission(),
                    pairingInput = appState.pairingInput,
                    onUpdatePairingInput = appState::updatePairingInput,
                    onSavePairingPayload = appState::savePairingPayload,
                    onConnectHostBridge = appState::connectHostBridge,
                    onReconnectHostBridge = appState::reconnectHostBridge,
                    onDisconnectHostBridge = appState::disconnectHostBridge,
                    onRequestLocalNetworkPermission = {
                        appState.requestLocalNetworkPermission()
                        localNetworkPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                    },
                    onNavigateBack = navController::popBackStack,
                    onOpenDiagnostics = {
                        navController.navigate(StukayDestination.Diagnostics.route)
                    },
                )
            }

            composable(route = StukayDestination.Diagnostics.route) {
                DiagnosticsRoute(
                    logger = appState.logger,
                    currentRouteContext = appState.currentRouteContext,
                    inspectedRouteContext = appState.lastInspectedRouteContext,
                    diagnosticsSummary = appState.diagnosticsSummary(),
                    hostBridgeState = appState.hostBridgeState,
                    onNavigateBack = navController::popBackStack,
                )
            }
        }
    }
}

private fun androidx.navigation.NavBackStackEntry?.toRouteContext(): RouteContext {
    if (this == null) {
        return RouteContext(routePattern = StukayDestination.Projects.route)
    }

    val routePattern = destination.route ?: StukayDestination.Projects.route
    val projectId = arguments?.getString(ProjectDetailsDestination.projectIdArg)?.let(ProjectDetailsDestination::decodeProjectId)
    val threadId = arguments?.getString(ThreadDestination.threadIdArg)?.let(ThreadDestination::decodeThreadId)
    return RouteContext(
        routePattern = routePattern,
        projectId = projectId,
        threadId = threadId,
    )
}
