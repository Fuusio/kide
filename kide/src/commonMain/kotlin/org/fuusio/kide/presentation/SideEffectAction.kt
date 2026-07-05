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
 * Defines a side-effect action. A [SideEffectAction] takes an input [ViewState] and produces
 * a [SideEffect].
 *
 * A [SideEffectAction] executes synchronously and inline on the intent-processing loop of
 * a [PresentationProcessor], ordered with any [ReducerAction]s dispatched around it. The
 * [dispatch] function is intentionally non-suspending: its contract is to *construct* a
 * side-effect object from the current state, not to perform work. Any asynchronous work
 * belongs in an [AsyncAction].
 *
 * @param S The type of the [ViewState].
 * @param E The type of the [SideEffect].
 * @property dispatch An optional function literal with receiver that produces the side effect.
 */
public open class SideEffectAction<S : ViewState, out E : SideEffect>(
    private val dispatch: (S.() -> E)? = null,
) : Action<S, E> {

    /**
     * Executes the side-effect action with the given [state] and returns the resulting [SideEffect].
     * Subclasses can override this function to perform custom side effect executions.
     */
    public open operator fun invoke(state: S): E =
        dispatch?.invoke(state) ?: throw UnsupportedOperationException(
            "Subclasses of SideEffectAction must either provide a dispatch function or override invoke(state)."
        )
}
