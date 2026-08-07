---
name: kide
description: Build Kotlin Multiplatform / Android app features with the Kide MVI library. Use when creating or modifying screens, processors, ViewStates, ViewIntents, side effects, or navigation in a project that depends on org.fuusio.kide artifacts, when adding ViewState persistence, when testing Kide processors, or when debugging a running Kide app through its MCP agent port.
---

# Building app features with Kide

Kide is an MVI library: one `PresentationProcessor` per screen turns dispatched
`ViewIntent`s into declarative `Action`s that reduce a `ViewState` or emit one-time
`SideEffect`s. Full API details: [reference.md](reference.md).

## Creating a new screen feature — the standard workflow

Follow the sample app's structure (`feature/<name>/{presentation,navigation,ui}`).

**1. Contract** — three types:

```kotlin
@Serializable // only if the screen opts into persistence (step 4)
data class FooViewState(
    val query: String = "",
    @Transient val results: List<Item> = emptyList(), // ephemera: @Transient
    @Transient val isLoading: Boolean = false,
) : ViewState

sealed interface FooIntent : ViewIntent {
    data class UpdateQuery(val query: String) : FooIntent
    data object Submit : FooIntent
}

sealed interface FooSideEffect : SideEffect {
    data class ShowToast(val message: String) : FooSideEffect
}
```

**2. Processor** — implement `map()` with the action builders; never mutate state directly:

```kotlin
class FooProcessor(
    private val useCase: FooUseCase,
    processorScope: CoroutineScope = defaultProcessorScope(),
    interceptors: List<KideInterceptor<FooIntent, FooViewState, FooSideEffect>> = emptyList(),
) : PresentationProcessor<FooIntent, FooViewState, FooSideEffect>(FooViewState(), processorScope, interceptors) {

    override suspend fun map(intent: FooIntent): Action<FooViewState, FooSideEffect>? =
        when (intent) {
            is FooIntent.UpdateQuery -> reduce { copy(query = intent.query) }
            FooIntent.Submit -> composite(
                reduce { copy(isLoading = true) },
                async(cancellationKey = "submit") {          // same key ⇒ restarts previous run
                    val result = useCase.execute(state.query) // suspend work off the intent loop
                    reduce { copy(results = result, isLoading = false) }
                },
            )
        }
}
```

Builder cheat sheet: `reduce { }` sync state change · `sideEffect { }` construct one-time
effect · `async { }` / `useCase { }` suspend work with `reduce { }` inside ·
`composite(a, b)` sequential group · return `null` for no-op.

**3. Nav key + screen** (module `kide-navigation`):

```kotlin
object FooNavKey : ScreenNavKey<FooProcessor> {
    override val serialKey = "foo"                    // stable string literal, NEVER a class name
    override fun createProcessor(): FooProcessor = get()  // fresh instance each call (DI factory)
    override val screen: @Composable ((ScreenContext<FooProcessor>) -> Unit)
        get() = { ctx -> FooScreen(ctx) }
}

@Composable
fun FooScreen(ctx: ScreenContext<FooProcessor>) {
    val state by ctx.processor.states.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        ctx.processor.sideEffects.collect { effect -> /* toast, navigation, ... */ }
    }
    // UI dispatches: ctx.processor.dispatch(FooIntent.Submit)
    // Navigate:      ctx.navigateTo(BarNavKey)
}
```

Register the key at startup, before first composition (usually in the feature's
`initialize()`): `ScreenNavKeyRegistry.register(FooNavKey)`. Wire the feature's Koin
module with a `factory { FooProcessor(get()) }` binding.

**Opening a screen with something** — override `setup()`. Which mechanism depends on one
question: *does the bootstrap intent need to await anything?*

| | The key already holds the data | The key holds only an id |
|---|---|---|
| Use | `processor.initializeWith(intent)` | `processor.dispatch(intent)` |
| Handled in | `reduceInitialIntent()` — synchronous | `map()` — may suspend |
| First frame | already correct | rendered before the data lands |

`initializeWith` **cannot** do the second job: `reduceInitialIntent` never reaches `map()`, so
an intent carrying an id has nowhere to await the repository, falls through to the default
`= state`, and is silently ignored. Dispatching from `setup` is ordinary — the intent loop
starts at construction. If a destination uses both, `initializeWith` must come first.

When you bootstrap by dispatch, make the screen distinguish *loading* from *empty*. Rendering
"Nothing to show here" while the fetch is in flight makes a working screen look broken.

**4. Persistence (optional)** — survive process death by adding one override to the nav key:

```kotlin
override val stateSerializer: KSerializer<out ViewState> get() = FooViewState.serializer()
```

Persist inputs and identity (query text, selected id); keep derived data (`results`,
`isLoading`, errors) `@Transient` with defaults so they reset and re-fetch. When state was
restored, the nav key's `setup()` is skipped (`processor.wasRestored == true`).

## Testing a processor

