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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/*
 * Concurrency specification for the documented execution guarantees.
 *
 * Every other processor spec installs `UnconfinedTestDispatcher` as Main, which makes dispatch
 * synchronous and the tests deterministic — the right default for testing behaviour, but it
 * means the intent loop, all async actions and all reductions share a single thread, so no
 * concurrency guarantee can actually fail there.
 *
 * These tests set up the configuration the class documentation calls out as supported *and*
 * concurrent: the processor's own scope is confined to a single thread, as the threading
 * contract requires, while async actions shift to `Dispatchers.Default` before reducing — an
 * ordinary `withContext(Dispatchers.IO) { ... }` use case, and the reason `reduceState` has to
 * be safe against genuine contention even though the intent loop is not. Intents are also
 * dispatched from several threads at once, which the contract permits.
 *
 * They are probabilistic in their ability to *provoke* contention, but their assertions are
 * exact: each one holds for every legal interleaving, so a failure is a real defect and never a
 * flake. Ordering is asserted only where a single coroutine produces the events; where events
 * originate on many threads, only the multiset is asserted.
 */

// ── Test fixtures ──────────────────────────────────────────────────────────────

private sealed interface ConcurrentIntent : ViewIntent {
    data object SyncIncrement : ConcurrentIntent
    data object AsyncIncrement : ConcurrentIntent
}

/** No side effects are exercised here; the type exists only to satisfy the type parameter. */
private sealed interface ConcurrentSideEffect : SideEffect

private class ConcurrentProcessor(
    scope: CoroutineScope,
    interceptor: KideInterceptor<ConcurrentIntent, TestViewState, ConcurrentSideEffect>,
) : PresentationProcessor<ConcurrentIntent, TestViewState, ConcurrentSideEffect>(
    TestViewState(),
    processorScope = scope,
    interceptors = listOf(interceptor),
) {
    override suspend fun map(intent: ConcurrentIntent): Action<TestViewState, ConcurrentSideEffect> =
        when (intent) {
            ConcurrentIntent.SyncIncrement -> reduce { copy(value = value + 1) }
            // No cancellation key, so every dispatch gets its own coroutine and they all run to
            // completion; the shift to Dispatchers.Default then puts the reductions on genuinely
            // different threads, racing one another the way a real use case doing IO would.
            ConcurrentIntent.AsyncIncrement -> useCase {
                withContext(Dispatchers.Default) {
                    reduce { copy(value = value + 1) }
                }
            }
        }
}

private class ThreadSafeRecorder : KideInterceptor<ConcurrentIntent, TestViewState, ConcurrentSideEffect> {

    val intents = CopyOnWriteArrayList<ConcurrentIntent>()
    val transitions = CopyOnWriteArrayList<Pair<Int, Int>>()

    override fun onIntent(intent: ConcurrentIntent) {
        intents += intent
    }

    override fun onStateChanged(oldState: TestViewState, newState: TestViewState) {
        transitions += oldState.value to newState.value
    }
}

private const val TIMEOUT_MILLIS = 30_000L

/**
 * How long to wait after the expected work has landed before asserting that *nothing else*
 * arrived. Assertions of the form "exactly N transitions" need a quiet period, because a
 * regression produces extra notifications rather than missing ones.
 */
private const val SETTLE_MILLIS = 150L

private class Fixture(
    val processor: ConcurrentProcessor,
    val recorder: ThreadSafeRecorder,
) {
    /**
     * Suspends until the processor has reached [target] *and* the recorder has been told about
     * [target] transitions, then waits out a short quiet period.
     *
     * Both waits are necessary: `reduceState` publishes the new state before notifying
     * interceptors, so observing the final value does not imply the final notification has been
     * recorded. Polling only the state would make the "exactly N" assertions flaky by one.
     */
    suspend fun awaitSettled(target: Int) {
        withTimeout(TIMEOUT_MILLIS) {
            while (processor.state.value < target) {
                delay(1)
            }
            while (recorder.transitions.size < target) {
                delay(1)
            }
        }
        delay(SETTLE_MILLIS)
    }

    fun dispatch(intent: ConcurrentIntent, times: Int) {
        repeat(times) { processor.dispatch(intent) }
    }
}

