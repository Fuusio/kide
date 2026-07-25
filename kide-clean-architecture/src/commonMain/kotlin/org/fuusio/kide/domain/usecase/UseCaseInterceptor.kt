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

package org.fuusio.kide.domain.usecase

import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.presentation.TraceContext

/**
 * Intercepts lifecycle events in a [UseCaseProcessor] — the domain-layer counterpart of
 * `KideInterceptor`.
 *
 * Three callbacks rather than six: the domain layer has no actions to map and no side effects
 * to emit, so an interceptor here sees intents, applied state changes, and failures.
 *
 * Every callback receives the [TraceContext] of the `ViewIntent` whose processing led here,
 * read from the coroutine context. It is `null` when the work has no originating intent —
 * a repository flow collected from application startup, for example — which is a legitimate
 * and useful thing to be able to see rather than a gap.
 *
 * Attaching a recorder that shares a `TraceBuffer` with the presentation layer's
 * `FlightRecorder` is what puts domain events into the *same* trace as the UI events that
 * caused them, instead of a second one a reader has to merge by timestamp.
 *
 * @param S The domain state this processor manages.
 * @param I The intents it handles.
 */
public interface UseCaseInterceptor<S : State, I : UseCaseIntent<S>> {

    /**
     * Invoked when the processor begins handling [intent].
     */
    public fun onIntent(intent: I, context: TraceContext?) {}

    /**
     * Invoked once for every domain state transition that was actually applied, after
     * [newState] has been published to the processor's state flow.
     *
     * Not reported for a reduction that left the state unchanged, nor for one discarded
     * because another coroutine won the compare-and-set race — so the sequence of calls is a
     * faithful account of the states the processor actually held.
     */
    public fun onStateChanged(oldState: S, newState: S, context: TraceContext?) {}

    /**
     * Invoked when handling [intent] threw.
     *
     * The exception is reported here and then **rethrown** — a `UseCaseProcessor` does not
     * swallow failures the way a `PresentationProcessor`'s intent loop does. Expect the same
     * failure to appear again in the trace from the presentation layer, reported against the
     * `ViewIntent` that called into the domain. Those are two different facts, both worth
     * recording: what failed, and what was interrupted by it.
     */
    public fun onError(throwable: Throwable, intent: I, context: TraceContext?) {}
}