```kotlin
class FooProcessorTest : DescribeSpec({
    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    it("updates query") {
        val processor = FooProcessor(FakeFooUseCase())
        processor.dispatch(FooIntent.UpdateQuery("kotlin"))   // synchronous under test Main
        processor.state.query shouldBe "kotlin"
    }
})
```

## Tracing the domain layer (optional)

By default a trace covers the presentation layer only. To have `UseCaseProcessor` events land
in the *same* trace, give both layers one `TraceBuffer` (module
`kide-clean-architecture-devtools`):

```kotlin
val buffer = TraceBuffer()                       // one per application is fine
val savedProjects = SavedProjectsProcessor(
    repository,
    interceptors = listOf(UseCaseFlightRecorder(buffer)),
)
val recorder = FlightRecorder<FooIntent, FooViewState, FooSideEffect>(buffer)
val processor = FooProcessor(savedProjects, interceptors = listOf(recorder))
KideDebug.attach("foo", processor, recorder)
```

A use-case singleton shared by several screens needs its recorder at construction, so an
application-wide buffer is usually simpler than one per screen.

## Debugging a running Kide app (agent port)

Debug builds may expose an MCP server (`kide-devtools`). If the project wires it
(`KideMcpServer.start(context)` in `Application.onCreate`):

```
adb forward tcp:8765 tcp:8765   # then register http://localhost:8765/mcp as an MCP server
```

Tools available to you: `kide_list_processors`, `kide_get_state`, `kide_get_trace`
(causal history with previous-state diffs — read this FIRST when diagnosing state bugs),
`kide_dispatch_intent` (inject a `@Serializable` intent; get the class name from the
trace's `payloadClass`), `kide_export_regression_test` (recorded session → kotest scaffold).

**Reading a trace.** Every event carries `correlationId` — everything one user interaction
caused shares one — and `source`, either `Presentation` or `Domain`. To work out why something
did not happen, group by `correlationId` and find the layer where the chain stops:

- stops after `Intent` → `map()` returned `null`, or the intent matched no branch
- stops after `ActionExecuting`, no `Domain` events → the use case was never dispatched to
- `Domain Intent` but no `Domain StateChanged` → the use case ran and reduced nothing
- `Domain StateChanged` but no `Presentation StateChanged` → the domain updated and the UI
  never reflected it

`"correlationId": null` is legitimate, not a gap: work with no originating intent, such as a
repository flow collected at startup.

## Common mistakes to avoid

- **Using `initializeWith` for a bootstrap intent that has to load something.**
  `reduceInitialIntent` is synchronous and never reaches `map()`, so an intent carrying only an
  id — "open item 42" — has nowhere to await the repository. It falls through to the default
  `= state` and is ignored: no exception, no branch, just a screen that renders as if it had
  been opened with nothing. Pass data you already hold to `initializeWith`; `dispatch()` the
  intent from `setup()` when it needs work. Both are legal there, `initializeWith` first.
  The only signal is a warning logged when the initial state comes back unchanged.
- **Forgetting `import org.fuusio.kide.presentation.reduce`.** `reduce` means two different
  things: inside `async { }` / `useCase { }` it is `AsyncScope.reduce`, a member that resolves
  by itself; at the top level of `map()` it is the action *builder*, a top-level function that
  needs importing. Omit the import and Kotlin does not suggest the builder — it falls through
  to `Iterable.reduce` and emits a page of errors about `UByteArray`. Same for `sideEffect`,
  `async`, `useCase` and `composite`.
- Blocking or long-running work in `map()` or in `reduce { }` — it stalls the intent loop.
  Suspend work belongs inside `async { }` / `useCase { }`.
- Passing a multi-threaded `processorScope` (`Dispatchers.Default`, a thread pool). It must be
  single-threaded; the default is correct. Inside `async { }` you may still
  `withContext(Dispatchers.IO) { ... }` and `reduce { }` from there.
- A `cancellationKey` on a `composite(...)` whose actions are all synchronous — such a
  composite runs inline and is never a cancellable job, so the key would do nothing. Every
  construction path rejects it, `copy()` included.
- Calling `AbstractUseCaseProcessor.reduce` from a non-suspending helper — both overloads are
  `suspend`, because reading the coroutine context is how a reduction learns which intent it
  belongs to. Mark the helper `suspend`.
- Using `reduce(state)` rather than `reduce { … }` on a use-case processor. The absolute
  overload overwrites instead of transforming, so a processor shared between screens can
  discard a concurrent change. `reduce { newState }` is the same thing, safely.
- Collecting `sideEffects` from more than one place — delivery is exactly-once to a
  single collector.
- Deriving `serialKey` from a class name — breaks saved state under R8/renames.
- Caching processor instances in `createProcessor()` — must return a fresh instance;
  retention is the host's job.
- Reusing a processor after `close()` — its scope is cancelled.
- Forgetting `@Transient` on non-serializable or derived `ViewState` fields when opting
  into persistence.
