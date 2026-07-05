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

## Common mistakes to avoid

- Blocking or long-running work in `map()` or in `reduce { }` — it stalls the intent loop.
  Suspend work belongs inside `async { }` / `useCase { }`.
- Collecting `sideEffects` from more than one place — delivery is exactly-once to a
  single collector.
- Deriving `serialKey` from a class name — breaks saved state under R8/renames.
- Caching processor instances in `createProcessor()` — must return a fresh instance;
  retention is the host's job.
- Reusing a processor after `close()` — its scope is cancelled.
- Forgetting `@Transient` on non-serializable or derived `ViewState` fields when opting
  into persistence.
