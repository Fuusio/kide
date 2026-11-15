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

package org.fuusio.kide.appstate.statestore

import androidx.appstate.statestore.StateStoreKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.domain.usecase.AbstractUseCaseProcessor
import org.fuusio.kide.domain.usecase.UseCaseIntent
import org.fuusio.kide.domain.usecase.UseCaseInterceptor
import org.fuusio.kide.presentation.TraceContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class CounterState(val count: Int = 0) : State

internal sealed interface CounterIntent : UseCaseIntent<CounterState> {
    data class Add(val amount: Int) : CounterIntent

    /** Replaces the state with one loaded from a store. */
    data class Restore(val state: CounterState) : CounterIntent

    /** Always fails, to show what a restore that throws does to the mirror. */
    data object Fail : CounterIntent
}

internal class RecordingUseCaseInterceptor : UseCaseInterceptor<CounterState, CounterIntent> {
    val transitions = CopyOnWriteArrayList<Pair<CounterState, CounterState>>()

    override fun onStateChanged(oldState: CounterState, newState: CounterState, context: TraceContext?) {
        transitions.add(oldState to newState)
    }
}

internal class CounterProcessor(
    interceptors: List<UseCaseInterceptor<CounterState, CounterIntent>> = emptyList(),
) : AbstractUseCaseProcessor<CounterState, CounterIntent>(CounterState(), interceptors) {

    override suspend fun map(intent: CounterIntent) {
        when (intent) {
            is CounterIntent.Add -> reduce { state -> state.copy(count = state.count + intent.amount) }
            is CounterIntent.Restore -> reduce { _ -> intent.state }
            CounterIntent.Fail -> throw IllegalStateException("restore failed")
        }
    }
}

/** An in-memory key. */
internal object CounterKey : StateStoreKey<CounterState>(CounterState())

/** A second in-memory key, to show that keys do not interfere. */
internal object OtherCounterKey : StateStoreKey<CounterState>(CounterState())

/** Polls [condition] in real time; `false` if it did not hold within [timeout]. */
internal suspend fun within(timeout: Duration = 5.seconds, condition: () -> Boolean): Boolean =
    withTimeoutOrNull(timeout) {
        while (!condition()) delay(20)
        true
    } ?: false
