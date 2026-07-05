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
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.json.Json
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState

private data object SerState : ViewState
private data object SerIntent : ViewIntent
private data object SerEffect : SideEffect

private class SerProcessor : PresentationProcessor<SerIntent, SerState, SerEffect>(initialState = SerState) {
    override suspend fun map(intent: SerIntent): Action<SerState, SerEffect>? = null
}

/** A destination without navigation arguments (default saveArgs/restoreArgs). */
private class PlainNavKey(
    override val serialKey: String,
) : ScreenNavKey<SerProcessor> {
    override val screen: @Composable ((ScreenContext<SerProcessor>) -> Unit) get() = {}
    override fun createProcessor(): SerProcessor = SerProcessor()
}

/** A destination carrying a navigation argument persisted via saveArgs/restoreArgs. */
private class ArgsNavKey(
    val projectId: String? = null,
) : ScreenNavKey<SerProcessor> {
    override val serialKey: String = "ser_args"
    override val screen: @Composable ((ScreenContext<SerProcessor>) -> Unit) get() = {}
    override fun createProcessor(): SerProcessor = SerProcessor()
    override fun saveArgs(): String? = projectId
    override fun restoreArgs(args: String): ScreenNavKey<SerProcessor> = ArgsNavKey(args)
}

class NavKeyWrapperSerializerTest : DescribeSpec({

    val json = Json

    describe("NavKeyWrapperSerializer") {

        describe("round-trip without arguments") {

            it("decodes back to the registered key instance") {
                val key = PlainNavKey("ser_plain")
                ScreenNavKeyRegistry.register(key)

                val encoded = json.encodeToString(NavKeyWrapperSerializer, NavKeyWrapper(key))
                val decoded = json.decodeFromString(NavKeyWrapperSerializer, encoded)

                decoded.screenNavKey shouldBeSameInstanceAs key
            }

            it("does not write an args element when saveArgs returns null") {
                val key = PlainNavKey("ser_plain_no_args")
                ScreenNavKeyRegistry.register(key)

                val encoded = json.encodeToString(NavKeyWrapperSerializer, NavKeyWrapper(key))

                encoded shouldBe """{"serialKey":"ser_plain_no_args"}"""
            }
        }

        describe("round-trip with arguments") {

            it("restores the key with its arguments") {
                val registered = ArgsNavKey()
                ScreenNavKeyRegistry.register(registered)

                val original = ArgsNavKey(projectId = "project-42")
                val encoded = json.encodeToString(NavKeyWrapperSerializer, NavKeyWrapper(original))
                val decoded = json.decodeFromString(NavKeyWrapperSerializer, encoded)

                val restored = decoded.screenNavKey.shouldBeInstanceOf<ArgsNavKey>()
                restored.projectId shouldBe "project-42"
            }

            it("persists both serialKey and args in the encoded form") {
                val registered = ArgsNavKey()
                ScreenNavKeyRegistry.register(registered)

                val encoded = json.encodeToString(
                    NavKeyWrapperSerializer,
                    NavKeyWrapper(ArgsNavKey(projectId = "abc")),
                )

                encoded shouldBe """{"serialKey":"ser_args","args":"abc"}"""
            }
        }

        describe("error cases") {

            it("fails restoration for an unregistered serialKey") {
                val key = PlainNavKey("ser_never_registered")
                // Intentionally not registered.
                val encoded = json.encodeToString(NavKeyWrapperSerializer, NavKeyWrapper(key))

                shouldThrow<IllegalStateException> {
                    json.decodeFromString(NavKeyWrapperSerializer, encoded)
                }
            }
        }
    }
})
