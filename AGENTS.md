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
| `kide-navigation` | Navigation 3 integration | on stable Nav3/lifecycle since 2.1.0 |
| `kide-clean-architecture` | domain/adapter/framework layer vocabulary | |
| `kide-test`, `kide-clean-architecture-test` | Turbine-based testing DSLs for `PresentationProcessor` / `UseCaseProcessor` | published test artifacts |
| `kide-koin` | Koin DI helpers | |
| `kide-devtools` | TraceBuffer, FlightRecorder, MCP agent port, console streaming | server code in `src/jvmShared` (shared jvm+android source set) |
| `kide-clean-architecture-devtools` | `UseCaseFlightRecorder` — domain events into a shared `TraceBuffer` | bridges two optional modules, which is why it is its own artifact |
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
   Android the guarded `start(context)` variant enforces this. It binds explicitly to
   the IPv4 loopback (`127.0.0.1`), not `InetAddress.getLoopbackAddress()` — see the
   comment at `KideMcpServer.kt:95-97` for why (`adb forward` can't reach `::1`).
7. **The processor is single-threaded by contract.** `PresentationProcessor.processorScope`
   must be confined to one thread (`Dispatchers.Main.immediate` by default; a single-threaded
   dispatcher in tests). The intent loop, the keyed-cancellation job registry (`activeJobs`)
   and the component registry (`children`) are unsynchronised plain maps and depend on it —
   which is also why completed jobs are left in `activeJobs` instead of being removed from an
   `invokeOnCompletion` handler, since that handler runs on whichever thread finished the job.
   This constrains the processor's own machinery only: `dispatch` is safe from any thread, and
   `AsyncScope.reduce` is explicitly safe to call after a `withContext(Dispatchers.IO)`, which
   is why `reduceState` uses a compare-and-set rather than relying on confinement.
8. **Trace fidelity is a public contract.** Every applied state transition is reported to
   `KideInterceptor.onStateChanged` (or `UseCaseInterceptor.onStateChanged`) exactly once,
   after the new state is published — from *every* reduction path: `ReducerAction` on the
   intent loop, `AsyncScope.reduce` inside an `AsyncAction`, and both `reduce` overloads on
   `AbstractUseCaseProcessor`. Transitions that changed nothing, or that lost a
   compare-and-set race and were discarded, are never reported. `kide-devtools` —
   `TraceBuffer`, `FlightRecorder`, the agent port, `TraceTestGenerator` — is only as correct
   as this, so all reductions go through a `reduceState` helper. Do not "simplify" those back
   to `MutableStateFlow.update`: that lambda re-runs when it loses a race, which reports
   phantom transitions. `InterceptorFidelityTest`, `PresentationProcessorConcurrencyTest`,
   `UseCaseTracingTest` and `UseCaseFlightRecorderTest` pin this down.
9. **Correlation is carried by the coroutine context, never by hand.** The intent loop creates
   a `TraceContext` per intent and installs it for that intent's whole processing; every
   interceptor callback in both layers receives it. Two consequences to preserve: interceptors
   are notified *from the loop* (not from `dispatch`, which cannot see the context and would
   report intents the processor never processes), and `processorScope.launch(context)` passes
   it explicitly because `launch` builds on the scope's context, not the caller's. Drop either
   and correlation silently becomes null for the asynchronous half of every interaction.

## Debugging the sample app

The app exposes the MCP agent port in debug builds (port 8765). As an agent you can
debug it directly:

```
adb forward tcp:8765 tcp:8765
# register http://localhost:8765/mcp as an MCP server, then use:
# kide_list_processors / kide_get_state / kide_get_trace / kide_dispatch_intent /
# kide_export_regression_test
```
