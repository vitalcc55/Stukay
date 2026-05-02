package dev.vitalcc.stukay.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StukayDestinationTest {
    @Test
    fun projectRouteEncodesReservedCharactersAndRoundTrips() {
        val rawId = "main workspace/thread#1?x=1"

        val route = ProjectDetailsDestination.route(rawId)
        val decoded = ProjectDetailsDestination.decodeProjectId(
            route.substringAfter("project/"),
        )

        assertTrue(route.startsWith("project/"))
        assertTrue(route != "project/$rawId")
        assertEquals(rawId, decoded)
    }

    @Test
    fun threadRouteEncodesReservedCharactersAndRoundTrips() {
        val rawId = "thread/main?approval=1"

        val route = ThreadDestination.route(rawId)
        val decoded = ThreadDestination.decodeThreadId(
            route.substringAfter("thread/"),
        )

        assertTrue(route.startsWith("thread/"))
        assertTrue(route != "thread/$rawId")
        assertEquals(rawId, decoded)
    }
}
