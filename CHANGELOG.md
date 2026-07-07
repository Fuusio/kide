# Changelog

All notable changes to Kide are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Kide adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
