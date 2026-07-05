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
 * A marker interface for repository components in the application's domain layer boundary.
 *
 * This interface is a key component of the application's clean architecture pattern, representing
 * the abstract definition of data repositories as viewed from the domain layer. Repositories
 * provide a clean API to the domain layer for accessing and manipulating data, abstracting away
 * the details of how and where the data is stored or retrieved.
 *
 * The Repository pattern creates a boundary between:
 * - The domain layer (which contains business logic and use cases)
 * - The data layer (which handles data persistence and retrieval)
 *
 * Key responsibilities of repositories include:
 * - Providing a collection-like interface to domain entities
 * - Coordinating data operations across multiple data sources when necessary
 * - Handling data caching strategies
 * - Converting between domain entities and data models
 * - Isolating the domain from data access technologies and implementation details
 *
 * In the clean architecture approach:
 * - This interface is defined in the domain layer
 * - Implementations reside in the adapter layer (see [org.fuusio.kide.adapter.AbstractRepository])
 * - Implementations may use various [org.fuusio.kide.framework.DataSource] objects
 *
 * Using this approach ensures that business logic depends only on abstractions,
 * not on concrete data access implementations, adhering to the Dependency Inversion Principle.
 */
public interface Repository : AdapterComponent