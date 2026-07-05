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

package org.fuusio.kide.log

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)

private class RecordingLogger : KideLogger {
    val entries = mutableListOf<LogEntry>()
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        entries.add(LogEntry(level, tag, message, throwable))
    }
}

private class TagFixture {
    fun logSomething() = logD { "hello from fixture" }
}

class KideLogTest : DescribeSpec({

    lateinit var logger: RecordingLogger

    beforeTest {
        logger = RecordingLogger()
        KideLog.logger = logger
        KideLog.minLevel = LogLevel.Verbose
    }

    afterTest {
        KideLog.logger = null
        KideLog.minLevel = LogLevel.Verbose
    }

    describe("KideLog") {

        describe("forwarding") {

            it("forwards level, tag, message, and throwable to the logger") {
                val exception = IllegalStateException("boom")
                KideLog.e("MyTag", exception) { "it failed" }

                logger.entries shouldBe listOf(
                    LogEntry(LogLevel.Error, "MyTag", "it failed", exception)
                )
            }

            it("passes null throwable for plain messages") {
                KideLog.i("Info") { "hello" }
                logger.entries.single().throwable.shouldBeNull()
            }

            it("logs each severity with the matching level") {
                KideLog.v("t") { "v" }
                KideLog.d("t") { "d" }
                KideLog.i("t") { "i" }
                KideLog.w("t") { "w" }
                KideLog.e("t") { "e" }

                logger.entries.map { it.level } shouldBe listOf(
                    LogLevel.Verbose,
                    LogLevel.Debug,
                    LogLevel.Info,
                    LogLevel.Warning,
                    LogLevel.Error,
                )
            }
        }

        describe("level filtering") {

            it("drops entries below minLevel") {
                KideLog.minLevel = LogLevel.Warning

                KideLog.d("t") { "dropped" }
                KideLog.i("t") { "dropped" }
                KideLog.w("t") { "kept" }
                KideLog.e("t") { "kept" }

                logger.entries.map { it.message } shouldBe listOf("kept", "kept")
            }

            it("LogLevel.None disables all logging") {
                KideLog.minLevel = LogLevel.None

                KideLog.e("t") { "dropped" }

                logger.entries.shouldBeEmpty()
            }

            it("isLoggable reflects minLevel and logger presence") {
                KideLog.minLevel = LogLevel.Info
                KideLog.isLoggable(LogLevel.Debug).shouldBeFalse()
                KideLog.isLoggable(LogLevel.Info).shouldBeTrue()

                KideLog.logger = null
                KideLog.isLoggable(LogLevel.Error).shouldBeFalse()
            }
        }

        describe("lazy message evaluation") {

            it("does not evaluate the message lambda when filtered out") {
                KideLog.minLevel = LogLevel.Error
                var evaluated = false

                KideLog.d("t") {
                    evaluated = true
                    "expensive"
                }

                evaluated.shouldBeFalse()
            }

            it("does not evaluate the message lambda when no logger is set") {
                KideLog.logger = null
                var evaluated = false

                KideLog.e("t") {
                    evaluated = true
                    "expensive"
                }

                evaluated.shouldBeFalse()
            }
        }

        describe("class-based tagging") {

            it("logTag is derived from the receiver's class name") {
                TagFixture().logTag shouldBe "TagFixture"
            }

            it("logD extension logs with the receiver class name as tag") {
                TagFixture().logSomething()

                logger.entries.single().tag shouldBe "TagFixture"
                logger.entries.single().message shouldBe "hello from fixture"
            }
        }
    }
})
