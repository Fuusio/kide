# Proposal: Jetpack AppState support (`kide-appstate`)

Status: draft, 2026-11-15; decisions of 2026-11-15 recorded under *Decisions*. Target: an experimental satellite module, first shipped next to
Kide 2.4.0. Written against AppState `1.0.0-alpha02` (alpha01 was released 2026-10-28,
alpha02 on 2026-11-04; the alpha02 change list was not available when this was written).

## Goal

Let applications that want to experiment with Jetpack AppState
(`androidx.appstate.transform`, `androidx.appstate.statestore`) use it together with Kide,
without any change in behaviour, dependencies or ABI for applications that do not.

## What AppState is, in Kide's terms

- **Transform** runs a `@Composable` block in a headless composition and publishes its
  result as Compose `State<R>`. Dependencies are tracked dynamically by snapshot reads;
  Flows are bridged inside the block with `collectAsState`. Output is **conflated and
  de-duplicated**: only the latest value survives several changes before a recomposition.
  `transform(initialValue, scope, dispatcher) { previous -> … }` is scoped by a
  `CoroutineScope`; `listener { }` is the suspending, no-output form.
- **StateStore** is a process-local, keyed container of `State<T>` for session state shared
  between activities, services and widgets, optionally persisted through DataStore
  (`PersistToDataStore`, `syncToDataStore`). Thread-safety of `setState`/`updateState` is
  not documented.
- Google's own layering keeps *transient UI state* on `SavedStateHandle`, and recommends
  plain `Flow` for anything that must see every value in order.

The last point decides the shape of this proposal. Kide's contract is a lossless FIFO
intent loop, dispatch-ordered reduction and exactly-once trace reporting. A conflating,
eventually-consistent composition cannot carry intents, reductions or side effects without
giving those guarantees up. It *can* compute the inputs a reduction works from.

## Design principles

1. **AppState feeds state in; it never replaces the loop.** A transform result enters a
   processor only through `AsyncScope.reduce`, i.e. through `reduceState`. Invariants 2, 3,
   7, 8 and 9 in `AGENTS.md` are untouched, and everything AppState contributes shows up in
   `KideInterceptor.onStateChanged`, the `FlightRecorder` and the MCP agent port.
2. **`kide` core stays coroutines-only** (invariant 1). No Compose runtime, no AppState.
   Ideally core is not modified at all.
3. **Nothing existing gains a dependency.** No current module depends on the new one; their
   `api/` dumps do not change. The alpha is reachable only by adding a new artifact.
4. **Thin bridge, not a wrapper.** AppState's API will change during alpha. Kide exposes
   the AppState types directly and adds the minimum glue, so an upstream rename costs one
   small module, not a parallel API.
5. **Experimental is visible at the call site and in the version.** A `RequiresOptIn`
   annotation at `ERROR` level on every declaration, and a pre-release version on the
   artifact, so it is exempt from Kide's SemVer promise in a way tooling can see.
6. **ViewState persistence does not move.** `ScreenNavKey.stateSerializer` +
   `SavedStateHandle` (and `StateKeeper` on Decompose) remain the only mechanism
   (invariants 4, 5). StateStore/DataStore is a different tier (session state) and is not
   offered as a ViewState saver.

## Decisions

- **Own pre-release version.** `kide-appstate` is versioned separately from the stable
  train (first release `2.4.0-alpha01`).
- **Android + JVM first.** iOS targets are added later, in their own phase, once the
  Android/JVM module has shipped; they do not gate phase 1.
- **Own git tag and CHANGELOG.** Releases are tagged `kide-appstate-<version>` and
  documented in `kide-appstate/CHANGELOG.md`; the root `CHANGELOG.md` only points to it.
- **The bridge function is named `transformFlow`**, mirroring upstream `transform`.

## What already works today, with no Kide change

- **Processor state as a transform input.** `processor.states` and
  `UseCaseProcessor.stateFlow` are `StateFlow`s, so inside any `transform { }` block
  `processor.states.collectAsState()` is all that is needed.
- **Managers exposing `State<T>`.** Google's "shared live state" pattern (a singleton with
  a private `mutableStateOf` and a public `State<T>`, e.g. a network monitor) is a Kide
  `Manager`. Only reading/writing `State` needs the Compose runtime on the classpath, not
  the compiler plugin.

The gap is the other direction: getting Compose `State` and transform results **into** a
`PresentationProcessor`'s `ViewState` with Kide's guarantees. That is what the module adds.

## API sketch

