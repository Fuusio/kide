# Contributing to Kide

Thanks for your interest in improving Kide! This document covers the practical basics.
(AI coding agents: also read [AGENTS.md](AGENTS.md).)

## Before you start

- **Bugs and small fixes**: open an issue (a `FlightRecorder` trace attached to bug
  reports is gold — see the bug template), or send a PR directly.
- **New features or API changes**: open an issue first. Kide's public API is guarded by
  ABI validation and explicit-API mode; additions are long-term commitments. Significant
  designs get a short proposal document in `docs/` before implementation (goal, design
  principles, API sketch, semantics and edge cases, rollout plan, open questions).

## Building and testing

```
./gradlew build              # compile all targets + tests + ABI check
./gradlew jvmTest            # fastest test cycle
./gradlew updateKotlinAbi    # REQUIRED after any public API change; commit the api/ dumps
```

Requirements: JDK 21, Android SDK. iOS targets build on any host (klib cross-compilation);
running the full matrix on macOS is ideal but not required for most changes.

## Conventions

- Every source file starts with the Apache 2.0 license header (copy from any file).
- Library modules use **explicit API mode**: explicit `public` modifiers and KDoc on
  every public declaration.
- Tests are kotest `DescribeSpec` on JUnit 5. Processor tests install
  `Dispatchers.setMain(UnconfinedTestDispatcher())` in `beforeSpec`, making `dispatch`
  synchronous — see `PresentationProcessorTest.kt` for the house style. New behavior
  needs tests.
- Library-internal logging goes through the lazy `logD { }` / `logW(e) { }` extensions —
  never `println`, never eager string building.
- Update `CHANGELOG.md` (Unreleased section) for user-visible changes.

## Architectural invariants

Please don't break these (PRs that do will be asked to change):

1. The `kide` core module depends on kotlinx-coroutines **only**.
2. The intent loop survives exceptions; `CancellationException` is always rethrown.
3. Side effects are delivered exactly once to a single collector.
4. Serialized formats (`NavKeyWrapper`, persisted `ViewState`, `serialKey` values) are
   forward-compatible contracts.
5. State persistence speaks `KSerializer` directly — no saver abstractions.
6. The MCP agent port is loopback-only and must never run in release builds.

## Pull requests

Keep PRs focused on one change. Include: what and why, test coverage, regenerated
`api/` dumps when the public API changed, and a green `./gradlew build`. By contributing
you agree that your contributions are licensed under the Apache License 2.0.
