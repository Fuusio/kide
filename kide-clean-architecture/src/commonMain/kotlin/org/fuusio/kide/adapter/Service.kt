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

package org.fuusio.kide.adapter

/**
 * A marker interface for service components in the application's domain layer boundary.
 *
 * This interface is a key component of the application's clean architecture pattern, representing
 * the abstract definition of services as viewed from the domain layer. Services provide a clean
 * API to the domain layer for accessing external system functionality or performing operations
 * that don't involve data persistence (which would be handled by repositories).
 *
 * Services typically handle operations such as:
 * - Network communication
 * - System or device functionality interaction
 * - Third-party API integration
 * - Utility or transformation operations
 * - Coordination of complex processes
 *
 * The Service pattern creates a boundary between:
 * - The Domain layer (which contains business logic and use cases)
 * - The Framework layer (which handles platform-specific details)
 *
 * In the clean architecture approach:
 * - This interface is defined in the domain layer
 * - Implementations reside in the adapter layer (see [org.fuusio.kide.adapter.AbstractService])
 * - Use cases depend on service interfaces, not concrete implementations
 *
 * Using this approach ensures that business logic depends only on abstractions,
 * not on concrete implementations of external services or system functionality, adhering to
 * the Dependency Inversion Principle. It also makes testing easier by allowing mock
 * implementations of services to be used in tests.
 */
public interface Service : AdapterComponent