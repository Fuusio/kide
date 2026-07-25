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

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/*
 * Trace-fidelity specification.
 *
 * `KideInterceptor` is the sole data source for `kide-devtools` — the FlightRecorder, the MCP
 * agent port, and the regression-test generator all read what interceptors are told. The
 * sequence of interceptor callbacks is therefore a public contract, not an implementation
 * detail: an agent diagnosing a live app can only be as correct as this trace is.
 *
 * These tests pin that contract down. They are deterministic and single-threaded; the
 * contention behaviour of the same contract is covered by PresentationProcessorConcurrencyTest.
 */

// ── Test fixtures ──────────────────────────────────────────────────────────────

private sealed interface FidelityIntent : ViewIntent {
    data object SyncIncrement : FidelityIntent
    data object AsyncIncrement : FidelityIntent
    data object AsyncTwoStep : FidelityIntent
    data object ReduceToSameInstance : FidelityIntent
    data object ReduceToEqualCopy : FidelityIntent
    data object SyncThenEffect : FidelityIntent
    data object ThrowInReducer : FidelityIntent
    data object ThrowInAsyncAfterReduce : FidelityIntent
}

private sealed interface FidelitySideEffect : SideEffect {
    data class Snapshot(val value: Int) : FidelitySideEffect
}

/** One recorded interceptor callback, in the order it was received. */
private sealed interface Recorded {
    data class Dispatched(val intent: FidelityIntent) : Recorded
    data class Mapped(val actionType: String?) : Recorded
    data class Executing(val actionType: String) : Recorded
    data class StateChanged(val old: TestViewState, val new: TestViewState) : Recorded
    data class Effect(val effect: FidelitySideEffect) : Recorded
    data class Failed(val message: String?) : Recorded
}

private class RecordingInterceptor : KideInterceptor<FidelityIntent, TestViewState, FidelitySideEffect> {

    val events = mutableListOf<Recorded>()

    /** Set after construction; lets assertions check what the processor exposed at callback time. */
    var processor: PresentationProcessor<FidelityIntent, TestViewState, FidelitySideEffect>? = null

    /** The state the processor reported for each `onStateChanged` call, in order. */
    val stateAtNotification = mutableListOf<TestViewState>()

    val stateChanges: List<Recorded.StateChanged>
        get() = events.filterIsInstance<Recorded.StateChanged>()

    override fun onIntent(intent: FidelityIntent) {
        events += Recorded.Dispatched(intent)
    }

    override fun onActionMapped(intent: FidelityIntent, action: Action<TestViewState, FidelitySideEffect>?) {
        events += Recorded.Mapped(action?.let { it::class.simpleName })
    }

    override fun onActionExecuting(action: Action<TestViewState, FidelitySideEffect>) {
        events += Recorded.Executing(action::class.simpleName ?: "?")
    }

    override fun onStateChanged(oldState: TestViewState, newState: TestViewState) {
        events += Recorded.StateChanged(oldState, newState)
        processor?.let { stateAtNotification += it.state }
    }

    override fun onSideEffect(sideEffect: FidelitySideEffect) {
        events += Recorded.Effect(sideEffect)
    }

    override fun onError(throwable: Throwable, intent: FidelityIntent) {
        events += Recorded.Failed(throwable.message)
    }
}

