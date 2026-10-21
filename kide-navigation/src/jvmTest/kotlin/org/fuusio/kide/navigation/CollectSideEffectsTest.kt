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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import org.fuusio.kide.presentation.sideEffect

/*
 * Specification for the lifecycle-bound side-effect collection behind CollectSideEffects.
 *
 * The point of the helper is what happens while the screen is not visible: effects must not
 * be consumed then, and must not be lost either. They stay buffered in the processor and are
 * delivered, in order and exactly once, when the lifecycle comes back.
 */

// ── Test fixtures ──────────────────────────────────────────────────────────────

private data object EffectViewState : ViewState

private data class Emit(val value: Int) : ViewIntent

private data class Effect(val value: Int) : SideEffect

private class EffectProcessor : PresentationProcessor<Emit, EffectViewState, Effect>(EffectViewState) {
    override suspend fun map(intent: Emit): Action<EffectViewState, Effect> =
        sideEffect { Effect(intent.value) }
}

private class TestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
}

// ── Specification ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class CollectSideEffectsTest : DescribeSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    /**
     * Starts collecting [processor]'s side effects against [owner]'s lifecycle and returns the
     * list they are delivered into. The caller cancels [scope] when done.
     */
    fun collect(
        scope: CoroutineScope,
        owner: TestLifecycleOwner,
        processor: EffectProcessor,
        minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    ): List<Int> {
        val received = mutableListOf<Int>()
        scope.launch {
            owner.lifecycle.collectSideEffects(processor, minActiveState) { received += it.value }
        }
        return received
    }

    describe("collectSideEffects") {

        it("delivers effects while the lifecycle is at least STARTED") {
            val owner = TestLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.STARTED
            val processor = EffectProcessor()
            val scope = CoroutineScope(testDispatcher)
            val received = collect(scope, owner, processor)

            processor.dispatch(Emit(1))
            processor.dispatch(Emit(2))

            scope.cancel()
            processor.close()
            received shouldBe listOf(1, 2)
        }

        it("buffers effects emitted before the lifecycle reaches STARTED") {
            val owner = TestLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.CREATED
            val processor = EffectProcessor()
            val scope = CoroutineScope(testDispatcher)
            val received = collect(scope, owner, processor)

            processor.dispatch(Emit(1))
            processor.dispatch(Emit(2))
            received.shouldBeEmpty()

            owner.registry.currentState = Lifecycle.State.STARTED

            scope.cancel()
            processor.close()
            received shouldBe listOf(1, 2)
        }

        it("stops consuming below STARTED and resumes in order, exactly once") {
            val owner = TestLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.RESUMED
            val processor = EffectProcessor()
            val scope = CoroutineScope(testDispatcher)
            val received = collect(scope, owner, processor)

            processor.dispatch(Emit(1))

            // App goes to the background: effects must wait, not be consumed.
            owner.registry.currentState = Lifecycle.State.CREATED
            processor.dispatch(Emit(2))
            processor.dispatch(Emit(3))
            received shouldBe listOf(1)

            // Back in the foreground: buffered effects arrive in order, none twice.
            owner.registry.currentState = Lifecycle.State.RESUMED
            processor.dispatch(Emit(4))

            scope.cancel()
            processor.close()
            received shouldBe listOf(1, 2, 3, 4)
        }

        it("honours a custom minActiveState") {
            val owner = TestLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.STARTED
            val processor = EffectProcessor()
            val scope = CoroutineScope(testDispatcher)
            val received = collect(scope, owner, processor, Lifecycle.State.RESUMED)

            processor.dispatch(Emit(1))
            received.shouldBeEmpty()

            owner.registry.currentState = Lifecycle.State.RESUMED

            scope.cancel()
            processor.close()
            received shouldBe listOf(1)
        }
    }
})
