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
import org.fuusio.kide.log.KideLog
import org.fuusio.kide.log.KideLogger
import org.fuusio.kide.log.LogLevel
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

/** Saves arguments but never restores them — the mistake N4 makes visible. */
private class ForgetfulArgsNavKey(
    private val projectId: String? = null,
) : ScreenNavKey<SerProcessor> {
    override val serialKey: String = "ser_forgetful"
    override val screen: @Composable ((ScreenContext<SerProcessor>) -> Unit) get() = {}
    override fun createProcessor(): SerProcessor = SerProcessor()
    override fun saveArgs(): String? = projectId
    // restoreArgs deliberately not overridden: the default returns the receiver unchanged.
}

private class RecordingLogger : KideLogger {
    val messages = mutableListOf<String>()
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        messages += message
    }
}

/** Captures everything [KideLog] emits while [block] runs, then restores the previous logger. */
private fun captureLogs(block: () -> Unit): List<String> {
    val recorder = RecordingLogger()
    val previousLogger = KideLog.logger
    val previousLevel = KideLog.minLevel
    KideLog.logger = recorder
    KideLog.minLevel = LogLevel.Verbose
    try {
        block()
    } finally {
        KideLog.logger = previousLogger
        KideLog.minLevel = previousLevel
    }
    return recorder.messages
}

class NavKeyWrapperSerializerTest : DescribeSpec({

    val json = Json

    // The registry is global process state; without this, keys registered by one case leak into
    // every later one and the specs become order-dependent.
    beforeTest { ScreenNavKeyRegistry.clear() }
    afterSpec { ScreenNavKeyRegistry.clear() }

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

        describe("dropped arguments") {

            // A destination that implements saveArgs() but not restoreArgs() serializes its
            // arguments correctly and then silently discards them, reopening with defaults —
            // a failure that surfaces only after process death.
            it("warns when restoreArgs returns the key unchanged") {
                val registered = ForgetfulArgsNavKey()
                ScreenNavKeyRegistry.register(registered)
                val encoded = json.encodeToString(
                    NavKeyWrapperSerializer,
                    NavKeyWrapper(ForgetfulArgsNavKey(projectId = "project-42")),
                )

                val logs = captureLogs {
                    val decoded = json.decodeFromString(NavKeyWrapperSerializer, encoded)
                    decoded.screenNavKey shouldBeSameInstanceAs registered
                }

                logs.any { "restoreArgs" in it && "ser_forgetful" in it } shouldBe true
            }

            it("stays silent when the destination restores its arguments properly") {
                ScreenNavKeyRegistry.register(ArgsNavKey())
                val encoded = json.encodeToString(
                    NavKeyWrapperSerializer,
                    NavKeyWrapper(ArgsNavKey(projectId = "project-42")),
                )

                val logs = captureLogs { json.decodeFromString(NavKeyWrapperSerializer, encoded) }

                logs.any { "restoreArgs" in it } shouldBe false
            }

            it("stays silent for a destination that carries no arguments") {
                ScreenNavKeyRegistry.register(PlainNavKey("ser_plain_silent"))
                val encoded = json.encodeToString(
                    NavKeyWrapperSerializer,
                    NavKeyWrapper(PlainNavKey("ser_plain_silent")),
                )

                val logs = captureLogs { json.decodeFromString(NavKeyWrapperSerializer, encoded) }

                logs.any { "restoreArgs" in it } shouldBe false
            }
        }

        describe("unresolvable destinations") {

            it("fails restoration for an unregistered serialKey when no fallback is configured") {
                val key = PlainNavKey("ser_never_registered")
                // Intentionally not registered.
                val encoded = json.encodeToString(NavKeyWrapperSerializer, NavKeyWrapper(key))

                shouldThrow<IllegalStateException> {
                    json.decodeFromString(NavKeyWrapperSerializer, encoded)
                }
            }

            // The upgrade path: a back stack saved by an older release names a destination this
            // build no longer has. Throwing would crash at startup, and because the saved state
            // survives restarts it would keep crashing until the user cleared app data.
            it("falls back to the configured key instead of throwing") {
                val fallback = PlainNavKey("ser_home")
                ScreenNavKeyRegistry.register(fallback)
                val encoded = json.encodeToString(
                    NavKeyWrapperSerializer,
                    NavKeyWrapper(PlainNavKey("ser_removed_in_this_release")),
                )

                val resilient = NavKeyWrapperSerializerImpl(fallbackKey = fallback)
                val logs = captureLogs {
                    val decoded = json.decodeFromString(resilient, encoded)
                    decoded.screenNavKey shouldBeSameInstanceAs fallback
                }

                logs.any { "ser_removed_in_this_release" in it } shouldBe true
            }

            it("still resolves registered destinations when a fallback is configured") {
                val fallback = PlainNavKey("ser_home_2")
                val real = PlainNavKey("ser_real")
                ScreenNavKeyRegistry.register(fallback)
                ScreenNavKeyRegistry.register(real)
                val encoded = json.encodeToString(NavKeyWrapperSerializer, NavKeyWrapper(real))

                val resilient = NavKeyWrapperSerializerImpl(fallbackKey = fallback)

                json.decodeFromString(resilient, encoded)
                    .screenNavKey shouldBeSameInstanceAs real
            }
        }
    }
})
