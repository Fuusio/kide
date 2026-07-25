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

package org.fuusio.kide.domain.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.TraceContext
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import org.fuusio.kide.presentation.async

/*
 * Domain-layer tracing.
 *
 * The point of carrying the correlation in the *coroutine* context rather than passing it by
 * hand is that it crosses boundaries nobody has to remember: from the presentation processor's
 * intent loop, into the coroutine an AsyncAction runs in, through a withContext, and on into a
 * use case that knows nothing about the UI. The integration test at the bottom is the one that
 * checks that end to end — everything above it pins down the pieces.
 */

// ── Domain fixtures ────────────────────────────────────────────────────────────

private data class CounterState(val value: Int = 0) : State

private sealed interface CounterIntent : UseCaseIntent<CounterState> {
    data object Increment : CounterIntent
    data object NoOpReduce : CounterIntent
    data class Replace(val value: Int) : CounterIntent
    data object Boom : CounterIntent
}

private sealed interface DomainEvent {
    data class Intent(val intent: CounterIntent, val correlationId: Long?) : DomainEvent
    data class StateChanged(val old: Int, val new: Int, val correlationId: Long?) : DomainEvent
    data class Failed(val message: String?, val correlationId: Long?) : DomainEvent
}

private class DomainRecorder : UseCaseInterceptor<CounterState, CounterIntent> {
    // Thread-safe: the correlation tests deliberately drive the domain from Dispatchers.Default,
    // so these callbacks arrive on a different thread from the one asserting on them.
    val events = CopyOnWriteArrayList<DomainEvent>()

    override fun onIntent(intent: CounterIntent, context: TraceContext?) {
        events += DomainEvent.Intent(intent, context?.correlationId)
    }

    override fun onStateChanged(oldState: CounterState, newState: CounterState, context: TraceContext?) {
        events += DomainEvent.StateChanged(oldState.value, newState.value, context?.correlationId)
    }

    override fun onError(throwable: Throwable, intent: CounterIntent, context: TraceContext?) {
        events += DomainEvent.Failed(throwable.message, context?.correlationId)
    }
}

private class CounterUseCase(
    recorder: DomainRecorder,
) : AbstractUseCaseProcessor<CounterState, CounterIntent>(CounterState(), listOf(recorder)) {

    override suspend fun map(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> reduce { it.copy(value = it.value + 1) }
            // Returns an equal state: no observable transition, so nothing to report.
            CounterIntent.NoOpReduce -> reduce { it.copy(value = it.value) }
            is CounterIntent.Replace -> reduce(CounterState(intent.value))
            CounterIntent.Boom -> throw IllegalStateException("domain failed")
        }
    }
}

// ── Presentation fixtures ──────────────────────────────────────────────────────

private data class ScreenState(val done: Boolean = false) : ViewState
private data object ScreenIntent : ViewIntent
private sealed interface ScreenEffect : SideEffect

private class ScreenProcessor(
    private val counter: CounterUseCase,
) : PresentationProcessor<ScreenIntent, ScreenState, ScreenEffect>(ScreenState()) {

    override suspend fun map(intent: ScreenIntent): Action<ScreenState, ScreenEffect> =
        async {
            // The shift is deliberate: a real use case does IO, and the correlation has to
            // survive the dispatcher change to be worth anything.
            withContext(Dispatchers.Default) {
                counter.dispatch(CounterIntent.Increment)
            }
            reduce { copy(done = true) }
        }
}

/**
 * Suspends until [recorder] has seen [count] events.
 *
 * The dispatcher shift inside [ScreenProcessor] means the domain work does *not* complete
 * synchronously, even under `UnconfinedTestDispatcher` — so asserting straight after
 * `dispatch` returns is a race, and one that hides itself: a failure message renders the
 * collection after the comparison, by which time the missing events have usually arrived.
 */
private suspend fun awaitEvents(recorder: DomainRecorder, count: Int) {
    withTimeout(10_000) {
        while (recorder.events.size < count) {
            delay(1)
        }
    }
    // Let any surplus event land, so "exactly N" means exactly N.
    delay(100)
}

