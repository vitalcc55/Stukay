package dev.vitalcc.stukay.runtime

import android.content.Context
import dev.vitalcc.stukay.feature.projects.data.FakeProjectsRepository
import dev.vitalcc.stukay.feature.projects.data.ProjectsRepository
import dev.vitalcc.stukay.feature.thread.data.FakeThreadRepository
import dev.vitalcc.stukay.feature.thread.data.ThreadRepository
import dev.vitalcc.stukay.runtime.hostbridge.HostBridgeRepository
import dev.vitalcc.stukay.runtime.hostbridge.SharedPreferencesHostBridgePairingStore
import dev.vitalcc.stukay.runtime.hostbridge.StubHostBridgeRepository

data class StukayRuntimeGraph(
    val hostBridgeRepository: HostBridgeRepository,
    val projectsRepository: ProjectsRepository,
    val threadRepository: ThreadRepository,
)

fun createStukayRuntimeGraph(context: Context): StukayRuntimeGraph {
    val hostBridgeRepository = StubHostBridgeRepository(
        pairingStore = SharedPreferencesHostBridgePairingStore(context),
    )
    return StukayRuntimeGraph(
        hostBridgeRepository = hostBridgeRepository,
        projectsRepository = FakeProjectsRepository(),
        threadRepository = FakeThreadRepository(),
    )
}
