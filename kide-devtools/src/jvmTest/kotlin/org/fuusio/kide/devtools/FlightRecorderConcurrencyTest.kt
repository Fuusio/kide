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

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState

/*
 * Ordering specification for FlightRecorder under concurrent recording.
 *
 * A trace is only useful to an agent if list order matches causal order. Sequence numbers are
 * allocated before the compare-and-set race that inserts an event, so a thread holding a higher
 * number can win the race and insert first; appending blindly would leave the buffer out of
 * order. That would corrupt two things at once — the order TraceTestGenerator replays events in,
 * and capacity trimming, which drops from the front and would therefore evict a newer event
 * while keeping an older one.
 *
 * These tests drive the recorder's interceptor callbacks directly rather than through a
 * processor: that maximises contention on the buffer, which is what is under test here.
 */

// ── Test fixtures ──────────────────────────────────────────────────────────────

private data class RaceState(val value: Int = 0) : ViewState

private data class RaceIntent(val id: Int) : ViewIntent

private sealed interface RaceEffect : SideEffect

private const val THREADS = 8
private const val PER_THREAD = 200
private const val TOTAL = THREADS * PER_THREAD

/** Records [PER_THREAD] events from each of [THREADS] coroutines on the default pool. */
private suspend fun hammer(recorder: FlightRecorder<RaceIntent, RaceState, RaceEffect>) {
    coroutineScope {
        repeat(THREADS) { thread ->
            launch(Dispatchers.Default) {
                repeat(PER_THREAD) { index ->
                    recorder.onIntent(RaceIntent(thread * PER_THREAD + index))
                }
            }
        }
    }
}

// ── Tests ──────────────────────────────────────────────────────────────────────

class FlightRecorderConcurrencyTest : DescribeSpec({

    describe("FlightRecorder recorded concurrently from many threads") {

        it("keeps the buffer ordered by sequence number") {
            val recorder = FlightRecorder<RaceIntent, RaceState, RaceEffect>()

            hammer(recorder)

            val seqs = recorder.events.map { it.seq }
            seqs shouldBe seqs.sorted()
            seqs.distinct().size shouldBe seqs.size
        }

        it("records every event exactly once when the buffer is large enough") {
            val recorder = FlightRecorder<RaceIntent, RaceState, RaceEffect>(capacity = TOTAL * 2)

            hammer(recorder)

            recorder.events.map { it.seq } shouldBe (0L until TOTAL.toLong()).toList()
        }

        it("retains exactly the most recent events once capacity is exceeded") {
            val capacity = 500
            val recorder = FlightRecorder<RaceIntent, RaceState, RaceEffect>(capacity = capacity)

            hammer(recorder)

            recorder.events.size shouldBe capacity
            // Eviction drops from the front, so this only holds if the buffer stayed ordered:
            // an out-of-order buffer evicts whatever happens to sit at the front instead.
            recorder.events.map { it.seq } shouldBe
                ((TOTAL - capacity).toLong() until TOTAL.toLong()).toList()
        }

        it("exports the most recent events in order, newest last") {
            val recorder = FlightRecorder<RaceIntent, RaceState, RaceEffect>()

            hammer(recorder)
            val json = recorder.toJson(limit = 3)

            Regex("\"seq\":").findAll(json).count() shouldBe 3
            json shouldContain "\"seq\":${TOTAL - 1},"
            json shouldNotContain "\"seq\":0,"
        }

        it("assigns every event a distinct sequence number") {
            val recorder = FlightRecorder<RaceIntent, RaceState, RaceEffect>(capacity = TOTAL * 2)

            hammer(recorder)

            recorder.events.map { it.seq }.toSet().size shouldBe TOTAL
        }
    }
})
