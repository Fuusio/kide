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

/**
 * Interface representing a client that can send events to Kide DevTools companion.
 */
public interface DevToolsClient {

    /**
     * Sends a message payload to the DevTools server.
     */
    public fun send(message: String): Unit

    /**
     * Closes the client connection.
     */
    public fun close(): Unit
}

/**
 * Platform-specific factory function to instantiate a [DevToolsClient].
 */
public expect fun createDevToolsClient(host: String, port: Int): DevToolsClient

/**
 * Platform-specific epoch timestamp provider.
 */
public expect fun getEpochMillis(): Long
