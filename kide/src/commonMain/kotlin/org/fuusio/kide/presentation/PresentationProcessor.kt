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

package org.fuusio.kide.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fuusio.kide.log.logD
import org.fuusio.kide.log.logE
import org.fuusio.kide.log.logW
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * An abstract base class that serves as the core component of the Presentation Layer that manages
 * the unidirectional data flow (MVI-like architecture) for a UI component. It handles user intents,
 * by mapping them into actions, executes the actions, and updates the UI state or emits one-time
 * side effects.
 *
 * ### Execution guarantees
 * - **No intent is dropped:** Intents are queued on an unbounded [Channel] and processed
 *   sequentially in dispatch order.
 * - **Deterministic reduction order:** Synchronous actions ([ReducerAction], [SideEffectAction],
 *   and [CompositeAction]s containing only those) execute inline on the intent-processing loop,
 *   so state reductions are applied in the exact order their intents were dispatched.
 * - **Non-blocking use cases:** An [AsyncAction] (or a [CompositeAction] containing one) is
 *   executed in its own coroutine so that long-running work never stalls the intent loop.
 *   Concurrent executions can be coalesced with a cancellation key: dispatching an action with
 *   the same key cancels the previous, still-running execution.
 * - **No side effect is lost:** Side effects are buffered on a [Channel] until a collector is
 *   attached and are delivered exactly once — including effects emitted while the UI was not
 *   collecting (for example, during a configuration change).
 * - **Errors do not kill the processor:** An exception thrown while mapping an intent or
 *   executing an action (synchronous or asynchronous) is caught, logged, reported to the
 *   [interceptors] via [KideInterceptor.onError] and to [onError], and the intent loop
 *   continues with the next intent. [CancellationException]s are rethrown as usual.
 * - **Every event is attributable:** each intent is given a [TraceContext] when the loop picks
 *   it up, installed into the coroutine context for the whole of its processing and passed to
 *   every [KideInterceptor] callback. Work an [AsyncAction] goes on to do carries it
 *   automatically, including across a `withContext`, so a recorded trace can say which
 *   interaction caused a given state change rather than only that the two happened nearby.
 * - **State changes are reported exactly once:** every transition that is actually applied —
 *   whether by a [ReducerAction] on the intent loop or by [AsyncScope.reduce] inside an
 *   [AsyncAction] — is reported to [KideInterceptor.onStateChanged] exactly once, after the
 *   new state has been published to [states]. Transformations that leave the state unchanged,
 *   and transformations discarded because another coroutine won the race, are not reported.
 *   Trace fidelity is a public contract: `kide-devtools` and its regression-test generator
 *   depend on it.
 *
 * ### Threading
 * **[processorScope] must be confined to a single thread.** The default
 * ([defaultProcessorScope]) satisfies this with `Dispatchers.Main.immediate`; tests typically
 * substitute a single-threaded test dispatcher. The intent loop and the processor's internal
 * bookkeeping — the keyed-cancellation job registry and the component-processor registry — are
 * unsynchronised and rely on this. A multi-threaded scope (`Dispatchers.Default`, an arbitrary
 * thread pool) is unsupported and can corrupt that bookkeeping.
 *
 * This constrains where the processor's *own* machinery runs, not where your work runs.
 * Reductions themselves are safe from any thread: an [AsyncAction] is free to
 * `withContext(Dispatchers.IO) { ... }` and call [AsyncScope.reduce] from there, and state is
 * published with a compare-and-set so concurrent reductions cannot be lost or double-reported.
 * [dispatch] is likewise safe to call from any thread.
 *
 * ### Hosting and lifecycle
 * [PresentationProcessor] is a plain class, independent of any UI or lifecycle framework. Its
 * lifetime is defined by two points: construction (the intent loop starts in [processorScope])
 * and [close] (channels are closed and the scope is canceled). Any *host* that calls [close]
 * at the end of the processor's life can own one — an AndroidX/JetBrains `ViewModel` (see
 * `ViewModelHost`, used by Kide's own navigation), a Decompose `InstanceKeeper.Instance`, or
 * a Voyager `ScreenModel`.
 *
 * @param I The type of [ViewIntent] that this processor handles. Intents represent user actions or
 * UI events.
 * @param S The type of [ViewState] that this processor manages. View state represents the current
 * state of the UI.
 * @param E The type of [SideEffect] that this processor can emit.
 * @param initialState The initial state of the UI before any intents are processed.
 */
