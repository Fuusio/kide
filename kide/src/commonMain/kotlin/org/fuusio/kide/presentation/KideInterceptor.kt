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
 *
 * Every callback receives the [TraceContext] of the intent being processed, so that events can
 * be attributed to the interaction that caused them rather than merely ordered near it. It is
 * `null` only when there is genuinely no originating intent. Callbacks that do not care can
 * ignore the parameter; all of them have empty default bodies, so an implementation overrides
 * only what it needs.
 *
 * Callbacks are invoked on the processor's intent loop, in processing order — including
 * [onIntent], which fires when the loop picks an intent up rather than when [dispatch] queued
 * it. Under concurrent dispatch from several threads those orders differ, and the loop's is the
 * one that describes what actually happened.
 */
public interface KideInterceptor<I : ViewIntent, S : ViewState, E : SideEffect> {

    /**
     * Invoked when the intent loop begins processing a [ViewIntent].
     */
    public fun onIntent(intent: I, context: TraceContext?) {}

    /**
     * Invoked after the [ViewIntent] has been mapped to an [Action].
     *
     * @param intent The source intent.
     * @param action The mapped action, or null if no action is triggered.
     */
    public fun onActionMapped(intent: I, action: Action<S, E>?, context: TraceContext?) {}

    /**
     * Invoked immediately before an [Action] starts executing.
     */
    public fun onActionExecuting(action: Action<S, E>, context: TraceContext?) {}

    /**
     * Invoked once for every state transition that was actually applied, after [newState] has
     * been published to the processor's state flow.
     *
     * Reported for reductions from both execution paths: a `ReducerAction` running on the
     * intent loop, and [AsyncScope.reduce] called from inside an [AsyncAction]. *Not* reported
     * for a transformation that left the state unchanged, nor for one that was discarded
     * because another coroutine won the compare-and-set race — so the sequence of
     * `onStateChanged` calls is a faithful account of the states the processor actually held.
     *
     * @param oldState The previous view state.
     * @param newState The new view state, already published when this is invoked.
     */
    public fun onStateChanged(oldState: S, newState: S, context: TraceContext?) {}

    /**
     * Invoked when a [SideEffect] has been successfully posted to the collector stream.
     */
    public fun onSideEffect(sideEffect: E, context: TraceContext?) {}

    /**
     * Invoked when an exception is thrown while mapping [intent] or executing the [Action] it
     * was mapped to. Cancellation exceptions are not reported. The processor logs the error
     * and continues processing subsequent intents.
     *
     * @param throwable The thrown exception.
     * @param intent The intent whose processing failed.
     */
    public fun onError(throwable: Throwable, intent: I, context: TraceContext?) {}
}
