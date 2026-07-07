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

import kotlinx.coroutines.flow.StateFlow
import org.fuusio.kide.domain.entity.State

/**
 * Defines the core business logic for processing use case intents in the application's domain layer.
 *
 * This interface is a key component of the application's clean architecture pattern, encapsulating
 * the business rules and application-specific logic. A use case represents a specific action,
 * process, or operation that the application can perform, independent of any UI or external concerns.
 *
 * `UseCaseProcessor` implementations receive [UseCaseIntent]s that represent commands or requests to
 * perform some business operation. The implementation processes these intents according to business
 * rules, typically updating some domain [State] as a result. This follows an intent-driven
 * architecture pattern that promotes clear separation of concerns and testability.
 *
 * The generic nature of this interface allows different use cases to work with different types
 * of states and intents, while maintaining a consistent pattern across the application.
 *
 * @param S The type of state that this use case logic works with
 * @param I The type of intent that this use case logic handles
 */
public interface UseCaseProcessor<S : State, I : UseCaseIntent<S>> : UseCaseComponent {
    /**
     * The current state managed by this use case logic.
     */
    public val state: S

    /**
     * A [StateFlow] that provides access to the current domain [State] managed by this logic.
     */
    public val stateFlow: StateFlow<S>

    /**
     * Dispatches the given [intent] to be processed by this [UseCaseProcessor].
     */
    public suspend fun dispatch(intent: I)
}