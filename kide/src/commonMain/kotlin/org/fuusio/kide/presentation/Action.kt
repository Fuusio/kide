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
 * An [Action] defines an interface for any action object that can be mapped to
 * an [ViewIntent] dispatched to a [PresentationProcessor].
 *
 * @param S The type of the [ViewState] the action operates on.
 * @param E The type of the [SideEffect] the action can produce. The parameter is covariant;
 * actions that cannot produce side effects (such as [ReducerAction] and [AsyncAction])
 * implement `Action<S, Nothing>`, which is a subtype of `Action<S, E>` for any [E].
 */
public sealed interface Action<S : ViewState, out E : SideEffect> : PresentationComponent