New module `kide-appstate` (android and jvm; iOS targets follow in phase 4), package
`org.fuusio.kide.appstate`. Dependencies: `api(project(":kide"))`,
`api(androidx.appstate.transform:transform)`, Compose runtime; applies
`org.jetbrains.kotlin.plugin.compose`.

```kotlin
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Built on Jetpack AppState, which is in alpha. This API can change or be " +
        "removed in any Kide release and is not covered by Kide's compatibility guarantees.",
)
public annotation class ExperimentalKideAppStateApi

/**
 * A cold Flow backed by a headless AppState composition. Each collection runs [block] in
 * its own composition for as long as it is collected, emitting the block's latest result.
 * Conflated and distinct-until-changed.
 */
@ExperimentalKideAppStateApi
public fun <T> transformFlow(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: @Composable () -> T,
): Flow<T>

/** [State] as a Flow, driven by AppState rather than `snapshotFlow` (see edge cases). */
@ExperimentalKideAppStateApi
public fun <T> State<T>.asFlow(): Flow<T> = transformFlow { value }
```

Implementation (confirmed by the phase 0 spike):

```kotlin
channelFlow {
    listener(dispatcher) {
        val value = block()
        SideEffect { trySend(value) }
    }
}.conflate().distinctUntilChanged()
```

The bridge is a **cold Flow on purpose**: `PresentationProcessor.observe(key, flow)`
already provides everything else — a lifetime bound to the processor, keyed replacement,
`isObserving`/`stopObserving`, error routing to `onObservationError`, reductions reported
to interceptors, and confinement of the bookkeeping to the processor thread. No subclass
and no new protected surface is required.

```kotlin
@OptIn(ExperimentalKideAppStateApi::class)
override suspend fun map(intent: DashboardIntent) = when (intent) {
    OnLaunched -> {
        if (!isObserving("inputs")) {
            observe("inputs", transformFlow {
                val online = networkMonitor.isOnline.value          // Manager: State<Boolean>
                val session = focusSessions.session.value           // StateStore-backed
                val tasks by tasksRepository.tasks.collectAsState(emptyList())
                DashboardInputs(online, session, tasks)
            }) { inputs ->
                reduce { copy(online = inputs.online, session = inputs.session, tasks = inputs.tasks) }
            }
        }
        null
    }
    // …
}
```

A single StateStore key or Manager state needs no composable:
`observe("session", focusSessions.session.asFlow()) { reduce { copy(session = it) } }`.

### Phase 3 additions (separate artifact, see rollout)

`kide-clean-architecture-appstate` — bridges two optional modules, same reasoning as
`kide-clean-architecture-devtools`:

```kotlin
/** One-way mirror: the processor stays the single writer; the store is a read replica. */
@ExperimentalKideAppStateApi
public fun <S : State> UseCaseProcessor<S, *>.mirrorTo(
    stateStore: StateStore,
    key: StateStoreKey<S>,
    scope: CoroutineScope,
): Job
```

This makes domain state readable as Compose `State` from widgets, services and other
activities, and persistable by marking the key `PersistToDataStore`, while every write
still goes through `UseCaseInterceptor`.

## Semantics and edge cases

