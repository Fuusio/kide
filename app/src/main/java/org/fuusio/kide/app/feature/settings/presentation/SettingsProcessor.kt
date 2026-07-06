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
package org.fuusio.kide.app.feature.settings.presentation

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.fuusio.kide.app.domain.usecase.GetSettingsUseCase
import org.fuusio.kide.app.domain.usecase.UpdateDarkModeUseCase
import org.fuusio.kide.app.domain.usecase.UpdateDefaultLanguageUseCase
import org.fuusio.kide.app.domain.usecase.UpdateResultsLimitUseCase
import org.fuusio.kide.presentation.*

class SettingsProcessor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateDarkModeUseCase: UpdateDarkModeUseCase,
    private val updateDefaultLanguageUseCase: UpdateDefaultLanguageUseCase,
    private val updateResultsLimitUseCase: UpdateResultsLimitUseCase,
) : PresentationProcessor<SettingsIntent, SettingsViewState, SettingsSideEffect>(
    SettingsViewState(),
) {

    init {
        processorScope.launch {
            getSettingsUseCase.execute().collectLatest { settings ->
                dispatch(SettingsLoaded(settings))
            }
        }
    }

    override suspend fun map(intent: SettingsIntent): Action<SettingsViewState, SettingsSideEffect>? =
        when (intent) {
            is LoadSettings -> null
            is SettingsLoaded -> reduce {
                copy(
                    darkMode = intent.settings.darkMode,
                    defaultLanguage = intent.settings.defaultLanguage,
                    resultsLimit = intent.settings.resultsLimit
                )
            }
            is UpdateDarkMode -> useCase {
                updateDarkModeUseCase.execute(intent.darkMode)
            }
            is UpdateDefaultLanguage -> useCase {
                updateDefaultLanguageUseCase.execute(intent.language)
            }
            is UpdateResultsLimit -> useCase {
                updateResultsLimitUseCase.execute(intent.limit)
            }
        }
}
