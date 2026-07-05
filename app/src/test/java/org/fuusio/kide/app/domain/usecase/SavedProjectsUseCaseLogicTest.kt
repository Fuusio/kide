/* Kide
 *
 * Copyright 2025 - 2026 Marko Salmela.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fuusio.kide.app.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fuusio.kide.app.domain.adapter.project.ProjectRepository
import org.fuusio.kide.app.domain.entity.Label
import org.fuusio.kide.app.domain.entity.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedProjectsUseCaseLogicTest {

    private val repository: ProjectRepository = mockk()

    @Test
    fun testSavedProjectsUseCaseLogicFlowCollectionAndIntents() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val projectList = listOf(
            Project(
                id = 1L,
                name = "Kide",
                fullName = "Fuusio/Kide",
                description = "MVI framework",
                htmlUrl = "https://github.com/Fuusio/Kide",
                language = "Kotlin",
                starsCount = 100,
                forksCount = 10,
                licenseSpdxId = "Apache-2.0",
                notes = null,
                labels = emptyList(),
                isSaved = true,
                savedAt = 12345L
            )
        )
        val labelList = listOf(Label("Architecture", "#FF0000"))

        val projectsFlow = MutableStateFlow(projectList)
        val labelsFlow = MutableStateFlow(labelList)

        every { repository.getSavedProjects() } returns projectsFlow
        every { repository.getLabels() } returns labelsFlow
        coEvery { repository.saveProject(any()) } returns Unit
        coEvery { repository.deleteProject(any()) } returns Unit

        val useCaseLogic = SavedProjectsUseCaseLogic(
            repository = repository,
            scope = testScope
        )

        assertEquals(projectList, useCaseLogic.state.projects)
        assertEquals(labelList, useCaseLogic.state.labels)

        val newProject = projectList[0].copy(id = 2L, name = "NewProject")
        useCaseLogic.onIntent(SaveProject(newProject))
        coVerify(exactly = 1) { repository.saveProject(newProject) }

        val selected = Label("Architecture", "#FF0000")
        useCaseLogic.onIntent(SelectLabel(selected))
        assertEquals(selected, useCaseLogic.state.selectedLabel)

        useCaseLogic.onIntent(SetSearchQuery("search-term"))
        assertEquals("search-term", useCaseLogic.state.searchQuery)
    }
}
