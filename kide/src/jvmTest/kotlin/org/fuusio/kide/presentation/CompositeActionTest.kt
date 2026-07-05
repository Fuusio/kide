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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

private sealed interface TestEffect : SideEffect {
    data object Triggered : TestEffect
}

class CompositeActionTest : DescribeSpec({

    describe("CompositeAction") {

        describe("data class constructor") {

            it("stores the given actions list") {
                val action = ReducerAction<TestViewState>()
                val composite = CompositeAction(actions = listOf(action))
                composite.actions shouldContainExactly listOf(action)
            }

            it("defaults cancellationKey to null") {
                val composite = CompositeAction<TestViewState, TestEffect>(actions = emptyList())
                composite.cancellationKey.shouldBeNull()
            }

            it("stores an explicit cancellationKey") {
                val composite = CompositeAction<TestViewState, TestEffect>(emptyList(), "search")
                composite.cancellationKey shouldBe "search"
            }

            it("accepts an empty actions list") {
                val composite = CompositeAction<TestViewState, TestEffect>(actions = emptyList())
                composite.actions.shouldBeEmpty()
            }
        }

        describe("companion create()") {

            it("creates with no actions") {
                val composite = CompositeAction.create<TestViewState, TestEffect>()
                composite.actions.shouldBeEmpty()
            }

            it("creates with a single action") {
                val action = ReducerAction<TestViewState>()
                val composite = CompositeAction.create(action)
                composite.actions shouldContainExactly listOf(action)
            }

            it("creates with multiple actions preserving order") {
                val a1 = ReducerAction<TestViewState>()
                val a2 = AsyncAction<TestViewState>()
                val a3 = ReducerAction<TestViewState>()
                val composite = CompositeAction.create(a1, a2, a3)
                composite.actions shouldContainExactly listOf(a1, a2, a3)
            }

            it("defaults cancellationKey to null") {
                val composite = CompositeAction.create<TestViewState, TestEffect>()
                composite.cancellationKey.shouldBeNull()
            }
        }

        describe("composite() builder") {

            it("creates with no actions") {
                val result = composite<TestViewState, TestEffect>()
                result.actions.shouldBeEmpty()
            }

            it("creates with multiple actions preserving order") {
                val a1 = ReducerAction<TestViewState>()
                val a2 = AsyncAction<TestViewState>()
                val result = composite(a1, a2)
                result.actions shouldContainExactly listOf(a1, a2)
            }

            it("defaults cancellationKey to null") {
                val result = composite<TestViewState, TestEffect>()
                result.cancellationKey.shouldBeNull()
            }

            it("stores a provided cancellationKey") {
                val result = composite<TestViewState, TestEffect>(cancellationKey = "profile")
                result.cancellationKey shouldBe "profile"
            }

            it("produces the same action list as create() for identical inputs") {
                val a1 = ReducerAction<TestViewState>()
                val a2 = AsyncAction<TestViewState>()
                composite(a1, a2).actions shouldBe CompositeAction.create(a1, a2).actions
            }
        }

        describe("heterogeneous action types") {

            it("holds ReducerAction and AsyncAction") {
                val reducer = ReducerAction<TestViewState>()
                val useCase = AsyncAction<TestViewState>()
                val composite = composite(reducer, useCase)
                composite.actions[0].shouldBeInstanceOf<ReducerAction<TestViewState>>()
                composite.actions[1].shouldBeInstanceOf<AsyncAction<TestViewState>>()
            }

            it("holds a SideEffectAction") {
                val sideEffectAction = SideEffectAction<TestViewState, TestEffect> { TestEffect.Triggered }
                val composite = CompositeAction.create(sideEffectAction)
                composite.actions.single().shouldBeInstanceOf<SideEffectAction<TestViewState, TestEffect>>()
            }

            it("holds all three action types together") {
                val reducer = ReducerAction<TestViewState>()
                val useCase = AsyncAction<TestViewState>()
                val effect = SideEffectAction<TestViewState, TestEffect> { TestEffect.Triggered }
                val composite = composite(reducer, useCase, effect)
                composite.actions shouldHaveSize 3
            }
        }

        describe("nesting") {

            it("can hold a nested CompositeAction") {
                val inner = composite<TestViewState, TestEffect>()
                val outer = composite(inner)
                outer.actions.single().shouldBeInstanceOf<CompositeAction<TestViewState, TestEffect>>()
            }

            it("preserves inner actions inside a nested CompositeAction") {
                val leaf = ReducerAction<TestViewState>()
                val inner = composite(leaf)
                val outer = composite(inner)
                val retrieved = outer.actions.single() as CompositeAction<TestViewState, TestEffect>
                retrieved.actions shouldContainExactly listOf(leaf)
            }
        }

        describe("data class semantics") {

            it("is equal to another instance with the same actions and key") {
                val action = ReducerAction<TestViewState>()
                val c1 = CompositeAction(listOf(action), "key")
                val c2 = CompositeAction(listOf(action), "key")
                c1 shouldBe c2
            }

            it("is not equal when cancellationKeys differ") {
                val c1 = CompositeAction<TestViewState, TestEffect>(emptyList(), "a")
                val c2 = CompositeAction<TestViewState, TestEffect>(emptyList(), "b")
                c1 shouldNotBe c2
            }

            it("is not equal when actions lists differ") {
                val c1 = CompositeAction(listOf(ReducerAction<TestViewState>()), null)
                val c2 = CompositeAction<TestViewState, TestEffect>(emptyList(), null)
                c1 shouldNotBe c2
            }

            it("copy() replaces cancellationKey while keeping actions") {
                val action = ReducerAction<TestViewState>()
                val original = CompositeAction(listOf(action), "search")
                val copy = original.copy(cancellationKey = "load")
                copy.cancellationKey shouldBe "load"
                copy.actions shouldContainExactly listOf(action)
            }

            it("copy() replaces actions while keeping cancellationKey") {
                val a1 = ReducerAction<TestViewState>()
                val a2 = AsyncAction<TestViewState>()
                val original = CompositeAction(listOf(a1), "key")
                val copy = original.copy(actions = listOf(a2))
                copy.actions shouldContainExactly listOf(a2)
                copy.cancellationKey shouldBe "key"
            }
        }

        describe("type hierarchy") {

            it("is an instance of Action") {
                val composite = composite<TestViewState, TestEffect>()
                composite.shouldBeInstanceOf<Action<TestViewState, TestEffect>>()
            }

            it("is an instance of PresentationComponent") {
                val composite = composite<TestViewState, TestEffect>()
                composite.shouldBeInstanceOf<PresentationComponent>()
            }
        }
    }
})
