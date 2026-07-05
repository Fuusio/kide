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
 * An abstract base implementation of the [Manager] interface that serves as a foundation for
 * manager components in the application's framework layer.
 *
 * This class is part of the application's clean architecture pattern, representing the lowest
 * level components that control a specific capability of the underlying platform — such as
 * system settings, device drivers, or sensors — and provide an API for operating it. Managers
 * are typically used by service components ([org.fuusio.kide.adapter.Service] implementations),
 * which coordinate and abstract the platform management operations.
 *
 * The class includes a coroutine dispatcher that subclasses can use to execute management
 * operations on appropriate threads. By default, operations will run on [Dispatchers.IO].
 *
 * Subclasses should implement functionality specific to the platform capability they manage,
 * using the provided dispatcher for coroutine execution when needed.
 *
 * @param dispatcher The coroutine dispatcher to use for management operations. Defaults to
 * [Dispatchers.IO].
 */
public abstract class AbstractManager(
    protected val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Manager {

    protected suspend fun <T> dispatch(block: suspend () -> T): T =
        withContext(dispatcher) { block() }
}