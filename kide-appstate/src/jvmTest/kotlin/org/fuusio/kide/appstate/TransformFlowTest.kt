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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * [transformFlow] on its own, against real dispatchers and real time: the composition runs
 * on a plain JVM with no UI toolkit present, which is the situation it is used in.
 */
@OptIn(ExperimentalKideAppStateApi::class)
class TransformFlowTest : DescribeSpec({

    describe("transformFlow") {

        it("emits the first result with no UI present") {
            val source = mutableStateOf(1)
            transformFlow { source.value * 2 }.test(timeout = 5.seconds) {
                awaitItem() shouldBe 2
                cancelAndIgnoreRemainingEvents()
            }
        }

        it("recomposes after a plain write to the global snapshot") {
            val source = mutableStateOf(1)
            transformFlow { source.value * 2 }.test(timeout = 5.seconds) {
                awaitItem() shouldBe 2
                source.value = 2
                awaitItem() shouldBe 4
                cancelAndIgnoreRemainingEvents()
            }
        }

        it("recomposes after Snapshot.withMutableSnapshot") {
            val source = mutableStateOf(1)
            transformFlow { source.value * 2 }.test(timeout = 5.seconds) {
                awaitItem() shouldBe 2
                Snapshot.withMutableSnapshot { source.value = 3 }
                awaitItem() shouldBe 6
                cancelAndIgnoreRemainingEvents()
            }
        }

        it("bridges a Flow read with collectAsState") {
            val upstream = MutableStateFlow(1)
            transformFlow {
                val value by upstream.collectAsState()
                value + 1
            }.test(timeout = 5.seconds) {
                awaitItem() shouldBe 2
                upstream.value = 10
                awaitItem() shouldBe 11
                cancelAndIgnoreRemainingEvents()
            }
        }

        it("State.asFlow follows the state") {
            val source = mutableStateOf("a")
            source.asFlow().test(timeout = 5.seconds) {
                awaitItem() shouldBe "a"
                source.value = "b"
                awaitItem() shouldBe "b"
                cancelAndIgnoreRemainingEvents()
            }
        }

        it("conflates a burst and still delivers the last value") {
            val source = mutableStateOf(0)
            transformFlow { source.value }.test(timeout = 5.seconds) {
                awaitItem() shouldBe 0
                for (i in 1..1_000) source.value = i
                // However many intermediate values are skipped, the last one arrives.
                do {
                    val item = awaitItem()
                } while (item != 1_000)
                cancelAndIgnoreRemainingEvents()
            }
        }

    }

    describe("errors and cancellation") {

        it("an exception thrown by the block fails the collection") {
            val source = mutableStateOf(0)
            val outcome = withTimeoutOrNull(5.seconds) {
                runCatching {
                    transformFlow {
                        if (source.value >= 1) throw TestFailure()
                        source.value
                    }.collect { if (it == 0) source.value = 1 }
                }
            }
            (outcome?.exceptionOrNull() is TestFailure) shouldBe true
        }

        it("an exception thrown by the very first composition fails the collection") {
            val outcome = withTimeoutOrNull(5.seconds) {
                runCatching { transformFlow<Int> { throw TestFailure() }.collect { } }
            }
            (outcome?.exceptionOrNull() is TestFailure) shouldBe true
        }

        it("cancelling the collector disposes the composition and stops recomposition") {
            val source = mutableStateOf(0)
            val started = CompletableDeferred<Unit>()
            val disposed = CompletableDeferred<Unit>()
            val compositions = AtomicInteger()
            coroutineScope {
                val job = launch {
                    transformFlow {
                        compositions.incrementAndGet()
                        DisposableEffect(Unit) { onDispose { disposed.complete(Unit) } }
                        source.value
                    }.collect { started.complete(Unit) }
                }
                (withTimeoutOrNull(5.seconds) { started.await() } != null) shouldBe true

                job.cancelAndJoin()
                (withTimeoutOrNull(5.seconds) { disposed.await() } != null) shouldBe true

                val before = compositions.get()
                source.value = 1
                delay(500)
                compositions.get() shouldBe before
            }
        }
    }
})
