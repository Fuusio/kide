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

import kotlinx.coroutines.CoroutineScope
import org.fuusio.kide.app.domain.usecase.SearchGitHubProjectsUseCase
import org.fuusio.kide.app.domain.usecase.SavedProjectsProcessor
import org.fuusio.kide.app.domain.usecase.SaveProject
import org.fuusio.kide.app.domain.usecase.DeleteProject
import org.fuusio.kide.presentation.*

class SearchProcessor(
    private val searchUseCase: SearchGitHubProjectsUseCase,
    private val savedProjectsUseCase: SavedProjectsProcessor,
    processorScope: CoroutineScope = defaultProcessorScope(),
    interceptors: List<KideInterceptor<SearchIntent, SearchViewState, SearchSideEffect>> = emptyList(),
) : PresentationProcessor<SearchIntent, SearchViewState, SearchSideEffect>(
    SearchViewState(),
    processorScope,
    interceptors,
) {

    override suspend fun map(intent: SearchIntent): Action<SearchViewState, SearchSideEffect>? =
        when (intent) {
            is UpdateQuery -> reduce { copy(query = intent.query) }
            is UpdateLanguageFilter -> reduce { copy(languageFilter = intent.language) }
            is UpdateLicenseFilter -> reduce { copy(licenseFilter = intent.licenseSpdxId) }
            is TriggerSearch -> {
                if (state.query.isBlank()) {
                    sideEffect { ShowToast("Search query cannot be empty") }
                } else {
                    composite<SearchViewState, SearchSideEffect>(
                        reduce { copy(isLoading = true, errorMessage = null) },
                        async(cancellationKey = "search_repos_job") {
                            val result = searchUseCase.execute(
                                query = state.query,
                                language = state.languageFilter,
                                license = state.licenseFilter
                            )
                            reduce {
                                result.fold(
                                    onSuccess = { list ->
                                        copy(results = list, isLoading = false)
                                    },
                                    onFailure = { error ->
                                        copy(
                                            errorMessage = error.message ?: "Failed to perform search",
                                            isLoading = false,
                                            results = emptyList()
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }
            is ToggleSave -> {
                val updatedProject = intent.project.copy(isSaved = !intent.project.isSaved)
                composite<SearchViewState, SearchSideEffect>(
                    useCase {
                        if (updatedProject.isSaved) {
                            savedProjectsUseCase.dispatch(SaveProject(updatedProject))
                        } else {
                            savedProjectsUseCase.dispatch(DeleteProject(updatedProject.id))
                        }
                        reduce {
                            copy(
                                results = results.map { p ->
                                    if (p.id == intent.project.id) updatedProject else p
                                }
                            )
                        }
                    },
                    sideEffect {
                        ShowToast(
                            if (updatedProject.isSaved) "Saved to local database"
                            else "Removed from local database"
                        )
                    }
                )
            }
        }
}
