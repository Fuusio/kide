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

import androidx.compose.runtime.Composable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState

private data object TestState : ViewState
private data object TestIntent : ViewIntent
private data object TestEffect : SideEffect

private class MockProcessor : PresentationProcessor<TestIntent, TestState, TestEffect>(initialState = TestState) {
    override suspend fun map(intent: TestIntent): Action<TestState, TestEffect>? = null
}

private class MockScreenNavKey(
    override val serialKey: String,
    override val screen: @Composable ((ScreenContext<MockProcessor>) -> Unit),
) : ScreenNavKey<MockProcessor> {
    override fun createProcessor(): MockProcessor = MockProcessor()
}

class ScreenNavKeyRegistryTest : DescribeSpec({

    describe("ScreenNavKeyRegistry") {

        it("should register and retrieve a ScreenNavKey") {
            val key = MockScreenNavKey("test_key_1", {})
            ScreenNavKeyRegistry.register(key)
            
            val retrieved = ScreenNavKeyRegistry.get("test_key_1")
            retrieved shouldBe key
        }

        it("should throw IllegalStateException for unregistered keys") {
            val exception = shouldThrow<IllegalStateException> {
                ScreenNavKeyRegistry.get("unregistered_key")
            }
            exception.message shouldBe "NavKey for unregistered_key was not registered. Ensure the module is initialized."
        }
    }
})
