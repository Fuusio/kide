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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.log.logD

/**
 * An abstract base implementation of the [UseCaseLogic] interface that provides state management
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
 * - [onIntent] (from the [UseCaseLogic] interface) to handle specific intent types
 * and can use [updateState] to modify the state during business operations.
 *
 * @param initialState The initial domain state for the use case
 * @param S The type of state that this use case logic works with
 * @param I The type of intent that this use case logic handles
 */
public abstract class AbstractUseCaseLogic<S : State, I : UseCaseIntent<S>>(initialState: S)
    : UseCaseLogic<S, I> {

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
     * Updates the current domain state with a new state.
     * This method should be called by subclasses when business logic results in state changes.
     *
     * @param state The new domain state to set
     */
    protected fun updateState(state: S) {
        logD { "State updated directly: $state" }
        _stateFlow.value = state
    }

    /**
     * Updates the current domain state with a given [reducer].
     *
     * @param reducer A reducer function
     */
    protected fun updateState(reducer: (S) -> S) {
        _stateFlow.update { currentState ->
            val newState = reducer(currentState)
            logD { "State updated via reducer: $newState" }
            newState
        }
    }
}