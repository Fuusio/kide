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
package org.fuusio.kide.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import org.fuusio.kide.presentation.PresentationProcessor

/**
 * [ScreenContext] provides the necessary components and utilities for a screen Composable to
 * interact with its [PresentationProcessor] and manage navigation.
 *
 * The context is parameterized by the concrete processor type [T], so screens access the
 * processor's `states`, `sideEffects`, and `dispatch` members in a fully type-safe way:
 *
 * ```kotlin
 * @Composable
 * fun FooScreen(ctx: ScreenContext<FooProcessor>) {
 *     val state by ctx.processor.states.collectAsStateWithLifecycle()
 *     val onDispatch = ctx.processor::dispatch
 *     // ...
 * }
 * ```
 *
 * @param T The concrete type of the [PresentationProcessor] driving this screen.
 * @property processor The [PresentationProcessor] responsible for handling business logic and
 * state management.
 * @property backStack The [NavBackStack] containing the current navigation state and keys.
 * @property onBack A callback function to be invoked when a back navigation action is requested.
 * @property callbacks Optional host-provided callbacks (for example, opening a navigation
 * drawer), looked up by name via [callback].
 */
public class ScreenContext<T : PresentationProcessor<*, *, *>>(
    public val processor: T,
    public val backStack: NavBackStack<NavKey>,
    public val onBack: (() -> Unit) = {},
    public val callbacks: Map<String, () -> Unit> = emptyMap(),
) {
    public val scope: CoroutineScope get() = processor.processorScope

    /**
     * Returns the host-provided callback registered under [name], or a no-op if none exists.
     */
    public fun callback(name: String): () -> Unit = callbacks[name] ?: {}

    /**
     * Navigates to a destination represented by the given [navKey].
     *
     * @param navKey The [ScreenNavKey] identifying the destination screen to navigate to.
     */
    public fun navigateTo(navKey: ScreenNavKey<*>): Unit = backStack.pushTo(navKey)

    /**
     * Opens the navigation menu or drawer by invoking the callback registered with the [MENU] key.
     */
    public fun openMenu(): Unit = callback(MENU)()

    public companion object {
        /**
         * Conventional callback name for opening the host's navigation menu or drawer.
         */
        public const val MENU: String = "menu"
    }
}
