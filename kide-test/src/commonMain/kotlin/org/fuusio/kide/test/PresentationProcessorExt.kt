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
package org.fuusio.kide.test

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import kotlin.test.assertEquals

/**
 * A testing DSL context for evaluating [PresentationProcessor] behaviors.
 */
public class ProcessorTestContext<I : ViewIntent, S : ViewState, E : SideEffect>(
    public val processor: PresentationProcessor<I, S, E>,
    private val stateTurbine: TurbineTestContext<S>,
    private val sideEffectTurbine: TurbineTestContext<E>
) {
    /**
     * Dispatches an [intent] to the processor.
     */
    public fun dispatch(intent: I) {
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
     * Awaits the next side effect emission and asserts it using the provided [assertion].
     */
    public suspend fun expectSideEffect(assertion: (E) -> Unit) {
        assertion(sideEffectTurbine.awaitItem())
    }
    
    /**
     * Awaits the next side effect emission and asserts that it is equal to [expected].
     */
    public suspend fun expectSideEffect(expected: E) {
        assertEquals(expected, sideEffectTurbine.awaitItem())
    }

    /**
     * Awaits complete initialization, discarding the initial state.
     */
    public suspend fun skipInitialState() {
        stateTurbine.skipItems(1)
    }
}

/**
 * Runs a block of test code against this [PresentationProcessor].
 * Sets up Turbine listeners for both [PresentationProcessor.states] and [PresentationProcessor.sideEffects].
 * 
 * Note: To test processors properly, you should set the Main dispatcher to a TestDispatcher.
 * e.g., `Dispatchers.setMain(UnconfinedTestDispatcher())` in your test setup.
 */
public suspend fun <I : ViewIntent, S : ViewState, E : SideEffect> PresentationProcessor<I, S, E>.test(
    block: suspend ProcessorTestContext<I, S, E>.() -> Unit
) {
    val proc = this
    proc.states.test {
        val stateContext = this
        proc.sideEffects.test {
            val sideEffectContext = this
            val testContext = ProcessorTestContext(
                processor = proc,
                stateTurbine = stateContext,
                sideEffectTurbine = sideEffectContext
            )
            testContext.block()
            
            // Cancel remaining events after block completes
            stateContext.cancelAndIgnoreRemainingEvents()
            sideEffectContext.cancelAndIgnoreRemainingEvents()
        }
    }
}
