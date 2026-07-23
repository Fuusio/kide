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

package org.fuusio.kide.devtools.mcp

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import org.fuusio.kide.devtools.FlightRecorder
import org.fuusio.kide.devtools.KideDebug
import org.fuusio.kide.devtools.TraceTestGenerator
import org.fuusio.kide.log.KideLog
import org.fuusio.kide.presentation.ViewState

/**
 * An embedded MCP (Model Context Protocol) server that turns a **running Kide application
 * into a debugging tool for AI coding agents**.
 *
 * Where classic MVI debug tooling renders a GUI for human eyes, the agent port exposes the
 * app's live MVI machinery as MCP *tools*: an agent (Claude Code, or any MCP client) can
 * list processors, read current [ViewState]s, query the causal [FlightRecorder] trace
 * (intent → action → state diff → side effect → error), inject intents into the running
 * app, and export a recorded bug session as a regression-test scaffold.
 *
 * ### Usage (debug builds only)
 *
 * ```kotlin
 * // Application startup:
 * if (isDebuggable) KideMcpServer.start(port = 8765)
 * ```
 *
 * Android device or emulator:
 * ```
 * adb forward tcp:8765 tcp:8765
 * claude mcp add --transport http kide http://localhost:8765/mcp
 * ```
 *
 * Then ask the agent things like *"why is isLoading stuck true on the search screen?"* —
 * it will read the trace, correlate with source, and can reproduce the bug by dispatching
 * the same intents.
 *
 * The server implements the minimal JSON-RPC 2.0 subset of MCP's streamable HTTP
 * transport (`initialize`, `ping`, `tools/list`, `tools/call`; notifications are
 * accepted with `202`). It binds to the loopback interface by design; never enable it
 * in release builds.
 */
public object KideMcpServer {

    private const val PROTOCOL_VERSION = "2025-06-18"
    private const val SERVER_NAME = "kide-devtools"
    private const val SERVER_VERSION = "0.1.0"
    private const val TAG = "KideMcpServer"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var serverSocket: ServerSocket? = null

    /**
     * Starts the agent port on the loopback interface at [port]. Idempotent; call from
     * application startup in debug builds only.
     */
    public fun start(port: Int = 8765) {
        if (serverSocket != null) return
        // Bind explicitly to the IPv4 loopback: `adb forward` always connects to the
        // device's 127.0.0.1, but InetAddress.getLoopbackAddress() can resolve to the
        // IPv6 loopback (::1) on some Android devices, which `adb forward` can't reach.
        val socket = ServerSocket(port, 8, java.net.InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        KideLog.i(TAG) { "Kide agent port (MCP) listening on 127.0.0.1:$port" }
        KideLog.w(TAG) {
            "The agent port exposes app state and intent injection; it must only run in " +
                "debug builds. On Android, prefer the guarded start(context) variant."
        }
        thread(isDaemon = true, name = "KideMcpServer") {
            while (serverSocket != null) {
                try {
                    val client = socket.accept()
                    thread(isDaemon = true) { client.use(::handleConnection) }
                } catch (exception: Exception) {
                    if (serverSocket != null) {
                        KideLog.w(TAG, exception) { "Accept failed" }
                    }
                }
            }
        }
    }

    /**
     * Stops the agent port.
     */
    public fun stop() {
        val socket = serverSocket
        serverSocket = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    // ── HTTP layer ─────────────────────────────────────────────────────────────

    private fun handleConnection(client: Socket) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val head = readHead(input) ?: return
            val (requestLine, headers) = head
            val method = requestLine.substringBefore(' ').uppercase()
            when (method) {
                "POST" -> {
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = readBody(input, length)
                    handleJsonRpc(body, output)
                }
                "DELETE" -> writeResponse(output, 200, "{}")
                else -> writeResponse(output, 405, """{"error":"method not allowed"}""")
            }
            output.flush()
        } catch (exception: Exception) {
            KideLog.w(TAG, exception) { "Connection handling failed" }
        }
    }

