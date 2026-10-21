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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect

/**
 * Collects the [PresentationProcessor.sideEffects] of [processor] while the current
 * lifecycle is at least [minActiveState], handing each effect to [onEffect].
 *
 * This is the recommended way to consume side effects from a screen. A plain
 * `LaunchedEffect { processor.sideEffects.collect { … } }` keeps collecting for as long as
 * the composable is in the composition — including while the app is in the background — so
 * a toast, a navigation or a permission request can be consumed while nothing is visible
 * and is then gone. `CollectSideEffects` stops collecting when the lifecycle drops below
 * [minActiveState]; effects emitted meanwhile stay buffered in the processor and are
 * delivered, in order and exactly once, when the lifecycle comes back.
 *
 * ```kotlin
 * @Composable
 * fun SearchScreen(ctx: ScreenContext<SearchProcessor>) {
 *     val state by ctx.processor.states.collectAsStateWithLifecycle()
 *
 *     CollectSideEffects(ctx.processor) { effect ->
 *         when (effect) {
 *             is ShowToast -> snackbarHostState.showSnackbar(effect.message)
 *             is NavigateToDetails -> ctx.navigateTo(DetailsNavKey(effect.id))
 *         }
 *     }
 *     // ...
 * }
 * ```
 *
 * Delivery is exactly-once to a single collector, so call this from exactly one place per
 * processor. [onEffect] may suspend (for example, to show a snackbar); the next effect is
 * delivered once it returns. The latest [onEffect] is always used, so it can safely capture
 * values that change across recompositions.
 *
 * Collection runs on `Dispatchers.Main.immediate`. The processor emits on the main thread
 * by default, so an effect is handed over in the same frame it is sent, and there is no
 * window in which a lifecycle stop can cancel the collector between receiving an effect and
 * handling it.
 *
 * @param processor The processor whose side effects are collected.
 * @param minActiveState The lifecycle state at or above which effects are collected.
 * Defaults to [Lifecycle.State.STARTED], i.e. while the screen is visible.
 * @param onEffect Invoked for each side effect, on the main thread.
 */
@Composable
public fun <E : SideEffect> CollectSideEffects(
    processor: PresentationProcessor<*, *, E>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (E) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnEffect by rememberUpdatedState(onEffect)

    LaunchedEffect(processor, lifecycle, minActiveState) {
        lifecycle.collectSideEffects(processor, minActiveState) { effect -> currentOnEffect(effect) }
    }
}

/**
 * The lifecycle-bound collection behind [CollectSideEffects], kept free of Compose so it can
 * be tested against a plain `LifecycleRegistry`.
 */
internal suspend fun <E : SideEffect> Lifecycle.collectSideEffects(
    processor: PresentationProcessor<*, *, E>,
    minActiveState: Lifecycle.State,
    onEffect: suspend (E) -> Unit,
) {
    repeatOnLifecycle(minActiveState) {
        withContext(Dispatchers.Main.immediate) {
            processor.sideEffects.collect { effect -> onEffect(effect) }
        }
    }
}
