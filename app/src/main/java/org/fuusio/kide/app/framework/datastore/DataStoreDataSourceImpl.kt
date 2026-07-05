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
package org.fuusio.kide.app.framework.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.fuusio.kide.app.adapter.settings.SettingsDataSource
import org.fuusio.kide.app.domain.entity.AppSettings
import org.fuusio.kide.app.domain.entity.DarkMode
import org.fuusio.kide.framework.AbstractDataSource

class DataStoreDataSourceImpl(private val preferencesDataStore: PreferencesDataStore) :
    AbstractDataSource(), SettingsDataSource {

    override fun getSettings(): Flow<AppSettings> = preferencesDataStore.appSettingsFlow

    override suspend fun updateDarkMode(darkMode: DarkMode) = withContext(dispatcher) {
        preferencesDataStore.updateDarkMode(darkMode)
    }

    override suspend fun updateDefaultLanguage(language: String) = withContext(dispatcher) {
        preferencesDataStore.updateDefaultLanguage(language)
    }

    override suspend fun updateResultsLimit(limit: Int) = withContext(dispatcher) {
        preferencesDataStore.updateResultsLimit(limit)
    }
}
