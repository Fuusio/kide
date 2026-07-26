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

package org.fuusio.kide.domain.usecase.devtools

import org.fuusio.kide.devtools.TraceBuffer
import org.fuusio.kide.devtools.TraceEventSource
import org.fuusio.kide.devtools.TraceEventType
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.domain.usecase.UseCaseIntent
import org.fuusio.kide.domain.usecase.UseCaseInterceptor
import org.fuusio.kide.presentation.TraceContext

/**
 * A [UseCaseInterceptor] that records a use case's intents, applied state changes and failures
 * into a [TraceBuffer] — the domain-layer counterpart of `FlightRecorder`.
 *
 * Pass it the *same* buffer as the screen's `FlightRecorder` and the two layers produce a
 * single causally ordered trace, with each event tagged [TraceEventSource.Domain] or
 * [TraceEventSource.Presentation] and grouped by the correlation id of the `ViewIntent` that
 * caused it:
 *
 * ```kotlin
 * val buffer = TraceBuffer()
 * val savedProjects = SavedProjectsProcessor(repo, listOf(UseCaseFlightRecorder(buffer)))
 * val processor = SearchProcessor(savedProjects, interceptors = listOf(FlightRecorder(buffer)))
 * KideDebug.attach("search", processor, recorder)
 * ```
 *
 * Without this, a recorded session shows the UI half of an interaction and stops where the real
 * work begins — a load starting and never visibly finishing.
 *
 * ### A shared use case appears in one trace
 * Use-case processors are usually singletons shared by several screens, but a recorder writes
 * to the buffer it was constructed with, so a shared processor's events land in one screen's
 * trace. Nothing is *misattributed* — the correlation id still identifies which interaction
 * drove each write — but events caused from another screen will be missing from that screen's
 * trace. Give a use case its own recorder per buffer if that matters.
 *
 * @param buffer The event log to write into. Share it with the presentation layer's recorder.
 */
public class UseCaseFlightRecorder<S : State, I : UseCaseIntent<S>>(
    public val buffer: TraceBuffer,
) : UseCaseInterceptor<S, I> {

    override fun onIntent(intent: I, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.Intent,
            payload = intent.toString(),
            payloadClass = intent::class.qualifiedName,
            correlationId = context?.correlationId,
            source = TraceEventSource.Domain,
        )
    }

    override fun onStateChanged(oldState: S, newState: S, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.StateChanged,
            payload = newState.toString(),
            payloadClass = newState::class.qualifiedName,
            previousState = oldState.toString(),
            correlationId = context?.correlationId,
            source = TraceEventSource.Domain,
        )
    }

    override fun onError(throwable: Throwable, intent: I, context: TraceContext?) {
        buffer.record(
            type = TraceEventType.Error,
            payload = "${throwable::class.simpleName}: ${throwable.message} (while processing: $intent)",
            payloadClass = throwable::class.qualifiedName,
            correlationId = context?.correlationId,
            source = TraceEventSource.Domain,
        )
    }
}
