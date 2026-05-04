package dev.vitalcc.stukay.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBridgeProbeBarrierTest {
    @Test
    fun disableInvalidatesPreviouslyCapturedProbeTicket() {
        val barrier = HostBridgeProbeBarrier()

        barrier.enableForGeneration(7L)
        val ticket = barrier.capture(generation = 7L)

        assertNotNull(ticket)
        barrier.disable()

        assertFalse(barrier.allows(ticket!!, generation = 7L))
    }

    @Test
    fun newGenerationInvalidatesOldProbeTicketAndAllowsNewOne() {
        val barrier = HostBridgeProbeBarrier()

        barrier.enableForGeneration(3L)
        val oldTicket = barrier.capture(generation = 3L)
        barrier.enableForGeneration(4L)
        val newTicket = barrier.capture(generation = 4L)

        assertNotNull(oldTicket)
        assertNotNull(newTicket)
        assertFalse(barrier.allows(oldTicket!!, generation = 4L))
        assertTrue(barrier.allows(newTicket!!, generation = 4L))
    }
}
