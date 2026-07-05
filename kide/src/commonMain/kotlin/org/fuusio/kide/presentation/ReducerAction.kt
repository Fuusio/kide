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
 * [ReducerAction] is an [Action] that takes an input [ViewState] and reduces it to
 * a new [ViewState].
 *
 * A [ReducerAction] executes synchronously and inline on the intent-processing loop of
 * a [PresentationProcessor]: reductions are applied in the exact order their intents were
 * dispatched. The [transform] must therefore be pure and fast; it must not block.
 *
 * [ReducerAction] produces no side effects, hence `Action<S, Nothing>`.
 *
 * @param S The type of the [ViewState].
 * @property transform An optional function literal with receiver defining how the state is
 * transformed.
 */
public open class ReducerAction<S : ViewState>(
    private val transform: (S.() -> S)? = null,
) : Action<S, Nothing> {

    /**
     * Executes the state transformation. Subclasses can override this function to perform custom
     * reductions.
     */
    public open operator fun invoke(state: S): S =
        transform?.invoke(state) ?: state
}
