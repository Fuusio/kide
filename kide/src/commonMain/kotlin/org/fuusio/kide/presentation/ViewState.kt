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
 * A marker interface for UI state representations in the application's presentation layer architecture.
 *
 * ViewState represents the complete state of a view or screen in the application. It encapsulates
 * all the data needed to render the UI at any given moment, serving as a snapshot of the UI state.
 * 
 * Implementations of this interface should be immutable data classes that contain all the
 * properties necessary to represent the current state of a view, such as:
 * - Content to display (text, images, lists of items)
 * - UI element states (enabled/disabled, visible/hidden)
 * - Loading states
 * - Error messages
 * - User input values
 *
 * The view state is managed by a [PresentationProcessor] and updated by a [ReducerAction] or
 * by an [AsyncAction]. A [ViewState] serves as the single source of truth for the UI layer,
 * promoting a unidirectional data flow and making the UI state predictable and easier to debug.
 */
public interface ViewState : PresentationComponent