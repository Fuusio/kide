# Kide API reference (for agents)

Deeper details behind [SKILL.md](SKILL.md). Package root: `org.fuusio.kide`.

## Artifacts

`org.fuusio.kide:<module>` — `kide` (core, depends only on kotlinx-coroutines),
`kide-navigation` (Navigation 3 + Compose), `kide-clean-architecture`, `kide-koin`,
`kide-devtools`, `kide-decompose`, `kide-voyager`. Targets: Android, JVM desktop, iOS.

## PresentationProcessor<I : ViewIntent, S : ViewState, E : SideEffect>

Constructor: `(initialState: S, processorScope: CoroutineScope = defaultProcessorScope(), interceptors: List<KideInterceptor<I, S, E>> = emptyList())`

| Member | Purpose |
|---|---|
| `dispatch(intent)` | Queue an intent (lossless FIFO; no-op after `close()`) |
| `states: StateFlow<S>` / `state: S` | Observe / read current state |
| `sideEffects: Flow<E>` | Buffered, exactly-once, **single collector** |
| `map(intent): Action<S, E>?` | abstract; pattern-match intent → action (`null` = no-op) |
| `initializeWith(intent)` / `reduceInitialIntent` | synchronous bootstrap before first composition |
| `restoreState(state)` / `wasRestored` | host-applied persisted state (before any dispatch) |
| `onSaveState(state): S?` | prune/veto a persistence snapshot (open; default = as-is) |
| `onError(throwable, intent)` | open hook; called after logging + interceptors |
| `getComponentProcessor(kclass) { factory }` | child processors, closed with the parent |
| `close()` | idempotent end-of-life; cancels scope, closes channels |

### Execution guarantees

Intents process sequentially in dispatch order. Synchronous actions (`ReducerAction`,
`SideEffectAction`, all-sync `CompositeAction`) run inline on the loop — reductions apply
in exact dispatch order. `AsyncAction` runs in its own coroutine (never stalls the loop);
same `cancellationKey` cancels the previous still-running execution. Side effects buffer
until collected, delivered exactly once. Exceptions in `map()` or actions are caught,
logged, sent to `KideInterceptor.onError` + `onError`, and the loop continues
(`CancellationException` rethrown).

### Action builders (top-level functions)

```kotlin
reduce<S> { copy(...) }                       // ReducerAction — pure, fast, inline
sideEffect<S, E> { SomeEffect(field) }        // SideEffectAction — constructs effect from state
async<S>(cancellationKey = "k") { ... }       // AsyncAction — suspend; use reduce { } inside
useCase<S>(cancellationKey = "k") { ... }     // alias of async, signals domain-layer call
composite(a, b, cancellationKey = "k")        // sequential group; async if any member is
```

Inside `async`/`useCase` the receiver is `AsyncScope<S>`: `state` (fresh snapshot) and
`reduce { }` (atomic).

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

- `KideInterceptor<I, S, E>`: `onIntent`, `onActionMapped`, `onActionExecuting`,
  `onStateChanged(old, new)`, `onSideEffect`, `onError(throwable, intent)` — all with
  empty defaults; pass instances via the processor constructor.
- `KideLog`: assign `KideLog.logger = KideLogger { level, tag, msg, thr -> ... }`
  (SAM), set `KideLog.minLevel` (`LogLevel.Verbose..Error`, `None` disables). In-class
  extensions `logV/logD/logI/logW/logE { }` derive the tag from the receiver class and
  evaluate messages lazily.

## DevTools (`kide-devtools`)

- `FlightRecorder<I, S, E>(capacity = 500)` — interceptor recording the causal trace
  (`TraceEvent`: seq, timestamp, type, payload, payloadClass, previousState);
  `events`, `toJson(limit)`, `clear()`.
- `KideDebug.attach(name, processor, recorder)` / `detach(name)` — registry for tooling.
- `KideMcpServer.start(port = 8765)` (JVM) / `KideMcpServer.start(context, port)`
  (Android; refuses unless debuggable). Loopback-only; debug builds only. MCP tools:
  `kide_list_processors`, `kide_get_state`, `kide_get_trace`, `kide_clear_trace`,
  `kide_dispatch_intent(processor, intent_class, intent_json)`,
  `kide_export_regression_test`.
- `KideDevToolsInterceptor(processorName, host, port, ...)` streams events to a desktop
  `KideDevToolsServer(port)` console.

## Clean Architecture module (`kide-clean-architecture`)

Markers: `Repository`, `Service`, `DataSource`, `Manager`, layer `*Component` interfaces.
Bases with a `dispatch { }` coroutine helper: `AbstractRepository`, `AbstractService`,
`AbstractManager`, `AbstractDataSource`. Use cases: `UseCaseIntent<S>`,
`UseCaseLogic<S, I>` with `state`/`stateFlow`/`suspend onIntent(intent)`, base
`AbstractUseCaseLogic(initialState)` with `updateState(state)` / `updateState { }`.
`Feature`/`ApplicationFeature` (+ `KoinFeature` in `kide-koin`) structure app assembly:
each feature registers nav keys in `initialize()` and provides a Koin module.

## Hosts

`ViewModelHost` (used by `AppNavigation` automatically) · Decompose
`InstanceKeeper.retainedProcessor(key) { }` or the persistence overload · Voyager
`ScreenModelHost`. A host owns the processor's lifetime and calls `close()` exactly once.
