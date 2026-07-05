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
import io.kotest.matchers.shouldBe

private class TestAsyncScope<S : ViewState>(initialState: S) : AsyncScope<S> {
    private var _state: S = initialState
    override val state: S get() = _state
    override fun reduce(transform: S.() -> S) {
        _state = _state.transform()
    }
}

class UseCaseScopeTest : DescribeSpec({

    describe("AsyncScope") {

        describe("state property") {

            it("returns the initial state") {
                val scope = TestAsyncScope(TestViewState(42))
                scope.state shouldBe TestViewState(42)
            }

            it("reflects the updated state after a reduce call") {
                val scope = TestAsyncScope(TestViewState(0))
                scope.reduce { copy(value = 7) }
                scope.state shouldBe TestViewState(7)
            }

            it("always returns the most recently reduced state") {
                val scope = TestAsyncScope(TestViewState(0))
                scope.reduce { copy(value = 1) }
                scope.reduce { copy(value = 2) }
                scope.state shouldBe TestViewState(2)
            }
        }

        describe("reduce") {

            it("applies the transform to the current state") {
                val scope = TestAsyncScope(TestViewState(5))
                scope.reduce { copy(value = value * 2) }
                scope.state shouldBe TestViewState(10)
            }

            it("identity transform leaves state unchanged") {
                val scope = TestAsyncScope(TestViewState(3))
                scope.reduce { this }
                scope.state shouldBe TestViewState(3)
            }

            it("accumulates state across multiple sequential calls") {
                val scope = TestAsyncScope(TestViewState(0))
                repeat(3) { scope.reduce { copy(value = value + 1) } }
                scope.state shouldBe TestViewState(3)
            }

            it("each call operates on the state produced by the previous one") {
                val scope = TestAsyncScope(TestViewState(1))
                scope.reduce { copy(value = value + 10) }
                scope.reduce { copy(value = value * 3) }
                scope.state shouldBe TestViewState(33)
            }

            it("transform receives the current state as its receiver") {
                val scope = TestAsyncScope(TestViewState(10))
                var capturedValue = -1
                scope.reduce { capturedValue = value; this }
                capturedValue shouldBe 10
            }

            it("transform sees the result of a prior reduce as its receiver") {
                val scope = TestAsyncScope(TestViewState(0))
                scope.reduce { copy(value = 5) }
                var capturedValue = -1
                scope.reduce { capturedValue = value; this }
                capturedValue shouldBe 5
            }
        }

        describe("as AsyncAction receiver") {

            it("allows reduce to be called from within a use case transform") {
                val scope = TestAsyncScope(TestViewState(0))
                val action = async<TestViewState> { reduce { copy(value = 99) } }
                action.invoke(scope)
                scope.state shouldBe TestViewState(99)
            }

            it("allows multiple reduces within a single use case transform") {
                val scope = TestAsyncScope(TestViewState(0))
                val action = async<TestViewState> {
                    reduce { copy(value = value + 1) }
                    reduce { copy(value = value + 1) }
                }
                action.invoke(scope)
                scope.state shouldBe TestViewState(2)
            }

            it("state property reflects intermediate reductions during the transform") {
                val scope = TestAsyncScope(TestViewState(0))
                var midState: TestViewState? = null
                val action = async<TestViewState> {
                    reduce { copy(value = 5) }
                    midState = state
                    reduce { copy(value = value + 1) }
                }
                action.invoke(scope)
                midState shouldBe TestViewState(5)
                scope.state shouldBe TestViewState(6)
            }

            it("an empty use case transform leaves state unchanged") {
                val scope = TestAsyncScope(TestViewState(7))
                val action = async<TestViewState> { }
                action.invoke(scope)
                scope.state shouldBe TestViewState(7)
            }
        }

        describe("as UseCaseAction receiver") {

            it("allows reduce to be called from within a use case transform") {
                val scope = TestAsyncScope(TestViewState(0))
                val action = useCase<TestViewState> { reduce { copy(value = 99) } }
                action.invoke(scope)
                scope.state shouldBe TestViewState(99)
            }

            it("allows multiple reduces within a single use case transform") {
                val scope = TestAsyncScope(TestViewState(0))
                val action = useCase<TestViewState> {
                    reduce { copy(value = value + 1) }
                    reduce { copy(value = value + 1) }
                }
                action.invoke(scope)
                scope.state shouldBe TestViewState(2)
            }

            it("state property reflects intermediate reductions during the transform") {
                val scope = TestAsyncScope(TestViewState(0))
                var midState: TestViewState? = null
                val action = useCase<TestViewState> {
                    reduce { copy(value = 5) }
                    midState = state
                    reduce { copy(value = value + 1) }
                }
                action.invoke(scope)
                midState shouldBe TestViewState(5)
                scope.state shouldBe TestViewState(6)
            }

            it("an empty use case transform leaves state unchanged") {
                val scope = TestAsyncScope(TestViewState(7))
                val action = useCase<TestViewState> { }
                action.invoke(scope)
                scope.state shouldBe TestViewState(7)
            }
        }
    }
})
