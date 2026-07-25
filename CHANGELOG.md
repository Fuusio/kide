# Changelog

All notable changes to Kide are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Kide adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- **`kide-navigation`** — a failure while *saving* a `ViewState` no longer propagates out of
  `ViewModelHost`'s saved-state provider. That provider is invoked by the platform inside its
  state-saving path, so an exception escaping it crashed the application while it was being
  backgrounded — reachable through an oversized snapshot hitting the binder transaction limit,
  an encoder error on a contextual or polymorphic field, or a `PresentationProcessor.onSaveState`
  override that throws. The failure is now logged and the snapshot skipped, matching what the
  restore path already did: persistence fails quietly in both directions and never takes the
  application down.
- **`kide-navigation`** — restoring a back stack that names a destination this build does not
  have no longer throws. Previously an application that removed or renamed a destination
  crashed at startup for any user who had it on their saved stack — and because saved state
  survives restarts, it kept crashing until app data was cleared. Unresolvable entries now
  resolve to the first initial key with a warning. `AGENTS.md` invariant 4 calls the
  `NavKeyWrapper` format a forward-compatible contract; hard-failing on an unknown key was not.
- **`kide-navigation`** — a destination that implements `saveArgs()` but not `restoreArgs()`
  now logs a warning instead of silently discarding its navigation arguments on restore. The
  default `restoreArgs()` returns the key unchanged, so such a destination reopened with
  default arguments and nothing anywhere reported it.
- **`kide-navigation`** — `ScreenNavKeyRegistry.register` rejects a *different* destination
  registered under an already-used `serialKey` instead of silently overwriting it. Overwriting
  meant the saved back stack resolved to whichever feature initialised last, sending the user
  to the wrong screen after process death, or throwing `ClassCastException` from inside a
  composable when the two destinations used different processor types. Re-registering the same
  or an equal key remains a no-op, so a feature whose `initialize()` runs twice is unaffected.
- **sample app** — `AboutFeature` never registered `AboutNavKey`, although the About
  destination is reachable from the navigation drawer. Backgrounding on that screen and losing
  the process crashed the app on relaunch — a live instance of the failure above.

### Added

- **`kide-navigation`** — `ScreenNavKeyRegistry.find(serialKey)`, a non-throwing lookup, and
  `ScreenNavKeyRegistry.clear()` for resetting the registry between tests.

## [1.2.0] - 2026-07-25

This release is the result of an audit of the MVI core and the agent-facing trace path. Most
of it concerns **trace fidelity**: `kide-devtools` — the `FlightRecorder`, the MCP agent port
and the regression-test generator — can only be as correct as what interceptors are told, and
several things were being reported that had not happened, or not reported at all. Traces of
async-heavy screens will contain events they did not contain before.

Binary-compatible with 1.1.1; source-compatible apart from one deprecation and one newly
rejected argument combination (both below).

### Fixed

- **`kide`** — state changes made through `AsyncScope.reduce` (that is, every reduction
  performed inside an `async { }` / `useCase { }` action) are now reported to
  `KideInterceptor.onStateChanged`. Previously they were written straight to the state flow
  without notifying interceptors, so the entire asynchronous half of a processor's work —
  network results, error handling, the reduction that clears a loading flag — was missing
  from `FlightRecorder` traces, from the `kide_get_trace` agent tool, and from the
  assertions produced by `TraceTestGenerator`. Traces of async-heavy screens will now
  contain events they did not contain before.
- **`kide`** — interceptors are no longer notified from inside `MutableStateFlow.update`.
  That lambda is re-evaluated whenever it loses a compare-and-set race, which reported
  state transitions that were computed, discarded, and never applied. Reductions now run
  through an explicit compare-and-set loop that notifies exactly once, for the transition
  that won, after it has been published.
- **`kide`** — a reduction that leaves the state unchanged (returning the receiver, or an
  `equals` copy) no longer reports an `onStateChanged`. `StateFlow` conflates such an
  update and emits nothing, so the previous behaviour recorded transitions that no
  collector ever observed.
