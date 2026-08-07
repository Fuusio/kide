# Kide API reference (for agents)

Deeper details behind [SKILL.md](SKILL.md). Package root: `org.fuusio.kide`.

## Artifacts

`org.fuusio.kide:<module>` — `kide` (core, depends only on kotlinx-coroutines),
`kide-navigation` (Navigation 3 + Compose), `kide-clean-architecture`, `kide-koin`,
`kide-devtools`, `kide-clean-architecture-devtools`, `kide-decompose`, `kide-voyager`.
Targets: Android, JVM desktop, iOS.

## PresentationProcessor<I : ViewIntent, S : ViewState, E : SideEffect>

Constructor: `(initialState: S, processorScope: CoroutineScope = defaultProcessorScope(), interceptors: List<KideInterceptor<I, S, E>> = emptyList())`

| Member | Purpose |
|---|---|
| `dispatch(intent)` | Queue an intent (lossless FIFO; no-op after `close()`; safe from any thread) |
| `isClosed` | `true` once `close()` has run |
| `states: StateFlow<S>` / `state: S` | Observe / read current state |
| `sideEffects: Flow<E>` | Buffered, exactly-once, **single collector** |
| `map(intent): Action<S, E>?` | abstract; pattern-match intent → action (`null` = no-op) |
| `initializeWith(intent)` / `reduceInitialIntent` | synchronous bootstrap before first composition; **does not reach `map()`** — an intent needing suspending work must be `dispatch`ed instead (legal from a nav key's `setup`). Warns when the state comes back unchanged |
| `restoreState(state)` / `wasRestored` | host-applied persisted state (before any dispatch) |
| `onSaveState(state): S?` | prune/veto a persistence snapshot (open; default = as-is) |
| `onError(throwable, intent)` | open hook; called after logging + interceptors |
| `getComponentProcessor(kclass) { factory }` | child processors, closed with the parent |
| `close()` | idempotent end-of-life; cancels scope, closes channels |

### Threading

`processorScope` **must be single-threaded** (`Dispatchers.Main.immediate` by default;
`UnconfinedTestDispatcher`/`StandardTestDispatcher` in tests). Never pass
`Dispatchers.Default` or a thread pool. This constrains the processor's own machinery only —
`dispatch(intent)` is safe from any thread, and an `async { }` / `useCase { }` body may
`withContext(Dispatchers.IO) { ... }` and call `reduce { }` from there; state is published
with a compare-and-set.

### Execution guarantees

Intents process sequentially in dispatch order. Synchronous actions (`ReducerAction`,
`SideEffectAction`, all-sync `CompositeAction`) run inline on the loop — reductions apply
in exact dispatch order. `AsyncAction` runs in its own coroutine (never stalls the loop);
same `cancellationKey` cancels the previous still-running execution. Side effects buffer
until collected, delivered exactly once. Exceptions in `map()` or actions are caught,
logged, sent to `KideInterceptor.onError` + `onError`, and the loop continues
(`CancellationException` rethrown). Every applied state transition is reported to
interceptors exactly once, after publication; no-op reductions and reductions that lost a
compare-and-set race are not reported.

### Trace context

`TraceContext(correlationId)` is a `CoroutineContext.Element` the loop creates per intent and
installs for that intent's whole processing — including the coroutine an `AsyncAction` runs in,
and anything it reaches through `withContext`. Every interceptor callback in both layers
receives it; `currentTraceContext()` reads it from suspending code. `null` means no originating
intent (startup work, a flow emitting on its own). Ids are per-processor, not global.

### Action builders (top-level functions)

```kotlin
reduce<S> { copy(...) }                       // ReducerAction — pure, fast, inline
sideEffect<S, E> { SomeEffect(field) }        // SideEffectAction — constructs effect from state
async<S>(cancellationKey = "k") { ... }       // AsyncAction — suspend; use reduce { } inside
useCase<S>(cancellationKey = "k") { ... }     // alias of async, signals domain-layer call
composite(a, b, cancellationKey = "k")        // sequential group; async if any member is
```

**These are top-level functions and must be imported** (`org.fuusio.kide.presentation.reduce`
and friends). Inside `async`/`useCase` the receiver is `AsyncScope<S>`, whose `reduce { }`
member shadows the builder — which is why a missing import only breaks at the top level of
`map()`, and breaks confusingly (Kotlin falls through to `Iterable.reduce`).

`AsyncScope<S>` gives `state` (fresh snapshot) and `reduce { }` (compare-and-set).

A `cancellationKey` on a composite with no async member throws `IllegalArgumentException` from
every construction path — the key would otherwise be silently ignored.

## Navigation (`kide-navigation`)

- `ScreenNavKey<T>`: `serialKey` (stable literal), `screen`, `createProcessor()` (fresh
  instance), optional `setup(processor)` (bootstrap; skipped when restored), optional
  `saveArgs()`/`restoreArgs(args)` (nav-argument persistence), optional
  `stateSerializer` (ViewState persistence), optional `onBack(backStack)`.
- `ScreenNavKeyRegistry.register(key)` — at startup, before composition.
- `rememberAppNavBackStack(vararg keys)` + `AppNavigation(backStack, callbacks)`.
- `NavBackStack.navigateTo(key)` (clears stack) vs `pushTo(key)` (pushes).
- `ScreenContext<T>`: `processor`, `backStack`, `onBack`, `navigateTo(key)`,
  `callback(name)` / `openMenu()`.
- Retention: one `ViewModelHost` per destination (config-change safe); back stack and
  opted-in ViewStates survive process death.

## Persistence

ViewState: `@Serializable` state class, `@Transient` (with defaults) for ephemera,
`stateSerializer` override on the nav key. Snapshots are lazy (taken only when the
platform saves state); restore happens before first composition; decode failure logs a
warning and starts fresh. Decompose:
`retainedProcessor(key, stateKeeper, stateSerializer) { factory() }`. Keep snapshots
small (Android transaction limits) — persist inputs, not result lists.

## Observability & error handling

- `KideInterceptor<I, S, E>`: `onIntent(intent, context)`,
  `onActionMapped(intent, action, context)`, `onActionExecuting(action, context)`,
  `onStateChanged(old, new, context)`, `onSideEffect(effect, context)`,
  `onError(throwable, intent, context)` — every callback takes a trailing
  `context: TraceContext?`, all have empty defaults; pass instances via the processor
  constructor. Callbacks fire on the intent loop in processing order, `onIntent` included.
  (`PresentationProcessor.onError(throwable, intent)`, the processor's own hook for
  application code, is a different thing and takes no context.)
- `KideLog`: assign `KideLog.logger = KideLogger { level, tag, msg, thr -> ... }`
  (SAM), set `KideLog.minLevel` (`LogLevel.Verbose..Error`, `None` disables). In-class
  extensions `logV/logD/logI/logW/logE { }` derive the tag from the receiver class and
  evaluate messages lazily.

## DevTools (`kide-devtools`)

- `TraceBuffer(capacity = 500)` — the ordered event log. `events`, `record(...)`,
  `toJson(limit)`, `clear()`. Several recorders can share one and produce a single stream;
  kept sorted by `seq` even under concurrent recording.
- `TraceEvent`: `seq`, `timestamp`, `type`, `payload`, `payloadClass`, `previousState`,
  `correlationId` (groups one interaction; nullable), `source` (`Presentation` / `Domain`).
- `FlightRecorder<I, S, E>(buffer = TraceBuffer())` — presentation interceptor writing to a
  buffer. `FlightRecorder(capacity = n)` gives it a buffer of its own. `events`,
  `toJson(limit)`, `clear()` delegate.
- `UseCaseFlightRecorder<S, I>(buffer)` (module `kide-clean-architecture-devtools`) — the
  domain-layer counterpart. Pass the *same* buffer to get one merged trace.
- `KideDebug.attach(name, processor, recorder)` — `inline`/`reified`, so the handle records the
  intent type and rejects a wrongly typed injection. `detach(name)`, `handle(name)`,
  `handles()`. `DebugHandle`: `currentState()`, `isClosed`, `intentClassName`, `dispatch`.
- `KideMcpServer.start(port = 8765): Boolean` (JVM) / `start(context, port): Boolean`
  (Android; refuses unless debuggable). Never throws — `false` means the port could not be
  bound and the app runs on without an agent port. Loopback-only; debug builds only. MCP tools:
  `kide_list_processors`, `kide_get_state`, `kide_get_trace`, `kide_clear_trace`,
  `kide_dispatch_intent(processor, intent_class, intent_json)`,
  `kide_export_regression_test`.
- `KideDevToolsInterceptor(processorName, host, port, ...)` streams events to a desktop
  `KideDevToolsServer(port)` console.

## Clean Architecture module (`kide-clean-architecture`)

Markers: `Repository`, `Service`, `DataSource`, `Manager`, layer `*Component` interfaces.
Bases with a `dispatch { }` coroutine helper: `AbstractRepository`, `AbstractService`,
`AbstractManager`, `AbstractDataSource`. Use cases: `UseCaseIntent<S>`,
`UseCaseProcessor<S, I>` with `state` / `stateFlow` / `suspend dispatch(intent)` /
`interceptors`, base `AbstractUseCaseProcessor(initialState, interceptors = emptyList())`
implementing `suspend map(intent)` with `suspend reduce { }` (preferred) or
`suspend reduce(state)` (unconditional overwrite — unsafe on a shared processor).

`dispatch` is **not** a queued loop like the presentation layer's: concurrent calls interleave
with no ordering guarantee, and an exception is reported to `interceptors` and then **rethrown**
rather than swallowed. Called from an `AsyncAction`, the presentation processor's guard catches
it, so a domain failure appears in the trace from both layers.

`UseCaseInterceptor<S, I>`: `onIntent(intent, context)`,
`onStateChanged(old, new, context)`, `onError(throwable, intent, context)` — three callbacks,
since the domain has no actions to map and no side effects.
`Feature`/`ApplicationFeature` (+ `KoinFeature` in `kide-koin`) structure app assembly:
each feature registers nav keys in `initialize()` and provides a Koin module.

## Hosts

`ViewModelHost` (used by `AppNavigation` automatically) · Decompose
`InstanceKeeper.retainedProcessor(key) { }` or the persistence overload · Voyager
`ScreenModelHost`. A host owns the processor's lifetime and calls `close()` exactly once.
