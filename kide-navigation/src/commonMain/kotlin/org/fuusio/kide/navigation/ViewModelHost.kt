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
package org.fuusio.kide.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedState
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.KSerializer
import org.fuusio.kide.log.logW
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.PresentationProcessorHost
import org.fuusio.kide.presentation.ViewState

/**
 * A [ViewModel]-based implementation of [PresentationProcessorHost].
 *
 * [PresentationProcessor] is framework-independent; this adapter gives it [ViewModel] retention
 * semantics: the processor survives configuration changes with the ViewModel and is
 * [closed][PresentationProcessor.close] exactly once when the ViewModel is cleared.
 *
 * When both [savedStateHandle] and [stateSerializer] are provided, the host additionally
 * persists the processor's `ViewState` across process death: a previously saved state is
 * restored via [PresentationProcessor.restoreState] before first composition, and a **lazy**
 * save provider serializes [PresentationProcessor.stateToSave] only at the moment the
 * platform snapshots state — never during normal state emissions.
 *
 * Persistence never propagates a failure to the caller in either direction. A failed restore
 * (schema change, corrupt data) is logged and the processor starts from its initial state; a
 * failed save (oversized snapshot, an encoder error, a throwing `onSaveState`) is logged and
 * the snapshot is skipped. Persistence is an optimisation, and losing it must never take the
 * application down — least of all from inside the platform's state-saving path, which runs
 * while the app is being backgrounded.
 *
 * Used internally by Kide's navigation (`AppNavigation`), where each `NavEntry`'s
 * `ViewModelStore` retains one host per destination. Alternative hosts (e.g. for Decompose or
 * Voyager) only need to call [PresentationProcessor.close] at end-of-life.
 *
 * @param P The type of the hosted [PresentationProcessor].
 * @property processor The hosted processor.
 * @param savedStateHandle Optional handle backing `ViewState` persistence.
 * @param stateSerializer Optional serializer for the processor's `ViewState`; persistence is
 * active only when both this and [savedStateHandle] are non-null.
 */
public class ViewModelHost<P : PresentationProcessor<*, *, *>>(
    override val processor: P,
    savedStateHandle: SavedStateHandle? = null,
    stateSerializer: KSerializer<out ViewState>? = null,
) : ViewModel(), PresentationProcessorHost<P> {

    init {
        addCloseable(processor)
        if (savedStateHandle != null && stateSerializer != null) {
            connectStatePersistence(savedStateHandle, stateSerializer)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun connectStatePersistence(
        handle: SavedStateHandle,
        serializer: KSerializer<out ViewState>,
    ) {
        val stateSerializer = serializer as KSerializer<ViewState>
        val restorable = processor as PresentationProcessor<*, ViewState, *>

        handle.get<SavedState>(VIEW_STATE_KEY)?.let { saved ->
            try {
                restorable.restoreState(decodeFromSavedState(stateSerializer, saved))
            } catch (exception: Exception) {
                logW(exception) { "Failed to restore ViewState; starting from initial state" }
            }
        }
        handle.setSavedStateProvider(VIEW_STATE_KEY) {
            // This lambda runs inside the platform's state-saving path, so anything thrown here
            // propagates into the framework — on Android, a crash while the app is being
            // backgrounded. Reachable without exotic conditions: an oversized snapshot hitting
            // the binder transaction limit, a contextual or polymorphic field that fails at
            // encode time, or a user override of onSaveState that throws. Losing a snapshot is
            // a far better outcome, and it is the one the restore path above already chooses.
            try {
                restorable.stateToSave()
                    ?.let { state -> encodeToSavedState(stateSerializer, state) }
                    ?: savedState { }
            } catch (exception: Exception) {
                logW(exception) {
                    "Failed to save ViewState; this destination will start from its initial state"
                }
                savedState { }
            }
        }
    }

    private companion object {
        const val VIEW_STATE_KEY = "org.fuusio.kide.view_state"
    }
}
