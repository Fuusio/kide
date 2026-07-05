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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.KideInterceptor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState

/**
 * An implementation of [KideInterceptor] that streams MVI lifecycle events to the Kide DevTools server.
 */
public class KideDevToolsInterceptor<I : ViewIntent, S : ViewState, E : SideEffect>(
    private val processorName: String,
    private val host: String = "127.0.0.1",
    private val port: Int = 8082,
    private val intentSerializer: KSerializer<I>? = null,
    private val stateSerializer: KSerializer<S>? = null,
    private val effectSerializer: KSerializer<E>? = null,
    private val json: Json = Json
) : KideInterceptor<I, S, E> {

    private val client: DevToolsClient = createDevToolsClient(host, port)

    private fun sendEvent(type: String, details: String) {
        val timestamp = getEpochMillis()
        val packet = buildJsonObject {
            put("type", type)
            put("processor", processorName)
            put("timestamp", timestamp)
            put("payload", details)
        }
        client.send(packet.toString())
    }

    override fun onIntent(intent: I): Unit {
        val payload = if (intentSerializer != null) {
            json.encodeToString(intentSerializer, intent)
        } else {
            intent.toString()
        }
        sendEvent("intent", payload)
    }

    override fun onActionMapped(intent: I, action: Action<S, E>?): Unit {
        val payload = buildJsonObject {
            put("intent", intent.toString())
            put("action", action?.toString() ?: "null")
        }
        sendEvent("action_mapped", payload.toString())
    }

    override fun onActionExecuting(action: Action<S, E>): Unit {
        sendEvent("action_executing", action.toString())
    }

    override fun onStateChanged(oldState: S, newState: S): Unit {
        val payload = buildJsonObject {
            put("oldState", if (stateSerializer != null) json.encodeToString(stateSerializer, oldState) else oldState.toString())
            put("newState", if (stateSerializer != null) json.encodeToString(stateSerializer, newState) else newState.toString())
        }
        sendEvent("state_changed", payload.toString())
    }

    override fun onSideEffect(sideEffect: E): Unit {
        val payload = if (effectSerializer != null) {
            json.encodeToString(effectSerializer, sideEffect)
        } else {
            sideEffect.toString()
        }
        sendEvent("side_effect", payload)
    }

    public fun close(): Unit {
        client.close()
    }
}