- **Conflation.** A transform result may skip intermediate values. Use it for *current
  inputs* (connectivity, session, a query's latest result). Anything that must be seen
  value-by-value stays a plain `Flow` in `observe`, and anything that is an event stays an
  intent. Document this next to the API.
- **First value is asynchronous.** The first reduction lands after the first headless
  composition, so one frame may render without it — the same as `observe` with any flow.
  `reduceInitialIntent` cannot use it.
- **Threading.** The block composes on `dispatcher` (`Dispatchers.Default` by default);
  `onEach` runs in `processorScope`; `reduce` is compare-and-set. `observe` must still be
  called on the processor thread. Invariant 7 holds.
- **Trace.** Reductions carry a `null` `TraceContext`, like every observation. Reads of the
  processor's own `state` inside the block are not snapshot-tracked; feed ViewState-derived
  parameters in through `states.collectAsState()` or re-`observe` under the same key.
- **Errors.** Expected: an exception in the block fails the collection and reaches
  `onObservationError(throwable, key)`; the intent loop is unaffected. Not documented
  upstream, but confirmed by the spike and pinned by `TransformFlowObserveTest`.
- **`State.asFlow()` and `snapshotFlow`.** The concern was that `snapshotFlow` depends on
  global snapshot apply notifications, which a UI toolkit normally drives, so it might
  never fire in a process with no composition. The spike did not confirm that: on a plain
  JVM `snapshotFlow` saw a plain global write — though in a JVM where AppState compositions
  had already run. If an isolated run shows the same, `asFlow()` should be built on
  `snapshotFlow` (no composition per collector); until then it stays on `transformFlow`,
  which is known to work.
- **Lifecycle.** No lifecycle awareness: the composition runs while the processor lives,
  including in the background, exactly like `observe`. `collectAsStateWithLifecycle` is not
  usable in a headless block.
- **StateStore writes.** Thread-safety of `updateState` is undocumented, so Kide never
  uses a StateStore as the backing store of a processor (it would put the compare-and-set
  trace guarantee on undocumented ground). Mirror out, observe in.

## Explicit non-goals

- A composable `map`/reducer ("presenter" style). Breaks ordering and trace fidelity.
- `ViewState` built from granular `MutableState` fields. Breaks `KSerializer` persistence,
  equality-based change detection and trace diffs.
- A `State<S>` property on `PresentationProcessor`, or any AppState type in `kide`,
  `kide-navigation`, `kide-test` or the host adapters.
- Replacing `collectAsStateWithLifecycle()` in screens.

## Build, versioning and compatibility

- `libs.versions.toml`: `appstate = "1.0.0-alpha02"`, entries for `transform`,
  `transform-testing`, `statestore`, `statestore-datastore`.
- `kide-appstate/gradle.properties` overrides `VERSION_NAME` (`2.4.0-alpha01` for the
  first release) next to `POM_ARTIFACT_ID`, so the artifact is recognisably pre-release
  while the other modules stay on the stable train. The alpha counter advances with every
  breaking change, whether it comes from Kide or from a new AppState alpha.
- The module declares only `jvm()` and `android { }` until phase 4. A Kotlin Multiplatform
  consumer with iOS targets cannot put it in `commonMain` until then; the README says so.
- Explicit API mode, KDoc, Apache header, `abiValidation()` as everywhere else. The ABI
  dump is committed but breaking it between alphas is allowed and called out in the
  module's own changelog.
- Releases of the module are tagged `kide-appstate-<version>` (first:
  `kide-appstate-2.4.0-alpha01`) and published on their own with
  `./gradlew :kide-appstate:publishAndReleaseToMavenCentral`, independent of Kide's release
  tags. `.github/workflows/publish.yml` triggers on `v*` and runs the root
  `publishAndReleaseToMavenCentral`, which would otherwise also publish `kide-appstate` at
  whatever alpha version it carries on every stable release. So the module applies the
  publish plugin only when `-Pkide.publishExperimental` is passed — without it the module
  has no publishing tasks and the stable workflow is untouched — and
  `.github/workflows/publish-appstate.yml` runs on `kide-appstate-*` tags, checks the tag
  against the module's version and that the Kide version it depends on is on Maven
  Central, and publishes only that module. Each entry in
  `kide-appstate/CHANGELOG.md` states the AppState version it was built against and the
  minimum Kide version it requires. The root `CHANGELOG.md` and README carry one line
  pointing there, so a stable release note never lists experimental changes.
- Verification that nothing leaked: `./gradlew build` with unchanged `api/` dumps for all
  existing modules, and `:kide:dependencies` still showing coroutines only.
- Consumers need the `google()` repository for `androidx.appstate`, including on JVM
  desktop.

## Rollout plan

Phase 0 status (2026-11-15): behaviour questions 2–4 answered positively on JVM, see
*Phase 0 results*. Question 1 is answered for JVM:
AppState `1.0.0-alpha02` requests `androidx.compose.runtime:runtime:1.11.2`, below the
stable `1.12.1` that Compose Multiplatform 1.12.2 already brings, so adding the module
upgrades no Compose artifact and introduces no pre-release Compose dependency. The only
alpha in a consumer's graph is AppState itself. The android target resolves, compiles and
assembles an AAR. **Phase 0 is complete; the Android + JVM plan stands.** One minor item is
carried into phase 1: whether `State.asFlow()` can be built on `snapshotFlow` (see
*Semantics and edge cases*).

**Phase 0 — spike (throwaway branch, no publication).** Android and JVM only:
1. Does `androidx.appstate.transform` resolve for the jvm target, and which Compose
   runtime version does it pull in on android and jvm? If it forces a newer or pre-release
   runtime than Compose Multiplatform 1.12.x and the sample app's Compose BOM ship, decide
   how that is surfaced to consumers before going further.
2. Does a headless composition recompose on plain JVM with no UI present (this is also
   what the `jvmTest` suite depends on)?
3. Error propagation out of `listener`, and cancellation promptness when the collector is
   cancelled (keyed replacement relies on it).
4. Do `transformTest`/`advance()` compose with Kide's `Dispatchers.setMain(
   UnconfinedTestDispatcher())` test style and the Turbine DSL in `kide-test`?

**Phase 1 — `kide-appstate` alpha.** Module, opt-in annotation, `transformFlow`,
`State.asFlow()`. Tests in house style (`DescribeSpec`): emission, conflation, dedupe,
keyed replacement cancels the composition, `close()` cancels it, error →
`onObservationError`, and an `InterceptorFidelityTest`-style case proving each applied
reduction is reported exactly once. `settings.gradle.kts`, ABI dump,
`kide-appstate/CHANGELOG.md`, README module table (marked experimental), `AGENTS.md`
module map, and the release workflow in `.github/` checked so a `kide-appstate-*` tag
publishes only this module and a Kide release tag does not publish it.

**Phase 2 — guidance and sample.** One feature in `app` using it end to end (a
`NetworkMonitor` `Manager` exposing `State<Boolean>`, consumed through `transformFlow`);
`BrowserProcessor`'s hand-rolled `stateFlow.collectLatest` is a natural before/after. A
section in `docs/kide_guide.md` and `skills/kide/reference.md` covering the
state-tier table (what goes in ViewState, StateStore, a Manager, a repository Flow) and
the conflation rule. Testing recipe; a `kide-appstate-test` artifact only if phase 0
shows the recipe needs real glue.

Status: done on `feature/kide-appstate`. The sample is the Search screen's offline notice
(`NetworkMonitor` → `ConnectivityService` → `SearchProcessor`), chosen over the
`BrowserProcessor` rewrite because it adds a Compose `State` input rather than replacing a
flow that `observe` already handles. Guidance is section 9 of `docs/kide_guide.md` and the
AppState sections of `skills/kide`. No `kide-appstate-test` artifact: the recipe is
`transformTest` with a shared dispatcher, which needs no glue.

**Phase 3 — StateStore bridge.** `kide-clean-architecture-appstate` with `mirrorTo`, and
optionally a `listener`-based recorder that writes StateStore key changes into a shared
`TraceBuffer`, so the agent port can see session state too.

**Phase 4 — iOS.** Add `iosArm64()` and `iosSimulatorArm64()` once the upstream artifacts
and the Compose runtime they need are confirmed to resolve alongside Compose
Multiplatform, and a headless composition is shown to recompose there. Purely additive
for Android and JVM consumers.

**Graduation.** AppState beta → opt-in level drops to `WARNING`, API frozen. AppState
stable → module joins the main version train and the annotation is deprecated. Core stays
untouched throughout; if AppState is abandoned upstream, the module is removed with no
effect on any other artifact.

## Phase 0 results (2026-11-15, AppState 1.0.0-alpha02)

| Question | Result |
|---|---|
| Resolves for jvm and android | Yes; jvm compiled and ran, android assembled an AAR. |
| Compose runtime | AppState requests `androidx.compose.runtime:runtime:1.11.2`; Compose Multiplatform 1.12.2 already brings stable `1.12.1`, which wins. Nothing is upgraded. |
| Headless composition on a plain JVM | Runs and recomposes: first result, plain global write, `withMutableSnapshot`, `collectAsState` bridge. |
| Conflation | 1000 back-to-back writes arrived as 10 emissions, ending on the last value. |
| `snapshotFlow` without a UI | Saw a plain global write, but in a JVM where AppState compositions had already run. Inconclusive. |
| Block exception | Fails the collection with the original exception, on the first and on later compositions; reaches `onObservationError`; the intent loop survives. |
| Cancellation | Key reuse, `stopObserving` and `close()` each dispose the composition. |
| Trace fidelity | Each applied transition reported once, no gaps; unchanged emissions not reported. |
| `transformTest` | `advance()` drives a processor observation when the processor scope and the composition share `testDispatcher`. |

## Open questions

None outstanding. Settled on 2026-11-15:

- No `AppStatePresentationProcessor` base class; `observe(key, transformFlow { … })` is
  the only form.
- Restoring a `UseCaseProcessor` from a persisted StateStore key is designed in phase 3.
- The module has its own git tag and its own CHANGELOG file (see *Build, versioning and
  compatibility*).