    private fun readHead(input: InputStream): Pair<String, Map<String, String>>? {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte == -1) return null
            head.append(byte.toChar())
        }
        val lines = head.toString().split("\r\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val index = line.indexOf(':')
            if (index > 0) line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim() else null
        }.toMap()
        return lines.first() to headers
    }

    private fun readBody(input: InputStream, length: Int): String {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(buffer, read, length - read)
            if (count == -1) break
            read += count
        }
        return buffer.decodeToString(0, read)
    }

    private fun writeResponse(output: OutputStream, status: Int, body: String?) {
        val statusText = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val bytes = body?.encodeToByteArray() ?: ByteArray(0)
        val head = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(head.encodeToByteArray())
        output.write(bytes)
    }

    // ── JSON-RPC / MCP layer ───────────────────────────────────────────────────

    private fun handleJsonRpc(body: String, output: OutputStream) {
        val request = try {
            json.parseToJsonElement(body).jsonObject
        } catch (exception: Exception) {
            writeResponse(output, 200, errorResponse(JsonNull, -32700, "Parse error: ${exception.message}"))
            return
        }
        val id = request["id"] ?: JsonNull
        val method = request["method"]?.jsonPrimitive?.content ?: ""
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        if (request["id"] == null) {
            // Notification (e.g. notifications/initialized): acknowledge without a body.
            writeResponse(output, 202, null)
            return
        }

        val response = when (method) {
            "initialize" -> resultResponse(id, initializeResult(params))
            "ping" -> resultResponse(id, JsonObject(emptyMap()))
            "tools/list" -> resultResponse(id, buildJsonObject { put("tools", toolDefinitions()) })
            "tools/call" -> handleToolCall(id, params)
            else -> errorResponse(id, -32601, "Method not found: $method")
        }
        writeResponse(output, 200, response)
    }

    private fun initializeResult(params: JsonObject): JsonObject {
        val requestedVersion =
            params["protocolVersion"]?.jsonPrimitive?.content ?: PROTOCOL_VERSION
        return buildJsonObject {
            put("protocolVersion", requestedVersion)
            putJsonObject("capabilities") { putJsonObject("tools") {} }
            putJsonObject("serverInfo") {
                put("name", SERVER_NAME)
                put("version", SERVER_VERSION)
            }
            put(
                "instructions",
                "This is a live Kide MVI application exposing its presentation layer for " +
                    "debugging. Use kide_list_processors to discover screens, kide_get_state " +
                    "for current ViewStates, kide_get_trace for the causal event history " +
                    "(intents, actions, state diffs, side effects, errors), " +
                    "kide_dispatch_intent to inject intents into the running app (intent " +
                    "classes must be @Serializable), and kide_export_regression_test to turn " +
                    "a recorded session into a test scaffold.",
            )
        }
    }

    private fun resultResponse(id: JsonElement, result: JsonObject): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }.toString()

    private fun errorResponse(id: JsonElement, code: Int, message: String): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString()

    // ── Tools ──────────────────────────────────────────────────────────────────

    private fun toolDefinitions(): JsonArray = buildJsonArray {
        add(tool("kide_list_processors", "List all debuggable Kide processors with their class names and current ViewState.") {})
        add(
            tool("kide_get_state", "Get the current ViewState of a processor.") {
                stringProperty("processor", "Processor name from kide_list_processors")
            },
        )
        add(
            tool(
                "kide_get_trace",
                "Get the recorded causal MVI trace of a processor as JSON: intents, mapped actions, " +
                    "state changes (with previous state for diffing), side effects, and errors, in order.",
            ) {
                stringProperty("processor", "Processor name from kide_list_processors")
                intProperty("limit", "Maximum number of most recent events to return (default 100)")
            },
        )
        add(
            tool("kide_clear_trace", "Clear the recorded trace of a processor.") {
                stringProperty("processor", "Processor name from kide_list_processors")
            },
        )
        add(
            tool(
                "kide_dispatch_intent",
                "Inject a ViewIntent into a running processor. The intent class must be @Serializable. " +
                    "Provide the fully qualified class name (see payloadClass in the trace) and the " +
                    "intent's properties as JSON (use {} for object intents).",
            ) {
                stringProperty("processor", "Processor name from kide_list_processors")
                stringProperty("intent_class", "Fully qualified intent class name")
                stringProperty("intent_json", "JSON encoding of the intent's properties")
            },
        )
        add(
            tool(
                "kide_export_regression_test",
                "Export the recorded session of a processor as a kotest regression-test scaffold " +
                    "replaying the captured intents and asserting the captured state transitions.",
            ) {
                stringProperty("processor", "Processor name from kide_list_processors")
            },
        )
    }

    private class SchemaBuilder {
        val properties = mutableMapOf<String, JsonObject>()
        val required = mutableListOf<String>()

        fun stringProperty(name: String, description: String) {
            properties[name] = buildJsonObject {
                put("type", "string")
                put("description", description)
            }
            if (name != "limit") required.add(name)
        }

        fun intProperty(name: String, description: String) {
            properties[name] = buildJsonObject {
                put("type", "integer")
                put("description", description)
            }
        }
    }

    private fun tool(name: String, description: String, schema: SchemaBuilder.() -> Unit): JsonObject {
        val builder = SchemaBuilder().apply(schema)
        return buildJsonObject {
            put("name", name)
            put("description", description)
            putJsonObject("inputSchema") {
                put("type", "object")
                put("properties", JsonObject(builder.properties))
                put("required", buildJsonArray { builder.required.forEach { add(JsonPrimitive(it)) } })
            }
        }
    }

    private fun handleToolCall(id: JsonElement, params: JsonObject): String {
        val name = params["name"]?.jsonPrimitive?.content ?: ""
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val text = try {
            callTool(name, arguments)
        } catch (exception: Exception) {
            return resultResponse(id, toolResult("Error: ${exception.message ?: exception::class.simpleName}", isError = true))
        }
        return resultResponse(id, toolResult(text, isError = false))
    }

    private fun toolResult(text: String, isError: Boolean): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
            },
        )
        put("isError", isError)
    }

    private fun callTool(name: String, arguments: JsonObject): String {
        fun handleArg() = arguments["processor"]?.jsonPrimitive?.content?.let { processorName ->
            KideDebug.handle(processorName) ?: error("No processor named '$processorName'. Use kide_list_processors.")
        } ?: error("Missing 'processor' argument")

        return when (name) {
            "kide_list_processors" -> {
                val handles = KideDebug.handles()
                if (handles.isEmpty()) {
                    "No processors attached. Attach FlightRecorders via KideDebug.attach in the app."
                } else {
                    buildJsonArray {
                        handles.values.forEach { handle ->
                            add(
                                buildJsonObject {
                                    put("name", handle.name)
                                    put("processorClass", handle.processorClassName)
                                    put("currentState", handle.currentState())
                                    put("recordedEvents", handle.recorder.events.size)
                                },
                            )
                        }
                    }.toString()
                }
            }
            "kide_get_state" -> handleArg().currentState()
            "kide_get_trace" -> {
                val limit = arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100
                handleArg().recorder.toJson(limit)
            }
            "kide_clear_trace" -> {
                handleArg().recorder.clear()
                "Trace cleared."
            }
            "kide_dispatch_intent" -> {
                val handle = handleArg()
                val className = arguments["intent_class"]?.jsonPrimitive?.content
                    ?: error("Missing 'intent_class' argument")
                val intentJson = arguments["intent_json"]?.jsonPrimitive?.content ?: "{}"
                val type: java.lang.reflect.Type = Class.forName(className)
                val deserializer = serializer(type)
                val intent = json.decodeFromString(deserializer, intentJson)
                handle.dispatch(intent)
                "Dispatched $className to '${handle.name}'. New state: ${handle.currentState()}"
            }
            "kide_export_regression_test" -> TraceTestGenerator.generate(handleArg())
            else -> error("Unknown tool: $name")
        }
    }
}
