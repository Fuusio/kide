# Changelog

All notable changes to Kide are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Kide adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-08-07

**The `iosX64` target is gone.** Kotlin deprecated the Apple x86_64 targets, and Compose
Multiplatform 1.11.0 removed `iosX64` and `macosX64` from every module it publishes; the
JetBrains lifecycle and Navigation 3 artifacts did the same. Kide cannot publish a target its
dependencies no longer have, so `iosX64` is dropped from all ten modules. It is listed under
*Removed* rather than treated as a major-version break because the target no longer exists
upstream — nothing can depend on Kide's `iosX64` artifact and still resolve Compose 1.11.

Besides that, one silent failure closed. A destination that carries only an *id* had no obvious
way to load what it points at. `setup` documented `initializeWith` and nothing else, but
`initializeWith` calls `reduceInitialIntent`, which is synchronous and never reaches `map` — so
an intent that needs to await a repository matched no branch, fell through to the default, and
returned the state unchanged. No exception, no branch taken, and a screen that renders as though
it had been given nothing. `dispatch` was always legal from `setup` and does reach `map`, but
nothing said so.

Kide's own declarations are unchanged and binary-compatible with 2.0.0. The ABI dumps change
only in their target list.

> **Upgrading:** two things need attention, both inherited from upstream rather than chosen
> here.
>
> - **iOS x86_64 is no longer buildable.** If you still build for the Intel iOS simulator, that
>   path ends at Compose Multiplatform 1.10.x — for Kide, at 2.0.0. Apple Silicon simulators
>   (`iosSimulatorArm64`) and devices (`iosArm64`) are unaffected. The minimum supported iOS
>   version also rises from 13.0 to 14.0, set by Compose Multiplatform.
> - **`kide-voyager` moves across a Voyager major version**, `1.1.0-beta03` →
>   `2.2.21-1.10.3`, and exposes it with `api`, so it lands on your compile classpath.
>   Applications using `kide-voyager` need to migrate to Voyager 2.x at the same time. Other
>   modules are unaffected.

### Removed

