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
 * [CompositeAction] is an [Action] that encapsulates a collection of multiple [Action]s.
 *
 * This class allows grouping related actions to be treated as a single unit of work. The
 * contained actions always execute sequentially, in order. If all contained actions are
 * synchronous ([ReducerAction], [SideEffectAction]), the whole composite executes inline on
 * the intent-processing loop; if it contains at least one [AsyncAction], the whole composite
 * executes in its own coroutine.
 *
 * By optionally providing a [cancellationKey], the lifecycle and cancellation of the
 * entire group can be managed collectively. Note that only the outermost [cancellationKey]
 * participates in cancellation; keys of nested actions are ignored.
 *
 * @param S The type of the [ViewState] the actions operate on.
 * @param E The type of the [SideEffect] the actions can produce.
 * @property actions A [List] of [Action]s
 * @property cancellationKey An optional key used to identify and manage the cancellation of this action group.
 */
public data class CompositeAction<S : ViewState, out E : SideEffect>(
    val actions: List<Action<S, E>>,
    val cancellationKey: String? = null,
) : Action<S, E> {

    public companion object {
        public fun <S : ViewState, E : SideEffect> create(
            vararg actions: Action<S, E>,
            cancellationKey: String? = null,
        ): CompositeAction<S, E> = CompositeAction(actions.toList(), cancellationKey)
    }
}