private class FidelityProcessor(
    interceptor: RecordingInterceptor,
) : PresentationProcessor<FidelityIntent, TestViewState, FidelitySideEffect>(
    TestViewState(),
    interceptors = listOf(interceptor),
) {
    override suspend fun map(intent: FidelityIntent): Action<TestViewState, FidelitySideEffect>? =
        when (intent) {
            FidelityIntent.SyncIncrement -> reduce { copy(value = value + 1) }

            FidelityIntent.AsyncIncrement -> useCase { reduce { copy(value = value + 1) } }

            FidelityIntent.AsyncTwoStep -> useCase {
                reduce { copy(value = value + 1) }
                reduce { copy(value = value + 10) }
            }

            // Returns the receiver itself — no change at all.
            FidelityIntent.ReduceToSameInstance -> reduce<TestViewState> { this }

            // Returns a distinct but `equals` instance — no observable change.
            FidelityIntent.ReduceToEqualCopy -> reduce<TestViewState> { copy(value = value) }

            FidelityIntent.SyncThenEffect -> composite(
                reduce { copy(value = value + 1) },
                sideEffect<TestViewState, FidelitySideEffect> { FidelitySideEffect.Snapshot(value) },
            )

            FidelityIntent.ThrowInReducer ->
                reduce<TestViewState> { throw IllegalStateException("reducer failed") }

            FidelityIntent.ThrowInAsyncAfterReduce -> useCase {
                reduce { copy(value = value + 1) }
                throw IllegalStateException("async failed")
            }
        }
}

private fun processorWithRecorder(): Pair<FidelityProcessor, RecordingInterceptor> {
    val interceptor = RecordingInterceptor()
    val processor = FidelityProcessor(interceptor)
    interceptor.processor = processor
    return processor to interceptor
}

