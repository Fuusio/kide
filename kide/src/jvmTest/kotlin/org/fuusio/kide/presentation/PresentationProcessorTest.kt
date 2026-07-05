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

package org.fuusio.kide.presentation

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

// ── Test fixtures ──────────────────────────────────────────────────────────────

private sealed interface TestIntent : ViewIntent {
    data object Increment : TestIntent
    data object Decrement : TestIntent
    data object Reset : TestIntent
    data object NoOp : TestIntent
    data object TriggerEffect : TestIntent
    data object TriggerEffectWithState : TestIntent
    data object UseCaseIncrement : TestIntent
    data class UseCaseWithKey(val key: String) : TestIntent
    data object DoubleIncrement : TestIntent
    data object IncrementThenEffect : TestIntent
    data object ThrowInMap : TestIntent
    data object ThrowInReducer : TestIntent
    data object ThrowInAsync : TestIntent
}

private sealed interface TestSideEffect : SideEffect {
    data object Triggered : TestSideEffect
    data class ValueSnapshot(val value: Int) : TestSideEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TestProcessor(
    initialState: TestViewState = TestViewState(),
    interceptors: List<KideInterceptor<TestIntent, TestViewState, TestSideEffect>> = emptyList(),
) : PresentationProcessor<TestIntent, TestViewState, TestSideEffect>(initialState, interceptors = interceptors) {

    val errors = mutableListOf<Pair<TestIntent, Throwable>>()

    override fun onError(throwable: Throwable, intent: TestIntent) {
        errors.add(intent to throwable)
    }

    override suspend fun map(intent: TestIntent): Action<TestViewState, TestSideEffect>? =
        when (intent) {
            TestIntent.Increment -> reduce { copy(value = value + 1) }
            TestIntent.Decrement -> reduce { copy(value = value - 1) }
            TestIntent.Reset -> reduce { copy(value = 0) }
            TestIntent.NoOp -> null
            TestIntent.TriggerEffect -> sideEffect { TestSideEffect.Triggered }
            TestIntent.TriggerEffectWithState -> sideEffect { TestSideEffect.ValueSnapshot(value) }
            TestIntent.UseCaseIncrement -> useCase { reduce { copy(value = value + 1) } }
            is TestIntent.UseCaseWithKey -> useCase(cancellationKey = intent.key) { reduce { copy(value = value + 1) } }
            TestIntent.DoubleIncrement -> composite(
                reduce { copy(value = value + 1) },
                reduce { copy(value = value + 1) },
            )
            TestIntent.IncrementThenEffect -> composite(
                reduce { copy(value = value + 1) },
                sideEffect<TestViewState, TestSideEffect> { TestSideEffect.ValueSnapshot(value) },
            )
            TestIntent.ThrowInMap -> throw IllegalStateException("map failed")
            TestIntent.ThrowInReducer -> reduce { throw IllegalStateException("reducer failed") }
            TestIntent.ThrowInAsync -> useCase { throw IllegalStateException("async failed") }
        }
}

// ── Tests ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationProcessorTest : DescribeSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    describe("PresentationProcessor") {

