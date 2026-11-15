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

package org.fuusio.kide.appstate

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.KideInterceptor
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.TraceContext
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import org.fuusio.kide.presentation.defaultProcessorScope
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Deliberately not an IllegalStateException: a coroutine timeout is one. */
internal class TestFailure : RuntimeException("test failure")

internal data class TestState(val value: Int = 0) : ViewState

internal sealed interface TestIntent : ViewIntent {
    /** Observes `source * multiplier` through [transformFlow] under the key "source". */
    data class Watch(val multiplier: Int) : TestIntent

    /** Observes a block that throws [TestFailure] once the source reaches 100. */
    data object WatchFailing : TestIntent

    data object Unwatch : TestIntent
}

/** Records every reported transition, from whichever thread reports it. */
internal class RecordingInterceptor : KideInterceptor<TestIntent, TestState, SideEffect> {
    val transitions = CopyOnWriteArrayList<Pair<TestState, TestState>>()

    override fun onStateChanged(oldState: TestState, newState: TestState, context: TraceContext?) {
        transitions.add(oldState to newState)
    }
}

@OptIn(ExperimentalKideAppStateApi::class)
internal class TestProcessor(
    private val source: MutableState<Int>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    scope: CoroutineScope = defaultProcessorScope(),
    interceptors: List<KideInterceptor<TestIntent, TestState, SideEffect>> = emptyList(),
) : PresentationProcessor<TestIntent, TestState, SideEffect>(TestState(), scope, interceptors) {

    val observationErrors = CopyOnWriteArrayList<Pair<String, Throwable>>()

    /** How many headless compositions started by [TestIntent.Watch] have been disposed. */
    val disposals = AtomicInteger()

    override fun onObservationError(throwable: Throwable, key: String) {
        observationErrors.add(key to throwable)
    }

    override suspend fun map(intent: TestIntent): Action<TestState, SideEffect>? {
        when (intent) {
            is TestIntent.Watch -> observe(
                "source",
                transformFlow(dispatcher) {
                    DisposableEffect(Unit) { onDispose { disposals.incrementAndGet() } }
                    source.value * intent.multiplier
                },
            ) { value -> reduce { copy(value = value) } }

            TestIntent.WatchFailing -> observe(
                "failing",
                transformFlow(dispatcher) {
                    if (source.value >= 100) throw TestFailure()
                    source.value
                },
            ) { value -> reduce { copy(value = value) } }

            TestIntent.Unwatch -> stopObserving("source")
        }
        return null
    }
}

/** Polls [condition] in real time; `false` if it did not hold within [timeout]. */
internal suspend fun within(timeout: Duration = 5.seconds, condition: () -> Boolean): Boolean =
    withTimeoutOrNull(timeout) {
        while (!condition()) delay(20)
        true
    } ?: false
