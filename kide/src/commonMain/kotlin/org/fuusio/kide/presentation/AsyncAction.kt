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

package org.fuusio.kide.presentation

/**
 * [AsyncAction] defines an [Action] that involves invoking a use case.
 *
 * It is a specialized [Action] that can be mapped to an [ViewIntent] in a [PresentationProcessor].
 * This class provides a mechanism to perform asynchronous operations and transform the
 * [ViewState] accordingly. Unlike [ReducerAction] and [SideEffectAction], an [AsyncAction]
 * is executed in its own coroutine so that long-running work never blocks the
 * intent-processing loop.
 *
 * [AsyncAction] produces no side effects directly, hence `Action<S, Nothing>`; state changes
 * are applied through [AsyncScope.reduce].
 *
 * @param S The type of the [ViewState].
 * @property transform An optional suspending function literal with receiver defining the logic
 * for state transformation.
 * @property cancellationKey An optional key used to identify and cancel the execution of
 * this action if a new action with the same key is triggered.
 */
public open class AsyncAction<S : ViewState>(
    public val cancellationKey: String? = null,
    private val transform: (suspend AsyncScope<S>.() -> Unit)? = null,
) : Action<S, Nothing> {

    /**
     * Executes the asynchronous action with the given [scope].
     * Subclasses can override this function to perform custom use case executions.
     */
    public open suspend operator fun invoke(scope: AsyncScope<S>) {
        transform?.invoke(scope)
    }
}


