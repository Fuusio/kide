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

import org.fuusio.kide.domain.DomainComponent
import org.fuusio.kide.domain.entity.State

/**
 * A marker interface for intent objects in the application's domain layer.
 *
 * UseCaseIntent represents a command or request to perform a specific business operation
 * in a use case. It encapsulates all the data necessary to execute that operation, serving
 * as a transporter of information from the presentation layer to the domain layer.
 *
 * This interface is part of the application's intent-driven architecture, where business
 * operations are triggered by sending intents to [UseCaseLogic] implementations. This pattern
 * promotes a clear separation between the initiator of an action (typically a processor in
 * the presentation layer) and the executor of the action (a use case in the domain layer).
 *
 * Implementations of this interface should be immutable data classes that contain all the
 * parameters needed for a specific business operation. Each intent is typically associated
 * with a specific type of [State] that it might affect or interact with.
 *
 * Benefits of this approach include:
 * - Clear traceability of actions throughout the application
 * - Improved testability of business logic
 * - Decoupling of UI actions from business operations
 * - Ability to log, serialize, or replay business operations
 *
 * @param S The type of state that this intent is associated with
 */
public interface UseCaseIntent<S : State> : DomainComponent