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

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState

/**
 * A type-erased debugging handle for one attached [PresentationProcessor], giving tooling
 * (such as `KideMcpServer`) uniform access to the processor's current state, its recorded
 * trace, and intent injection — without knowing the processor's type parameters.
 */
public class DebugHandle internal constructor(
    public val name: String,
    public val processorClassName: String,
    public val recorder: FlightRecorder<*, *, *>,
    private val stateProvider: () -> Any,
    private val dispatcher: (Any) -> Unit,
) {
    /**
     * Rendering of the processor's current [ViewState].
     */
    public fun currentState(): String = stateProvider().toString()

    /**
     * Dispatches [intent] to the processor. The caller is responsible for providing an
     * instance of the processor's intent type; a mismatch throws [ClassCastException]
     * when the intent is processed.
     */
    public fun dispatch(intent: Any) {
        dispatcher(intent)
    }
}

/**
 * A global registry of debuggable [PresentationProcessor]s — the bridge between running
 * processors and out-of-process tooling such as the Kide MCP agent port.
 *
 * Attach a processor at construction time by passing a [FlightRecorder] into its
 * interceptors and registering the pair:
 *
 * ```kotlin
 * val recorder = FlightRecorder<SearchIntent, SearchViewState, SearchSideEffect>()
 * val processor = SearchProcessor(useCase, interceptors = listOf(recorder))
 * KideDebug.attach("search", processor, recorder)
 * ```
 *
 * Intended for debug builds; attach nothing in release builds and the registry stays empty.
 */
@OptIn(ExperimentalAtomicApi::class)
public object KideDebug {

    private val handlesRef = AtomicReference<Map<String, DebugHandle>>(emptyMap())

    /**
     * Registers [processor] and its [recorder] under [name], replacing any previous handle
     * with the same name (for example, after a destination is recreated).
     *
     * @return The registered [DebugHandle].
     */
    public fun <I : ViewIntent, S : ViewState, E : SideEffect> attach(
        name: String,
        processor: PresentationProcessor<I, S, E>,
        recorder: FlightRecorder<I, S, E>,
    ): DebugHandle {
        @Suppress("UNCHECKED_CAST")
        val handle = DebugHandle(
            name = name,
            processorClassName = processor::class.qualifiedName ?: "unknown",
            recorder = recorder,
            stateProvider = { processor.state },
            dispatcher = { intent -> processor.dispatch(intent as I) },
        )
        update { it + (name to handle) }
        return handle
    }

    /**
     * Removes the handle registered under [name], if any.
     */
    public fun detach(name: String) {
        update { it - name }
    }

    /**
     * A snapshot of all registered handles, keyed by name.
     */
    public fun handles(): Map<String, DebugHandle> = handlesRef.load()

    /**
     * Returns the handle registered under [name], or `null`.
     */
    public fun handle(name: String): DebugHandle? = handlesRef.load()[name]

    private fun update(transform: (Map<String, DebugHandle>) -> Map<String, DebugHandle>) {
        while (true) {
            val current = handlesRef.load()
            if (handlesRef.compareAndSet(current, transform(current))) return
        }
    }
}
