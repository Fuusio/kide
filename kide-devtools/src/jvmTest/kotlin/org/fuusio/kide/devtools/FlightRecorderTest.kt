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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
import org.fuusio.kide.presentation.sideEffect

private data class CounterState(val value: Int = 0) : ViewState

private sealed interface CounterIntent : ViewIntent {
    data object Increment : CounterIntent
    data object Boom : CounterIntent
    data object Ping : CounterIntent
}

private sealed interface CounterEffect : SideEffect {
    data object Pong : CounterEffect
}

/** An intent from an unrelated processor, used to check the handle's type boundary. */
private data object UnrelatedIntent : ViewIntent

private class CounterProcessor(
    recorder: FlightRecorder<CounterIntent, CounterState, CounterEffect>,
) : PresentationProcessor<CounterIntent, CounterState, CounterEffect>(
    CounterState(),
    interceptors = listOf(recorder),
) {
    override suspend fun map(intent: CounterIntent): Action<CounterState, CounterEffect>? =
        when (intent) {
            CounterIntent.Increment -> reduce { copy(value = value + 1) }
            CounterIntent.Boom -> throw IllegalStateException("boom")
            CounterIntent.Ping -> sideEffect { CounterEffect.Pong }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FlightRecorderTest : DescribeSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec { Dispatchers.setMain(testDispatcher) }
    afterSpec { Dispatchers.resetMain() }

    describe("FlightRecorder") {

        it("records the causal chain: intent, mapped action, state change") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)

            processor.dispatch(CounterIntent.Increment)

            recorder.events.map { it.type } shouldContainInOrder listOf(
                TraceEventType.Intent,
                TraceEventType.ActionMapped,
                TraceEventType.StateChanged,
            )
            val stateChange = recorder.events.last { it.type == TraceEventType.StateChanged }
            stateChange.previousState shouldBe "CounterState(value=0)"
            stateChange.payload shouldBe "CounterState(value=1)"
        }

        it("records side effects and errors") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)

            processor.dispatch(CounterIntent.Ping)
            processor.dispatch(CounterIntent.Boom)

            recorder.events.count { it.type == TraceEventType.SideEffect } shouldBe 1
            val error = recorder.events.single { it.type == TraceEventType.Error }
            error.payload shouldContain "boom"
        }

        it("evicts oldest events beyond capacity") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>(capacity = 5)
            val processor = CounterProcessor(recorder)

            repeat(10) { processor.dispatch(CounterIntent.Increment) }

            recorder.events.size shouldBe 5
            // Sequence numbers keep growing even after eviction:
            // 10 intents x 4 events (Intent, ActionMapped, ActionExecuting, StateChanged) - 1.
            recorder.events.last().seq shouldBe 39L
        }

        it("exports trace as JSON honoring the limit") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            processor.dispatch(CounterIntent.Increment)

            recorder.toJson(limit = 1) shouldContain "StateChanged"
            recorder.toJson() shouldContain "Intent"
        }

        it("clear empties the buffer") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            processor.dispatch(CounterIntent.Increment)

            recorder.clear()

            recorder.events.size shouldBe 0
        }
    }

    describe("KideDebug registry") {

        it("attach exposes state and dispatch through the handle") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            val handle = KideDebug.attach("counter", processor, recorder)

            handle.currentState() shouldBe "CounterState(value=0)"
            handle.dispatch(CounterIntent.Increment)
            handle.currentState() shouldBe "CounterState(value=1)"

            KideDebug.handle("counter") shouldBe handle
            KideDebug.detach("counter")
            KideDebug.handle("counter") shouldBe null
        }

        it("reports whether the attached processor is still open") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            val handle = KideDebug.attach("counter_closed", processor, recorder)

            handle.isClosed shouldBe false
            processor.close()
            handle.isClosed shouldBe true

            KideDebug.detach("counter_closed")
        }

        // A closed processor discards intents silently, so injecting into one used to look
        // like a successful dispatch that changed nothing at all — which sends whoever is
        // debugging, usually an agent, hunting for an application bug that does not exist.
        it("refuses to dispatch into a closed processor") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            val handle = KideDebug.attach("counter_dead", processor, recorder)
            processor.close()

            val failure = shouldThrow<IllegalStateException> {
                handle.dispatch(CounterIntent.Increment)
            }
            failure.message shouldContain "counter_dead"
            failure.message shouldContain "closed"
            handle.currentState() shouldBe "CounterState(value=0)"

            KideDebug.detach("counter_dead")
        }

        it("reports the intent type the processor accepts") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            val handle = KideDebug.attach("counter_type", processor, recorder)

            handle.intentClassName shouldBe "org.fuusio.kide.devtools.CounterIntent"

            KideDebug.detach("counter_type")
        }

        // Generics are erased, so the cast inside the handle compiles to nothing: a wrongly
        // typed intent used to sail into the intent channel and fail — or match no branch and
        // do nothing at all — deep inside map(), far from the call that caused it.
        it("refuses an intent that is not of the processor's intent type") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            val handle = KideDebug.attach("counter_wrong_type", processor, recorder)

            val failure = shouldThrow<IllegalArgumentException> {
                handle.dispatch(UnrelatedIntent)
            }
            failure.message shouldContain "CounterIntent"
            failure.message shouldContain "UnrelatedIntent"
            handle.currentState() shouldBe "CounterState(value=0)"
            recorder.events.shouldBeEmpty()

            KideDebug.detach("counter_wrong_type")
        }

        it("still exposes the trace recorded before the processor was closed") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            val handle = KideDebug.attach("counter_trace", processor, recorder)

            processor.dispatch(CounterIntent.Increment)
            processor.close()

            handle.recorder.events.count { it.type == TraceEventType.StateChanged } shouldBe 1

            KideDebug.detach("counter_trace")
        }
    }

    describe("TraceTestGenerator") {

        it("generates a replay scaffold containing intents and expected states") {
            val recorder = FlightRecorder<CounterIntent, CounterState, CounterEffect>()
            val processor = CounterProcessor(recorder)
            val handle = KideDebug.attach("counter_gen", processor, recorder)

            processor.dispatch(CounterIntent.Increment)
            processor.dispatch(CounterIntent.Increment)

            val source = TraceTestGenerator.generate(handle)

            source shouldContain "CounterProcessorReplayTest"
            source shouldContain "recorded intent: Increment"
            source shouldContain "expected state:  CounterState(value=2)"
            source shouldContain "shouldBe"
            KideDebug.detach("counter_gen")
        }
    }
})
