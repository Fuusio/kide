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

@file:Suppress("unused")

package org.fuusio.kide.presentation

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A builder class representing the scope of a [PresentationProcessor] test scenario.
 * It manages the collection of emitted view states and side effects, and provides a clean
 * DSL for dispatching intents and asserting expectations.
 */
class PresentationProcessorTestScope<I : ViewIntent, S : ViewState, E : SideEffect> internal constructor(
    private val processor: PresentationProcessor<I, S, E>,
    coroutineScope: CoroutineScope,
) {
    private val collectedStates = mutableListOf<S>()
    private val collectedSideEffects = mutableListOf<E>()

    init {
        // Start collecting view states
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            processor.states.collect { state ->
                collectedStates.add(state)
            }
        }
        // Start collecting side effects
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            processor.sideEffects.collect { sideEffect ->
                collectedSideEffects.add(sideEffect)
            }
        }
    }

    /**
     * Dispatches the given [intent] to the processor under test.
     */
    fun onIntent(intent: I) {
        processor.dispatch(intent)
    }

    /**
     * Dispatches multiple [intents] to the processor under test.
     */
    fun onIntents(vararg intents: I) {
        intents.forEach { processor.dispatch(it) }
    }

    /**
     * Asserts that the collected view states match exactly the [expectedStates] in the same order.
     * This includes the starting state (initial state) of the processor when collection began.
     */
    fun expectStates(vararg expectedStates: S) {
        collectedStates.shouldContainExactly(expectedStates.toList())
    }

    /**
     * Asserts that the collected view states match exactly the [expectedStates] in the same order,
     * ignoring the initial state (the first collected state) if it was not explicitly expected.
     */
    fun expectNewStates(vararg expectedStates: S) {
        val newStates = if (collectedStates.isNotEmpty()) {
            collectedStates.drop(1)
        } else {
            collectedStates
        }
        newStates.shouldContainExactly(expectedStates.toList())
    }

    /**
     * Runs custom assertions on the collected states list using a custom predicate lambda.
     */
    fun verifyStates(assertion: (List<S>) -> Unit) {
        assertion(collectedStates)
    }

    /**
     * Asserts that the collected side effects match exactly the [expectedSideEffects] in the same order.
     */
    fun expectSideEffects(vararg expectedSideEffects: E) {
        collectedSideEffects.shouldContainExactly(expectedSideEffects.toList())
    }

    /**
     * Runs custom assertions on the collected side effects list using a custom predicate lambda.
     */
    fun verifySideEffects(assertion: (List<E>) -> Unit) {
        assertion(collectedSideEffects)
    }

    /**
     * Returns a read-only list of all collected states during this test.
     */
    fun getCollectedStates(): List<S> = collectedStates.toList()

    /**
     * Returns a read-only list of all collected side effects during this test.
     */
    fun getCollectedSideEffects(): List<E> = collectedSideEffects.toList()
}

/**
 * Runs a fluent DSL-based test against this [PresentationProcessor].
 *
 * This helper automatically handles state and side-effect collection, eliminating the boilerplate
 * of launching background coroutine collection jobs and manually managing their lifecycles.
 *
 * It uses [Dispatchers.Main] under the hood, which is expected to be overridden with a test
 * dispatcher (like [kotlinx.coroutines.test.UnconfinedTestDispatcher]) in your test setup.
 *
 * ### Example Usage:
 * ```kotlin
 * processor.test {
 *     onIntent(TestIntent.Increment)
 *     onIntent(TestIntent.Increment)
 *
 *     expectStates(
 *         TestViewState(0), // Initial state
 *         TestViewState(1),
 *         TestViewState(2)
 *     )
 * }
 * ```
 *
 * @param block The test definition block.
 */
fun <I : ViewIntent, S : ViewState, E : SideEffect> PresentationProcessor<I, S, E>.test(
    block: PresentationProcessorTestScope<I, S, E>.() -> Unit,
) {
    val coroutineScope = CoroutineScope(Dispatchers.Main)
    val testScope = PresentationProcessorTestScope(this, coroutineScope)
    try {
        testScope.block()
    } finally {
        coroutineScope.cancel()
    }
}
