package dev.vitalcc.stukay

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.vitalcc.stukay.core.design.theme.StukayTheme
import dev.vitalcc.stukay.feature.diagnostics.ui.DiagnosticsRoute
import dev.vitalcc.stukay.feature.projects.ui.ProjectRoute
import dev.vitalcc.stukay.feature.projects.ui.ProjectsRoute
import dev.vitalcc.stukay.feature.settings.ui.SettingsRoute
import dev.vitalcc.stukay.feature.thread.ui.ThreadRoute
import dev.vitalcc.stukay.navigation.ProjectDetailsDestination
import dev.vitalcc.stukay.navigation.StukayDestination
import dev.vitalcc.stukay.navigation.ThreadDestination

@Composable
fun StukayApp() {
    val navController = rememberNavController()

    StukayTheme {
        NavHost(
            navController = navController,
            startDestination = StukayDestination.Projects.route,
        ) {
            composable(route = StukayDestination.Projects.route) {
                ProjectsRoute(
                    onOpenProject = { projectId ->
                        navController.navigate(ProjectDetailsDestination.route(projectId))
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
                ProjectRoute(
                    projectId = backStackEntry.arguments?.getString(ProjectDetailsDestination.projectIdArg).orEmpty(),
                    onNavigateBack = navController::popBackStack,
                    onOpenThread = { threadId ->
                        navController.navigate(ThreadDestination.route(threadId))
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
                ThreadRoute(
                    threadId = backStackEntry.arguments?.getString(ThreadDestination.threadIdArg).orEmpty(),
                    onNavigateBack = navController::popBackStack,
                )
            }

            composable(route = StukayDestination.Settings.route) {
                SettingsRoute(
                    onNavigateBack = navController::popBackStack,
                    onOpenDiagnostics = {
                        navController.navigate(StukayDestination.Diagnostics.route)
                    },
                )
            }

            composable(route = StukayDestination.Diagnostics.route) {
                DiagnosticsRoute(onNavigateBack = navController::popBackStack)
            }
        }
    }
}
