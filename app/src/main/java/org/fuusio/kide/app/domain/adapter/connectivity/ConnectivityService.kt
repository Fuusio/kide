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
package org.fuusio.kide.app.domain.adapter.connectivity

import androidx.compose.runtime.State
import org.fuusio.kide.adapter.Service

/**
 * Whether the device currently has an internet connection.
 *
 * Exposed as Compose [State] rather than a `Flow`: it is *live* state — always has a current
 * value, is never persisted, and is read by whoever needs it at the moment they need it.
 * A processor takes it in through `kide-appstate` (see `SearchProcessor`); a composable can
 * read it directly.
 */
interface ConnectivityService : Service {
    val isOnline: State<Boolean>
}
