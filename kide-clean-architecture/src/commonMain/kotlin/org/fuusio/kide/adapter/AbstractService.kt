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
import kotlinx.coroutines.withContext

/**
 * An abstract base implementation of the [Service] interface that serves as a foundation for
 * services in the application's adapter layer.
 *
 * This class is part of the application's clean architecture pattern, providing a gateway to
 * external system functionality or third-party APIs. Services typically encapsulate operations
 * that do not involve data persistence (which would be handled by repositories) but instead
 * focus on actions and processes such as network communication, system interactions, or
 * utility operations.
 *
 * The class includes a coroutine dispatcher that subclasses can use to execute service operations
 * on appropriate threads. By default, operations will run on [Dispatchers.Default], which is
 * optimized for CPU-intensive work. Services that perform IO-bound operations might want to use
 * [Dispatchers.IO] instead.
 *
 * Subclasses should implement specific service interfaces from the domain layer and provide
 * concrete implementations for service operations, using the provided dispatcher for coroutine
 * execution when needed.
 *
 * @param dispatcher The coroutine dispatcher to use for service operations. Defaults to [Dispatchers.Default].
 */
public abstract class AbstractService(
    protected val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Service {

    /**
     * Dispatches the execution of the given [block] to the [dispatcher] configured for this
     * [Service].
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