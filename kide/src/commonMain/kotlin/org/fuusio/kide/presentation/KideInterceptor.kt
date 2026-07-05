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
 * Intercepts MVI lifecycle events in a [PresentationProcessor].
 * Implementations can be used for logging, analytics tracking, or debugger integration.
 */
public interface KideInterceptor<I : ViewIntent, S : ViewState, E : SideEffect> {

    /**
     * Invoked when a new [ViewIntent] is dispatched to the processor.
     */
    public fun onIntent(intent: I) {}

    /**
     * Invoked after the [ViewIntent] has been mapped to an [Action].
     *
     * @param intent The source intent.
     * @param action The mapped action, or null if no action is triggered.
     */
    public fun onActionMapped(intent: I, action: Action<S, E>?) {}

    /**
     * Invoked immediately before an [Action] starts executing.
     */
    public fun onActionExecuting(action: Action<S, E>) {}

    /**
     * Invoked after a state change is computed and about to be set.
     *
     * @param oldState The previous view state.
     * @param newState The new view state.
     */
    public fun onStateChanged(oldState: S, newState: S) {}

    /**
     * Invoked when a [SideEffect] is successfully posted to the collector stream.
     */
    public fun onSideEffect(sideEffect: E) {}

    /**
     * Invoked when an exception is thrown while mapping [intent] or executing the [Action] it
     * was mapped to. Cancellation exceptions are not reported. The processor logs the error
     * and continues processing subsequent intents.
     *
     * @param throwable The thrown exception.
     * @param intent The intent whose processing failed.
     */
    public fun onError(throwable: Throwable, intent: I) {}
}