- **`kide-devtools`** — `FlightRecorder` now keeps its buffer ordered by `TraceEvent.seq`.
  Sequence numbers are allocated before the compare-and-set that inserts an event, so a
  thread holding a higher number could win the race and land first, leaving list order and
  causal order disagreeing. Everything downstream reads the buffer positionally — `events`
  is documented oldest-first, `toJson(limit)` slices the tail, and `TraceTestGenerator`
  numbers replay steps in list order — and capacity trimming drops from the front, so an
  out-of-order buffer could also evict a newer event while retaining an older one.
- **`kide`** — a `SideEffect` is reported to `KideInterceptor.onSideEffect` only once the
  side-effect channel has accepted it. The result of the send was previously discarded and
  interceptors were notified beforehand, so an effect produced while the processor was
  closing was recorded as delivered when it had in fact been dropped.
- **`kide`** — `dispatch()` on a closed processor no longer notifies interceptors. It was
  already a documented no-op, but `onIntent` fired first, leaving an intent in the trace
  with no mapping, no state change and no effect after it — indistinguishable from a `map()`
  that returned `null` or from an intent loop that had stalled.
- **`kide-devtools`** — `DebugHandle.dispatch` now throws `IllegalStateException` instead of
  silently doing nothing when the attached processor has been closed. Handles are not removed
  automatically when a screen is popped, so `kide_dispatch_intent` against a stale handle used
  to report success while changing nothing.
- **`kide`** — the keyed-cancellation job registry is no longer mutated from a job completion
  handler. `invokeOnCompletion` runs on whichever thread completed or cancelled the job, which
  put a concurrent writer on an unsynchronised map; completed jobs are now left in place and
  replaced on the next dispatch under the same key. The map is bounded by the number of
  distinct keys in use, and cancelling an already-completed job is a no-op.
- **`kide-devtools`** — `DebugHandle.dispatch` now rejects an intent that is not of the
  processor's intent type, instead of relying on an erased cast that compiled to nothing and
  let the wrong object fail deep inside `map()` — or match no branch and do nothing at all.

### Added

- **`kide`** — `PresentationProcessor.isClosed`.
- **`kide-devtools`** — `DebugHandle.intentClassName`, also reported as `intentClass` by the
  `kide_list_processors` MCP tool, so a caller knows what type to construct for
  `kide_dispatch_intent`.
- **`kide-devtools`** — `DebugHandle.isClosed`, and a `closed` field on each entry returned by
  the `kide_list_processors` MCP tool, so an agent can tell a live processor from the remains
  of a destination that has been popped.

### Changed

- **`kide`** — `KideInterceptor.onStateChanged` is now invoked *after* the new state has
  been published to `states`, rather than immediately before it is set. An interceptor that
  reads `processor.state` during the callback now observes the transition it was just told
  about. The KDoc has been updated to state the fidelity contract that `kide-devtools`
  depends on.
- **`kide`** — the threading contract is now documented rather than merely assumed:
  `processorScope` must be confined to a single thread. The default
  (`Dispatchers.Main.immediate`) and single-threaded test dispatchers satisfy it;
  `Dispatchers.Default` and thread pools do not. This constrains the processor's own
  machinery only — `dispatch` is safe from any thread, and `AsyncScope.reduce` remains safe
  to call after a `withContext`.
- **`kide`** — `composite(...)` now rejects a `cancellationKey` when none of the contained
  actions is asynchronous. Such a composite executes inline on the intent loop and is never
  launched as a cancellable job, so the key was silently ignored. `CompositeAction`'s
  constructor and `create()` are unchanged; prefer the builder.

### Deprecated

- **`kide-devtools`** — `KideDebug.attach` in favour of `KideDebug.attachTyped`, which is
  `inline` with a `reified` intent type and can therefore give the handle a real type to check
  injected intents against. `attach` remains, with unchanged behaviour and an unchanged JVM
  signature, so code compiled against 1.1.x keeps working; handles it creates accept any object
  and report their intent type as `"unknown"`. The two are separate functions only because a
  `reified` function emits no callable JVM method — `attachTyped` will take the `attach` name at
  the next major release.

