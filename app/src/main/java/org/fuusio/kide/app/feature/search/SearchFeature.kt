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
package org.fuusio.kide.app.feature.search

import android.content.Context
import org.fuusio.kide.app.feature.search.navigation.SearchNavKey
import org.fuusio.kide.app.feature.search.presentation.SearchIntent
import org.fuusio.kide.app.feature.search.presentation.SearchProcessor
import org.fuusio.kide.app.feature.search.presentation.SearchSideEffect
import org.fuusio.kide.app.feature.search.presentation.SearchViewState
import org.fuusio.kide.app.FuusioApplicationFeature
import org.fuusio.kide.devtools.FlightRecorder
import org.fuusio.kide.devtools.KideDebug
import org.fuusio.kide.feature.KoinFeature
import org.fuusio.kide.navigation.ScreenNavKeyRegistry
import org.koin.dsl.module

object SearchFeature : KoinFeature {

    override fun initialize() {
        ScreenNavKeyRegistry.register(SearchNavKey)
    }

    override fun koinModule(applicationContext: Context) = module {
        includes(FuusioApplicationFeature.koinModule(applicationContext))
        factory {
            // Attach a flight recorder so the Kide agent port (MCP) can inspect and
            // drive this processor in debug builds.
            val recorder = FlightRecorder<SearchIntent, SearchViewState, SearchSideEffect>()
            SearchProcessor(get(), get(), interceptors = listOf(recorder)).also { processor ->
                KideDebug.attach("search", processor, recorder)
            }
        }
    }
}
