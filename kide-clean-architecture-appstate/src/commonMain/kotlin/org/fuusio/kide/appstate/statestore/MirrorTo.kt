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
import androidx.appstate.statestore.StateStoreKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.fuusio.kide.appstate.ExperimentalKideAppStateApi
import org.fuusio.kide.appstate.asFlow
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.domain.usecase.UseCaseIntent
import org.fuusio.kide.domain.usecase.UseCaseProcessor
import org.fuusio.kide.log.logW

/**
 * Mirrors this processor's domain state into [stateStore] under [key], so that other parts of
 * the process — another activity, a service, a widget, a `transformFlow` block — can read it
 * as Compose state with `stateStore.getState(key)`.
 *
 * The mirror is **one-way**. The processor remains the only place the state changes, so every
 * change still goes through its intents and is still reported to its
 * [interceptors][UseCaseProcessor.interceptors]; the store is a read replica.
 *
 * The current state is written **before this function returns**, on the calling thread, and
 * every later state as it is published. That first write is synchronous on purpose:
 * `StateStore` is not safe for a key's *first* access from two threads at once — a
 * `getState` racing the write that creates the key can be handed a `State` the store then
 * forgets, which never updates. Once this function has returned the key exists, and
 * `stateStore.getState(key)` is safe from any thread. So call this at application start,
 * before anything else can ask for [key].
 *
 * [key] must be written by this mirror alone. Nothing here reads the store, so a value
 * written to [key] by anyone else is neither seen by the processor nor preserved: the next
 * state the processor publishes replaces it. Keep the key `internal` to the module that
 * owns the processor and expose `stateStore.getState(key)` as a read-only `State`.
 *
 * Do not use this overload with a `PersistToDataStore` key: it restores nothing, so the
 * store would hold the persisted value while the processor holds its initial state. Use the
 * overload that takes a `restore` function.
 *
 * @param stateStore The store to write to; normally the application's single instance.
 * @param key The key the domain state is published under.
 * @param scope The scope the mirror runs in — normally the application scope, since both the
 * processor and the store are application-scoped. Cancelling it, or the returned [Job],
 * stops the mirror and leaves the last mirrored value in the store.
 * @return The [Job] running the mirror.
 */
@ExperimentalKideAppStateApi
public fun <S : State> UseCaseProcessor<S, *>.mirrorTo(
    stateStore: StateStore,
    key: StateStoreKey<S>,
    scope: CoroutineScope,
): Job {
    // Creates the key here rather than in the launched coroutine; see the KDoc. The
    // collector below starts with this same value, and writing it again changes nothing.
    stateStore.setState(key, stateFlow.value)
    return scope.launch {
        stateFlow.collect { state -> stateStore.setState(key, state) }
    }
}

/**
 * Mirrors this processor's domain state into [stateStore] under [key], like the plain
 * [mirrorTo], and additionally **restores** the processor from the store: a value that
 * appears under [key] without this mirror having written it is handed to [restore], and the
 * intent it returns is dispatched to the processor.
 *
 * This is what makes a `PersistToDataStore` key usable. A persisted value is loaded shortly
 * after start-up — some tens of milliseconds — and replaces whatever the key held; the
 * store gives no signal that loading has finished. So instead of restoring once before
 * mirroring, the mirror watches the key for as long as it runs and treats any value it did
 * not write as one to restore.
 *
 * The restored state still enters the processor as an intent, so it goes through
 * `map`, is reported to the [interceptors][UseCaseProcessor.interceptors] and appears in a
 * recorded trace like any other change:
 *
 * ```kotlin
 * savedProjects.mirrorTo(stateStore, SavedProjectsKey, applicationScope) { persisted ->
 *     RestoreSavedProjects(persisted)
 * }
 * ```
 *
 * After the intent has been processed the processor's resulting state is written back, so
 * the store and the processor agree even when the processor did not adopt the value exactly.
 * Return `null` from [restore] to ignore a value; the processor's state is then written back
 * over it. If dispatching the intent throws, the failure is logged, the processor's state is
 * written back, and the mirror keeps running.
 *
 * ### What to know
 * - **A change made before the persisted value has loaded can be replaced by it.** If the
 *   domain state changes in the first moments after start-up, the persisted value still
 *   arrives afterwards and is restored over it. [restore] sees only the stored value; read
 *   the processor's `state` there to decide otherwise.
 * - **[key]'s default value should equal the processor's initial state.** Otherwise the
 *   default is indistinguishable from a value to restore.
 * - As with the plain overload, [key] must have no writer but this mirror (and the store's
 *   own persistence), and this function must be called at application start, before
 *   anything else asks for [key]: it creates the key before returning.
 *
 * @param stateStore The store to mirror into; normally the application's single instance.
 * @param key The key the domain state is published under.
 * @param scope The scope the mirror runs in — normally the application scope.
 * @param restore Maps a stored value this mirror did not write to the intent that restores
 * it, or `null` to ignore the value. Invoked in [scope].
 * @return The [Job] running the mirror.
 */
@ExperimentalKideAppStateApi
public fun <S : State, I : UseCaseIntent<S>> UseCaseProcessor<S, I>.mirrorTo(
    stateStore: StateStore,
    key: StateStoreKey<S>,
    scope: CoroutineScope,
    restore: (S) -> I?,
): Job {
    val processor = this

    // The value this mirror last put in the store. Written here once, then only from the
    // single coroutine launched below, which is what makes the comparison in reconcile
    // meaningful without synchronisation.
    var lastWritten = stateFlow.value
    stateStore.setState(key, lastWritten)
    val stored = stateStore.getState(key)

    fun write(state: S) {
        lastWritten = state
        stateStore.setState(key, state)
    }

    // Brings the store and the processor back into agreement. Works from what both hold
    // *now* rather than from the value that triggered it: the store's flow is conflated, so
    // a trigger may be stale by the time it is handled.
    suspend fun reconcile() {
        val storedState = stored.value
        if (storedState == processor.state) return
        if (storedState == lastWritten) {
            // The store still holds what the mirror wrote; the processor has moved on.
            write(processor.state)
            return
        }
        // Not written by this mirror: loaded from disk.
        try {
            restore(storedState)?.let { intent -> processor.dispatch(intent) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            processor.logW(throwable) { "Failed to restore from StateStore; keeping the current state" }
        }
        write(processor.state)
    }

    return scope.launch {
        // One collector for both directions, so reconciliations never interleave.
        merge(stateFlow.map { }, stored.asFlow().map { }).collect { reconcile() }
    }
}