## [1.1.1] - 2026-07-23

### Fixed

- **`kide-devtools`** — `KideMcpServer.start()` now binds explicitly to the IPv4 loopback
  (`127.0.0.1`) instead of `InetAddress.getLoopbackAddress()`, which could resolve to the
  IPv6 loopback (`::1`) on some Android devices. `adb forward` only ever reaches the
  device's IPv4 loopback, so the agent port was unreachable in that case.

## [1.1.0] - 2026-07-07

### Added

- **`kide-clean-architecture-test`** — a new module providing a Turbine-based testing DSL
  for exercising `UseCaseProcessor` implementations: `UseCaseProcessor.test { }` with a
  `UseCaseProcessorTestContext` exposing `dispatch(intent)`, `expectState(...)`, and
  `skipInitialState()`. Shipping it as a separate artifact (mirroring `kide-test`) keeps
  test-only dependencies out of the `kide-clean-architecture` main artifact.

### Deprecated

- **`kide-clean-architecture`** — the use-case processing types `UseCaseLogic` and
  `AbstractUseCaseLogic` are deprecated in favour of `UseCaseProcessor` and
  `AbstractUseCaseProcessor`, aligning the domain layer's naming with the MVI vocabulary
  used elsewhere in Kide. The abstract base's overridable `onIntent(intent)` is replaced by
  `map(intent)`. The deprecated types remain as `@Deprecated(ReplaceWith(...))` aliases and
  will be removed in a future major release; migrate by renaming the type and the overridden
  method.

## [1.0.0] - 2026-07-05

Initial public release, comprising:

### Added

- **`kide` (core)** — MVI presentation engine with a coroutines-only dependency:
  `PresentationProcessor` with lossless FIFO intent processing, dispatch-ordered
  synchronous reduction, exactly-once buffered side effects, and keyed cancellation for
  async actions (`async(cancellationKey = ...)`).
- **Error resilience**: exceptions thrown while mapping or executing are logged, reported
  to `KideInterceptor.onError` and the processor's overridable `onError`, and the intent
  loop keeps running.
- **`KideInterceptor`** lifecycle hooks (intents, mapped actions, state changes, side
  effects, errors) and the **`KideLog`** facade with severity levels, automatic
  class-based tagging, and lazy message evaluation.
- **ViewState persistence** across process death, kotlinx-serialization-native: opt-in
  `stateSerializer` per `ScreenNavKey` with lazy snapshots and restore-before-composition;
  same contract on Decompose via Essenty `StateKeeper`.
- **`kide-navigation`** — Navigation 3 integration: typed `ScreenNavKey`/`ScreenContext`,
  `AppNavigation`, back-stack persistence with `saveArgs`/`restoreArgs`, thread-safe
  `ScreenNavKeyRegistry`, `ViewModelHost` retention.
- **`kide-clean-architecture`** — domain/adapter/framework layer vocabulary, use-case
  pattern (`UseCaseLogic`, `AbstractUseCaseLogic`), `Feature` assembly.
- **`kide-koin`**, **`kide-decompose`**, **`kide-voyager`** — DI helpers and host
  adapters for alternative retention ecosystems.
- **`kide-devtools`** — `FlightRecorder` causal trace interceptor, console event
  streaming (`KideDevToolsInterceptor`/`KideDevToolsServer`), and the **MCP agent port**
  (`KideMcpServer`): AI coding agents can inspect live state, query traces, inject
  intents, and export recorded sessions as regression-test scaffolds. Debug builds only;
  Android `start(context)` refuses non-debuggable processes.
- Engineering: explicit API mode, Kotlin ABI validation, GitHub Actions CI, Maven Central
  publishing configuration, agent instructions (`AGENTS.md`, `skills/kide/`).

### Targets

Android, JVM (desktop), iOS (`iosArm64`, `iosSimulatorArm64`, `iosX64`).
