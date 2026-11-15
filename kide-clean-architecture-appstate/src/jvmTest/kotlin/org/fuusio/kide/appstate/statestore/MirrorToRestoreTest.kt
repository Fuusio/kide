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
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.fuusio.kide.appstate.ExperimentalKideAppStateApi
import java.util.concurrent.atomic.AtomicInteger

/**
 * The restoring overload of [mirrorTo]. A value loaded from disk is simulated by writing to
 * the key from outside the mirror, which is all the mirror can observe of it; the real
 * round trip through DataStore is covered by `PersistedMirrorTest` in `androidHostTest`.
 */
@OptIn(ExperimentalKideAppStateApi::class)
class MirrorToRestoreTest : DescribeSpec({

    describe("mirrorTo with restore") {

        it("restores a value it did not write, through an intent the interceptors see") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val recorder = RecordingUseCaseInterceptor()
            val processor = CounterProcessor(listOf(recorder))
            processor.mirrorTo(store, CounterKey, scope) { CounterIntent.Restore(it) }

            store.setState(CounterKey, CounterState(7))

            within { processor.state == CounterState(7) } shouldBe true
            recorder.transitions.toList() shouldBe listOf(CounterState(0) to CounterState(7))
            store.getState(CounterKey).value shouldBe CounterState(7)
            scope.cancel()
        }

        it("does not treat its own writes as values to restore") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val restores = AtomicInteger()
            val processor = CounterProcessor()
            processor.mirrorTo(store, CounterKey, scope) {
                restores.incrementAndGet()
                CounterIntent.Restore(it)
            }

            repeat(20) { processor.dispatch(CounterIntent.Add(1)) }

            within { store.getState(CounterKey).value == CounterState(20) } shouldBe true
            delay(200)
            restores.get() shouldBe 0
            processor.state shouldBe CounterState(20)
            scope.cancel()
        }

        it("keeps mirroring after a restore") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            processor.mirrorTo(store, CounterKey, scope) { CounterIntent.Restore(it) }
            store.setState(CounterKey, CounterState(7))
            within { processor.state == CounterState(7) } shouldBe true

            processor.dispatch(CounterIntent.Add(1))

            within { store.getState(CounterKey).value == CounterState(8) } shouldBe true
            scope.cancel()
        }

        it("writes the processor's state back when restore returns null") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            processor.dispatch(CounterIntent.Add(3))
            processor.mirrorTo(store, CounterKey, scope) { null }

            store.setState(CounterKey, CounterState(99))

            within { store.getState(CounterKey).value == CounterState(3) } shouldBe true
            processor.state shouldBe CounterState(3)
            scope.cancel()
        }

        it("ends with the store holding what the processor made of the value") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            // The processor does not adopt the stored value; it only counts that one arrived.
            processor.mirrorTo(store, CounterKey, scope) { CounterIntent.Add(1) }

            store.setState(CounterKey, CounterState(50))

            within { store.getState(CounterKey).value == CounterState(1) } shouldBe true
            processor.state shouldBe CounterState(1)
            scope.cancel()
        }

        it("survives a restore that throws") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = StateStore()
            val processor = CounterProcessor()
            processor.mirrorTo(store, CounterKey, scope) { CounterIntent.Fail }

            store.setState(CounterKey, CounterState(50))
            within { store.getState(CounterKey).value == CounterState(0) } shouldBe true

            processor.dispatch(CounterIntent.Add(2))
            within { store.getState(CounterKey).value == CounterState(2) } shouldBe true
            scope.cancel()
        }
    }
})
