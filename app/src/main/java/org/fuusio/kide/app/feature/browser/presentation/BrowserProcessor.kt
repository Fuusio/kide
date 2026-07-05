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
package org.fuusio.kide.app.feature.browser.presentation

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.fuusio.kide.app.domain.entity.Label
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.app.domain.usecase.*
import org.fuusio.kide.presentation.*

class BrowserProcessor(
    private val savedProjectsUseCase: SavedProjectsUseCaseLogic
) : PresentationProcessor<BrowserIntent, BrowserViewState, BrowserSideEffect>(BrowserViewState()) {

    init {
        processorScope.launch {
            savedProjectsUseCase.stateFlow.collectLatest { domainState ->
                dispatch(DomainStateUpdated(domainState))
            }
        }
    }

    private fun filterProjects(
        projects: List<Project>,
        query: String,
        label: Label?
    ): List<Project> {
        return projects.filter { project ->
            val matchesQuery = query.isBlank() ||
                    project.name.contains(query, ignoreCase = true) ||
                    project.fullName.contains(query, ignoreCase = true) ||
                    (project.description != null && project.description.contains(query, ignoreCase = true))

            val matchesLabel = label == null || project.labels.any { it.name == label.name }

            matchesQuery && matchesLabel
        }
    }

    override suspend fun map(intent: BrowserIntent): Action<BrowserViewState, BrowserSideEffect>? =
        when (intent) {
            is LoadSaved -> null
            is DomainStateUpdated -> reduce {
                val filtered = filterProjects(
                    intent.domainState.projects,
                    intent.domainState.searchQuery,
                    intent.domainState.selectedLabel
                )
                copy(
                    projects = intent.domainState.projects,
                    labels = intent.domainState.labels,
                    selectedLabel = intent.domainState.selectedLabel,
                    searchQuery = intent.domainState.searchQuery,
                    filteredProjects = filtered
                )
            }
            is UpdateLocalSearchQuery -> useCase {
                savedProjectsUseCase.onIntent(SetSearchQuery(intent.query))
            }
            is SelectLocalLabel -> useCase {
                savedProjectsUseCase.onIntent(SelectLabel(intent.label))
            }
            is RemoveLocalProject -> composite<BrowserViewState, BrowserSideEffect>(
                useCase {
                    savedProjectsUseCase.onIntent(DeleteProject(intent.project.id))
                },
                sideEffect { ShowBrowserToast("Removed ${intent.project.name} from saved libraries") }
            )
        }
}
