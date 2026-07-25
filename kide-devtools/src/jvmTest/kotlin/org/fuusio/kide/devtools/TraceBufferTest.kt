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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import org.fuusio.kide.presentation.reduce

/*
 * TraceBuffer exists so that several recorders can write into one causally ordered stream —
 * a presentation-layer FlightRecorder and a domain-layer recorder appearing in a single trace
 * rather than two that a reader has to merge by timestamp. The buffer's ordering, eviction and
 * concurrency behaviour is already covered through FlightRecorder by FlightRecorderTest and
 * FlightRecorderConcurrencyTest; what is new, and tested here, is the sharing.
 */

// ── Test fixtures ──────────────────────────────────────────────────────────────

private data class AlphaState(val value: Int = 0) : ViewState
private data object AlphaIntent : ViewIntent
private sealed interface AlphaEffect : SideEffect

private data class BetaState(val value: Int = 0) : ViewState
private data object BetaIntent : ViewIntent
private sealed interface BetaEffect : SideEffect

private class AlphaProcessor(
    recorder: FlightRecorder<AlphaIntent, AlphaState, AlphaEffect>,
) : PresentationProcessor<AlphaIntent, AlphaState, AlphaEffect>(
    AlphaState(),
    interceptors = listOf(recorder),
) {
    override suspend fun map(intent: AlphaIntent): Action<AlphaState, AlphaEffect> =
        reduce { copy(value = value + 1) }
}

private class BetaProcessor(
    recorder: FlightRecorder<BetaIntent, BetaState, BetaEffect>,
) : PresentationProcessor<BetaIntent, BetaState, BetaEffect>(
    BetaState(),
    interceptors = listOf(recorder),
) {
    override suspend fun map(intent: BetaIntent): Action<BetaState, BetaEffect> =
        reduce { copy(value = value + 1) }
}

// ── Tests ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class TraceBufferTest : DescribeSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec { Dispatchers.setMain(testDispatcher) }
    afterSpec { Dispatchers.resetMain() }

    describe("TraceBuffer") {

        it("defaults to the documented capacity") {
            TraceBuffer().capacity shouldBe 500
            FlightRecorder<AlphaIntent, AlphaState, AlphaEffect>().capacity shouldBe 500
        }

        it("gives a recorder constructed with a capacity a buffer of that size") {
            FlightRecorder<AlphaIntent, AlphaState, AlphaEffect>(capacity = 7).capacity shouldBe 7
        }

        describe("shared by several recorders") {

            it("interleaves their events into one stream in dispatch order") {
                val buffer = TraceBuffer()
                val alpha = AlphaProcessor(FlightRecorder(buffer))
                val beta = BetaProcessor(FlightRecorder(buffer))

                alpha.dispatch(AlphaIntent)
                beta.dispatch(BetaIntent)
                alpha.dispatch(AlphaIntent)

                // Intent, ActionMapped, ActionExecuting, StateChanged — three times over.
                buffer.events.filter { it.type == TraceEventType.Intent }
                    .map { it.payload } shouldContainExactly
                    listOf("AlphaIntent", "BetaIntent", "AlphaIntent")
            }

            it("assigns one continuous sequence across both") {
                val buffer = TraceBuffer()
                val alpha = AlphaProcessor(FlightRecorder(buffer))
                val beta = BetaProcessor(FlightRecorder(buffer))

                alpha.dispatch(AlphaIntent)
                beta.dispatch(BetaIntent)

                val seqs = buffer.events.map { it.seq }
                seqs shouldContainExactly (0L until seqs.size.toLong()).toList()
            }

            it("records the state of whichever processor changed") {
                val buffer = TraceBuffer()
                val alpha = AlphaProcessor(FlightRecorder(buffer))
                val beta = BetaProcessor(FlightRecorder(buffer))

                alpha.dispatch(AlphaIntent)
                beta.dispatch(BetaIntent)

                buffer.events.filter { it.type == TraceEventType.StateChanged }
                    .map { it.payload } shouldContainExactly
                    listOf("AlphaState(value=1)", "BetaState(value=1)")
            }

            it("is visible through either recorder's view of it") {
                val buffer = TraceBuffer()
                val alphaRecorder = FlightRecorder<AlphaIntent, AlphaState, AlphaEffect>(buffer)
                val betaRecorder = FlightRecorder<BetaIntent, BetaState, BetaEffect>(buffer)
                AlphaProcessor(alphaRecorder).dispatch(AlphaIntent)

                alphaRecorder.events shouldContainExactly buffer.events
                betaRecorder.events shouldContainExactly buffer.events
            }

            it("shares one capacity budget rather than one per recorder") {
                val buffer = TraceBuffer(capacity = 5)
                val alpha = AlphaProcessor(FlightRecorder(buffer))
                val beta = BetaProcessor(FlightRecorder(buffer))

                repeat(3) { alpha.dispatch(AlphaIntent) }
                repeat(3) { beta.dispatch(BetaIntent) }

                buffer.events.size shouldBe 5
                // 6 intents x 4 events = 24; the last five survive.
                buffer.events.map { it.seq } shouldContainExactly (19L..23L).toList()
            }

            it("is cleared for every recorder at once") {
                val buffer = TraceBuffer()
                val alphaRecorder = FlightRecorder<AlphaIntent, AlphaState, AlphaEffect>(buffer)
                val betaRecorder = FlightRecorder<BetaIntent, BetaState, BetaEffect>(buffer)
                AlphaProcessor(alphaRecorder).dispatch(AlphaIntent)

                betaRecorder.clear()

                alphaRecorder.events.size shouldBe 0
                buffer.events.size shouldBe 0
            }
        }
    }
})
