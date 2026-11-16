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

package org.fuusio.kide.appstate

import androidx.appstate.transform.listener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A cold [Flow] backed by a headless AppState composition.
 *
 * Each collection runs [block] in its own composition for as long as it is collected and
 * emits the block's latest result. The block recomposes whenever a Compose
 * [State] it read changes; flows are bridged inside it with `collectAsState`.
 *
 * The flow is **conflated and distinct-until-changed**: when several inputs change before
 * the block recomposes, only the latest result is emitted. Use it for *current inputs* to a
 * reduction, never for values that must each be seen. This is a property of snapshot state
 * itself, which only ever has a latest value, and holds for any bridge from [State] to a
 * [Flow]; it is why events and intents cannot travel through one.
 *
 * **[block] must be idempotent and free of side effects.** It is a composable: it can run
 * more than once for the same inputs, or once after a burst of changes, and a composition
 * can be discarded before it is applied. Anything it does besides computing its result —
 * incrementing a counter, logging, dispatching an intent, writing a [State] — happens an
 * unpredictable number of times. Compute the result in the block and act on it in the
 * collector, which for a processor is the `observe` lambda and its `reduce`.
 *
 * Nothing enforces this. Unlike `snapshotFlow`, whose block runs in a read-only snapshot and
 * fails on a write, this block runs in a composition, where a write to a [State] succeeds
 * silently — and a write to a [State] the block also reads makes it recompose itself
 * (observed with AppState `1.0.0-alpha02`).
 *
 * Intended for `PresentationProcessor.observe`, which supplies the lifetime, keyed
 * replacement and error routing:
 *
 * ```kotlin
 * observe("inputs", transformFlow { Inputs(monitor.isOnline.value, session.value) }) { inputs ->
 *     reduce { copy(online = inputs.online, session = inputs.session) }
 * }
 * ```
 *
 * @param dispatcher The dispatcher the composition runs on.
 * @param block The composable computation whose result is emitted.
 */
@ExperimentalKideAppStateApi
public fun <T> transformFlow(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: @Composable () -> T,
): Flow<T> = channelFlow {
    // listener suspends until this collection is cancelled, which is what ties the
    // composition's lifetime to the collector's.
    listener(dispatcher) {
        val value = block()
        // Published after the composition has been applied, never from its body: a
        // composition that is discarded must not emit.
        SideEffect { trySend(value) }
    }
}.conflate().distinctUntilChanged()

/**
 * This [State] as a cold [Flow], driven by an AppState composition rather than by
 * `snapshotFlow`, so that it does not depend on a UI toolkit sending global snapshot apply
 * notifications.
 */
@ExperimentalKideAppStateApi
public fun <T> State<T>.asFlow(): Flow<T> = transformFlow { value }