// ── Tests ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class InterceptorFidelityTest : DescribeSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec { Dispatchers.setMain(testDispatcher) }
    afterSpec { Dispatchers.resetMain() }

    describe("interceptor trace fidelity") {

        describe("the full callback sequence") {

            it("records intent, mapping, execution and state change for a synchronous reduction") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.SyncIncrement)

                recorder.events shouldContainExactly listOf(
                    Recorded.Dispatched(FidelityIntent.SyncIncrement),
                    Recorded.Mapped("ReducerAction"),
                    Recorded.Executing("ReducerAction"),
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                )
            }

            it("records the same sequence for an asynchronous reduction") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.AsyncIncrement)

                recorder.events shouldContainExactly listOf(
                    Recorded.Dispatched(FidelityIntent.AsyncIncrement),
                    Recorded.Mapped("AsyncAction"),
                    Recorded.Executing("AsyncAction"),
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                )
            }

            it("orders a side effect after the reduction it was composed with") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.SyncThenEffect)

                recorder.events shouldContainExactly listOf(
                    Recorded.Dispatched(FidelityIntent.SyncThenEffect),
                    Recorded.Mapped("CompositeAction"),
                    Recorded.Executing("CompositeAction"),
                    Recorded.Executing("ReducerAction"),
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                    Recorded.Executing("SideEffectAction"),
                    Recorded.Effect(FidelitySideEffect.Snapshot(1)),
                )
            }
        }

        describe("reductions made from AsyncScope") {

            // Regression: AsyncScope.reduce previously wrote straight to the state flow without
            // notifying interceptors, so every state change produced by an AsyncAction — network
            // results, error handling, the reduce that clears isLoading — was missing from the
            // recorded trace.
            it("are reported to interceptors") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.AsyncIncrement)

                recorder.stateChanges shouldContainExactly listOf(
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                )
            }

            it("are reported once per reduce call within a single async action") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.AsyncTwoStep)

                recorder.stateChanges shouldContainExactly listOf(
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                    Recorded.StateChanged(TestViewState(1), TestViewState(11)),
                )
                processor.state shouldBe TestViewState(11)
            }

            it("are interleaved with synchronous reductions in execution order") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.SyncIncrement)
                processor.dispatch(FidelityIntent.AsyncIncrement)
                processor.dispatch(FidelityIntent.SyncIncrement)

                recorder.stateChanges shouldContainExactly listOf(
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                    Recorded.StateChanged(TestViewState(1), TestViewState(2)),
                    Recorded.StateChanged(TestViewState(2), TestViewState(3)),
                )
            }

            it("reports the reduction that happened before an async action threw") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.ThrowInAsyncAfterReduce)

                recorder.stateChanges shouldContainExactly listOf(
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                )
                recorder.events.last() shouldBe Recorded.Failed("async failed")
            }
        }

        describe("reductions that change nothing") {

            it("are not reported when the reducer returns the receiver itself") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.ReduceToSameInstance)

                recorder.stateChanges.shouldBeEmpty()
            }

            it("are not reported when the reducer returns an equal copy") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.ReduceToEqualCopy)

                recorder.stateChanges.shouldBeEmpty()
            }

            it("still record the intent and the action that ran") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.ReduceToEqualCopy)

                recorder.events shouldContainExactly listOf(
                    Recorded.Dispatched(FidelityIntent.ReduceToEqualCopy),
                    Recorded.Mapped("ReducerAction"),
                    Recorded.Executing("ReducerAction"),
                )
            }

            it("do not suppress a later real change") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.ReduceToEqualCopy)
                processor.dispatch(FidelityIntent.SyncIncrement)

                recorder.stateChanges shouldContainExactly listOf(
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                )
                processor.state shouldBe TestViewState(1)
            }
        }

        describe("notification timing") {

            it("publishes the new state before notifying interceptors") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.SyncIncrement)
                processor.dispatch(FidelityIntent.AsyncIncrement)

                // An interceptor reading processor.state must see the transition it was just
                // told about, not the state before it.
                recorder.stateAtNotification shouldContainExactly listOf(
                    TestViewState(1),
                    TestViewState(2),
                )
            }
        }

        describe("side effects") {

            // The processor reports an effect only once the channel has accepted it. The
            // channel is unbounded and buffers until a collector attaches, so "nobody is
            // listening yet" must still count as delivered — this guards the success path
            // against that check being read as "no collector, no report".
            it("are reported when buffered with no collector attached") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.SyncThenEffect)

                recorder.events.filterIsInstance<Recorded.Effect>() shouldContainExactly listOf(
                    Recorded.Effect(FidelitySideEffect.Snapshot(1)),
                )
            }
        }

        describe("a closed processor") {

            it("ignores dispatched intents") {
                val (processor, _) = processorWithRecorder()
                processor.dispatch(FidelityIntent.SyncIncrement)
                processor.close()

                processor.dispatch(FidelityIntent.SyncIncrement)

                processor.state shouldBe TestViewState(1)
            }

            // Regression: interceptors used to be notified before the intent was queued, so an
            // intent dispatched after close() left an entry in the trace with no mapping, no
            // state change and no effect — indistinguishable from a map() that returned null or
            // from an intent loop that had stalled.
            it("does not report ignored intents to interceptors") {
                val (processor, recorder) = processorWithRecorder()
                processor.close()

                processor.dispatch(FidelityIntent.SyncIncrement)

                recorder.events.shouldBeEmpty()
            }

            it("exposes isClosed") {
                val (processor, _) = processorWithRecorder()

                processor.isClosed shouldBe false
                processor.close()
                processor.isClosed shouldBe true
            }

            it("can be closed more than once") {
                val (processor, _) = processorWithRecorder()

                processor.close()
                processor.close()

                processor.isClosed shouldBe true
            }
        }

        describe("a throwing reducer") {

            it("reports the error and no state change") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.ThrowInReducer)

                recorder.stateChanges.shouldBeEmpty()
                recorder.events.last() shouldBe Recorded.Failed("reducer failed")
                processor.state shouldBe TestViewState(0)
            }

            it("leaves the intent loop able to process the next intent") {
                val (processor, recorder) = processorWithRecorder()

                processor.dispatch(FidelityIntent.ThrowInReducer)
                processor.dispatch(FidelityIntent.SyncIncrement)

                processor.state shouldBe TestViewState(1)
                recorder.stateChanges shouldContainExactly listOf(
                    Recorded.StateChanged(TestViewState(0), TestViewState(1)),
                )
            }
        }
    }
})
