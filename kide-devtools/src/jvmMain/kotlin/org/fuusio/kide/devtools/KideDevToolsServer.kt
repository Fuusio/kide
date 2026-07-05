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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.concurrent.thread

/**
 * A JVM standalone development server that listens for event streams from [KideDevToolsInterceptor]
 * and formats them cleanly in the console output.
 */
public class KideDevToolsServer(private val port: Int = 8082) {

    private var serverSocket: ServerSocket? = null
    private var running = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS")

    public fun start() {
        running = true
        val server = ServerSocket(port)
        serverSocket = server
        println("\u001B[32m[Kide DevTools Server listening on port $port]\u001B[0m")
        println("----------------------------------------------------------------------")

        thread(isDaemon = true) {
            while (running) {
                try {
                    val clientSocket = server.accept()
                    thread(isDaemon = true) {
                        BufferedReader(InputStreamReader(clientSocket.getInputStream(), "UTF-8")).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                logEvent(line!!)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }
    }

    public fun stop() {
        running = false
        serverSocket?.close()
    }

    private fun logEvent(jsonString: String) {
        try {
            val element = Json.parseToJsonElement(jsonString).jsonObject
            val type = element["type"]?.jsonPrimitive?.content ?: ""
            val processor = element["processor"]?.jsonPrimitive?.content ?: "UnknownProcessor"
            val timestamp = element["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
            val payload = element["payload"]?.jsonPrimitive?.content ?: ""

            val timeStr = timeFormat.format(Date(timestamp))

            when (type) {
                "intent" -> {
                    println("\u001B[36m$timeStr | $processor\u001B[0m")
                    println("  \u001B[34m→ INTENT:\u001B[0m $payload")
                }
                "action_mapped" -> {
                    val payloadObj = Json.parseToJsonElement(payload).jsonObject
                    val mappedAction = payloadObj["action"]?.jsonPrimitive?.content ?: "null"
                    println("  \u001B[35m├─ MAPPED ACTION:\u001B[0m $mappedAction")
                }
                "action_executing" -> {
                    println("  \u001B[33m├─ EXECUTING:\u001B[0m $payload")
                }
                "state_changed" -> {
                    val payloadObj = Json.parseToJsonElement(payload).jsonObject
                    val oldState = payloadObj["oldState"]?.jsonPrimitive?.content ?: ""
                    val newState = payloadObj["newState"]?.jsonPrimitive?.content ?: ""
                    println("  \u001B[32m├─ STATE CHANGED:\u001B[0m")
                    println("  │   - Old: $oldState")
                    println("  │   - New: $newState")
                }
                "side_effect" -> {
                    println("  \u001B[31m└─ SIDE EFFECT:\u001B[0m $payload")
                }
            }
        } catch (e: Exception) {
            println("Raw Log: $jsonString")
        }
    }
}
