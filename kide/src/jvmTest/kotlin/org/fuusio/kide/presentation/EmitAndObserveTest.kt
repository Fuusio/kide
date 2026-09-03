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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

// ── Test fixtures ──────────────────────────────────────────────────────────────

private sealed interface ObserveIntent : ViewIntent {
    /** A use case that reduces and then reports its result with [PresentationProcessor.emit]. */
    data class Save(val succeeds: Boolean) : ObserveIntent

    /** Subscribes to the processor's source under [key]; [onlyOnce] guards with `isObserving`. */
    data class Watch(val key: String = "source", val onlyOnce: Boolean = false) : ObserveIntent

    data class Unwatch(val key: String = "source") : ObserveIntent

    /** Subscribes to a flow that throws after its first value. */
    data object WatchFailing : ObserveIntent

    data object Ping : ObserveIntent
}

private sealed interface ObserveSideEffect : SideEffect {
    data class Saved(val value: Int) : ObserveSideEffect
    data object SaveFailed : ObserveSideEffect
    data object Pong : ObserveSideEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
private class ObservingProcessor(
    val source: MutableSharedFlow<Int> = MutableSharedFlow(),
    val failing: Flow<Int> = flow {
        emit(1)
        throw IllegalStateException("source failed")
    },
    interceptors: List<KideInterceptor<ObserveIntent, TestViewState, ObserveSideEffect>> = emptyList(),
) : PresentationProcessor<ObserveIntent, TestViewState, ObserveSideEffect>(
    TestViewState(),
    interceptors = interceptors,
) {

    val observationErrors = mutableListOf<Pair<String, Throwable>>()

    override fun onObservationError(throwable: Throwable, key: String) {
        observationErrors.add(key to throwable)
    }

    override suspend fun map(intent: ObserveIntent): Action<TestViewState, ObserveSideEffect>? =
        when (intent) {
            is ObserveIntent.Save -> useCase {
                reduce { copy(value = value + 1) }
                emit(if (intent.succeeds) ObserveSideEffect.Saved(state.value) else ObserveSideEffect.SaveFailed)
            }

            is ObserveIntent.Watch -> {
                if (!intent.onlyOnce || !isObserving(intent.key)) {
                    observe(intent.key, source) { value ->
                        reduce { copy(value = value) }
                        // An observation can end in a side effect as well as a reduction.
                        if (value < 0) emit(ObserveSideEffect.Pong)
                    }
                }
                null
            }

            is ObserveIntent.Unwatch -> {
                stopObserving(intent.key)
                null
            }

            ObserveIntent.WatchFailing -> {
                observe("failing", failing) { value -> reduce { copy(value = value) } }
                null
            }

            ObserveIntent.Ping -> sideEffect { ObserveSideEffect.Pong }
        }

    /** The guards are protected; the tests reach them through the intents above. */
    fun observing(key: String): Boolean = isObserving(key)
}

// ── Tests ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class EmitAndObserveTest : DescribeSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    suspend fun collecting(processor: ObservingProcessor, block: suspend (MutableList<ObserveSideEffect>) -> Unit) {
        val effects = mutableListOf<ObserveSideEffect>()
        val scope = CoroutineScope(testDispatcher)
        val job = scope.launch { processor.sideEffects.collect { effects.add(it) } }
        try {
            block(effects)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    describe("emit") {

        it("delivers a side effect emitted from inside a use case") {
            val processor = ObservingProcessor()
            collecting(processor) { effects ->
                processor.dispatch(ObserveIntent.Save(succeeds = true))
                effects shouldBe listOf(ObserveSideEffect.Saved(1))
            }
        }

        it("sees the reductions the use case made before it") {
            // The whole point: the effect is built from the result, not from a state snapshot
            // taken before the use case ran.
            val processor = ObservingProcessor()
            collecting(processor) { effects ->
                processor.dispatch(ObserveIntent.Save(succeeds = true))
                processor.dispatch(ObserveIntent.Save(succeeds = false))
                processor.dispatch(ObserveIntent.Save(succeeds = true))
                effects shouldBe listOf(
                    ObserveSideEffect.Saved(1),
                    ObserveSideEffect.SaveFailed,
                    ObserveSideEffect.Saved(3),
                )
            }
        }

        it("buffers an emitted effect until a collector attaches") {
            val processor = ObservingProcessor()
            processor.dispatch(ObserveIntent.Save(succeeds = true))
            collecting(processor) { effects ->
                effects shouldBe listOf(ObserveSideEffect.Saved(1))
            }
        }

        it("reports the effect to interceptors with the intent's trace context") {
            val reported = mutableListOf<Pair<ObserveSideEffect, TraceContext?>>()
            val contexts = mutableListOf<TraceContext?>()
            val interceptor = object : KideInterceptor<ObserveIntent, TestViewState, ObserveSideEffect> {
                override fun onIntent(intent: ObserveIntent, context: TraceContext?) {
                    contexts.add(context)
                }
                override fun onSideEffect(sideEffect: ObserveSideEffect, context: TraceContext?) {
                    reported.add(sideEffect to context)
                }
            }
            val processor = ObservingProcessor(interceptors = listOf(interceptor))
            collecting(processor) {
                processor.dispatch(ObserveIntent.Save(succeeds = true))
            }
            reported shouldBe listOf(ObserveSideEffect.Saved(1) to contexts.single())
        }
    }

    describe("observe") {

        it("reduces the state with every value the flow emits") {
            val processor = ObservingProcessor()
            processor.dispatch(ObserveIntent.Watch())

            processor.source.emit(7)
            processor.state shouldBe TestViewState(7)
            processor.source.emit(8)
            processor.state shouldBe TestViewState(8)
        }

        it("reports the observation as live until the flow completes or is stopped") {
            val processor = ObservingProcessor()
            processor.observing("source") shouldBe false

            processor.dispatch(ObserveIntent.Watch())
            processor.observing("source") shouldBe true

            processor.dispatch(ObserveIntent.Unwatch())
            processor.observing("source") shouldBe false

            processor.source.emit(9)
            processor.state shouldBe TestViewState(0)
        }

        it("replaces a previous observation registered under the same key") {
            val processor = ObservingProcessor()
            processor.dispatch(ObserveIntent.Watch())
            processor.dispatch(ObserveIntent.Watch())

            // One subscriber, not two: a single emission reduces once, and the source reports
            // exactly one collector.
            processor.source.subscriptionCount.value shouldBe 1
            processor.source.emit(3)
            processor.state shouldBe TestViewState(3)
        }

        it("isObserving lets an intent that fires on every entry subscribe only once") {
            val processor = ObservingProcessor()
            processor.dispatch(ObserveIntent.Watch(onlyOnce = true))
            processor.dispatch(ObserveIntent.Watch(onlyOnce = true))

            processor.source.subscriptionCount.value shouldBe 1
        }

        it("keys are independent") {
            val processor = ObservingProcessor()
            processor.dispatch(ObserveIntent.Watch(key = "a"))
            processor.dispatch(ObserveIntent.Watch(key = "b"))

            processor.source.subscriptionCount.value shouldBe 2
            processor.dispatch(ObserveIntent.Unwatch(key = "a"))
            processor.source.subscriptionCount.value shouldBe 1
            processor.observing("b") shouldBe true
        }

        it("can emit a side effect from onEach") {
            val processor = ObservingProcessor()
            collecting(processor) { effects ->
                processor.dispatch(ObserveIntent.Watch())
                processor.source.emit(-1)
                effects shouldBe listOf(ObserveSideEffect.Pong)
            }
        }

        it("reports reductions to interceptors with a null trace context") {
            val changes = mutableListOf<Pair<Int, TraceContext?>>()
            val interceptor = object : KideInterceptor<ObserveIntent, TestViewState, ObserveSideEffect> {
                override fun onStateChanged(oldState: TestViewState, newState: TestViewState, context: TraceContext?) {
                    changes.add(newState.value to context)
                }
            }
            val processor = ObservingProcessor(interceptors = listOf(interceptor))
            processor.dispatch(ObserveIntent.Watch())
            processor.source.emit(4)

            changes shouldBe listOf(4 to null)
        }

        it("a failing flow ends its observation, reaches onObservationError, and leaves the loop running") {
            val processor = ObservingProcessor()
            processor.dispatch(ObserveIntent.WatchFailing)

            processor.state shouldBe TestViewState(1)
            processor.observing("failing") shouldBe false
            processor.observationErrors.map { it.first } shouldBe listOf("failing")
            processor.observationErrors.single().second.message shouldBe "source failed"

            collecting(processor) { effects ->
                processor.dispatch(ObserveIntent.Ping)
                effects shouldBe listOf(ObserveSideEffect.Pong)
            }
        }

        it("close cancels every observation") {
            val processor = ObservingProcessor()
            processor.dispatch(ObserveIntent.Watch(key = "a"))
            processor.dispatch(ObserveIntent.Watch(key = "b"))
            processor.source.subscriptionCount.value shouldBe 2

            processor.close()

            processor.source.subscriptionCount.value shouldBe 0
            processor.observing("a") shouldBe false
        }
    }
})
