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

import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import io.kotest.core.spec.style.DescribeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Serializable
internal data class PersistedCounter(val count: Int = 0)

@Serializable
internal data object PersistedCounterKey : StateStoreKey<PersistedCounter>(PersistedCounter()), PersistToDataStore

/**
 * Not a test of Kide: a probe of how a StateStore synced to DataStore behaves around
 * start-up, which AppState does not document and the restore design depends on
 * (docs/proposal-appstate.md, phase 3). Runs as an Android host test because
 * `statestore-datastore` is published for Android only. Nothing here asserts; each case
 * prints a `[statestore]` line. Remove once the restore design is settled.
 */
class StateStorePersistenceProbe : DescribeSpec({

    suspend fun within(condition: () -> Boolean): Boolean =
        withTimeoutOrNull(5.seconds) {
            while (!condition()) delay(20)
            true
        } ?: false

    /** Runs [block] against a store synced to [path], then releases the file. */
    suspend fun synced(
        path: String,
        beforeSync: (StateStore) -> Unit = {},
        block: suspend (StateStore) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StateStore()
        beforeSync(store)
        scope.launch { store.syncToDataStore(path = path, coroutineScope = scope) }
        try {
            block(store)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    fun newFile(): String =
        Files.createTempDirectory("kide-statestore").resolve("statestore.json").toString()

    /** Persists `PersistedCounter(count)` to a fresh file and returns its path. */
    suspend fun seeded(count: Int): String {
        val path = newFile()
        synced(path) { store ->
            store.setState(PersistedCounterKey, PersistedCounter(count))
            delay(1_000)
        }
        return path
    }

    describe("persistence") {

        it("does a persisted value come back, and how long after getState?") {
            val path = seeded(7)
            synced(path) { store ->
                val start = TimeSource.Monotonic.markNow()
                val state = store.getState(PersistedCounterKey)
                println("[statestore] immediately after getState: ${state.value}")
                val restored = within { state.value == PersistedCounter(7) }
                println("[statestore] persisted value restored: $restored after ${start.elapsedNow()}")
            }
        }

        it("what survives a write made BEFORE syncToDataStore starts?") {
            val path = seeded(7)
            synced(path, beforeSync = { it.setState(PersistedCounterKey, PersistedCounter(55)) }) { store ->
                delay(1_500)
                println("[statestore] disk had 7, wrote 55 before sync -> in memory: ${store.getState(PersistedCounterKey).value}")
            }
            synced(path) { store ->
                val state = store.getState(PersistedCounterKey)
                delay(1_500)
                println("[statestore] disk had 7, wrote 55 before sync -> on disk afterwards: ${state.value}")
            }
        }

        it("what survives a write made RIGHT AFTER syncToDataStore starts?") {
            val path = seeded(7)
            synced(path) { store ->
                store.setState(PersistedCounterKey, PersistedCounter(99))
                delay(1_500)
                println("[statestore] disk had 7, wrote 99 right after starting sync -> in memory: ${store.getState(PersistedCounterKey).value}")
            }
            synced(path) { store ->
                val state = store.getState(PersistedCounterKey)
                delay(1_500)
                println("[statestore] disk had 7, wrote 99 right after starting sync -> on disk afterwards: ${state.value}")
            }
        }

        it("is a default-valued key distinguishable from one never persisted?") {
            val path = seeded(0)
            synced(path) { store ->
                delay(1_500)
                println("[statestore] after persisting the default value, keys known to the store: ${store.keys.toList()}")
            }
            synced(newFile()) { store ->
                delay(1_500)
                println("[statestore] with nothing persisted, keys known to the store: ${store.keys.toList()}")
            }
        }
    }
})
