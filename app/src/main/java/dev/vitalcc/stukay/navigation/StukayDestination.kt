package dev.vitalcc.stukay.navigation

sealed interface StukayDestination {
    val route: String

    data object Projects : StukayDestination {
        override val route: String = "projects"
    }

    data object Settings : StukayDestination {
        override val route: String = "settings"
    }

    data object Diagnostics : StukayDestination {
        override val route: String = "diagnostics"
    }
}

data object ProjectDetailsDestination {
    const val projectIdArg: String = "projectId"
    const val routePattern: String = "project/{$projectIdArg}"

    fun route(projectId: String): String = "project/$projectId"
}

data object ThreadDestination {
    const val threadIdArg: String = "threadId"
    const val routePattern: String = "thread/{$threadIdArg}"

    fun route(threadId: String): String = "thread/$threadId"
}
