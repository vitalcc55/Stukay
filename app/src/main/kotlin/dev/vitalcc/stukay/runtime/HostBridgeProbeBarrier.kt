package dev.vitalcc.stukay.runtime

internal data class HostBridgeProbeTicket(
    val generation: Long,
    val token: Long,
)

internal class HostBridgeProbeBarrier {
    @Volatile
    private var state: ProbeBarrierState = ProbeBarrierState(
        enabledGeneration = null,
        token = 0L,
    )

    fun enableForGeneration(generation: Long) {
        val current = state
        if (current.enabledGeneration == generation) {
            return
        }
        state = ProbeBarrierState(
            enabledGeneration = generation,
            token = current.token + 1,
        )
    }

    fun disable() {
        val current = state
        if (current.enabledGeneration == null) {
            return
        }
        state = ProbeBarrierState(
            enabledGeneration = null,
            token = current.token + 1,
        )
    }

    fun isEnabledForGeneration(generation: Long): Boolean = state.enabledGeneration == generation

    fun capture(generation: Long): HostBridgeProbeTicket? {
        val snapshot = state
        if (snapshot.enabledGeneration != generation) {
            return null
        }
        return HostBridgeProbeTicket(
            generation = generation,
            token = snapshot.token,
        )
    }

    fun allows(ticket: HostBridgeProbeTicket, generation: Long): Boolean {
        val snapshot = state
        return snapshot.enabledGeneration == generation &&
            ticket.generation == generation &&
            ticket.token == snapshot.token
    }
}

private data class ProbeBarrierState(
    val enabledGeneration: Long?,
    val token: Long,
)