public abstract class PresentationProcessor<I : ViewIntent, S : ViewState, E : SideEffect>(
    initialState: S,
    /**
     * The [CoroutineScope] in which the intent loop and all [AsyncAction]s run. The processor
     * owns this scope and cancels it in [close]. Defaults to a supervisor scope provided by
     * [defaultProcessorScope]; tests can inject a test scope directly.
     *
     * **Must be confined to a single thread** — see *Threading* in the class documentation.
     * `Dispatchers.Main.immediate` (the default) and single-threaded test dispatchers such as
     * `UnconfinedTestDispatcher` or `StandardTestDispatcher` qualify; `Dispatchers.Default`
     * and other pools do not.
     */
    public val processorScope: CoroutineScope = defaultProcessorScope(),
    /**
     * Interceptors to hook into the processor lifecycle (e.g. logging, time-travel).
     */
    protected val interceptors: List<KideInterceptor<I, S, E>> = emptyList(),
) : PresentationComponent, AutoCloseable {

    private val activeJobs = mutableMapOf<String, Job>()

    /**
     * Source of [TraceContext.correlationId]. A plain `var` because it is read and written only
     * from the intent loop, which is single-threaded by contract — see *Threading*. Allocating
     * in [dispatch] instead would make this shared mutable state and force an atomic into a
     * module that has none.
     */
    private var nextCorrelationId = 0L

    @Volatile
    private var intentDispatched = false
    private val intents = Channel<I>(Channel.UNLIMITED)
    private val sideEffectChannel = Channel<E>(Channel.UNLIMITED)

    private val children = mutableMapOf<KClass<*>, PresentationProcessor<*, *, *>>()

    @Volatile
    private var closed = false

    /**
     * A [Flow] that emits one-time [SideEffect]s.
     * Side effects represent UI events that are not part of the persistent state,
     * such as navigation, showing snackbars, or playing sounds.
     *
     * Effects emitted while no collector is attached are buffered and delivered when
     * collection (re)starts. Each effect is delivered exactly once, to a **single**
     * collector — collect this flow from exactly one place, typically inside
     * `repeatOnLifecycle(Lifecycle.State.STARTED)`.
     */
    public val sideEffects: Flow<E> = sideEffectChannel.receiveAsFlow()

    /**
     * A [StateFlow] that emits the current [ViewState]. UI components should observe this flow
     * to stay updated with the latest state changes.
     */
    public val states: StateFlow<S>
        get() = _states
    private val _states = MutableStateFlow(initialState)

    /**
     * The current [ViewState]. This property provides a synchronous way to access the current
     * state value held by the [states] flow.
     */
    public val state: S get() = states.value

    /**
     * The [AsyncScope] handed to one [AsyncAction], carrying the [TraceContext] of the intent
     * that produced it. Per-invocation rather than shared, because [AsyncScope.reduce] is not a
     * suspending function and so cannot read the context it is running in.
     */
    private inner class ActionScope(private val context: TraceContext?) : AsyncScope<S> {
        override val state: S get() = states.value
        override fun reduce(transform: S.() -> S) {
            reduceState(context) { currentState -> currentState.transform() }
        }
    }

    init {
        processorScope.launch {
            for (intent in intents) {
                // Allocated here, not in dispatch: the loop is single-threaded by contract, and
                // reporting from here means the trace records the order intents were *processed*
                // in — which is the order that actually happened — and never reports an intent
                // that the processor went on to discard.
                val context = TraceContext(correlationId = nextCorrelationId++)
                interceptors.forEach { it.onIntent(intent, context) }
                try {
                    // Installed for the whole of this intent's processing, so that anything the
                    // work goes on to do — including across a withContext — carries it.
                    withContext(context) {
                        this@PresentationProcessor.logD { "Processing intent: $intent" }
                        val action = map(intent)
                        interceptors.forEach { it.onActionMapped(intent, action, context) }
                        when {
                            action == null ->
                                this@PresentationProcessor.logD { "Intent mapped to null action (no-op)" }
                            action.isSynchronous -> execute(action, context)
                            else -> launchAsync(intent, action, context)
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    handleError(throwable, intent, context)
                }
            }
        }
    }

    /**
     * Processes the given [intent]. An [ViewIntent] is first mapped to an [Action] which is then
     * executed to either reduce the [ViewState] or to post a [SideEffect].
     *
     * Intents are queued without loss and processed sequentially in dispatch order. Safe to
     * call from any thread — the single-thread requirement applies to [processorScope], not to
     * callers.
     *
     * Dispatching after [close] is a no-op. The [interceptors] are notified by the intent loop
     * when it picks an intent up, not here, so an intent that will never be processed never
     * appears in a recorded trace — where it would be indistinguishable from one mapped to
     * `null`, or from a loop that had stalled.
     */
    public fun dispatch(intent: I) {
        if (intents.trySend(intent).isSuccess) {
            intentDispatched = true
        } else {
            logW { "Intent dispatched to a closed processor; ignoring: $intent" }
        }
    }

    /**
     * Maps the given [intent] to an [Action].
     *
     * This function is invoked on the intent-processing loop; it should return quickly and
     * must not perform long-running work. Long-running work belongs in the returned
     * [AsyncAction].
     *
     * @param intent The [ViewIntent] to be mapped.
     * @return The [Action] that corresponds to the given [intent], or `null` if the [intent]
     * cannot be mapped to any [Action].
     */
    protected abstract suspend fun map(intent: I): Action<S, E>?

    private fun launchAsync(intent: I, action: Action<S, E>, context: TraceContext) {
        val key = action.cancellationKeyOrNull
        if (key != null) {
            // [activeJobs] is a plain map and is only ever touched from here — that is, from
            // the intent loop, which is single-threaded by contract (see *Threading* in the
            // class documentation). Completed jobs are deliberately left in the map rather
            // than removed by a completion handler: `invokeOnCompletion` runs on whichever
            // thread completed or cancelled the job, which would put a concurrent writer on a
            // map that has no synchronisation. The map is bounded by the number of distinct
            // cancellation keys the processor uses — a handful of string literals in practice
            // — and cancelling an already-completed job is a no-op.
            activeJobs[key]?.let { previous ->
                logD { "Cancelling previous job for key: $key" }
                previous.cancel()
            }
            // The context is passed explicitly rather than inherited: processorScope.launch
            // builds on the *scope's* context, not the calling coroutine's, so without this the
            // asynchronous half of an intent's work would lose its correlation.
            activeJobs[key] = processorScope.launch(context) { executeGuarded(intent, action, context) }
        } else {
            processorScope.launch(context) { executeGuarded(intent, action, context) }
        }
    }

    private suspend fun executeGuarded(intent: I, action: Action<S, E>, context: TraceContext) {
        try {
            execute(action, context)
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            handleError(throwable, intent, context)
        }
    }

    /**
     * Central error handling for exceptions thrown while mapping an intent or executing an
     * action. Logs the error, notifies the [interceptors] via [KideInterceptor.onError], and
     * finally invokes [onError]. Never lets the exception escape, so the intent-processing
     * loop keeps running.
     */
    private fun handleError(throwable: Throwable, intent: I, context: TraceContext?) {
        logE(throwable) { "Error while processing intent: $intent" }
        interceptors.forEach { it.onError(throwable, intent, context) }
        onError(throwable, intent)
    }

    /**
     * Invoked when an exception is thrown while mapping [intent] or executing the action it
     * was mapped to — from both the synchronous intent-processing loop and asynchronously
     * executed [AsyncAction]s. [CancellationException]s are not reported.
     *
     * The default implementation does nothing; the error has already been logged and reported
     * to the [interceptors]. Override to, for example, reduce the state to an error state or
     * emit an error side effect.
     */
    protected open fun onError(throwable: Throwable, intent: I) {
        // By default, do nothing
    }

    /**
     * Applies [transform] to the current [ViewState] and, when the transformation actually
     * changed the state, notifies the [interceptors] exactly once — after the new state has
     * been published to [states].
     *
     * The compare-and-set retry loop is written out rather than delegating to
     * [kotlinx.coroutines.flow.update] deliberately: `update` re-evaluates its lambda when it
     * loses a race, which would re-run [transform] *and* re-report a transition that was
     * ultimately discarded. Interceptors — and therefore the `FlightRecorder` trace that agent
     * tooling reads — must only ever observe transitions that were actually applied.
     *
     * [transform] must be pure and fast; it can be evaluated more than once when another
     * coroutine wins the race.
     */
    private fun reduceState(context: TraceContext?, transform: (S) -> S) {
        while (true) {
            val currentState = _states.value
            val newState = transform(currentState)
            if (_states.compareAndSet(currentState, newState)) {
                if (newState != currentState) {
                    logD { "State updated: $newState" }
                    interceptors.forEach { it.onStateChanged(currentState, newState, context) }
                }
                return
            }
        }
    }

    private suspend fun execute(action: Action<S, E>, context: TraceContext?) {
        interceptors.forEach { it.onActionExecuting(action, context) }
        when (action) {
            is AsyncAction -> {
                logD { "Executing AsyncAction${action.cancellationKeyOrNull?.let { " (key: $it)" } ?: ""}" }
                action(ActionScope(context))
            }
            is CompositeAction -> {
                logD { "Executing CompositeAction with ${action.actions.size} actions" }
                action.actions.forEach { elementAction -> execute(elementAction, context) }
            }
            is ReducerAction -> {
                logD { "Executing ReducerAction" }
                reduceState(context) { currentState -> action(currentState) }
            }
            is SideEffectAction -> {
                val effect = action(state)
                if (sideEffectChannel.trySend(effect).isSuccess) {
                    logD { "Sent SideEffect: $effect" }
                    interceptors.forEach { it.onSideEffect(effect, context) }
                } else {
                    // Only reachable once the processor is closed. Reporting the effect anyway
                    // would put a delivery in the trace that never happened.
                    logW { "SideEffect dropped, processor is closed: $effect" }
                }
            }
        }
    }

    /**
     * Synchronously computes the initial [ViewState] from a bootstrap [intent] before the first
     * composition.
     */
    protected open fun reduceInitialIntent(intent: I): S = state

    /**
     * Applies a bootstrap [intent] synchronously before the first screen composition.
     * Calls [reduceInitialIntent] and immediately updates the state.
     *
     * Must be invoked before any [ViewIntent] is dispatched to this processor.
     *
     * @throws IllegalStateException if a [ViewIntent] has already been dispatched.
     */
    public fun initializeWith(intent: I) {
        check(!intentDispatched) {
            "initializeWith must be called before any intent is dispatched to this processor."
        }
        updateState(reduceInitialIntent(intent))
    }

    /**
     * Updates the current [ViewState] to given [newState]
     */
    internal fun updateState(newState: S) {
        _states.value = newState
    }

    /**
     * `true` if [restoreState] was applied to this processor. Hosts use this to skip
     * bootstrap logic (such as [initializeWith] via a navigation key's `setup`) when the
     * state was restored from persistent storage.
     */
    public var wasRestored: Boolean = false
        private set

    /**
     * Applies a [state] restored from persistent storage (for example, after process death).
     *
     * Must be invoked before any [ViewIntent] is dispatched to this processor — typically by
     * the host immediately after construction. A restored state takes precedence over
     * bootstrap initialization; see [wasRestored].
     *
     * @throws IllegalStateException if a [ViewIntent] has already been dispatched.
     */
    public fun restoreState(state: S) {
        check(!intentDispatched) {
            "restoreState must be called before any intent is dispatched to this processor."
        }
        wasRestored = true
        updateState(state)
    }

    /**
     * Maps the current state to the state that should be persisted across process death.
     *
     * The default implementation persists the state as-is. Override to prune values that
     * `@Transient` annotations on the state class cannot express (for example, clamping a
     * large list), or return `null` to skip persisting this snapshot entirely.
     *
     * Invoked lazily by hosts at the moment the platform snapshots state — never during
     * normal state emissions.
     */
    protected open fun onSaveState(state: S): S? = state

    /**
     * Returns the state to persist for the current snapshot, as produced by [onSaveState],
     * or `null` if this snapshot should not be persisted. Called by hosts; not intended for
     * application code.
     */
    public fun stateToSave(): S? = onSaveState(state)

    /**
     * Retrieves the component [PresentationProcessor] of type [P] owned by this processor,
     * creating it with [factory] on first access.
     *
     * Component (child) processors live and die with their parent: they are retained in an
     * internal registry and closed automatically when this processor is [close]d.
     *
     * @param P The type of the component [PresentationProcessor].
     * @param processorClass The [KClass] identifying the component processor.
     * @param factory Creates the processor on first access — typically resolving it from a
     * dependency injection container.
     * @return The retained or newly created instance of [P].
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <P : PresentationProcessor<*, *, *>> getComponentProcessor(
        processorClass: KClass<P>,
        factory: () -> P,
    ): P = children.getOrPut(processorClass) { factory() } as P

    /**
     * `true` once [close] has been called.
     *
     * A closed processor ignores [dispatch], emits no further states or side effects, and its
     * [processorScope] is cancelled. Debug and host tooling uses this to tell a live processor
     * from a dead one — see `DebugHandle.isClosed` in `kide-devtools`, which stops the agent
     * port from reporting a successful intent injection into a processor that can no longer
     * act on it.
     */
    public val isClosed: Boolean get() = closed

    /**
     * Ends this processor's life: closes the intent and side-effect channels, closes all
     * component processors created via [getComponentProcessor], and cancels [processorScope].
     *
     * Idempotent. Called by the host (e.g. `ViewModelHost` on ViewModel clear) — application
     * code normally never calls this directly. After closing, [dispatch] is a no-op.
     */
    override fun close() {
        if (closed) return
        closed = true
        children.values.forEach { it.close() }
        children.clear()
        activeJobs.clear()
        intents.close()
        sideEffectChannel.close()
        processorScope.cancel()
    }
}

/**
 * Creates the default [CoroutineScope] for a [PresentationProcessor]: a [SupervisorJob] on
 * `Dispatchers.Main.immediate`.
 *
 * Note for desktop (JVM) targets: `Dispatchers.Main` requires a main-dispatcher artifact such
 * as `kotlinx-coroutines-swing` on the classpath.
 */
public fun defaultProcessorScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/**
 * The cancellation key of this action, or `null` if the action does not participate in
 * keyed cancellation. Only the outermost key of a [CompositeAction] is considered.
 */
private val Action<*, *>.cancellationKeyOrNull: String?
    get() = when (this) {
        is AsyncAction<*> -> cancellationKey
        is CompositeAction<*, *> -> cancellationKey
        else -> null
    }

/**
 * Creates a [CompositeAction] that groups multiple [actions] into a single execution unit.
 *
 * This utility function allows for the sequential execution of various action types (such as
 * reducers or side effects) in response to a single intent.
 *
 * @param S The type of [ViewState] being managed.
 * @param E The type of [SideEffect] the grouped actions can produce.
 * @param cancellationKey An optional key used to manage job cancellation. If an action with the
 * same key is dispatched while a previous one is still running, the previous job will be canceled.
 * Requires at least one contained action to be asynchronous: a composite of only synchronous
 * actions runs inline on the intent-processing loop and is never launched as a cancellable job,
 * so a key on it would be silently ignored.
 * @param actions The variable list of [Action] objects to be executed.
 * @return A [CompositeAction] containing the provided [actions].
 * @throws IllegalArgumentException if a [cancellationKey] is given but no contained action is
 * asynchronous.
 */
public fun <S : ViewState, E : SideEffect> composite(
    vararg actions: Action<S, E>,
    cancellationKey: String? = null,
): CompositeAction<S, E> = CompositeAction(actions.toList(), cancellationKey)

/**
 * Creates a [ReducerAction] that encapsulates a state transformation.
 *
 * This utility function is used to define synchronous state updates by providing a [transform]
 * lambda that takes the current [ViewState] and returns a new updated [ViewState].
 *
 * @param S The type of the [ViewState].
 * @param transform A function that defines how the current state should be transformed into a new state.
 * @return A [ReducerAction] containing the state transformation logic.
 */
public fun <S : ViewState> reduce(transform: S.() -> S): ReducerAction<S> =
    ReducerAction(transform)

/**
 * A utility method for creating a [SideEffectAction] from the given [dispatch] lambda.
 *
 * The lambda is non-suspending by contract: it constructs a side-effect object from the current
 * state. It executes inline on the intent-processing loop, ordered with surrounding reducers.
 * Asynchronous work belongs in a [useCase] action.
 *
 * @param dispatch A function that takes the current [ViewState] as receiver and returns a [SideEffect].
 * @return A [SideEffectAction] that emits the produced side effect.
 */
public fun <S : ViewState, E : SideEffect> sideEffect(dispatch: S.() -> E): SideEffectAction<S, E> =
    SideEffectAction(dispatch)

/**
 * Creates an [AsyncAction] that performs an asynchronous operation to update the [ViewState].
 *
 * This function is typically used to bridge the presentation layer with asynchronous business logic.
 * It takes a [transform] lambda that receives the current state, performs asynchronous work (such as
 * network requests or database operations), and returns the new state.
 *
 * @param S The type of [ViewState] being managed.
 * @param cancellationKey An optional key used to manage job cancellation. If an action with the same
 * key is dispatched while a previous one is still running, the previous job will be canceled.
 * @param transform A suspending function that defines the logic for state transformation.
 * @return A [AsyncAction] encapsulating the provided [transform].
 */
public fun <S : ViewState> async(
    cancellationKey: String? = null,
    transform: suspend AsyncScope<S>.() -> Unit,
): AsyncAction<S> =
    AsyncAction(cancellationKey, transform)

/**
 * Creates an [AsyncAction] that performs an asynchronous operation to execute a use case
 * and to update the [ViewState] based on the received results.
 *
 * This function is typically used to bridge the presentation layer with domain layer use cases.
 * It takes a [transform] lambda that receives the current state, performs asynchronous work (such as
 * network requests or database operations), and returns the new state.
 *
 * @param S The type of [ViewState] being managed.
 * @param cancellationKey An optional key used to manage job cancellation. If an action with the same
 * key is dispatched while a previous one is still running, the previous job will be canceled.
 * @param transform A suspending function that defines the logic for state transformation.
 * @return An [AsyncAction] encapsulating the provided [transform].
 */
public fun <S : ViewState> useCase(
    cancellationKey: String? = null,
    transform: suspend AsyncScope<S>.() -> Unit,
): AsyncAction<S> =
    AsyncAction(cancellationKey, transform)
