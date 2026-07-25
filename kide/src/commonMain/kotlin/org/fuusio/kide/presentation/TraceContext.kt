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

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Identifies the [ViewIntent] whose processing is currently in flight, so that work performed
 * further down the stack can be attributed back to it.
 *
 * A [PresentationProcessor] creates one per intent it processes and installs it into the
 * coroutine context for the whole of that intent's processing — the mapping, the inline
 * reductions, and the coroutine launched for an [AsyncAction]. Because it travels in the
 * coroutine context, anything the intent's work goes on to do carries it automatically,
 * including across a `withContext(Dispatchers.IO)`. Nothing has to thread it by hand, so no
 * call site can forget to.
 *
 * Every [KideInterceptor] callback receives it, which is what allows a recorded trace to answer
 * *"which tap caused this?"* rather than only *"these things happened near each other"* — the
 * distinction that matters exactly when a trace is being read, because concurrency is usually
 * why it is being read.
 *
 * It is passed to callbacks as a *type* rather than a bare id deliberately: recording spans for
 * repository calls, or a dispatch depth, then adds a property here instead of changing every
 * interceptor signature again.
 *
 * @property correlationId Distinguishes intents within one processor. Not globally unique —
 * two processors will both emit id 0 — so consumers key on the processor together with the id.
 */
public data class TraceContext(
    val correlationId: Long,
) : AbstractCoroutineContextElement(Key) {

    public companion object Key : CoroutineContext.Key<TraceContext>
}

/**
 * The [TraceContext] of the intent currently being processed, or `null` when the caller is not
 * running inside one — application startup, or a coroutine launched outside a processor.
 */
public suspend fun currentTraceContext(): TraceContext? = coroutineContext[TraceContext]
