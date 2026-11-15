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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.fuusio.kide.appstate.ExperimentalKideAppStateApi
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.domain.usecase.AbstractUseCaseProcessor
import org.fuusio.kide.domain.usecase.UseCaseIntent
import org.fuusio.kide.domain.usecase.UseCaseInterceptor
import org.fuusio.kide.presentation.TraceContext
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class Tally(val count: Int = 0) : State

internal sealed interface TallyIntent : UseCaseIntent<Tally> {
    data class Add(val amount: Int) : TallyIntent
    data class Restore(val tally: Tally) : TallyIntent
}

@Serializable
internal data object TallyKey : StateStoreKey<Tally>(Tally()), PersistToDataStore

internal class TallyRecorder : UseCaseInterceptor<Tally, TallyIntent> {
    val transitions = CopyOnWriteArrayList<Pair<Tally, Tally>>()

    override fun onStateChanged(oldState: Tally, newState: Tally, context: TraceContext?) {
        transitions.add(oldState to newState)
    }
}

internal class TallyProcessor(
    interceptors: List<UseCaseInterceptor<Tally, TallyIntent>> = emptyList(),
) : AbstractUseCaseProcessor<Tally, TallyIntent>(Tally(), interceptors) {

    override suspend fun map(intent: TallyIntent) {
        when (intent) {
            is TallyIntent.Add -> reduce { tally -> tally.copy(count = tally.count + intent.amount) }
            is TallyIntent.Restore -> reduce { _ -> intent.tally }
        }
    }
}

/**
 * The restoring [mirrorTo] against a real StateStore persisted through DataStore: what a
 * use-case processor held when the application stopped is what a new one holds after it
 * starts again. An Android host test because `statestore-datastore` is Android-only.
 */
@OptIn(ExperimentalKideAppStateApi::class)
class PersistedMirrorTest : DescribeSpec({

    suspend fun within(condition: () -> Boolean): Boolean =
        withTimeoutOrNull(5.seconds) {
            while (!condition()) delay(20)
            true
        } ?: false

    /**
     * One run of the application: a fresh store synced to [path], a fresh processor mirrored
     * into it with restore, [block], and then everything shut down so the file is released.
     */
    suspend fun applicationRun(
        path: String,
        recorder: TallyRecorder = TallyRecorder(),
        block: suspend (TallyProcessor) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StateStore()
        val processor = TallyProcessor(listOf(recorder))
        scope.launch { store.syncToDataStore(path = path, coroutineScope = scope) }
        processor.mirrorTo(store, TallyKey, scope) { TallyIntent.Restore(it) }
        try {
            block(processor)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    fun newFile(): String =
        Files.createTempDirectory("kide-statestore").resolve("statestore.json").toString()

    describe("mirrorTo with restore, on a persisted key") {

        it("restores the processor across application runs") {
            val path = newFile()

            applicationRun(path) { processor ->
                delay(500) // past start-up, so the change is not made before loading finishes
                processor.dispatch(TallyIntent.Add(5))
                delay(1_000) // let it reach disk
            }

            val recorder = TallyRecorder()
            applicationRun(path, recorder) { processor ->
                within { processor.state == Tally(5) } shouldBe true
                // Restored as one ordinary, reported transition.
                recorder.transitions.toList() shouldBe listOf(Tally(0) to Tally(5))

                processor.dispatch(TallyIntent.Add(1))
                delay(1_000)
            }

            applicationRun(path) { processor ->
                within { processor.state == Tally(6) } shouldBe true
            }
        }

        it("starts from the initial state when nothing was persisted") {
            val recorder = TallyRecorder()
            applicationRun(newFile(), recorder) { processor ->
                delay(1_000)
                processor.state shouldBe Tally(0)
                recorder.transitions.toList() shouldBe emptyList()
            }
        }
    }
})
