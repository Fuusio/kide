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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.fuusio.kide.app.domain.entity.AppSettings
import org.fuusio.kide.app.domain.entity.DarkMode
import java.io.IOException
import timber.log.Timber

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesDataStore(private val context: Context) {

    private object PreferencesKeys {
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val RESULTS_LIMIT = intPreferencesKey("results_limit")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading datastore preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val darkModeStr = preferences[PreferencesKeys.DARK_MODE] ?: DarkMode.SYSTEM.name
            val darkMode = try {
                DarkMode.valueOf(darkModeStr)
            } catch (e: Exception) {
                DarkMode.SYSTEM
            }
            val defaultLanguage = preferences[PreferencesKeys.DEFAULT_LANGUAGE] ?: "Kotlin"
            val resultsLimit = preferences[PreferencesKeys.RESULTS_LIMIT] ?: 30

            AppSettings(
                darkMode = darkMode,
                defaultLanguage = defaultLanguage,
                resultsLimit = resultsLimit
            )
        }

    suspend fun updateDarkMode(darkMode: DarkMode) {
        Timber.d("DataStore: updating dark mode setting to: %s", darkMode.name)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = darkMode.name
        }
    }

    suspend fun updateDefaultLanguage(language: String) {
        Timber.d("DataStore: updating default language setting to: %s", language)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_LANGUAGE] = language
        }
    }

    suspend fun updateResultsLimit(limit: Int) {
        Timber.d("DataStore: updating results limit setting to: %d", limit)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESULTS_LIMIT] = limit
        }
    }
}
