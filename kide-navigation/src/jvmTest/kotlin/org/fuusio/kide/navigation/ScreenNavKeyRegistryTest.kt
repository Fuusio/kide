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
import io.kotest.matchers.string.shouldContain
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

/**
 * A key with value equality, standing in for a destination declared as a `data class` — two
 * separately constructed instances are equal, so re-registering one must be a no-op.
 */
private data class EquatableNavKey(override val serialKey: String) : ScreenNavKey<MockProcessor> {
    override val screen: @Composable ((ScreenContext<MockProcessor>) -> Unit) get() = {}
    override fun createProcessor(): MockProcessor = MockProcessor()
}

class ScreenNavKeyRegistryTest : DescribeSpec({

    // The registry is global process state; without this, keys registered by one case leak into
    // every later one and the specs become order-dependent.
    beforeTest { ScreenNavKeyRegistry.clear() }
    afterSpec { ScreenNavKeyRegistry.clear() }

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

        describe("find") {

            it("returns the registered key") {
                val key = MockScreenNavKey("test_find", {})
                ScreenNavKeyRegistry.register(key)

                ScreenNavKeyRegistry.find("test_find") shouldBe key
            }

            it("returns null instead of throwing for an unknown key") {
                ScreenNavKeyRegistry.find("no_such_key") shouldBe null
            }
        }

        describe("duplicate serialKeys") {

            // Silently overwriting means the saved back stack resolves to whichever feature
            // initialised last — the user lands on the wrong screen after process death, or a
            // ClassCastException surfaces from inside a composable if the processor types differ.
            it("rejects a different destination registered under an existing serialKey") {
                ScreenNavKeyRegistry.register(MockScreenNavKey("test_dup", {}))

                val exception = shouldThrow<IllegalStateException> {
                    ScreenNavKeyRegistry.register(MockScreenNavKey("test_dup", {}))
                }

                exception.message!! shouldContain "test_dup"
            }

            it("names the serialKey and both destinations in the failure") {
                ScreenNavKeyRegistry.register(MockScreenNavKey("test_dup_msg", {}))

                val exception = shouldThrow<IllegalStateException> {
                    ScreenNavKeyRegistry.register(MockScreenNavKey("test_dup_msg", {}))
                }

                exception.message!! shouldContain "MockScreenNavKey"
            }

            // A feature whose initialize() runs twice must stay harmless.
            it("accepts re-registration of the same instance") {
                val key = MockScreenNavKey("test_idempotent", {})

                ScreenNavKeyRegistry.register(key)
                ScreenNavKeyRegistry.register(key)

                ScreenNavKeyRegistry.get("test_idempotent") shouldBe key
            }

            it("accepts re-registration of an equal instance") {
                ScreenNavKeyRegistry.register(EquatableNavKey("test_equal"))
                ScreenNavKeyRegistry.register(EquatableNavKey("test_equal"))

                ScreenNavKeyRegistry.find("test_equal") shouldBe EquatableNavKey("test_equal")
            }
        }

        describe("clear") {

            it("removes every registered key") {
                ScreenNavKeyRegistry.register(MockScreenNavKey("test_clear", {}))

                ScreenNavKeyRegistry.clear()

                ScreenNavKeyRegistry.find("test_clear") shouldBe null
            }
        }
    }
})
