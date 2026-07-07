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

package org.fuusio.kide.domain.usecase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.domain.usecase.test.test

internal data class TestUseCaseState(val counter: Int = 0) : State

internal sealed interface TestUseCaseIntent : UseCaseIntent<TestUseCaseState> {
    data object Increment : TestUseCaseIntent
    data class SetValue(val value: Int) : TestUseCaseIntent
}

private class TestUseCaseProcessor(initialState: TestUseCaseState = TestUseCaseState()) :
    AbstractUseCaseProcessor<TestUseCaseState, TestUseCaseIntent>(initialState) {

    override suspend fun map(intent: TestUseCaseIntent) {
        when (intent) {
            TestUseCaseIntent.Increment -> reduce { state ->
                state.copy(counter = state.counter + 1)
            }
            is TestUseCaseIntent.SetValue -> reduce(TestUseCaseState(intent.value))
        }
    }
}

class AbstractUseCaseProcessorTest : DescribeSpec({

    describe("AbstractUseCaseProcessor") {
        
        it("should return the initial state") {
            val processor = TestUseCaseProcessor(TestUseCaseState(counter = 10))
            processor.state shouldBe TestUseCaseState(counter = 10)
            processor.stateFlow.value shouldBe TestUseCaseState(counter = 10)
        }

        it("should update state via a reducer function") {
            runTest {
                TestUseCaseProcessor(TestUseCaseState(counter = 5)).test {
                    skipInitialState()
                    dispatch(TestUseCaseIntent.Increment)
                    expectState(TestUseCaseState(counter = 6))
                }
            }
        }

        it("should update state directly") {
            runTest {
                TestUseCaseProcessor(TestUseCaseState(counter = 5)).test {
                    skipInitialState()
                    dispatch(TestUseCaseIntent.SetValue(42))
                    expectState { state -> state shouldBe TestUseCaseState(counter = 42) }
                }
            }
        }
    }
})
