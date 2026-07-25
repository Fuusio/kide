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
import androidx.savedstate.serialization.encodeToSavedState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState

/*
 * ViewState persistence specification for ViewModelHost.
 *
 * The save provider registered here is invoked by the platform while the application is being
 * backgrounded. Anything it throws propagates into the framework rather than into application
 * code, so the contract is that persistence fails quietly in *both* directions: a broken
 * restore starts from the initial state, and a broken save skips the snapshot. Losing a
 * snapshot is recoverable; crashing on the way to the background is not.
 */

// ── Test fixtures ──────────────────────────────────────────────────────────────

/** Must match the private `VIEW_STATE_KEY` in ViewModelHost — it is part of the saved format. */
private const val VIEW_STATE_KEY = "org.fuusio.kide.view_state"

private data class HostViewState(val value: Int = 0) : ViewState

private data object HostIntent : ViewIntent

private data object HostEffect : SideEffect

private class HostProcessor(
    initialState: HostViewState = HostViewState(),
    private val saveOverride: ((HostViewState) -> HostViewState?)? = null,
) : PresentationProcessor<HostIntent, HostViewState, HostEffect>(initialState) {

    override suspend fun map(intent: HostIntent): Action<HostViewState, HostEffect>? = null

    // Note the explicit null check rather than `saveOverride?.invoke(state) ?: state`: an
    // override that deliberately returns null (vetoing the snapshot) must not be turned back
    // into a save by the elvis operator.
    override fun onSaveState(state: HostViewState): HostViewState? =
        if (saveOverride != null) saveOverride.invoke(state) else state
}

/*
 * Serializers are hand-written rather than generated: this module does not apply the
 * kotlinx-serialization compiler plugin, and NavKeyWrapperSerializer sets the same precedent.
 */

private object HostViewStateSerializer : KSerializer<HostViewState> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HostViewState") {
        element<Int>("value")
    }

    override fun serialize(encoder: Encoder, value: HostViewState) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.value)
        }
    }

    override fun deserialize(decoder: Decoder): HostViewState =
        decoder.decodeStructure(descriptor) {
            var value = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> value = decodeIntElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            HostViewState(value)
        }
}

/** Stands in for an oversized snapshot, a contextual field, or any other encode-time failure. */
private object FailingEncodeSerializer : KSerializer<HostViewState> {
    override val descriptor: SerialDescriptor = HostViewStateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: HostViewState) {
        throw IllegalStateException("encode failed")
    }

    override fun deserialize(decoder: Decoder): HostViewState =
        HostViewStateSerializer.deserialize(decoder)
}

/** Stands in for a schema change: the stored snapshot no longer decodes. */
private object FailingDecodeSerializer : KSerializer<HostViewState> {
    override val descriptor: SerialDescriptor = HostViewStateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: HostViewState) {
        HostViewStateSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): HostViewState =
        throw IllegalStateException("decode failed")
}

private fun handleHolding(state: HostViewState): SavedStateHandle {
    val handle = SavedStateHandle()
    handle[VIEW_STATE_KEY] = encodeToSavedState(HostViewStateSerializer, state)
    return handle
}

// ── Tests ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelHostTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("ViewModelHost ViewState persistence") {

        describe("restoring") {

            it("applies a previously saved state before anything is dispatched") {
                val processor = HostProcessor()

                ViewModelHost(processor, handleHolding(HostViewState(7)), HostViewStateSerializer)

                processor.state shouldBe HostViewState(7)
                processor.wasRestored shouldBe true
            }

            it("starts from the initial state when the snapshot no longer decodes") {
                val processor = HostProcessor(HostViewState(1))

                ViewModelHost(processor, handleHolding(HostViewState(7)), FailingDecodeSerializer)

                processor.state shouldBe HostViewState(1)
                processor.wasRestored shouldBe false
            }

            it("does not restore when no serializer is provided") {
                val processor = HostProcessor(HostViewState(1))

                ViewModelHost(processor, handleHolding(HostViewState(7)), null)

                processor.state shouldBe HostViewState(1)
                processor.wasRestored shouldBe false
            }

            it("does not restore when no handle is provided") {
                val processor = HostProcessor(HostViewState(1))

                ViewModelHost(processor, null, HostViewStateSerializer)

                processor.state shouldBe HostViewState(1)
                processor.wasRestored shouldBe false
            }
        }

        /*
         * These assert non-propagation, which is the whole contract on this side: invoking the
         * provider must return rather than throw. `shouldNotBe null` is deliberately weak —
         * `saveState()` always returns a SavedState — so read these as "this line is reached".
         * That the encoding itself is *correct* is established by the restore tests above,
         * which round-trip a state through HostViewStateSerializer and back into a processor.
         */
        describe("saving") {

            it("returns a snapshot when the state encodes cleanly") {
                val handle = SavedStateHandle()
                ViewModelHost(HostProcessor(HostViewState(3)), handle, HostViewStateSerializer)

                handle.savedStateProvider().saveState() shouldNotBe null
            }

            // Regression: the save provider runs inside the platform's state-saving path. An
            // exception escaping it is a crash while the app is being backgrounded — the least
            // debuggable moment there is, and one that no application stack frame appears in.
            it("does not propagate an encoder failure out of the save provider") {
                val handle = SavedStateHandle()
                ViewModelHost(HostProcessor(HostViewState(3)), handle, FailingEncodeSerializer)

                handle.savedStateProvider().saveState() shouldNotBe null
            }

            it("does not propagate a failure thrown by onSaveState") {
                val handle = SavedStateHandle()
                val processor = HostProcessor(
                    HostViewState(3),
                    saveOverride = { throw IllegalStateException("onSaveState failed") },
                )
                ViewModelHost(processor, handle, HostViewStateSerializer)

                handle.savedStateProvider().saveState() shouldNotBe null
            }

            it("does not throw when onSaveState vetoes the snapshot") {
                val handle = SavedStateHandle()
                val processor = HostProcessor(HostViewState(3), saveOverride = { null })
                ViewModelHost(processor, handle, HostViewStateSerializer)

                handle.savedStateProvider().saveState() shouldNotBe null
            }

            // A guard that only holds once is no guard: the platform snapshots state every time
            // the app goes to the background.
            it("survives repeated snapshots after a failure") {
                val handle = SavedStateHandle()
                ViewModelHost(HostProcessor(HostViewState(3)), handle, FailingEncodeSerializer)

                repeat(3) { handle.savedStateProvider().saveState() shouldNotBe null }
            }
        }
    }
})
