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
package org.fuusio.kide.app.feature.search.presentation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.app.domain.usecase.SavedProjectsUseCaseLogic
import org.fuusio.kide.app.domain.usecase.SearchGitHubProjectsUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchProcessorTest {

    private val searchUseCase: SearchGitHubProjectsUseCase = mockk()
    private val savedProjectsUseCase: SavedProjectsUseCaseLogic = mockk()

    @Test
    fun testSearchCancellationWhenMultipleIntentsDispatched() = runTest {
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
                isSaved = false,
                savedAt = null
            )
        )

        coEvery { searchUseCase.execute("kide", any(), any()) } coAnswers {
            delay(1000)
            Result.success(projectList)
        }

        val processor = SearchProcessor(
            searchUseCase = searchUseCase,
            savedProjectsUseCase = savedProjectsUseCase,
            processorScope = testScope
        )

        processor.dispatch(UpdateQuery("kide"))
        processor.dispatch(TriggerSearch)

        testScheduler.advanceTimeBy(500)
        assertTrue(processor.state.isLoading)

        coEvery { searchUseCase.execute("kide-new", any(), any()) } coAnswers {
            delay(500)
            Result.success(emptyList())
        }

        processor.dispatch(UpdateQuery("kide-new"))
        processor.dispatch(TriggerSearch)

        testScheduler.advanceTimeBy(1000)

        assertFalse(processor.state.isLoading)
        assertEquals(emptyList<Project>(), processor.state.results)
    }
}
