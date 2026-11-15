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

package org.fuusio.kide.appstate.statestore

import androidx.appstate.statestore.StateStore
import app.cash.turbine.test
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import org.fuusio.kide.appstate.ExperimentalKideAppStateApi
import org.fuusio.kide.appstate.transformFlow
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKideAppStateApi::class)
class MirrorToTest : DescribeSpec({

    describe("mirrorTo") {

        it("has published the current state when it returns, then every later one") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            processor.dispatch(CounterIntent.Add(2))

            processor.mirrorTo(store, CounterKey, scope)
            // Held across later changes: this is how a reader uses it, and it is the case
            // that breaks if the key is first created on another thread while it is read.
            val mirrored = store.getState(CounterKey)
            mirrored.value shouldBe CounterState(2)

            processor.dispatch(CounterIntent.Add(3))
            within { mirrored.value == CounterState(5) } shouldBe true
            scope.cancel()
        }

        it("leaves the processor the only writer: changes are still reported once each") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val recorder = RecordingUseCaseInterceptor()
            val processor = CounterProcessor(listOf(recorder))
            processor.mirrorTo(store, CounterKey, scope)

            processor.dispatch(CounterIntent.Add(1))
            processor.dispatch(CounterIntent.Add(1))
            within { store.getState(CounterKey).value == CounterState(2) } shouldBe true

            recorder.transitions.toList() shouldBe listOf(
                CounterState(0) to CounterState(1),
                CounterState(1) to CounterState(2),
            )
            scope.cancel()
        }

        it("does not read the store: a foreign write is replaced, never adopted") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            processor.mirrorTo(store, CounterKey, scope)
            within { store.getState(CounterKey).value == CounterState(0) } shouldBe true

            store.setState(CounterKey, CounterState(99))
            delay(200)
            processor.state shouldBe CounterState(0)

            processor.dispatch(CounterIntent.Add(1))
            within { store.getState(CounterKey).value == CounterState(1) } shouldBe true
            scope.cancel()
        }

        it("writes only its own key") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            store.setState(OtherCounterKey, CounterState(7))
            processor.mirrorTo(store, CounterKey, scope)

            processor.dispatch(CounterIntent.Add(1))
            within { store.getState(CounterKey).value == CounterState(1) } shouldBe true
            store.getState(OtherCounterKey).value shouldBe CounterState(7)
            scope.cancel()
        }

        it("stops when its job is cancelled, leaving the last mirrored value") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            val job = processor.mirrorTo(store, CounterKey, scope)
            processor.dispatch(CounterIntent.Add(1))
            within { store.getState(CounterKey).value == CounterState(1) } shouldBe true

            job.cancelAndJoin()
            processor.dispatch(CounterIntent.Add(1))
            delay(200)
            store.getState(CounterKey).value shouldBe CounterState(1)
            scope.cancel()
        }

        it("makes domain state an input to transformFlow") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            processor.mirrorTo(store, CounterKey, scope)

            transformFlow { store.getState(CounterKey).value.count * 10 }.test(timeout = 5.seconds) {
                awaitItem() shouldBe 0
                processor.dispatch(CounterIntent.Add(4))
                awaitItem() shouldBe 40
                cancelAndIgnoreRemainingEvents()
            }
            scope.cancel()
        }
    }
})
