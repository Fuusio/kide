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
    /**
     * Fully qualified name of the [ViewIntent] type this processor accepts. Tooling reports it
     * so that a caller constructing an intent to inject knows what to build.
     */
    public val intentClassName: String,
    public val recorder: FlightRecorder<*, *, *>,
    private val stateProvider: () -> Any,
    private val closedProvider: () -> Boolean,
    private val intentTypeCheck: (Any) -> Boolean,
    private val dispatcher: (Any) -> Unit,
) {
    /**
     * Rendering of the processor's current [ViewState].
     */
    public fun currentState(): String = stateProvider().toString()

    /**
     * `true` if the attached processor has been closed and can no longer process intents.
     *
     * Handles are not removed automatically when a processor is closed — a destination that
     * has been popped leaves its handle in the registry until something re-attaches under the
     * same name or [KideDebug.detach] is called — so tooling should surface this rather than
     * present a dead processor as live.
     */
    public val isClosed: Boolean get() = closedProvider()

    /**
     * Dispatches [intent] to the processor, after checking that it is an instance of the
     * processor's intent type.
     *
     * The type check is done here, at the boundary, rather than being left to the cast inside
     * the handle: generics are erased, so that cast compiles to nothing and a wrong intent
     * would sail into the intent channel to fail — or silently match no branch — deep inside
     * the processor's `map()`, far from the call that caused it.
     *
     * @throws IllegalArgumentException if [intent] is not of the processor's intent type.
     * @throws IllegalStateException if the processor has already been closed. A closed
     * processor discards intents silently, so injecting into one would otherwise look like a
     * successful dispatch that mysteriously changed nothing — sending whoever is debugging
     * (often an agent) hunting for a bug in application code that does not exist.
     */
    public fun dispatch(intent: Any) {
        check(!isClosed) {
            "Processor '$name' ($processorClassName) is closed and cannot accept intents. " +
                "Its screen has most likely been popped or recreated; re-attach the current " +
                "instance with KideDebug.attachTyped(\"$name\", ...), or drop the stale handle with " +
                "KideDebug.detach(\"$name\")."
        }
        require(intentTypeCheck(intent)) {
            "Processor '$name' accepts $intentClassName, but got " +
                "${intent::class.qualifiedName ?: intent::class.simpleName}. Check the " +
                "intent class name — kide_list_processors reports the expected type as " +
                "'intentClass', and recorded intents carry it as 'payloadClass'."
        }
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
 * KideDebug.attachTyped("search", processor, recorder)
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
     * Prefer [attachTyped], which captures the processor's intent type and lets the handle
     * reject a wrongly typed injection at the boundary. A handle attached here accepts any
     * object and reports its intent type as `"unknown"`, because the type argument is erased
     * by the time this function runs.
     *
     * Handles are *not* removed when a processor is closed; call [detach] when a destination
     * goes away, or check [DebugHandle.isClosed] before trusting a handle.
     *
     * @return The registered [DebugHandle].
     */
    @Deprecated(
        message = "Use attachTyped, which type-checks injected intents at the agent port.",
        replaceWith = ReplaceWith("attachTyped(name, processor, recorder)"),
    )
    public fun <I : ViewIntent, S : ViewState, E : SideEffect> attach(
        name: String,
        processor: PresentationProcessor<I, S, E>,
        recorder: FlightRecorder<I, S, E>,
    ): DebugHandle = attachInternal(
        name = name,
        processor = processor,
        recorder = recorder,
        intentClassName = UNKNOWN_INTENT_CLASS,
        // The type argument is erased here, so no check is possible. Matches 1.1.x behaviour.
        intentTypeCheck = { true },
    )

    /**
     * Registers [processor] and its [recorder] under [name], replacing any previous handle
     * with the same name (for example, after a destination is recreated).
     *
     * Reified so that the resulting handle records the processor's intent type and can reject
     * a wrongly typed injection at the boundary — see [DebugHandle.dispatch]. This matters most
     * for the MCP agent port, where the intent is reconstructed from a class name supplied by
     * the caller.
     *
     * Handles are *not* removed when a processor is closed; call [detach] when a destination
     * goes away, or check [DebugHandle.isClosed] before trusting a handle.
     *
     * (Named separately from [attach] only to keep binary compatibility with 1.1.x, since a
     * `reified` function emits no callable JVM method. The deprecated [attach] can be removed
     * and this one renamed at the next major release.)
     *
     * @return The registered [DebugHandle].
     */
    public inline fun <reified I : ViewIntent, S : ViewState, E : SideEffect> attachTyped(
        name: String,
        processor: PresentationProcessor<I, S, E>,
        recorder: FlightRecorder<I, S, E>,
    ): DebugHandle = attachInternal(
        name = name,
        processor = processor,
        recorder = recorder,
        intentClassName = I::class.qualifiedName ?: I::class.simpleName ?: "unknown",
        // Captured at the call site, where I is still reified. This is what lets the handle
        // reject a wrongly typed intent before it reaches the processor.
        intentTypeCheck = { intent -> intent is I },
    )

    @PublishedApi
    internal fun <I : ViewIntent, S : ViewState, E : SideEffect> attachInternal(
        name: String,
        processor: PresentationProcessor<I, S, E>,
        recorder: FlightRecorder<I, S, E>,
        intentClassName: String,
        intentTypeCheck: (Any) -> Boolean,
    ): DebugHandle {
        @Suppress("UNCHECKED_CAST")
        val handle = DebugHandle(
            name = name,
            processorClassName = processor::class.qualifiedName ?: "unknown",
            intentClassName = intentClassName,
            recorder = recorder,
            stateProvider = { processor.state },
            closedProvider = { processor.isClosed },
            intentTypeCheck = intentTypeCheck,
            // Safe: DebugHandle.dispatch runs intentTypeCheck before calling this.
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

    /** Reported by handles attached without a reified intent type. */
    private const val UNKNOWN_INTENT_CLASS: String = "unknown"
}
