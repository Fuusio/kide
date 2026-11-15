# Changelog — kide-appstate

`kide-appstate` is **experimental**. It is built on Jetpack AppState, which is in alpha,
and is versioned, tagged (`kide-appstate-<version>`) and released separately from the
other Kide modules. It is **not** covered by Kide's Semantic Versioning promise: any
release may change or remove API, and every declaration requires opting in to
`ExperimentalKideAppStateApi`.

Each entry states the AppState version it was built against and the Kide version it
depends on.

## [Unreleased] — 2.4.0-alpha01

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
