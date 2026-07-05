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

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.KideInterceptor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * A [KideInterceptor] that records the full causal MVI trace of a processor into an
 * in-memory ring buffer: every intent, the action it was mapped to, every state change
 * (with the previous state for diffing), every side effect, and every error.
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
 * The recorder is thread-safe (lock-free CAS) and never throws from its callbacks.
 *
 * @param capacity Maximum number of retained events; older events are evicted first.
 */
@OptIn(ExperimentalAtomicApi::class)
public class FlightRecorder<I : ViewIntent, S : ViewState, E : SideEffect>(
    public val capacity: Int = DEFAULT_CAPACITY,
) : KideInterceptor<I, S, E> {

    private val nextSeq = AtomicLong(0L)
    private val eventsRef = AtomicReference<List<TraceEvent>>(emptyList())

    /**
     * A snapshot of the recorded events, oldest first.
     */
    public val events: List<TraceEvent> get() = eventsRef.load()

    /**
     * Clears all recorded events.
     */
    public fun clear() {
        eventsRef.store(emptyList())
    }

    /**
     * Returns the recorded trace (optionally only the most recent [limit] events) encoded
     * as a JSON array — suitable for attaching to bug reports or serving to agent tooling.
     */
    public fun toJson(limit: Int = Int.MAX_VALUE): String {
        val snapshot = events
        val tail = if (snapshot.size > limit) snapshot.subList(snapshot.size - limit, snapshot.size) else snapshot
        return Json.encodeToString(ListSerializer(TraceEvent.serializer()), tail)
    }

    override fun onIntent(intent: I) {
        record(TraceEventType.Intent, intent.toString(), intent::class.qualifiedName)
    }

    override fun onActionMapped(intent: I, action: Action<S, E>?) {
        record(
            type = TraceEventType.ActionMapped,
            payload = action?.toString() ?: "null (no-op)",
            payloadClass = action?.let { it::class.qualifiedName },
        )
    }

    override fun onActionExecuting(action: Action<S, E>) {
        record(TraceEventType.ActionExecuting, action.toString(), action::class.qualifiedName)
    }

    override fun onStateChanged(oldState: S, newState: S) {
        record(
            type = TraceEventType.StateChanged,
            payload = newState.toString(),
            payloadClass = newState::class.qualifiedName,
            previousState = oldState.toString(),
        )
    }

    override fun onSideEffect(sideEffect: E) {
        record(TraceEventType.SideEffect, sideEffect.toString(), sideEffect::class.qualifiedName)
    }

    override fun onError(throwable: Throwable, intent: I) {
        record(
            type = TraceEventType.Error,
            payload = "${throwable::class.simpleName}: ${throwable.message} (while processing: $intent)",
            payloadClass = throwable::class.qualifiedName,
        )
    }

    private fun record(
        type: TraceEventType,
        payload: String,
        payloadClass: String? = null,
        previousState: String? = null,
    ) {
        val event = TraceEvent(
            seq = nextSeq.fetchAndIncrement(),
            timestamp = getEpochMillis(),
            type = type,
            payload = payload,
            payloadClass = payloadClass,
            previousState = previousState,
        )
        while (true) {
            val current = eventsRef.load()
            val appended = current + event
            val trimmed = if (appended.size > capacity) {
                appended.subList(appended.size - capacity, appended.size).toList()
            } else {
                appended
            }
            if (eventsRef.compareAndSet(current, trimmed)) return
        }
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 500
    }
}
