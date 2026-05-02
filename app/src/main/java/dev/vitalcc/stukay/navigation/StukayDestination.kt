package dev.vitalcc.stukay.navigation

import dev.vitalcc.stukay.core.model.RouteContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    fun route(projectId: String): String = "project/${encodeRouteArg(projectId)}"

    fun decodeProjectId(encodedProjectId: String): String = decodeRouteArg(encodedProjectId)
}

data object ThreadDestination {
    const val threadIdArg: String = "threadId"
    const val routePattern: String = "thread/{$threadIdArg}"

    fun route(threadId: String): String = "thread/${encodeRouteArg(threadId)}"

    fun decodeThreadId(encodedThreadId: String): String = decodeRouteArg(encodedThreadId)
}

private fun encodeRouteArg(raw: String): String = URLEncoder.encode(
    raw,
    StandardCharsets.UTF_8,
).replace("+", "%20")

private fun decodeRouteArg(encoded: String): String = URLDecoder.decode(
    encoded,
    StandardCharsets.UTF_8,
)
