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

package org.fuusio.kide.devtools

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Serializable
private data class TestState(val value: String) : ViewState

@Serializable
private sealed interface TestIntent : ViewIntent {
    @Serializable
    data object Reload : TestIntent
}

@Serializable
private sealed interface TestEffect : SideEffect

class KideDevToolsInterceptorTest : DescribeSpec({

    describe("KideDevToolsInterceptor") {

        it("should stream MVI events as JSON lines to the socket server") {
            val server = ServerSocket(8085)
            val packets = LinkedBlockingQueue<String>()

            val serverThread = kotlin.concurrent.thread {
                try {
                    val socket = server.accept()
                    BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8")).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            packets.offer(line!!)
                        }
                    }
                } catch (e: Exception) {}
            }

            val interceptor = KideDevToolsInterceptor<TestIntent, TestState, TestEffect>(
                processorName = "TestProcessor",
                port = 8085,
                intentSerializer = TestIntent.serializer(),
                stateSerializer = TestState.serializer()
            )

            interceptor.onIntent(TestIntent.Reload)
            interceptor.onStateChanged(TestState("old"), TestState("new"))

            val firstPacket = packets.poll(3, TimeUnit.SECONDS)
            firstPacket shouldNotBe null
            firstPacket shouldContain "\"type\":\"intent\""
            firstPacket shouldContain "\"processor\":\"TestProcessor\""

            val secondPacket = packets.poll(3, TimeUnit.SECONDS)
            secondPacket shouldNotBe null
            secondPacket shouldContain "\"type\":\"state_changed\""

            interceptor.close()
            server.close()
            serverThread.join(1000)
        }
    }
})
