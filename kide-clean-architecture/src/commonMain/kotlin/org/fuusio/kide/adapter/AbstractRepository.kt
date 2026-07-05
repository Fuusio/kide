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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * An abstract base implementation of the [Repository] interface that serves as a foundation for
 * repositories in the application's adapter layer.
 *
 * This class is part of the application's clean architecture pattern, serving as a bridge between
 * the domain layer and external data sources (such as databases, network APIs, or device sensors).
 * Repositories provide a clean API to the domain layer for data operations, abstracting away the
 * details of data sources and their implementation.
 *
 * The class includes a coroutine dispatcher that subclasses can use to execute data operations
 * on appropriate threads. By default, operations will run on [Dispatchers.IO], which is
 * optimized for I/O-bound work such as database access and network requests.
 *
 * Subclasses should implement specific repository interfaces from the domain layer and provide
 * concrete implementations for data operations, using the provided dispatcher for coroutine
 * execution when needed.
 *
 * @param dispatcher The coroutine dispatcher to use for data operations. Defaults to [Dispatchers.IO].
 */
public abstract class AbstractRepository(
    protected val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository {

    /**
     * Dispatches the execution of the given [block] to the [dispatcher] configured for this
     * [Repository].
     *
     * This helper function ensures that data operations are executed on the appropriate coroutine
     * context, typically [Dispatchers.IO] for I/O-bound tasks or [Dispatchers.Default] for
     * CPU-intensive operations.
     *
     * @param T The type of the result returned by the [block].
     * @param block The suspending block of code to be executed.
     * @return The result of the [block] execution.
     */
    protected suspend fun <T> dispatch(block: suspend () -> T): T =
        withContext(dispatcher) { block() }
}