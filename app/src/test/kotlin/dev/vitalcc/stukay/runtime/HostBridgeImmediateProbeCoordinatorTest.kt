package dev.vitalcc.stukay.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBridgeImmediateProbeCoordinatorTest {
    @Test
    fun duplicateRequestsCoalesceIntoSingleRerun() {
        val coordinator = HostBridgeImmediateProbeCoordinator()
        coordinator.enableForGeneration(11L)

        assertTrue(coordinator.request(11L))
        assertFalse(coordinator.request(11L))
        assertFalse(coordinator.request(11L))
        assertTrue(coordinator.canRun(11L))
        assertEquals(HostBridgeImmediateProbeNextAction.Rerun, coordinator.finishRun(11L))
        assertTrue(coordinator.canRun(11L))
        assertEquals(HostBridgeImmediateProbeNextAction.Idle, coordinator.finishRun(11L))
        assertFalse(coordinator.canRun(11L))
    }

    @Test
    fun disableClearsQueuedOrRunningImmediateProbe() {
        val coordinator = HostBridgeImmediateProbeCoordinator()
        coordinator.enableForGeneration(3L)
        assertTrue(coordinator.request(3L))

        coordinator.disable()

        assertFalse(coordinator.canRun(3L))
        assertEquals(HostBridgeImmediateProbeNextAction.Disabled, coordinator.finishRun(3L))
    }

    @Test
    fun newGenerationResetsOldQueuedProbeState() {
        val coordinator = HostBridgeImmediateProbeCoordinator()
        coordinator.enableForGeneration(5L)
        assertTrue(coordinator.request(5L))

        coordinator.enableForGeneration(6L)

        assertFalse(coordinator.canRun(5L))
        assertFalse(coordinator.request(5L))
        assertTrue(coordinator.request(6L))
    }
}