- **all modules** — the `iosX64` target. Kotlin deprecated Apple x86_64
  ([KT-78660](https://youtrack.jetbrains.com/issue/KT-78660)) and Compose Multiplatform 1.11.0
  removed `iosX64` and `macosX64` from everything it publishes, as did
  `org.jetbrains.androidx.lifecycle` 2.11.0 and `org.jetbrains.androidx.navigation3` 1.1.1. Five
  Kide modules depend on those artifacts, and a Kotlin Multiplatform module cannot declare a
  target its dependencies do not publish. Dropping it from the other five as well keeps one
  target set across the published artifacts. Published targets are now `iosArm64`,
  `iosSimulatorArm64`, JVM, and Android.

### Changed

- **`kide`** — `PresentationProcessor.initializeWith` logs a warning when
  `reduceInitialIntent` returns the state unchanged. A bootstrap intent that changes nothing is
  nearly always an intent that belonged in `map()`: it matched no branch, or it matched one with
  no data to work from. Both were otherwise entirely silent. This is the only signal the mistake
  produces, so it is a warning rather than a throw — an unchanged state is legal, just almost
  never intended.
- **`kide-voyager`** — Voyager `1.1.0-beta03` → `2.2.21-1.10.3`. See the upgrade note above.
- **`kide-navigation`** — Navigation 3 `1.0.0-alpha05` → `1.1.1`, Navigation 3 Material
  `1.1.4` → `1.1.5`, and Lifecycle `2.10.0-alpha05` → `2.11.0`. The module no longer depends on
  pre-stable artifacts on its `api` surface.
- **build** — Kotlin `2.4.0` → `2.4.10`, Compose Multiplatform `1.10.3` → `1.11.1`, kotest
  `6.2.1` → `6.2.3`, Turbine `1.2.0` → `1.2.1`, Material 3 `1.5.0-alpha23` → `1.5.0-alpha25`.
  Unused version catalog entries (`core-ktx`, `concurrent-futures`, `coroutines-guava`,
  `junit-bom`, `material`) were removed.
- **minimum iOS version** — 13.0 → 14.0, set by Compose Multiplatform 1.11.0, not by Kide.

### Fixed

- **sample app** — the details screen never loaded its project. `DetailsNavKey` passed
  `LoadProjectDetails(projectId)` to `initializeWith`, `DetailsProcessor` handles it only in
  `map()`, and `DetailsScreen` never dispatched it — a live instance of exactly the failure the
  new warning reports. It now dispatches from `setup`.

### Documentation

- `PresentationProcessor.reduceInitialIntent` documents that it does not reach `map`, what that
  rules out, and why an unmatched intent is silent. `ScreenNavKey.setup` documents both
  bootstrap paths and the question that decides between them — does the intent need to await
  anything? — including the cost of dispatching, which is one composition rendered before the
  data arrives, and therefore an empty state that has to distinguish *loading* from *nothing to
  show*. `initializeWith` documents that it must precede any `dispatch` from the same `setup`.
- `skills/kide/SKILL.md` and `skills/kide/reference.md` updated to match.
- Six tests added to `PresentationProcessorTest`, including one asserting that an intent
  dispatched from `setup` reaches `map()` — if that ever fails, the new guidance is wrong.

### Targets

Android, JVM (desktop), iOS (`iosArm64`, `iosSimulatorArm64`). Minimum iOS 14.0.

## [2.0.0] - 2026-07-26

Two things at once: **the domain layer joins the trace**, and every known breaking change is
collected into one release so that no deprecated surface is carried forward.

Until now a recorded session covered the presentation layer only. Ask *"why didn't the saved
list update?"* and the trace showed the tap, the action, and then stopped exactly where the
real work began. Domain events now land in the same causally ordered stream, and every event —
in both layers — carries a correlation id identifying the interaction that caused it. Nothing
passes that id by hand: it travels in the coroutine context, so it survives an `AsyncAction`,
a `withContext(Dispatchers.IO)`, and the call into a use case that knows nothing about the UI.

The breaking changes exist because that was not expressible otherwise — interceptor callbacks
needed somewhere to put the context — and once one break was necessary, the rest were cheaper
to take together. See [docs/proposal-2.0.0.md](docs/proposal-2.0.0.md) for the reasoning and
[docs/migration-1.x-to-2.0.md](docs/migration-1.x-to-2.0.md) for step-by-step migration.

> **The one change the compiler cannot find for you:** a `cancellationKey` on a `composite(…)`
> with no asynchronous member now throws instead of being silently ignored. Grep for
> `cancellationKey` before upgrading. See *Changed* below.

### Added

- **`kide-devtools`** — `TraceBuffer`, the ordered capacity-bounded event log extracted from
  `FlightRecorder`. Several recorders can share one buffer and produce a single causally
  ordered stream — the mechanism by which domain-layer events will join the presentation trace
  instead of forming a second one that has to be merged by timestamp.
  `FlightRecorder(capacity = n)` still works and now means "a buffer of my own, that big";
  `FlightRecorder(buffer)` joins an existing trace. `events`, `clear()`, `toJson()` and
  `capacity` are unchanged and delegate.

- **`kide`** — `TraceContext`, a `CoroutineContext.Element` identifying the intent whose
  processing is in flight, and `currentTraceContext()` for reading it. A processor creates one
  per intent and installs it for the whole of that intent's processing, so work an
  `AsyncAction` goes on to do carries it automatically — including across a
  `withContext(Dispatchers.IO)` — without any call site having to thread it by hand.
- **`kide-clean-architecture`** — `UseCaseInterceptor`, the domain-layer counterpart of
  `KideInterceptor`, and `UseCaseProcessor.interceptors` to attach them. Three callbacks rather
  than six: the domain has no actions to map and no side effects to emit.

  This closes the trace's blind spot. Domain state changes previously reached no
  `FlightRecorder`, no `kide_get_trace` and nothing `TraceTestGenerator` produced, even though
  the module's own documentation places the real business logic there — so an agent asked *"why
  did the saved list not update?"* could only answer if the answer happened to live in the
  presentation layer. Because the correlation rides the coroutine context, a domain event
  carries the id of the `ViewIntent` that caused it without anything passing it by hand,
  including across a `withContext(Dispatchers.IO)`.
- **`kide-clean-architecture-devtools`** — a new module, containing `UseCaseFlightRecorder`.
  Give it the same `TraceBuffer` as a screen's `FlightRecorder` and both layers write one
  causally ordered trace. It is its own artifact because it bridges two optional modules:
  `kide-devtools` must not drag the Clean Architecture layer into every debug build, and
  `kide-clean-architecture` must not depend on debug tooling — the same split
  `kide-clean-architecture-test` already makes for test-only dependencies.
- **`kide-devtools`** — `TraceEventSource` (`Presentation` / `Domain`) and `TraceEvent.source`.
  Kept orthogonal to `TraceEventType` rather than adding `UseCaseIntent` and
  `UseCaseStateChanged` constants to it: an intent is an intent whichever layer dispatched it,
  and a parallel set of constants would have needed a third the moment domain errors mattered.
- **`kide-devtools`** — `TraceEvent.correlationId`, letting a recorded trace group everything
  produced by one interaction. `null` when an event had no originating intent, which is
  legitimate rather than a gap. The field is defaulted, so traces persisted by an older build
  still decode.

### Fixed

- **`kide-devtools`** — `KideMcpServer.start` no longer crashes the application when the port
  cannot be bound. Called from `Application.onCreate`, a `BindException` took the whole app
  down before it drew a frame — and the trigger is mundane: reinstalling over a running build,
  or a previous process whose socket is still in `TIME_WAIT`, leaves the port occupied. A
  debugging tool that can kill the application it exists to debug is worse than no tool. The
  failure is now logged with the likely cause, `SO_REUSEADDR` is set so a restart can reclaim
  a lingering port, and `start` returns `Boolean` rather than `Unit` so a caller can tell.

### Changed

- **`kide`** — **every `KideInterceptor` callback now takes a trailing
  `context: TraceContext?`.** This is what allows a trace to answer *"which tap caused this
  state change?"* rather than only *"these happened near each other"* — the distinction that
  matters precisely when a trace is being read, because concurrency is usually why it is being
  read. Passing a type rather than a bare id leaves room to record spans or dispatch depth
  later without changing these signatures again.
  **Migration:** add `context: TraceContext?` to each overridden callback and ignore it if
  unused. The compiler finds every site.
- **`kide-clean-architecture`** — `AbstractUseCaseProcessor.reduce` is now `suspend`, in both
  overloads. A non-suspending function cannot read the coroutine context, and reading it is how
  a reduction learns which intent it belongs to. In practice this breaks nothing: `reduce` is
  called from `map`, which is already `suspend`.
  **Migration:** none for the usual case. A `reduce` called from a non-suspending helper needs
  that helper marked `suspend` too.
- **`kide-clean-architecture`** — domain reductions now run through a compare-and-set loop that
  logs and notifies once, for the transition that won, after it is published — the same shape
  `PresentationProcessor` adopted in 1.2.0. Previously `MutableStateFlow.update` re-evaluated
  its lambda when it lost a race, logging states that were computed and discarded, and
  `reduce(state)` overwrote the flow directly with no compare-and-set at all. *(Findings C2 and
  C4.)*
- **`kide`** — **`onIntent` is now reported by the intent loop rather than by `dispatch`.**
  It could not carry a `TraceContext` otherwise: the context belongs to an intent's
  *processing*, and `dispatch` runs before the loop has seen anything, so the first event of
  every causal chain would have been the one event with no correlation.

  Three long-standing warts went with it. `dispatch` no longer needs a `closed` guard — that
  existed only to stop interceptors being told about an intent that would never be processed.
  The narrow race where `close()` landed between the guard and the send is gone. And the
  ordering constraint that `onIntent` *must* precede `trySend` — because under an unconfined
  dispatcher `trySend` can synchronously resume the loop — no longer exists, since every
  notification now originates in the same coroutine.

  **Behavioural consequence:** `onIntent` fires when the loop picks an intent up, not when the
  caller dispatched it. Under concurrent dispatch from several threads those orders differ, and
  the loop's is the one that describes what actually happened. An interceptor used for click
  analytics sees a negligible delay. `dispatch`'s signature is unchanged.

### Removed

- **`kide-devtools`** — the deprecated non-inline `KideDebug.attach`. The reified
  `attachTyped`, introduced in 1.2.0 solely because renaming would have broken binary
  compatibility, takes the `attach` name back. Handles now always carry a real intent type, so
  `intentClassName` is never `"unknown"`.
  **Migration:** rename `KideDebug.attachTyped(...)` to `KideDebug.attach(...)`.
- **`kide-clean-architecture`** — `UseCaseLogic` and `AbstractUseCaseLogic`, deprecated since
  1.1.0. They also held a byte-for-byte copy of `AbstractUseCaseProcessor`'s state-management
  code, which would otherwise have to be fixed twice.
  **Migration:** `UseCaseLogic` → `UseCaseProcessor`, `AbstractUseCaseLogic` →
  `AbstractUseCaseProcessor`, `onIntent(intent)` → `map(intent)` — as the `ReplaceWith` has
  been advising for two minor releases.

### Changed

- **`kide`** — **`CompositeAction` now rejects a `cancellationKey` when none of its actions is
  asynchronous, from every construction path** — the constructor, `create()`, the `composite()`
  builder and `copy()`. 1.2.0 checked only the builder, because validating in the data class
  makes `copy()` throw.

  This is the one change in this release that the compiler cannot find for you: it fails at
  runtime, inside `map()`, where the intent loop catches it and reports it through `onError`, so
  the screen degrades rather than crashing. Code that hits it is already broken — such a key is
  silently ignored by the processor and always has been — but the failure is now audible.
  **Migration:** drop the key, or include an `async { }` / `useCase { }` action in the composite.

## [1.3.0] - 2026-07-25

An audit of `kide-navigation`, focused on the **process-death restore path** — where failures
are either silent (arguments vanish, the wrong screen opens) or fatal (a crash at startup that
survives restarts), and where none of the code had test coverage. Binary-compatible with 1.2.0.

> **Upgrading:** `ScreenNavKeyRegistry.register` now fails fast on a duplicate `serialKey`
> rather than overwriting silently. If your application currently registers two different
> destinations under the same key, it will throw on first launch after upgrading. That is
> deliberate — a duplicate `serialKey` is a bug, and failing immediately for everyone is far
> better than failing only for the users who reach process death on the wrong screen — but it
> is a behaviour change, and it surfaces at startup. See *Changed* below.

### Changed

- **`kide-navigation`** — `ScreenNavKeyRegistry.register` rejects a *different* destination
  registered under an already-used `serialKey` instead of silently overwriting it. Overwriting
  meant the saved back stack resolved to whichever feature initialised last, sending the user
  to the wrong screen after process death, or throwing `ClassCastException` from inside a
  composable when the two destinations used different processor types. Re-registering the same
  or an `equals` key remains a no-op, so a feature whose `initialize()` runs twice is
  unaffected.

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
