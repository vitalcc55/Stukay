package dev.vitalcc.stukay.runtime

internal enum class HostBridgeImmediateProbeNextAction {
    Idle,
    Rerun,
    Disabled,
}

internal class HostBridgeImmediateProbeCoordinator {
    private var enabledGeneration: Long? = null
    private var submittedOrRunning: Boolean = false
    private var rerunRequested: Boolean = false

    @Synchronized
    fun enableForGeneration(generation: Long) {
        if (enabledGeneration == generation) {
            return
        }
        enabledGeneration = generation
        submittedOrRunning = false
        rerunRequested = false
    }

    @Synchronized
    fun disable() {
        enabledGeneration = null
        submittedOrRunning = false
        rerunRequested = false
    }

    @Synchronized
    fun isEnabledForGeneration(generation: Long): Boolean = enabledGeneration == generation

    @Synchronized
    fun request(generation: Long): Boolean {
        if (enabledGeneration != generation) {
            return false
        }
        if (submittedOrRunning) {
            rerunRequested = true
            return false
        }
        submittedOrRunning = true
        rerunRequested = false
        return true
    }

    @Synchronized
    fun canRun(generation: Long): Boolean = enabledGeneration == generation && submittedOrRunning

    @Synchronized
    fun finishRun(generation: Long): HostBridgeImmediateProbeNextAction {
        if (enabledGeneration != generation) {
            submittedOrRunning = false
            rerunRequested = false
            return HostBridgeImmediateProbeNextAction.Disabled
        }
        if (rerunRequested) {
            rerunRequested = false
            return HostBridgeImmediateProbeNextAction.Rerun
        }
        submittedOrRunning = false
        return HostBridgeImmediateProbeNextAction.Idle
    }
}
