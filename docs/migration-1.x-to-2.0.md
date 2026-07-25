# Migrating from Kide 1.x to 2.0.0

2.0.0 collects every known breaking change into one release, so that the compatibility shims
1.x had accumulated could be deleted rather than carried. Most of it is mechanical: the
compiler finds all but one of the changes below.

Work through it in this order — each section says what breaks, what to do, and why. If you only
have a few minutes, sections 1 and 6 are the ones that matter.

**The one the compiler cannot find for you** is section 5 (`cancellationKey` on a synchronous
composite). It fails at runtime. Read that one even if nothing else applies.

---

## 1. Interceptor callbacks take a `TraceContext?`

Every `KideInterceptor` callback gained a trailing `context: TraceContext?`.

```kotlin
// 1.x
class AnalyticsInterceptor : KideInterceptor<MyIntent, MyState, MyEffect> {
    override fun onIntent(intent: MyIntent) { … }
    override fun onStateChanged(oldState: MyState, newState: MyState) { … }
}

// 2.0
class AnalyticsInterceptor : KideInterceptor<MyIntent, MyState, MyEffect> {
    override fun onIntent(intent: MyIntent, context: TraceContext?) { … }
    override fun onStateChanged(oldState: MyState, newState: MyState, context: TraceContext?) { … }
}
```

Add the parameter and ignore it if you do not need it. All six callbacks changed:
`onIntent`, `onActionMapped`, `onActionExecuting`, `onStateChanged`, `onSideEffect`, `onError`.

**What it buys you.** The context carries a `correlationId` identifying the intent being
processed, so a trace can say *which interaction caused this state change* rather than only
that the two happened near each other — the distinction that matters exactly when you are
reading a trace, because concurrency is usually why you are reading one.

**Not to be confused with** `PresentationProcessor.onError(throwable, intent)`, the processor's
own hook for application code. That one is unchanged and still takes two parameters. If you
override both, only the interceptor one changes.

## 2. `onIntent` fires from the intent loop, not from `dispatch`

No code change. Behaviour: interceptors are now notified when the loop picks an intent up,
rather than when the caller queued it.

Two consequences worth knowing:

- Under concurrent dispatch from several threads, the reported order is now *processing* order.
  That is the order things actually happened in, and the one a trace should show.
- An intent dispatched to a closed processor is no longer reported at all. Previously it left
  an entry in the trace with nothing after it, indistinguishable from a `map()` that returned
  `null` or from a stalled loop.

If you use an interceptor for click analytics, events now fire a negligible moment later. If
you depended on `onIntent` running synchronously inside `dispatch` — nothing in Kide or its
sample app did — that no longer holds.

## 3. `KideDebug.attachTyped` is `KideDebug.attach` again

```kotlin
// 1.2.x / 1.3.x
KideDebug.attachTyped("search", processor, recorder)

// 2.0
KideDebug.attach("search", processor, recorder)
```

The old untyped `attach` is gone. It existed only because a `reified` function emits no
callable JVM method, so renaming in 1.2.0 would have broken binary compatibility. Handles now
always carry a real intent type, so `intentClassName` is never `"unknown"` and
`kide_dispatch_intent` rejects a wrongly typed intent at the boundary instead of failing deep
inside `map()`.

## 4. `UseCaseLogic` and `AbstractUseCaseLogic` are gone

Deprecated since 1.1.0, with a `ReplaceWith` that has been doing the work for two releases:

| 1.x | 2.0 |
|---|---|
| `UseCaseLogic<S, I>` | `UseCaseProcessor<S, I>` |
| `AbstractUseCaseLogic<S, I>` | `AbstractUseCaseProcessor<S, I>` |
| `override suspend fun onIntent(intent)` | `override suspend fun map(intent)` |

## 5. `cancellationKey` on an all-synchronous composite now throws

**This is the one the compiler will not catch.**

```kotlin
// Compiles in both. Throws IllegalArgumentException at runtime in 2.0.
composite(
    reduce { copy(isLoading = true) },
    sideEffect { ShowToast("saved") },
    cancellationKey = "save",
)
```

A composite whose actions are all synchronous runs inline on the intent loop and is never
launched as a cancellable job, so the key never did anything — 1.x ignored it silently. 2.0
rejects it from every construction path: the constructor, `create()`, the `composite()` builder
and `copy()`.

**Fix:** drop the key, or include an `async { }` / `useCase { }` action so the composite
actually becomes cancellable.

**Where it surfaces:** inside `map()`, so the intent loop catches it and reports it through
`onError` — the screen degrades rather than crashing, and the error appears in the trace. Code
that hits this was already broken; the change makes it audible. Worth grepping for
`cancellationKey` before upgrading.

## 6. `AbstractUseCaseProcessor.reduce` is `suspend`

Both overloads. In practice this breaks nothing — `reduce` is called from `map`, which is
already `suspend` — but a non-suspending private helper that calls `reduce` needs marking:

```kotlin
// 1.x
private fun applyResult(items: List<Item>) = reduce { it.copy(items = items) }

// 2.0
private suspend fun applyResult(items: List<Item>) = reduce { it.copy(items = items) }
```

A non-suspending function cannot read the coroutine context, and reading it is how a reduction
learns which intent it belongs to.

While you are here: prefer `reduce { … }` over `reduce(state)`. The absolute overload
overwrites rather than transforms, so a processor shared between screens — the usual case for a
DI singleton — can discard a change another coroutine made in between. `reduce { newState }`
says the same thing safely.

## 7. `KideMcpServer.start` returns `Boolean`

It also no longer throws. Previously a `BindException` propagated out of
`Application.onCreate` and killed the app before it drew a frame, which is easy to trigger by
reinstalling over a running build.

```kotlin
// 2.0 — the app runs either way; false just means no agent port
if (!KideMcpServer.start(this)) {
    // optional: surface it, or ignore
}
```

---

## New in 2.0, nothing to migrate

- **`kide-clean-architecture-devtools`** — a new artifact containing `UseCaseFlightRecorder`.
  Give it the same `TraceBuffer` as a screen's `FlightRecorder` and domain events join the UI
  events in one causally ordered trace, grouped by the correlation id of the interaction that
  caused them. Before this, a recorded session showed the UI half of an interaction and stopped
  where the real work began.

  ```kotlin
  val buffer = TraceBuffer()                                   // one per app is fine
  val savedProjects = SavedProjectsProcessor(repo, interceptors = listOf(UseCaseFlightRecorder(buffer)))
  val recorder = FlightRecorder<SearchIntent, SearchViewState, SearchSideEffect>(buffer)
  val processor = SearchProcessor(useCase, savedProjects, interceptors = listOf(recorder))
  KideDebug.attach("search", processor, recorder)
  ```

- **`TraceBuffer`** — the event log, split out of `FlightRecorder` so several recorders can
  share one. `FlightRecorder(capacity = n)` still works and means "a buffer of my own, that
  big".
- **`TraceEvent.correlationId` and `TraceEvent.source`** — group a trace by interaction, and
  tell the layers apart within it. Both are defaulted, so traces persisted by an older build
  still decode.
- **`ScreenNavKeyRegistry.find` and `clear`** (added in 1.3.0) — a non-throwing lookup, and a
  reset for tests.
