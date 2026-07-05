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
package org.fuusio.kide.app.feature.search.navigation

import androidx.compose.runtime.Composable
import kotlinx.serialization.KSerializer
import org.fuusio.kide.app.feature.search.presentation.SearchProcessor
import org.fuusio.kide.app.feature.search.presentation.SearchViewState
import org.fuusio.kide.app.feature.search.ui.SearchScreen
import org.fuusio.kide.navigation.ScreenContext
import org.fuusio.kide.navigation.ScreenNavKey
import org.fuusio.kide.di.get
import org.fuusio.kide.presentation.ViewState

object SearchNavKey : ScreenNavKey<SearchProcessor> {
    override val serialKey: String = "search"
    override fun createProcessor(): SearchProcessor = get()
    override val screen: @Composable ((ScreenContext<SearchProcessor>) -> Unit)
        get() = { ctx -> SearchScreen(ctx) }

    /** Persist the user's search inputs across process death. */
    override val stateSerializer: KSerializer<out ViewState>
        get() = SearchViewState.serializer()
}
