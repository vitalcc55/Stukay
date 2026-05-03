package dev.vitalcc.stukay.runtime

import android.content.Context
import dev.vitalcc.stukay.feature.projects.data.FakeProjectsRepository
import dev.vitalcc.stukay.feature.projects.data.ProjectsRepository
import dev.vitalcc.stukay.feature.thread.data.FakeThreadRepository
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeRepository
import dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepository
import dev.vitalcc.stukay.runtime.hostbridge.OkHttpHostBridgeClient
import dev.vitalcc.stukay.runtime.hostbridge.SharedPreferencesHostBridgePairingStore

data class StukayRuntimeGraph(
    val hostBridgeRepository: HostBridgeRepository,
    val projectsRepository: ProjectsRepository,
    val threadRepository: ThreadRepository,
)

fun createStukayRuntimeGraph(
    context: Context,
    localNetworkPermissionGranted: Boolean,
): StukayRuntimeGraph {
    val hostBridgeRepository = HttpJsonHostBridgeRepository(
        pairingStore = SharedPreferencesHostBridgePairingStore(context),
        client = OkHttpHostBridgeClient(),
        initialNearbyWifiDevicesGranted = localNetworkPermissionGranted,
    )
    val fakeProjectsRepository = FakeProjectsRepository()
    val fakeThreadRepository = FakeThreadRepository()
    return StukayRuntimeGraph(
        hostBridgeRepository = hostBridgeRepository,
        projectsRepository = RuntimeProjectsRepository(fakeProjectsRepository),
        threadRepository = RuntimeThreadRepository(fakeThreadRepository),
    )
}
