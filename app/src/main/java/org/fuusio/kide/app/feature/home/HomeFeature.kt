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
package org.fuusio.kide.app.feature.home

import android.content.Context
import org.fuusio.kide.app.feature.home.navigation.HomeNavKey
import org.fuusio.kide.feature.KoinFeature
import org.fuusio.kide.app.feature.home.presentation.HomeProcessor
import org.fuusio.kide.app.feature.home.presentation.HomeViewState
import org.fuusio.kide.navigation.ScreenNavKeyRegistry
import org.koin.dsl.module

object HomeFeature : KoinFeature {

    override fun initialize() {
        ScreenNavKeyRegistry.register(HomeNavKey)
    }

    override fun koinModule(applicationContext: Context) = module {

        factory<HomeViewState> { HomeViewState() }

        factory { HomeProcessor(get()) }
    }
}