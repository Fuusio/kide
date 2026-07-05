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

import kotlinx.serialization.Serializable
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.presentation.ViewIntent

sealed interface SearchIntent : ViewIntent

// The simple intents are @Serializable so that the Kide agent port (kide-devtools MCP
// server) can inject them into the running app via the kide_dispatch_intent tool.
@Serializable
data class UpdateQuery(val query: String) : SearchIntent

@Serializable
data class UpdateLanguageFilter(val language: String?) : SearchIntent

@Serializable
data class UpdateLicenseFilter(val licenseSpdxId: String?) : SearchIntent

@Serializable
object TriggerSearch : SearchIntent

data class ToggleSave(val project: Project) : SearchIntent
