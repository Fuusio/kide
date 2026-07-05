# Changelog

All notable changes to Kide are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Kide adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
