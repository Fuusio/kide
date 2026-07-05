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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue

public class JvmDevToolsClient(
    private val host: String,
    private val port: Int
) : DevToolsClient {

    private val queue = LinkedBlockingQueue<String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var closed = false

    init {
        scope.launch {
            while (!closed) {
                try {
                    val message = queue.take()
                    ensureConnection()
                    writer?.write(message + "\n")
                    writer?.flush()
                } catch (e: Exception) {
                    closeSocket()
                }
            }
        }
    }

    private fun ensureConnection() {
        if (socket == null || socket?.isClosed == true) {
            socket = Socket(host, port)
            writer = OutputStreamWriter(socket!!.getOutputStream(), "UTF-8")
        }
    }

    private fun closeSocket() {
        try { writer?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        writer = null
        socket = null
    }

    override fun send(message: String): Unit {
        if (!closed) {
            queue.offer(message)
        }
    }

    override fun close(): Unit {
        closed = true
        scope.launch {
            closeSocket()
        }
    }
}

public actual fun createDevToolsClient(host: String, port: Int): DevToolsClient =
    JvmDevToolsClient(host, port)

public actual fun getEpochMillis(): Long = System.currentTimeMillis()
