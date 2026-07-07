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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.fuusio.kide.app.domain.entity.SavedProjectsState
import org.fuusio.kide.app.domain.adapter.project.ProjectRepository
import org.fuusio.kide.domain.usecase.AbstractUseCaseProcessor

class SavedProjectsProcessor(
    private val repository: ProjectRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : AbstractUseCaseProcessor<SavedProjectsState, SavedProjectsUseCaseIntent>(SavedProjectsState()) {

    init {
        scope.launch {
            repository.getSavedProjects().collectLatest { projects ->
                reduce { it.copy(projects = projects) }
            }
        }
        scope.launch {
            repository.getLabels().collectLatest { labels ->
                reduce { it.copy(labels = labels) }
            }
        }
    }

    override suspend fun map(intent: SavedProjectsUseCaseIntent) {
        when (intent) {
            is LoadSavedProjects -> {
                // Reactive loading via flows in init block
            }
            is SaveProject -> {
                repository.saveProject(intent.project)
            }
            is DeleteProject -> {
                repository.deleteProject(intent.projectId)
            }
            is SetSearchQuery -> {
                reduce { it.copy(searchQuery = intent.query) }
            }
            is SelectLabel -> {
                reduce { it.copy(selectedLabel = intent.label) }
            }
            is AddLabel -> {
                repository.saveLabel(intent.label)
            }
            is RemoveLabel -> {
                repository.deleteLabel(intent.labelName)
            }
        }
    }
}
