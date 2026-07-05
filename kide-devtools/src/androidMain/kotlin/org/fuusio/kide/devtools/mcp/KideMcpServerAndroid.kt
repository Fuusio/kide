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

import android.content.Context
import android.content.pm.ApplicationInfo
import org.fuusio.kide.log.KideLog

/**
 * Starts the Kide agent port only when the application is **debuggable**.
 *
 * This is the recommended way to start [KideMcpServer] on Android: even if the call is
 * accidentally left in a release code path, the server refuses to start and logs an
 * error instead of exposing the app's presentation layer.
 *
 * ```kotlin
 * // Application.onCreate():
 * KideMcpServer.start(this)
 * ```
 *
 * @return `true` if the server was started (or already running), `false` if it was
 * refused because the application is not debuggable.
 */
public fun KideMcpServer.start(context: Context, port: Int = 8765): Boolean {
    val isDebuggable =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!isDebuggable) {
        KideLog.e("KideMcpServer") {
            "Refusing to start the Kide agent port: application is not debuggable. " +
                "The MCP debug server must never run in release builds."
        }
        return false
    }
    start(port)
    return true
}
