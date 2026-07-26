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
package org.fuusio.kide.devtools

import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.KideInterceptor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.TraceContext
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState

/**
 * A [KideInterceptor] that records the full causal MVI trace of a processor into a
 * [TraceBuffer]: every intent, the action it was mapped to, every state change (with the
 * previous state for diffing), every side effect, and every error.
 *
 * Where [KideDevToolsInterceptor] *streams* events out to a console, the flight recorder
 * keeps a *queryable history* inside the app. It is the data source for the Kide agent
 * port ([attach][KideDebug.attach] + `KideMcpServer`), for attaching traces to bug
 * reports ([toJson]), and for turning a recorded session into a regression-test scaffold
 * ([TraceTestGenerator]).
 *
 * Because Kide processes intents losslessly and reduces synchronous actions in dispatch
 * order, a recorded trace is a *sound* account of what happened — not a sampled
 * approximation.
 *
 * The recorder is thread-safe and never throws from its callbacks; ordering and capacity are
 * the [buffer]'s responsibility. Pass an existing [TraceBuffer] to have several recorders
 * write into one trace — which is how a domain-layer recorder and this one end up in a single
 * causally ordered stream rather than two a reader has to merge.
 *
 * @param buffer The event log this recorder writes to. A fresh one by default.
 */
public class FlightRecorder<I : ViewIntent, S : ViewState, E : SideEffect>(
    public val buffer: TraceBuffer = TraceBuffer(),
) : KideInterceptor<I, S, E> {

    /** Creates a recorder with a buffer of its own, retaining at most [capacity] events. */
    public constructor(capacity: Int) : this(TraceBuffer(capacity))

    /** Maximum number of events retained by this recorder's [buffer]. */
    public val capacity: Int get() = buffer.capacity

    /**
     * A snapshot of the recorded events, oldest first. See [TraceBuffer.events].
     */
    public val events: List<TraceEvent> get() = buffer.events

    /**
     * Clears all recorded events.
     */
    public fun clear() {
        buffer.clear()
    }

    /**
     * Returns the recorded trace (optionally only the most recent [limit] events) encoded
     * as a JSON array — suitable for attaching to bug reports or serving to agent tooling.
     */
    public fun toJson(limit: Int = Int.MAX_VALUE): String = buffer.toJson(limit)

    override fun onIntent(intent: I, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.Intent,
            payload = intent.toString(),
            payloadClass = intent::class.qualifiedName,
            correlationId = context?.correlationId,
        )
    }

    override fun onActionMapped(intent: I, action: Action<S, E>?, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.ActionMapped,
            payload = action?.toString() ?: "null (no-op)",
            payloadClass = action?.let { it::class.qualifiedName },
            correlationId = context?.correlationId,
        )
    }

    override fun onActionExecuting(action: Action<S, E>, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.ActionExecuting,
            payload = action.toString(),
            payloadClass = action::class.qualifiedName,
            correlationId = context?.correlationId,
        )
    }

    override fun onStateChanged(oldState: S, newState: S, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.StateChanged,
            payload = newState.toString(),
            payloadClass = newState::class.qualifiedName,
            previousState = oldState.toString(),
            correlationId = context?.correlationId,
        )
    }

    override fun onSideEffect(sideEffect: E, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.SideEffect,
            payload = sideEffect.toString(),
            payloadClass = sideEffect::class.qualifiedName,
            correlationId = context?.correlationId,
        )
    }

    override fun onError(throwable: Throwable, intent: I, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.Error,
            payload = "${throwable::class.simpleName}: ${throwable.message} (while processing: $intent)",
            payloadClass = throwable::class.qualifiedName,
            correlationId = context?.correlationId,
        )
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = TraceBuffer.DEFAULT_CAPACITY
    }
}
