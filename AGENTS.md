# AGENTS.md — working on the Kide repository

Instructions for AI coding agents contributing to this repository. (If you are building an
*application that uses* Kide, read `skills/kide/SKILL.md` instead.)

## What this is

Kide is a Kotlin Multiplatform (Android, JVM desktop, iOS) MVI + Clean Architecture library.
The MVI core is `kide/src/commonMain/.../presentation/PresentationProcessor.kt` — read it
first; its KDoc documents the execution guarantees that the rest of the repo depends on:
lossless FIFO intent processing, dispatch-ordered synchronous reduction, exactly-once
buffered side effects, keyed cancellation for async actions, and an error-guarded intent
loop that must never die.

## Module map

| Module | Purpose | Notes |
|---|---|---|
| `kide` | MVI core | **coroutines-only dependency — never add another** |
| `kide-navigation` | Navigation 3 integration | rides pre-stable Nav3/lifecycle alphas |
| `kide-clean-architecture` | domain/adapter/framework layer vocabulary | |
| `kide-koin` | Koin DI helpers | |
| `kide-devtools` | FlightRecorder, MCP agent port, console streaming | server code in `src/jvmShared` (shared jvm+android source set) |
| `kide-decompose`, `kide-voyager` | host adapters | |
| `app` | Android sample app (not published) | reference for all patterns |

## Build and verify

```
./gradlew build              # compile + all tests + ABI check
./gradlew jvmTest            # fastest test cycle
./gradlew updateKotlinAbi    # after ANY public API change; commit the api/ dumps
```

Library modules use **explicit API mode**: every public declaration needs an explicit
`public` modifier and KDoc. ABI validation (`checkKotlinAbi`) runs as part of `check`;
a public API change without regenerated dumps fails the build.

## Conventions

- Every source file starts with the Apache 2.0 header (copy from any existing file).
- Tests are kotest `DescribeSpec` (`describe`/`it`), run on JUnit 5. Processor tests set
  `Dispatchers.setMain(UnconfinedTestDispatcher())` in `beforeSpec` — dispatch is then
  synchronous in tests. See `PresentationProcessorTest.kt` for the house style.
- Logging inside library code: `logD { }` / `logW(e) { }` extensions (class-derived tag,
  lazy message) from `org.fuusio.kide.log`. Never log eagerly; never println.
- KDoc every public declaration. Reference types with `[Brackets]`.
- Design docs and proposals live in `docs/`. Significant designs get a proposal file
  before implementation (structure: goal, design principles, API sketch, semantics and
  edge cases, rollout plan, open questions).

## Invariants — do not break these

1. `kide` core depends on kotlinx-coroutines only. Persistence, serialization, DI, and
   navigation knowledge live in the satellite modules.
2. The intent loop must survive exceptions: errors are logged, reported to
   `KideInterceptor.onError` and the processor's `onError`, and processing continues.
   `CancellationException` is always rethrown.
3. Side effects are delivered exactly once to a single collector; never convert the
   channel to a hot flow.
4. `ScreenNavKey.serialKey` values are persisted in saved navigation state — treat the
   serialization formats of `NavKeyWrapper` and persisted `ViewState` as forward-compatible
   contracts.
5. State persistence uses `KSerializer` directly (`stateSerializer` on `ScreenNavKey`,
   `StateKeeper` on Decompose). There is deliberately **no** saver abstraction — a
   previous `ViewStateSaver` was removed; do not reintroduce one.
6. The MCP agent port binds to loopback and must never start in release builds; on
   Android the guarded `start(context)` variant enforces this.

## Debugging the sample app

The app exposes the MCP agent port in debug builds (port 8765). As an agent you can
debug it directly:

```
adb forward tcp:8765 tcp:8765
# register http://localhost:8765/mcp as an MCP server, then use:
# kide_list_processors / kide_get_state / kide_get_trace / kide_dispatch_intent /
# kide_export_regression_test
```
