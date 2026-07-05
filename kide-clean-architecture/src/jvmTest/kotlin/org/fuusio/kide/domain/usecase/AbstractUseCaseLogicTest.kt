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
import org.fuusio.kide.domain.entity.State

private data class TestUseCaseState(val counter: Int = 0) : State

private sealed interface TestUseCaseIntent : UseCaseIntent<TestUseCaseState> {
    data object Increment : TestUseCaseIntent
    data class SetValue(val value: Int) : TestUseCaseIntent
}

private class TestUseCaseLogic(initialState: TestUseCaseState = TestUseCaseState()) :
    AbstractUseCaseLogic<TestUseCaseState, TestUseCaseIntent>(initialState) {

    override suspend fun onIntent(intent: TestUseCaseIntent) {
        when (intent) {
            TestUseCaseIntent.Increment -> updateState { state ->
                state.copy(counter = state.counter + 1)
            }
            is TestUseCaseIntent.SetValue -> updateState(TestUseCaseState(intent.value))
        }
    }
}

class AbstractUseCaseLogicTest : DescribeSpec({

    describe("AbstractUseCaseLogic") {
        
        it("should return the initial state") {
            val logic = TestUseCaseLogic(TestUseCaseState(counter = 10))
            logic.state shouldBe TestUseCaseState(counter = 10)
            logic.stateFlow.value shouldBe TestUseCaseState(counter = 10)
        }

        it("should update state via a reducer function") {
            val logic = TestUseCaseLogic(TestUseCaseState(counter = 5))
            logic.onIntent(TestUseCaseIntent.Increment)
            
            logic.state shouldBe TestUseCaseState(counter = 6)
            logic.stateFlow.value shouldBe TestUseCaseState(counter = 6)
        }

        it("should update state directly") {
            val logic = TestUseCaseLogic(TestUseCaseState(counter = 5))
            logic.onIntent(TestUseCaseIntent.SetValue(42))
            
            logic.state shouldBe TestUseCaseState(counter = 42)
            logic.stateFlow.value shouldBe TestUseCaseState(counter = 42)
        }
    }
})
