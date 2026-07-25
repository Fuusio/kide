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
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * An ordered, capacity-bounded log of [TraceEvent]s: the storage behind a recorded session.
 *
 * A buffer is deliberately separate from the interceptors that write to it, so that **several
 * recorders can share one buffer** and produce a single causally ordered stream. That is what
 * lets a presentation-layer [FlightRecorder] and a domain-layer recorder appear in one trace
 * rather than two that a reader has to merge by timestamp.
 *
 * The buffer is thread-safe (lock-free CAS), never throws from [record], and keeps itself
 * sorted by [TraceEvent.seq] — see [insertOrdered] for why that is not the same as appending.
 *
 * @param capacity Maximum number of retained events; the oldest are evicted first.
 */
@OptIn(ExperimentalAtomicApi::class)
public class TraceBuffer(
    public val capacity: Int = DEFAULT_CAPACITY,
) {
    private val nextSeq = AtomicLong(0L)
    private val eventsRef = AtomicReference<List<TraceEvent>>(emptyList())

    /**
     * A snapshot of the recorded events, oldest first.
     *
     * Always ordered by [TraceEvent.seq], including when events were recorded concurrently
     * from several threads — list position and causal order never disagree. Once [capacity]
     * is reached the snapshot holds exactly the most recent [capacity] events.
     */
    public val events: List<TraceEvent> get() = eventsRef.load()

    /**
     * Clears all recorded events. Sequence numbering continues from where it left off.
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

    /**
     * Appends an event, assigning it the next sequence number.
     */
    public fun record(
        type: TraceEventType,
        payload: String,
        payloadClass: String? = null,
        previousState: String? = null,
        correlationId: Long? = null,
        source: TraceEventSource = TraceEventSource.Presentation,
    ) {
        val event = TraceEvent(
            seq = nextSeq.fetchAndIncrement(),
            timestamp = getEpochMillis(),
            type = type,
            payload = payload,
            payloadClass = payloadClass,
            previousState = previousState,
            correlationId = correlationId,
            source = source,
        )
        while (true) {
            val current = eventsRef.load()
            val inserted = current.insertOrdered(event)
            val trimmed = if (inserted.size > capacity) {
                inserted.subList(inserted.size - capacity, inserted.size).toList()
            } else {
                inserted
            }
            if (eventsRef.compareAndSet(current, trimmed)) return
        }
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 500
    }
}

/**
 * Returns this buffer with [event] inserted so that the result remains ordered by
 * [TraceEvent.seq].
 *
 * Sequence numbers are allocated *before* the insertion race is run — deliberately, so that
 * they stay gap-free — which means a thread holding a higher number can still win the race and
 * insert first. Appending blindly would then leave the buffer out of order, and that matters
 * twice over: [TraceBuffer.events] is documented as oldest-first and is consumed in list
 * order by the agent port and by [TraceTestGenerator], and capacity trimming drops from the
 * front, so an out-of-order buffer would evict a *newer* event while keeping an older one.
 *
 * The overwhelmingly common case is an in-order append, which costs a single comparison. The
 * backwards scan only runs when a race actually reordered two events, and then only walks the
 * few positions they were displaced by.
 */
private fun List<TraceEvent>.insertOrdered(event: TraceEvent): List<TraceEvent> {
    if (isEmpty() || last().seq < event.seq) return this + event

    var index = size
    while (index > 0 && this[index - 1].seq > event.seq) {
        index--
    }
    return buildList(size + 1) {
        addAll(this@insertOrdered.subList(0, index))
        add(event)
        addAll(this@insertOrdered.subList(index, this@insertOrdered.size))
    }
}