private suspend fun withProcessor(block: suspend Fixture.() -> Unit) {
    // Confined to one thread, as the processor's threading contract requires. The concurrency
    // under test comes from the async actions shifting off it, not from the loop itself.
    val loopDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + loopDispatcher)
    val recorder = ThreadSafeRecorder()
    val fixture = Fixture(ConcurrentProcessor(scope, recorder), recorder)
    try {
        fixture.block()
    } finally {
        fixture.processor.close()
        loopDispatcher.close()
    }
}

// ── Tests ──────────────────────────────────────────────────────────────────────

class PresentationProcessorConcurrencyTest : DescribeSpec({

    describe("a processor running on a multi-threaded dispatcher") {

        describe("concurrent reductions from async actions") {

            /*
             * These three assertions pin down the post-fix contract from two directions.
             *
             * Before the fix, `AsyncScope.reduce` wrote straight to the state flow without
             * notifying interceptors at all, so `transitions` came back empty and all three
             * failed on the count (K2).
             *
             * Now that async reductions *are* reported, contention on the state flow is real,
             * and these are what would catch a regression to notifying from inside
             * `MutableStateFlow.update` — whose lambda is re-evaluated when it loses a
             * compare-and-set race, reporting transitions that were computed, discarded, and
             * never applied (K1).
             */

            it("reports exactly one transition per applied state change") {
                withProcessor {
                    val count = 300

                    dispatch(ConcurrentIntent.AsyncIncrement, count)
                    awaitSettled(count)

                    processor.state.value shouldBe count
                    // Events originate on many threads, so their recorded order is arbitrary;
                    // the multiset of transitions is not.
                    recorder.transitions.size shouldBe count
                    recorder.transitions.map { (_, new) -> new }.toSet() shouldBe (1..count).toSet()
                }
            }

            it("reports each transition as a step of exactly one from its predecessor") {
                withProcessor {
                    val count = 300

                    dispatch(ConcurrentIntent.AsyncIncrement, count)
                    awaitSettled(count)

                    val malformed = recorder.transitions.filter { (old, new) -> new != old + 1 }
                    malformed shouldBe emptyList()
                }
            }

            it("never reports the same transition twice") {
                withProcessor {
                    val count = 300

                    dispatch(ConcurrentIntent.AsyncIncrement, count)
                    awaitSettled(count)

                    val duplicated = recorder.transitions
                        .groupingBy { it }
                        .eachCount()
                        .filterValues { it > 1 }

                    duplicated shouldBe emptyMap()
                }
            }
        }

        describe("dispatch ordering") {

            it("applies synchronous reductions in dispatch order") {
                withProcessor {
                    val count = 200

                    dispatch(ConcurrentIntent.SyncIncrement, count)
                    awaitSettled(count)

                    // Synchronous reductions all run inline on the single intent-loop coroutine,
                    // so here the recorded order is meaningful and must be exact.
                    recorder.transitions shouldContainExactly (0 until count).map { it to it + 1 }
                }
            }
        }

        describe("lossless dispatch") {

            it("processes every intent when dispatched concurrently from many threads") {
                withProcessor {
                    val threads = 8
                    val perThread = 50
                    val total = threads * perThread

                    coroutineScope {
                        repeat(threads) {
                            launch(Dispatchers.Default) {
                                dispatch(ConcurrentIntent.SyncIncrement, perThread)
                            }
                        }
                    }
                    awaitSettled(total)

                    processor.state.value shouldBe total
                    recorder.intents.size shouldBe total
                    recorder.transitions.size shouldBe total
                }
            }
        }
    }
})
