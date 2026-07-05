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
package org.fuusio.kide.app.feature.browser.presentation

import org.fuusio.kide.app.domain.entity.Label
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.presentation.ViewState

data class BrowserViewState(
    val projects: List<Project> = emptyList(),
    val labels: List<Label> = emptyList(),
    val selectedLabel: Label? = null,
    val searchQuery: String = "",
    val filteredProjects: List<Project> = emptyList()
) : ViewState
