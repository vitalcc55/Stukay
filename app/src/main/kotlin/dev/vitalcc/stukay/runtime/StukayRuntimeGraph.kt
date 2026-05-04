package dev.vitalcc.stukay.runtime

import android.content.Context
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeRepository
import dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepository
import dev.vitalcc.stukay.runtime.hostbridge.OkHttpHostBridgeClient
import dev.vitalcc.stukay.runtime.hostbridge.SharedPreferencesHostBridgePairingStore

data class StukayRuntimeGraph(
    val hostBridgeRepository: HostBridgeRepository,
    val projectsRepository: RuntimeProjectsRepository,
    val threadRepository: RuntimeThreadRepository,
)

fun createStukayRuntimeGraph(
    context: Context,
    localNetworkPermissionGranted: Boolean,
): StukayRuntimeGraph {
    val client = OkHttpHostBridgeClient()
    val pairingStore = SharedPreferencesHostBridgePairingStore(context)
    val hostBridgeRepository = HttpJsonHostBridgeRepository(
        pairingStore = pairingStore,
        client = client,
        initialNearbyWifiDevicesGranted = localNetworkPermissionGranted,
    )
    val store = RuntimeThreadStore()
    return StukayRuntimeGraph(
        hostBridgeRepository = hostBridgeRepository,
        projectsRepository = RuntimeProjectsRepository(store),
        threadRepository = RuntimeThreadRepository(
            hostBridgeRepository = hostBridgeRepository,
            store = store,
        ),
    )
}
