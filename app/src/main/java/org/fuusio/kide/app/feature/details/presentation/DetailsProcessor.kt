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
package org.fuusio.kide.app.feature.details.presentation

import org.fuusio.kide.app.domain.adapter.project.ProjectRepository
import org.fuusio.kide.presentation.*

class DetailsProcessor(
    private val projectRepository: ProjectRepository
) : PresentationProcessor<DetailsIntent, DetailsViewState, DetailsSideEffect>(DetailsViewState()) {

    override suspend fun map(intent: DetailsIntent): Action<DetailsViewState, DetailsSideEffect>? =
        when (intent) {
            is LoadProjectDetails -> composite<DetailsViewState, DetailsSideEffect>(
                reduce { copy(isLoading = true, projectId = intent.projectId) },
                async {
                    val project = projectRepository.getProjectDetails(intent.projectId)
                    reduce {
                        copy(
                            project = project,
                            isLoading = false,
                            noteInput = project?.notes ?: ""
                        )
                    }
                }
            )
            is UpdateNotesText -> reduce { copy(noteInput = intent.notes) }
            is SaveNotes -> composite<DetailsViewState, DetailsSideEffect>(
                useCase {
                    projectRepository.updateProjectNotes(state.projectId, state.noteInput)
                    val updatedProject = projectRepository.getProjectDetails(state.projectId)
                    reduce { copy(project = updatedProject) }
                },
                sideEffect { ShowDetailsToast("Notes saved") }
            )
            is AddLabelToProject -> useCase {
                projectRepository.addLabelToProject(state.projectId, intent.labelName)
                val updatedProject = projectRepository.getProjectDetails(state.projectId)
                reduce { copy(project = updatedProject) }
            }
            is RemoveLabelFromProject -> useCase {
                projectRepository.removeLabelFromProject(state.projectId, intent.labelName)
                val updatedProject = projectRepository.getProjectDetails(state.projectId)
                reduce { copy(project = updatedProject) }
            }
        }
}
