package dev.vitalcc.stukay.feature.projects.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProjectsRepositoryTest {
    @Test
    fun loadProjectsReturnsDeterministicSeedData() {
        val repository = FakeProjectsRepository()

        val projects = repository.loadProjects()

        assertEquals(2, projects.size)
        assertEquals("main", projects.first().id.value)
        assertTrue(projects.any { it.id.value == "diagnostics" })
    }
}
