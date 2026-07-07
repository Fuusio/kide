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
package org.fuusio.kide.domain.usecase.test

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.domain.usecase.UseCaseIntent
import org.fuusio.kide.domain.usecase.UseCaseProcessor
import kotlin.test.assertEquals

/**
 * A testing DSL context for evaluating [UseCaseProcessor] behaviors.
 *
 * Unlike a presentation-layer processor, a [UseCaseProcessor] exposes a single reactive
 * channel — its [UseCaseProcessor.stateFlow] — so this context only wraps a state
 * [TurbineTestContext]. Dispatch intents with [dispatch] and assert on the resulting
 * state emissions with [expectState].
 *
 * Because a [kotlinx.coroutines.flow.StateFlow] always replays its current value, the first
 * emission observed by the DSL is the processor's initial state. Call [skipInitialState] to
 * discard it before dispatching intents.
 */
public class UseCaseProcessorTestContext<S : State, I : UseCaseIntent<S>>(
    public val processor: UseCaseProcessor<S, I>,
    private val stateTurbine: TurbineTestContext<S>,
) {
    /**
     * The processor's current domain state, read directly (without awaiting an emission).
     */
    public val state: S get() = processor.state

    /**
     * Dispatches an [intent] to the processor under test.
     */
    public suspend fun dispatch(intent: I) {
        processor.dispatch(intent)
    }

    /**
     * Awaits the next state emission and asserts it using the provided [assertion].
     */
    public suspend fun expectState(assertion: (S) -> Unit) {
        assertion(stateTurbine.awaitItem())
    }

    /**
     * Awaits the next state emission and asserts that it is equal to [expected].
     */
    public suspend fun expectState(expected: S) {
        assertEquals(expected, stateTurbine.awaitItem())
    }

    /**
     * Discards the replayed initial state, so that subsequent [expectState] calls observe
     * only the states produced by dispatched intents.
     */
    public suspend fun skipInitialState() {
        stateTurbine.skipItems(1)
    }
}

/**
 * Runs a block of test code against this [UseCaseProcessor], collecting its
 * [UseCaseProcessor.stateFlow] through Turbine.
 *
 * Invoke this from within [kotlinx.coroutines.test.runTest] so that state collection is
 * driven deterministically by the test scheduler:
 *
 * ```kotlin
 * runTest {
 *     SavedProjectsProcessor(repository).test {
 *         skipInitialState()
 *         dispatch(SaveProject(project))
 *         expectState { state -> assertTrue(project in state.projects) }
 *     }
 * }
 * ```
 */
public suspend fun <S : State, I : UseCaseIntent<S>> UseCaseProcessor<S, I>.test(
    block: suspend UseCaseProcessorTestContext<S, I>.() -> Unit
) {
    val processor = this
    processor.stateFlow.test {
        val context = UseCaseProcessorTestContext(processor, this)
        context.block()
        cancelAndIgnoreRemainingEvents()
    }
}
