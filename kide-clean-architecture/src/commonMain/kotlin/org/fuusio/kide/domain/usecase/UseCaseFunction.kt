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

/**
 * A marker interface for simple use case components in the application's domain layer which are
 * implemented as function interfaces a.k.a. single-abstract-method (SAM) interfaces.
 *
 * This interface is a key component of the application's clean architecture pattern, representing
 * application-specific business rules and operations. A use case encapsulates a single, specific
 * business action, rule, or flow that the application can perform.
 *
 * Use cases serve as the primary entry points to the domain layer from the presentation layer.
 * They orchestrate the flow of data and actions between entities and the outer layers of the
 * application, while enforcing business rules. By focusing each use case on a single responsibility,
 * the application's business logic becomes more modular, maintainable, and testable.
 *
 * Use cases should be independent of UI, frameworks, or external agencies, ensuring the
 * business logic remains pure and focused solely on the application's core functionality.
 */
public interface UseCaseFunction : UseCaseComponent
