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
 * [SideEffect] defines a marker interface for representing an effect that occurs as a result of
 * an [Action] invoked from UI/presentation layer. These effects are often "fire-and-forget"
 * operations that don't directly alter the application's state but rather trigger external
 * interactions, such as navigation, showing a toast message, or displaying a dialog.
 *
 * This marker interface is used to group different side effect types for handling within a View.
 */
public interface SideEffect : PresentationComponent