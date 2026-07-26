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

import kotlinx.serialization.Serializable

/**
 * The kind of MVI lifecycle event captured in a [TraceEvent].
 */
@Serializable
public enum class TraceEventType {
    Intent,
    ActionMapped,
    ActionExecuting,
    StateChanged,
    SideEffect,
    Error,
}

/**
 * The architectural layer a [TraceEvent] came from.
 *
 * Kept orthogonal to [TraceEventType] on purpose: an intent is an intent whichever layer
 * dispatched it, so the *kind* of event and the *layer* it happened in are two independent
 * facts. Adding `UseCaseIntent` and `UseCaseStateChanged` to [TraceEventType] instead would
 * have meant a parallel set of constants — and a third one the moment domain errors needed
 * distinguishing — while making it harder to ask "show me everything about this interaction,
 * both layers".
 */
@Serializable
public enum class TraceEventSource {
    /** A `PresentationProcessor`: view intents, actions, view-state changes, side effects. */
    Presentation,

    /** A `UseCaseProcessor`: domain intents and domain state changes. */
    Domain,
}

/**
 * One causally ordered entry in a [FlightRecorder] trace.
 *
 * Events carry `toString()` renderings rather than full serialized objects so that *any*
 * intent, action, state, or effect can be recorded without requiring serializers. The
 * [payloadClass] preserves the fully qualified class name, which agent tooling uses to
 * reconstruct intents for injection (see the `kide_dispatch_intent` MCP tool).
 *
 * @property seq Monotonic sequence number, unique within one recorder.
 * @property timestamp Epoch milliseconds at capture time.
 * @property type The lifecycle event kind.
 * @property payload Rendering of the event subject (intent, action, new state, effect, error).
 * @property payloadClass Fully qualified class name of the event subject, when available.
 * @property previousState For [TraceEventType.StateChanged]: rendering of the state before
 * the change, enabling state diffing.
 * @property correlationId Groups every event produced while processing one intent — see
 * `TraceContext`. `null` when the event had no originating intent, which is legitimate: work
 * started at application launch, or a flow emitting on its own. Unique only within one
 * processor, so consumers key on the processor together with the id.
 * @property source Which layer produced the event. Presentation and domain events share one
 * buffer and one sequence, so a trace reads as a single causal stream; this is how a consumer
 * tells them apart within it.
 */
@Serializable
public data class TraceEvent(
    val seq: Long,
    val timestamp: Long,
    val type: TraceEventType,
    val payload: String,
    val payloadClass: String? = null,
    val previousState: String? = null,
    val correlationId: Long? = null,
    val source: TraceEventSource = TraceEventSource.Presentation,
)
