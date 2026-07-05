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

package org.fuusio.kide.framework

import org.fuusio.kide.adapter.Repository

/**
 * A marker interface for data source components in the application's Framework layer.
 *
 * This interface is part of the application's clean architecture pattern, representing the lowest
 * level of data access components. Data sources are responsible for handling the technical details
 * of interacting with specific data origin points such as:
 * - Local databases
 * - Remote API services
 * - File systems
 * - Device sensors
 * - In-memory caches
 *
 * Data sources are typically used by repositories ([Repository] implementations), which coordinate
 * and abstract the data access operations. By separating the data source implementation details
 * from the repositories, the application can:
 * - Switch between different data sources easily (e.g., mock implementations for testing)
 * - Handle data format conversions at the appropriate layer
 * - Implement caching strategies at the repository level
 * - Maintain a clean separation between data access mechanics and business logic
 *
 * Implementations of this interface should focus on a single data source and provide methods
 * specific to the operations needed on that source, such as CRUD operations for databases
 * or API calls for network services.
 */
public interface DataSource : FrameworkComponent