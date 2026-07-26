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

package org.fuusio.kide.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.log.logD
import org.fuusio.kide.presentation.currentTraceContext

/**
 * An abstract base implementation of the [UseCaseProcessor] interface that provides state management
 * functionality for use cases in the application's domain layer.
 *
 * This class is a key component of the application's clean architecture pattern, implementing
 * the core business logic for processing intents and managing state within a use case. It provides
 * a reactive state management solution using Kotlin Flows, allowing observers to react to state
 * changes in a lifecycle-aware manner.
 *
 * The state flow pattern enables a unidirectional data flow where:
 * 1. Intents trigger business logic in the use case
 * 2. Business logic updates the state
 * 3. Observers (typically in the presentation layer) react to state changes
 *
 * Subclasses must implement:
 * - [map] function to handle specific intent types
 * and can use [reduce] to modify the state during business operations.
 *
 * @param initialState The initial domain state for the use case
 * @param S The type of state that this use case logic works with
 * @param I The type of intent that this use case logic handles
 */
public abstract class AbstractUseCaseProcessor<S : State, I : UseCaseIntent<S>>(
    initialState: S,
    override val interceptors: List<UseCaseInterceptor<S, I>> = emptyList(),
) : UseCaseProcessor<S, I> {

    private val _stateFlow = MutableStateFlow(initialState)

    /**
     * The current domain state.
     */
    override val state: S get() = _stateFlow.value

    /**
     * An immutable state flow that exposes the current domain state to observers.
     * This flow can be collected by components that need to react to state changes.
     */
    override val stateFlow: StateFlow<S> = _stateFlow.asStateFlow()

    /**
     * Dispatches the given [intent] to be processed by [map].
     *
     * See [UseCaseProcessor.dispatch] for the contract: no ordering guarantee, and exceptions
     * are reported to [interceptors] and then rethrown rather than swallowed.
     */
    public override suspend fun dispatch(intent: I) {
        logD { "Dispatched use case intent ${intent::class.simpleName}" }
        val context = currentTraceContext()
        interceptors.forEach { it.onIntent(intent, context) }
        try {
            map(intent)
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            // Reported, not handled: the caller — normally an AsyncAction — still needs to see
            // this, and its own guard will attribute it to the originating ViewIntent.
            interceptors.forEach { it.onError(throwable, intent, context) }
            throw throwable
        }
    }

    /**
     * Processes the given [intent].
     *
     * Subclasses must implement this method to define the business logic for each specific intent.
     * This typically involves performing operations and updating the state using the [reduce] methods.
     *
     * @param intent The intent to be processed.
     */
    protected abstract suspend fun map(intent: I)

    /**
     * Replaces the current domain state with [state], unconditionally.
     *
     * Prefer [reduce] with a reducer. This overload overwrites whatever is there rather than
     * transforming it, so when a processor is shared — and use-case processors usually are,
     * being singletons in a DI graph — it can discard a change another coroutine made in
     * between. `reduce { state }` expresses the same intent while staying explicit about that.
     *
     * @param state The new domain state to set
     */
    protected suspend fun reduce(state: S) {
        reduceState { state }
    }

    /**
     * Updates the current domain state with a given [reducer].
     *
     * [reducer] must be pure: it is evaluated again if another coroutine changes the state
     * first.
     *
     * @param reducer A reducer function
     */
    protected suspend fun reduce(reducer: (S) -> S) {
        reduceState(reducer)
    }

    /**
     * Applies [transform] and, when the state actually changed, notifies the [interceptors]
     * exactly once — after the new state has been published.
     *
     * The compare-and-set loop is written out rather than delegating to
     * [kotlinx.coroutines.flow.update] deliberately: `update` re-evaluates its lambda when it
     * loses a race, which would log and report a transition that was computed, discarded and
     * never applied. This mirrors `PresentationProcessor.reduceState`; the two layers report
     * state changes on identical terms.
     */
    private suspend fun reduceState(transform: (S) -> S) {
        while (true) {
            val currentState = _stateFlow.value
            val newState = transform(currentState)
            if (_stateFlow.compareAndSet(currentState, newState)) {
                if (newState != currentState) {
                    logD { "State updated: $newState" }
                    val context = currentTraceContext()
                    interceptors.forEach { it.onStateChanged(currentState, newState, context) }
                }
                return
            }
        }
    }
}