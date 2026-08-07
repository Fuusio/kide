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

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.KSerializer
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.ViewState

/**
 * Represents a navigation destination within the application. It acts as a descriptor that links
 * a [PresentationProcessor] with its corresponding UI [screen].
 *
 * @param T The specific type of [PresentationProcessor] that manages the state and logic for this screen.
 */
public interface ScreenNavKey<T : PresentationProcessor<*, *, *>> : NavigationComponent {
    /**
     * A stable, unique identifier for this destination. The key is persisted as part of the
     * saved navigation back stack and used to look the destination up from
     * [ScreenNavKeyRegistry] during state restoration.
     *
     * The value must remain stable across app releases, class or package renames, and code
     * minification (R8/ProGuard). Do not derive it from a class name; use an explicit string
     * literal such as `"home"`.
     */
    public val serialKey: String
    public val screen: @Composable (ScreenContext<T>) -> Unit

    /**
     * Non-null to opt this destination into `ViewState` persistence across process death.
     *
     * When provided, the host lazily serializes the processor's state at the moment the
     * platform snapshots state, and restores it — before first composition — when the
     * destination is recreated after process death. A restored state takes precedence over
     * bootstrap initialization: [setup] is skipped when
     * [PresentationProcessor.wasRestored] is `true`.
     *
     * Mark transient fields of the `@Serializable` state class (loading flags, results,
     * error messages) with `@Transient` so restored snapshots reset them; override
     * [PresentationProcessor.onSaveState] for pruning that annotations cannot express.
     *
     * The default is `null`: no persistence, today's behavior.
     */
    public val stateSerializer: KSerializer<out ViewState>? get() = null
    public val onBack: ((backStack: NavBackStack<NavKey>) -> Unit) get() = { backStack ->
        if (backStack.size > 1) { backStack.removeLastOrNull() }
    }

    /**
     * Creates a new [PresentationProcessor] instance for this destination.
     *
     * This is a factory method by design: it is invoked by the navigation runtime whenever the
     * destination needs a fresh processor (i.e., when no retained instance exists in the
     * destination's ViewModelStore). Implementations must return a **new** instance on every
     * call — typically by resolving it from a dependency injection container — and must never
     * cache or reuse an instance, because a previously used processor may already have been
     * cleared and its coroutine scope cancelled.
     */
    public fun createProcessor(): T

    /**
     * Called once with a newly created [processor], before the first composition of this
     * destination's screen. Override to bootstrap the screen with what it was opened for.
     *
     * There are two ways to do that, and the choice turns on one question: **does the intent
     * need to await anything?**
     *
     * - **No** — the destination already holds the data, as a key carrying an entity or a flag
     *   does. Use [PresentationProcessor.initializeWith]. It applies synchronously before the
     *   first composition, so the screen's first frame is already correct and nothing flashes.
     * - **Yes** — the destination holds only an id, a path, a query. Use
     *   [PresentationProcessor.dispatch]. Dispatching here is legal and ordinary: the intent
     *   loop starts at construction and the queue is unbounded, so the intent is picked up
     *   immediately and goes through `map`, where it may suspend.
     *
     * `initializeWith` cannot do the second job. It calls
     * [PresentationProcessor.reduceInitialIntent], which is synchronous and never reaches `map`,
     * so an intent that needs to load something falls through to the default and is silently
     * ignored.
     *
     * Dispatching means one composition happens before the data arrives, so the screen's empty
     * state has to be honest about the difference between *loading* and *nothing to show* — a
     * screen that says "Nothing here" for both is indistinguishable from a broken one.
     *
     * Both may be used together, but `initializeWith` must come first; it throws once any intent
     * has been dispatched.
     *
     * ```kotlin
     * override fun setup(processor: ItemProcessor) {
     *     processor.dispatch(OpenItem(uid))   // needs a repository read
     * }
     * ```
     *
     * Not called when the processor's state was restored from persistent storage
     * (see [stateSerializer]); a restored state takes precedence over bootstrapping.
     */
    public fun setup(processor: T) {
        // By default, do nothing
    }

    /**
     * Serializes this destination's navigation arguments for back-stack persistence.
     *
     * The returned string is stored alongside [serialKey] when the navigation back stack is
     * saved and is passed to [restoreArgs] during state restoration — including after process
     * death. Return `null` (the default) for destinations that carry no arguments.
     */
    public fun saveArgs(): String? = null

    /**
     * Recreates this destination from [args] previously produced by [saveArgs].
     *
     * Implementations that carry navigation arguments should return a new key instance with the
     * arguments restored. The default implementation returns `this` unchanged.
     */
    public fun restoreArgs(args: String): ScreenNavKey<T> = this
}
