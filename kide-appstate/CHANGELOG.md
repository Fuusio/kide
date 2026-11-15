# Changelog — experimental AppState modules

Covers `kide-appstate` and `kide-clean-architecture-appstate`. Both are **experimental**:
they are built on Jetpack AppState, which is in alpha, and are versioned, tagged
(`kide-appstate-<version>`) and released together, separately from the other Kide modules.
They are **not** covered by Kide's Semantic Versioning promise: any release may change or
remove API, and every declaration requires opting in to `ExperimentalKideAppStateApi`.

Each entry states the AppState version it was built against and the Kide version it
depends on.

## [2.4.0-alpha02] — 2026-11-15

Built against AppState `1.0.0-alpha02`. Depends on Kide `2.3.1`. Targets: Android, JVM.

### Added

- **`kide-clean-architecture-appstate`** (new artifact) —
  `UseCaseProcessor.mirrorTo(stateStore, key, scope)` mirrors a use-case processor's domain
  state one-way into an AppState `StateStore` key, so other parts of the process (another
  activity, a service, a widget, a `transformFlow` block) can read it as Compose `State`.
  The processor stays the only writer, so every change still goes through its intents and
  interceptors.
- `mirrorTo(stateStore, key, scope) { stored -> restoreIntent }` — the same mirror, plus
  restore: a value that appears under the key without the mirror having written it (one
  loaded from disk, for a `PersistToDataStore` key) is turned into an intent and dispatched
  to the processor, so a restored state is reported to interceptors like any other change.
  A domain change made before the persisted value has loaded can be replaced by it.
  Persistence itself is Android-only, because AppState's `statestore-datastore` is.

### Changed

- `kide-appstate` is unchanged; it is re-released so that both modules share one version.

## [2.4.0-alpha01] — 2026-11-15

Built against AppState `1.0.0-alpha02`. Depends on Kide `2.3.1`. Targets: Android, JVM.

### Added

- `transformFlow(dispatcher) { … }` — a cold `Flow` backed by a headless AppState
  composition. Each collection runs the composable block in its own composition and emits
  its latest result, conflated and distinct-until-changed. Pass it to
  `PresentationProcessor.observe(key, flow) { reduce { … } }` to feed Compose `State`,
  StateStore keys and flows read with `collectAsState` into a processor's `ViewState`:
  the observation supplies the lifetime, keyed replacement and error routing, and every
  applied reduction is reported to the processor's interceptors as usual.
- `State<T>.asFlow()` — a single Compose `State` as a cold `Flow`.
- `ExperimentalKideAppStateApi` — the opt-in marker (`ERROR` level) on all of the above.
