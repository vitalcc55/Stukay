package dev.vitalcc.stukay

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
fun StukayApp() {
    val navController = rememberNavController()
    val appState = androidx.compose.runtime.remember { StukayAppState() }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: StukayDestination.Projects.route

    LaunchedEffect(Unit) {
        appState.logger.info(
            logEvent(
                area = LogArea.App,
                eventName = "app_started",
                messageHuman = "Stukay app shell started",
            ),
        )
    }

    LaunchedEffect(currentRoute) {
        appState.updateCurrentScreenRoute(currentRoute)
        appState.logger.info(
            logEvent(
                area = LogArea.Navigation,
                eventName = "navigation_changed",
                messageHuman = "Navigation route changed",
                fields = mapOf("route" to currentRoute),
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
                val projectId = backStackEntry.arguments?.getString(ProjectDetailsDestination.projectIdArg).orEmpty()
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
                val threadId = backStackEntry.arguments?.getString(ThreadDestination.threadIdArg).orEmpty()
                ThreadRoute(
                    thread = appState.thread(threadId),
                    timeline = appState.timeline(threadId),
                    logger = appState.logger,
                    onStartFakeTurn = {
                        appState.startFakeTurn(threadId)
                    },
                    onCompleteFakeTurn = {
                        appState.completeFakeTurn(threadId)
                    },
                    onResolveApproval = { approvalId, decision ->
                        appState.resolveApproval(
                            threadId = threadId,
                            approvalId = approvalId.value,
                            decision = decision,
                        )
                    },
                    onNavigateBack = navController::popBackStack,
                )
            }

            composable(route = StukayDestination.Settings.route) {
                SettingsRoute(
                    logger = appState.logger,
                    onNavigateBack = navController::popBackStack,
                    onOpenDiagnostics = {
                        navController.navigate(StukayDestination.Diagnostics.route)
                    },
                )
            }

            composable(route = StukayDestination.Diagnostics.route) {
                DiagnosticsRoute(
                    logger = appState.logger,
                    currentScreenRoute = appState.currentScreenRoute,
                    diagnosticsSummary = appState.diagnosticsSummary(),
                    onNavigateBack = navController::popBackStack,
                )
            }
        }
    }
}
