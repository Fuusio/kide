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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Not a test of Kide: a probe of StateStore behaviour that AppState does not document and
 * that the design of *restoring* a use-case processor from a persisted key depends on
 * (docs/proposal-appstate.md, phase 3). Nothing here asserts; each case prints a
 * `[statestore]` line with what it observed. Persistence is probed separately, in
 * `androidHostTest`, because `statestore-datastore` is Android-only. Remove both once the
 * restore design is settled.
 */
class StateStoreProbe : DescribeSpec({

    describe("concurrency") {

        it("is updateState atomic under contention?") {
            val store = StateStore()
            coroutineScope {
                repeat(8) {
                    launch(Dispatchers.Default) {
                        repeat(10_000) {
                            store.updateState(CounterKey) { it.copy(count = it.count + 1) }
                        }
                    }
                }
            }
            val count = store.getState(CounterKey).value.count
            println("[statestore] 80000 concurrent updateState increments produced $count (lost ${80_000 - count})")
        }

        it("is a key's first getState safe against a concurrent first setState?") {
            var orphaned = 0
            val rounds = 5_000
            repeat(rounds) {
                val store = StateStore()
                coroutineScope {
                    val writer = launch(Dispatchers.Default) { store.setState(CounterKey, CounterState(1)) }
                    val held = store.getState(CounterKey)
                    writer.join()
                    // A reader keeps the State it was given. It is orphaned if the store now
                    // hands out a different one, or if it never saw the write.
                    if (held !== store.getState(CounterKey) || held.value != CounterState(1)) orphaned++
                }
            }
            println("[statestore] first getState racing first setState: $orphaned of $rounds readers were left holding a State that does not update")
        }
    }
})
