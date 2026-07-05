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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * An abstract base implementation of the [DataSource] interface that serves as a foundation for
 * data sources in the application's framework layer.
 *
 * This class is part of the application's clean architecture pattern, representing the lowest level
 * components that directly interact with data origin points such as local databases, remote APIs,
 * file systems, or device sensors. Data sources are typically used by repositories, which coordinate
 * and abstract the data access operations.
 *
 * The primary responsibility of a data source is to handle the technical details of data retrieval,
 * storage, and manipulation from a specific source, providing a clean API that shields higher layers
 * from implementation details.
 *
 * The class includes a coroutine dispatcher that subclasses can use to execute data operations
 * on appropriate threads. By default, operations will run on [Dispatchers.IO], which is
 * optimized IO-bound operations.
 *
 * Subclasses should implement specific data source functionality based on the type of data they
 * handle and the origin of that data, using the provided dispatcher for coroutine execution
 * when needed.
 *
 * @param dispatcher The coroutine dispatcher to use for data operations. Defaults to
 * [Dispatchers.IO].
 */
public abstract class AbstractDataSource(
    protected val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : DataSource {

    protected suspend fun <T> dispatch(block: suspend () -> T): T =
        withContext(dispatcher) { block() }
}