// ── Tests ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseTracingTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("UseCaseProcessor interceptors") {

        it("report the intent and the state change it produced") {
            runTest {
                val recorder = DomainRecorder()

                CounterUseCase(recorder).dispatch(CounterIntent.Increment)

                recorder.events shouldContainExactly listOf(
                    DomainEvent.Intent(CounterIntent.Increment, null),
                    DomainEvent.StateChanged(0, 1, null),
                )
            }
        }

        it("report nothing for a reduction that changed nothing") {
            runTest {
                val recorder = DomainRecorder()

                CounterUseCase(recorder).dispatch(CounterIntent.NoOpReduce)

                recorder.events.filterIsInstance<DomainEvent.StateChanged>().shouldBeEmpty()
            }
        }

        it("report a state change made through the absolute reduce overload") {
            runTest {
                val recorder = DomainRecorder()

                CounterUseCase(recorder).dispatch(CounterIntent.Replace(42))

                recorder.events.filterIsInstance<DomainEvent.StateChanged>() shouldContainExactly
                    listOf(DomainEvent.StateChanged(0, 42, null))
            }
        }

        it("are empty by default, so a processor without tracing is unaffected") {
            runTest {
                val processor = object : AbstractUseCaseProcessor<CounterState, CounterIntent>(
                    CounterState(),
                ) {
                    override suspend fun map(intent: CounterIntent) {
                        reduce { it.copy(value = it.value + 1) }
                    }
                }

                processor.dispatch(CounterIntent.Increment)

                processor.interceptors.shouldBeEmpty()
                processor.state shouldBe CounterState(1)
            }
        }
    }

    describe("a failing use case") {

        // Reported *and* rethrown: the domain records what failed, and the caller still learns
        // that its work was interrupted. Swallowing here would leave an AsyncAction believing
        // its use case had succeeded.
        it("reports the failure and rethrows it") {
            runTest {
                val recorder = DomainRecorder()
                val useCase = CounterUseCase(recorder)

                val failure = shouldThrow<IllegalStateException> {
                    useCase.dispatch(CounterIntent.Boom)
                }

                failure.message shouldBe "domain failed"
                recorder.events shouldContainExactly listOf(
                    DomainEvent.Intent(CounterIntent.Boom, null),
                    DomainEvent.Failed("domain failed", null),
                )
            }
        }
    }

    describe("correlation across the layer boundary") {

        // The payoff. Nothing in ScreenProcessor or CounterUseCase passes a correlation id;
        // it rides the coroutine context from the intent loop, through the AsyncAction's
        // coroutine, across a withContext(Dispatchers.Default), and into the domain.
        it("attributes domain events to the ViewIntent that caused them") {
            val recorder = DomainRecorder()
            val screen = ScreenProcessor(CounterUseCase(recorder))

            screen.dispatch(ScreenIntent)
            awaitEvents(recorder, count = 2)

            recorder.events shouldContainExactly listOf(
                DomainEvent.Intent(CounterIntent.Increment, 0L),
                DomainEvent.StateChanged(0, 1, 0L),
            )
        }

        // Three AsyncActions run concurrently on Dispatchers.Default, so their domain events
        // interleave arbitrarily. What must hold for every interleaving is that each ViewIntent
        // got its own id and both of its domain events carry it — so the multiset is asserted,
        // not the order.
        it("gives each ViewIntent's domain work its own correlation id") {
            val recorder = DomainRecorder()
            val screen = ScreenProcessor(CounterUseCase(recorder))

            repeat(3) { screen.dispatch(ScreenIntent) }
            awaitEvents(recorder, count = 6)

            val idCounts = recorder.events
                .map { event ->
                    when (event) {
                        is DomainEvent.Intent -> event.correlationId
                        is DomainEvent.StateChanged -> event.correlationId
                        is DomainEvent.Failed -> event.correlationId
                    }
                }
                .groupingBy { it }
                .eachCount()

            idCounts shouldBe mapOf(0L to 2, 1L to 2, 2L to 2)
        }
    }
})
