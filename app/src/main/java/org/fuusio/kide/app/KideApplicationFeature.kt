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
package org.fuusio.kide.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import org.fuusio.kide.app.adapter.app_info.AppInfoDataSource
import org.fuusio.kide.app.adapter.app_info.AppInfoRepositoryImpl
import org.fuusio.kide.app.domain.adapter.app_info.AppInfoRepository
import org.fuusio.kide.app.domain.usecase.GetAppVersionUseCase
import org.fuusio.kide.app.framework.app_info.AppInfoDataSourceImpl
import org.fuusio.kide.feature.KoinApplicationFeature
import org.koin.core.module.Module
import org.koin.dsl.module

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fuusio")

object FuusioApplicationFeature : KoinApplicationFeature {

    override fun koinModule(applicationContext: Context): Module = module {

        // Framework

        single<DataStore<Preferences>> { applicationContext.dataStore }
        single<AppInfoDataSource> { AppInfoDataSourceImpl() }
        single { org.fuusio.kide.app.framework.db.AppDatabase.getDatabase(applicationContext) }
        single { get<org.fuusio.kide.app.framework.db.AppDatabase>().projectDao() }
        single { org.fuusio.kide.app.framework.datastore.PreferencesDataStore(applicationContext) }

        // Data Sources

        single<org.fuusio.kide.app.adapter.project.ProjectLocalDataSource> {
            org.fuusio.kide.app.framework.db.RoomDataSourceImpl(get())
        }
        single<org.fuusio.kide.app.adapter.project.ProjectRemoteDataSource> {
            org.fuusio.kide.app.framework.network.GitHubApiDataSourceImpl()
        }
        single<org.fuusio.kide.app.adapter.settings.SettingsDataSource> {
            org.fuusio.kide.app.framework.datastore.DataStoreDataSourceImpl(get())
        }

        // Adapters

        single<AppInfoRepository> { AppInfoRepositoryImpl(get()) }
        single<org.fuusio.kide.app.domain.adapter.project.ProjectRepository> {
            org.fuusio.kide.app.adapter.project.ProjectRepositoryImpl(get(), get())
        }
        single<org.fuusio.kide.app.domain.adapter.settings.SettingsRepository> {
            org.fuusio.kide.app.adapter.settings.SettingsRepositoryImpl(get())
        }

        // Use Cases

        factory<GetAppVersionUseCase> {
            GetAppVersionUseCase { get<AppInfoRepository>().getVersion() }
        }
        single { org.fuusio.kide.app.domain.usecase.SavedProjectsProcessor(get()) }
        factory<org.fuusio.kide.app.domain.usecase.SearchGitHubProjectsUseCase> {
            val repository = get<org.fuusio.kide.app.domain.adapter.project.ProjectRepository>()
            org.fuusio.kide.app.domain.usecase.SearchGitHubProjectsUseCase { query, language, license ->
                try {
                    Result.success(repository.searchProjects(query, language, license))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        factory<org.fuusio.kide.app.domain.usecase.UpdateDarkModeUseCase> {
            val repository = get<org.fuusio.kide.app.domain.adapter.settings.SettingsRepository>()
            org.fuusio.kide.app.domain.usecase.UpdateDarkModeUseCase { darkMode ->
                repository.updateDarkMode(darkMode)
            }
        }
        factory<org.fuusio.kide.app.domain.usecase.UpdateDefaultLanguageUseCase> {
            val repository = get<org.fuusio.kide.app.domain.adapter.settings.SettingsRepository>()
            org.fuusio.kide.app.domain.usecase.UpdateDefaultLanguageUseCase { language ->
                repository.updateDefaultLanguage(language)
            }
        }
        factory<org.fuusio.kide.app.domain.usecase.UpdateResultsLimitUseCase> {
            val repository = get<org.fuusio.kide.app.domain.adapter.settings.SettingsRepository>()
            org.fuusio.kide.app.domain.usecase.UpdateResultsLimitUseCase { limit ->
                repository.updateResultsLimit(limit)
            }
        }
    }


    override fun initialize() {
        // TODO
    }
}