        describe("initial state") {

            it("state property returns the provided initial state") {
                val processor = TestProcessor(TestViewState(42))
                processor.state shouldBe TestViewState(42)
            }

            it("states flow starts with the provided initial state") {
                val processor = TestProcessor(TestViewState(10))
                processor.states.value shouldBe TestViewState(10)
            }

            it("no side effects are emitted before any dispatch") {
                val processor = TestProcessor()
                val effects = mutableListOf<TestSideEffect>()
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.sideEffects.collect { effects.add(it) } }
                job.cancel()
                scope.cancel()
                effects shouldBe emptyList()
            }
        }

        describe("updateState") {

            it("updates the state property to the new state") {
                val processor = TestProcessor()
                processor.updateState(TestViewState(99))
                processor.state shouldBe TestViewState(99)
            }

            it("updates states flow value to the new state") {
                val processor = TestProcessor()
                processor.updateState(TestViewState(55))
                processor.states.value shouldBe TestViewState(55)
            }

            it("successive calls always reflect the latest state") {
                val processor = TestProcessor()
                processor.updateState(TestViewState(1))
                processor.updateState(TestViewState(2))
                processor.updateState(TestViewState(3))
                processor.state shouldBe TestViewState(3)
            }

            it("states flow emits each distinct state update") {
                val processor = TestProcessor()
                val emitted = mutableListOf<TestViewState>()
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.states.collect { emitted.add(it) } }
                processor.updateState(TestViewState(1))
                processor.updateState(TestViewState(2))
                job.cancel()
                scope.cancel()
                emitted shouldContainExactly listOf(TestViewState(0), TestViewState(1), TestViewState(2))
            }
        }

        describe("dispatch → ReducerAction") {

            it("updates state when intent maps to a ReducerAction") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.Increment)
                processor.state shouldBe TestViewState(1)
            }

            it("accumulates state across multiple dispatches") {
                val processor = TestProcessor()
                repeat(3) { processor.dispatch(TestIntent.Increment) }
                processor.state shouldBe TestViewState(3)
            }

            it("applies the reducer transform relative to the current state") {
                val processor = TestProcessor(TestViewState(5))
                processor.dispatch(TestIntent.Decrement)
                processor.state shouldBe TestViewState(4)
            }

            it("reset reducer replaces state with the zero value") {
                val processor = TestProcessor(TestViewState(7))
                processor.dispatch(TestIntent.Reset)
                processor.state shouldBe TestViewState(0)
            }

            it("states flow emits each reduced state") {
                val processor = TestProcessor()
                val emitted = mutableListOf<TestViewState>()
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.states.collect { emitted.add(it) } }
                processor.dispatch(TestIntent.Increment)
                processor.dispatch(TestIntent.Increment)
                job.cancel()
                scope.cancel()
                emitted shouldContainExactly listOf(TestViewState(0), TestViewState(1), TestViewState(2))
            }
        }

        describe("dispatch → AsyncScope") {

            it("updates state via the async transform") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.UseCaseIncrement)
                processor.state shouldBe TestViewState(1)
            }

            it("handles multiple sequential dispatches correctly") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.UseCaseIncrement)
                processor.dispatch(TestIntent.UseCaseIncrement)
                processor.state shouldBe TestViewState(2)
            }
        }

        describe("dispatch → SideEffectAction") {

            it("emits the side effect to the sideEffects flow") {
                val processor = TestProcessor()
                val effects = mutableListOf<TestSideEffect>()
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.sideEffects.collect { effects.add(it) } }
                processor.dispatch(TestIntent.TriggerEffect)
                job.cancel()
                scope.cancel()
                effects shouldBe listOf(TestSideEffect.Triggered)
            }

            it("does not change state when emitting a side effect") {
                val processor = TestProcessor(TestViewState(3))
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.sideEffects.collect { } }
                processor.dispatch(TestIntent.TriggerEffect)
                job.cancel()
                scope.cancel()
                processor.state shouldBe TestViewState(3)
            }

            it("passes the current state to the side effect dispatch lambda") {
                val processor = TestProcessor(TestViewState(9))
                val effects = mutableListOf<TestSideEffect>()
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.sideEffects.collect { effects.add(it) } }
                processor.dispatch(TestIntent.TriggerEffectWithState)
                job.cancel()
                scope.cancel()
                effects shouldBe listOf(TestSideEffect.ValueSnapshot(9))
            }

            it("emits a side effect for each dispatch") {
                val processor = TestProcessor()
                val effects = mutableListOf<TestSideEffect>()
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.sideEffects.collect { effects.add(it) } }
                processor.dispatch(TestIntent.TriggerEffect)
                processor.dispatch(TestIntent.TriggerEffect)
                job.cancel()
                scope.cancel()
                effects shouldBe listOf(TestSideEffect.Triggered, TestSideEffect.Triggered)
            }
        }

        describe("dispatch → null action") {

            it("leaves state unchanged when intent maps to null") {
                val processor = TestProcessor(TestViewState(7))
                processor.dispatch(TestIntent.NoOp)
                processor.state shouldBe TestViewState(7)
            }
        }

        describe("dispatch → CompositeAction") {

            it("executes all contained ReducerActions in order") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.DoubleIncrement)
                processor.state shouldBe TestViewState(2)
            }

            it("applies each action on the state produced by the previous one") {
                val processor = TestProcessor(TestViewState(10))
                processor.dispatch(TestIntent.DoubleIncrement)
                processor.state shouldBe TestViewState(12)
            }

            it("executes mixed ReducerAction and SideEffectAction in order") {
                val processor = TestProcessor()
                val effects = mutableListOf<TestSideEffect>()
                val scope = CoroutineScope(testDispatcher)
                val job = scope.launch { processor.sideEffects.collect { effects.add(it) } }
                processor.dispatch(TestIntent.IncrementThenEffect)
                job.cancel()
                scope.cancel()
                processor.state shouldBe TestViewState(1)
                effects shouldBe listOf(TestSideEffect.ValueSnapshot(1))
            }
        }

        describe("cancellation key") {

            it("AsyncScopes with different keys both apply") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.UseCaseWithKey("alpha"))
                processor.dispatch(TestIntent.UseCaseWithKey("beta"))
                processor.state shouldBe TestViewState(2)
            }

            it("dispatching a AsyncScope with an existing key does not crash") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.UseCaseWithKey("search"))
                processor.dispatch(TestIntent.UseCaseWithKey("search"))
                processor.state shouldNotBe null
            }
        }

        describe("type hierarchy") {

            it("is an AutoCloseable (host contract)") {
                val processor = TestProcessor()
                processor.shouldBeInstanceOf<AutoCloseable>()
            }

            it("is an instance of PresentationComponent") {
                val processor = TestProcessor()
                processor.shouldBeInstanceOf<PresentationComponent>()
            }
        }

        describe("builder function: reduce()") {

            it("returns a ReducerAction instance") {
                val action = reduce<TestViewState> { this }
                action.shouldBeInstanceOf<ReducerAction<TestViewState>>()
            }

            it("applies the transform to the given state") {
                val action = reduce<TestViewState> { copy(value = value + 10) }
                action.invoke(TestViewState(5)) shouldBe TestViewState(15)
            }

            it("identity transform leaves state unchanged") {
                val action = reduce<TestViewState> { this }
                action.invoke(TestViewState(3)) shouldBe TestViewState(3)
            }
        }

        describe("builder function: async()") {

            it("returns a AsyncAction instance") {
                val action = async<TestViewState> { }
                action.shouldBeInstanceOf<AsyncAction<TestViewState>>()
            }

            it("applies the transform to the given state") {
                var finalState = TestViewState(5)
                val scope = object : AsyncScope<TestViewState> {
                    override val state: TestViewState get() = finalState
                    override fun reduce(transform: TestViewState.() -> TestViewState) {
                        finalState = finalState.transform()
                    }
                }
                val action = async<TestViewState> { reduce { copy(value = value * 2) } }
                action.invoke(scope)
                finalState shouldBe TestViewState(10)
            }

            it("defaults cancellationKey to null") {
                val action = async<TestViewState>(transform = { })
                action.cancellationKey.shouldBeNull()
            }

            it("stores the provided cancellationKey") {
                val action = async<TestViewState>(transform = { }, cancellationKey = "load")
                action.cancellationKey shouldBe "load"
            }
        }

        describe("builder function: useCase()") {

            it("returns a AsyncAction instance") {
                val action = useCase<TestViewState> { }
                action.shouldBeInstanceOf<AsyncAction<TestViewState>>()
            }

            it("applies the transform to the given state") {
                var finalState = TestViewState(5)
                val scope = object : AsyncScope<TestViewState> {
                    override val state: TestViewState get() = finalState
                    override fun reduce(transform: TestViewState.() -> TestViewState) {
                        finalState = finalState.transform()
                    }
                }
                val action = useCase<TestViewState> { reduce { copy(value = value * 2) } }
                action.invoke(scope)
                finalState shouldBe TestViewState(10)
            }

            it("defaults cancellationKey to null") {
                val action = useCase<TestViewState>(transform = { })
                action.cancellationKey.shouldBeNull()
            }

            it("stores the provided cancellationKey") {
                val action = useCase<TestViewState>(transform = { }, cancellationKey = "load")
                action.cancellationKey shouldBe "load"
            }
        }

        describe("builder function: sideEffect()") {

            it("returns a SideEffectAction instance") {
                val action = sideEffect<TestViewState, TestSideEffect> { TestSideEffect.Triggered }
                action.shouldBeInstanceOf<SideEffectAction<TestViewState, TestSideEffect>>()
            }

            it("dispatches the side effect produced from the current state") {
                val action = sideEffect<TestViewState, TestSideEffect> { TestSideEffect.ValueSnapshot(value) }
                action.invoke(TestViewState(7)) shouldBe TestSideEffect.ValueSnapshot(7)
            }
        }

        describe("builder function: composite()") {

            it("returns a CompositeAction instance") {
                val result = composite<TestViewState, TestSideEffect>()
                result.shouldBeInstanceOf<CompositeAction<TestViewState, TestSideEffect>>()
            }

            it("wraps the provided actions in the correct order") {
                val a1 = reduce<TestViewState> { this }
                val a2 = useCase<TestViewState> { }
                val result = composite(a1, a2)
                result.actions shouldContainExactly listOf(a1, a2)
            }

            it("defaults cancellationKey to null") {
                val result = composite<TestViewState, TestSideEffect>()
                result.cancellationKey.shouldBeNull()
            }

            it("stores the provided cancellationKey") {
                val result = composite<TestViewState, TestSideEffect>(cancellationKey = "batch")
                result.cancellationKey shouldBe "batch"
            }
        }

        describe("testing DSL helper") {

            it("collects and asserts state sequences correctly") {
                val processor = TestProcessor()
                processor.test {
                    onIntent(TestIntent.Increment)
                    onIntent(TestIntent.Increment)

                    expectStates(
                        TestViewState(0),
                        TestViewState(1),
                        TestViewState(2)
                    )
                }
            }

            it("collects and asserts new state sequences correctly, ignoring initial state") {
                val processor = TestProcessor()
                processor.test {
                    onIntent(TestIntent.Increment)
                    onIntent(TestIntent.Increment)

                    expectNewStates(
                        TestViewState(1),
                        TestViewState(2)
                    )
                }
            }

            it("collects and asserts side effects correctly") {
                val processor = TestProcessor()
                processor.test {
                    onIntent(TestIntent.TriggerEffect)
                    onIntent(TestIntent.TriggerEffect)

                    expectSideEffects(
                        TestSideEffect.Triggered,
                        TestSideEffect.Triggered
                    )
                }
            }

            it("supports dispatching multiple intents and custom list verifications") {
                val processor = TestProcessor()
                processor.test {
                    onIntents(TestIntent.Increment, TestIntent.TriggerEffect)

                    verifyStates { states ->
                        states.size shouldBe 2
                        states.last() shouldBe TestViewState(1)
                    }

                    verifySideEffects { effects ->
                        effects.size shouldBe 1
                        effects.first() shouldBe TestSideEffect.Triggered
                    }
                }
            }
        }

        describe("state persistence primitives") {

            it("restoreState applies the state and sets wasRestored") {
                val processor = TestProcessor()
                processor.wasRestored shouldBe false

                processor.restoreState(TestViewState(42))

                processor.state shouldBe TestViewState(42)
                processor.wasRestored shouldBe true
            }

            it("restoreState throws after an intent has been dispatched") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.Increment)

                io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                    processor.restoreState(TestViewState(42))
                }
            }

            it("stateToSave returns the current state by default") {
                val processor = TestProcessor(TestViewState(7))
                processor.stateToSave() shouldBe TestViewState(7)
            }

            it("stateToSave honours an onSaveState override") {
                val processor = object : PresentationProcessor<TestIntent, TestViewState, TestSideEffect>(
                    TestViewState(9)
                ) {
                    override suspend fun map(intent: TestIntent): Action<TestViewState, TestSideEffect>? = null
                    override fun onSaveState(state: TestViewState): TestViewState? =
                        if (state.value > 100) null else state.copy(value = 0)
                }

                processor.stateToSave() shouldBe TestViewState(0)
                processor.updateState(TestViewState(101))
                processor.stateToSave().shouldBeNull()
            }
        }

        describe("error handling") {

            it("keeps processing intents after map() throws") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.ThrowInMap)
                processor.dispatch(TestIntent.Increment)
                processor.state shouldBe TestViewState(1)
                processor.errors.size shouldBe 1
                processor.errors.first().first shouldBe TestIntent.ThrowInMap
                processor.errors.first().second.shouldBeInstanceOf<IllegalStateException>()
            }

            it("keeps processing intents after a synchronous action throws") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.ThrowInReducer)
                processor.dispatch(TestIntent.Increment)
                processor.state shouldBe TestViewState(1)
                processor.errors.size shouldBe 1
                processor.errors.first().first shouldBe TestIntent.ThrowInReducer
            }

            it("reports errors thrown in async actions and keeps processing") {
                val processor = TestProcessor()
                processor.dispatch(TestIntent.ThrowInAsync)
                processor.dispatch(TestIntent.Increment)
                processor.state shouldBe TestViewState(1)
                processor.errors.size shouldBe 1
                processor.errors.first().first shouldBe TestIntent.ThrowInAsync
            }

            it("notifies interceptors via onError") {
                val reported = mutableListOf<Pair<Throwable, TestIntent>>()
                val interceptor = object : KideInterceptor<TestIntent, TestViewState, TestSideEffect> {
                    override fun onError(throwable: Throwable, intent: TestIntent) {
                        reported.add(throwable to intent)
                    }
                }
                val processor = TestProcessor(interceptors = listOf(interceptor))
                processor.dispatch(TestIntent.ThrowInMap)
                reported.size shouldBe 1
                reported.first().second shouldBe TestIntent.ThrowInMap
            }
        }

        describe("interceptors") {

            it("triggers interceptor callbacks for MVI events") {
                val events = mutableListOf<String>()
                val interceptor = object : KideInterceptor<TestIntent, TestViewState, TestSideEffect> {
                    override fun onIntent(intent: TestIntent) {
                        events.add("onIntent:$intent")
                    }
                    override fun onActionMapped(intent: TestIntent, action: Action<TestViewState, TestSideEffect>?) {
                        events.add("onActionMapped:$intent")
                    }
                    override fun onActionExecuting(action: Action<TestViewState, TestSideEffect>) {
                        events.add("onActionExecuting")
                    }
                    override fun onStateChanged(oldState: TestViewState, newState: TestViewState) {
                        events.add("onStateChanged:${oldState.value}->${newState.value}")
                    }
                    override fun onSideEffect(sideEffect: TestSideEffect) {
                        events.add("onSideEffect:$sideEffect")
                    }
                }

                val processor = TestProcessor(interceptors = listOf(interceptor))
                processor.test {
                    onIntent(TestIntent.Increment)
                    onIntent(TestIntent.TriggerEffect)

                    verifyStates {
                        events.shouldContainExactly(
                            "onIntent:Increment",
                            "onActionMapped:Increment",
                            "onActionExecuting",
                            "onStateChanged:0->1",
                            "onIntent:TriggerEffect",
                            "onActionMapped:TriggerEffect",
                            "onActionExecuting",
                            "onSideEffect:Triggered"
                        )
                    }
                }
            }
        }
    }
})
