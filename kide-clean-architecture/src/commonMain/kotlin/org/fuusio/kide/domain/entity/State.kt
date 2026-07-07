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

package org.fuusio.kide.domain.entity

import org.fuusio.kide.domain.DomainComponent

/**
 * A marker interface for state objects in the application's domain layer.
 *
 * This interface is a key component of the application's clean architecture pattern, representing
 * the state of domain entities or processes. State objects encapsulate the data that defines
 * a particular state of a domain concept at a given point in time.
 *
 * In the context of the application's architecture:
 * - States are typically immutable data objects
 * - They are managed and updated by use cases ([org.fuusio.kide.domain.usecase.UseCaseProcessor])
 * - They are often exposed as flows to allow reactive updates in the presentation layer
 * - They represent the single source of truth for the domain layer
 *
 * State objects should:
 * - Contain only the data necessary to represent the state (no behavior)
 * - Be immutable to prevent uncontrolled state changes
 * - Be serializable when persistence is required
 * - Follow a clear versioning pattern when state transitions occur
 *
 * This pattern promotes:
 * - Predictable state management
 * - Unidirectional data flow
 * - Time-travel debugging capabilities
 * - Clear separation between state and behavior
 *
 * Implementations of this interface represent various domain states, such as the state
 * of an entity, a business process, or a domain-specific concept.
 */
public interface State : DomainComponent