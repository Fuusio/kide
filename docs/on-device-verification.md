# On-device verification: ViewState persistence & the MCP agent port

Manual smoke-test protocol for the two features that unit tests cannot fully cover.
Run before each release, on a debug build: `./gradlew :app:installDebug`.

Keep a log window open throughout:

```
adb logcat -s KideMcpServer ViewModelHost AppNavigation SearchProcessor
```

---

## A. ViewState persistence (Search screen)

**A1 — Retention baseline (config change).**
Open Search → type `kotlin` → pick a Language filter → run the search → rotate the
device. Expect: query, filters, *and results* all survive (ViewModel retention — no
serialization involved). This isolates step A2: if A1 fails, the problem is retention,
not persistence.

**A2 — Process death (the real test).**
1. With query + filters set (results loaded), press **Home** (background the app —
   do *not* swipe it away from recents; that discards saved state on many OEMs).
2. Kill the process while its saved state is retained:
   ```
   adb shell am kill org.fuusio.kide.app
   ```
   (Verify it died: `adb shell pidof org.fuusio.kide.app` → empty.)
3. Relaunch from the launcher and navigate to Search.

**Expected:** query and both filters restored; `results`, `isLoading`, `errorMessage`
reset (they are `@Transient`) — re-running the search re-fetches. No crash, no
"Failed to restore ViewState" warning in logcat.

**Failure signatures:**
- `SavedStateHandle unavailable for 'search'; ViewState persistence disabled` in logcat →
  the Nav3-alpha spike risk materialized: `createSavedStateHandle()` isn't supported
  under `rememberViewModelStoreNavEntryDecorator`. Persistence degrades gracefully;
  the fallback is a `rememberSaveable`-based provider (see the persistence proposal).
- Query empty after relaunch with no warning → the save provider never ran; check that
  the back stack itself restored (Search still the current screen?) — nav-key
  restoration (`NavKeyWrapper`) is a prerequisite.

**A3 — Schema-evolution resilience (once per release).**
Install a build, save state (A2 steps 1–2), then install a build where
`SearchViewState` has a renamed field, relaunch. Expected: warning logged, screen starts
fresh, **no crash**.

---

## B. MCP agent port

**B0 — Server up.** On app launch, logcat shows
`Kide agent port (MCP) listening on 127.0.0.1:8765`. Then:

```
adb forward tcp:8765 tcp:8765
```

**B1 — Protocol smoke test with curl** (faster diagnosis than a full agent):

```bash
MCP=http://localhost:8765/mcp
# initialize → expect serverInfo "kide-devtools" + instructions text
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'
# tools/list → expect 6 kide_* tools
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

**B2 — Live inspection.** *Navigate to the Search screen first* — the `search`
processor is attached lazily when the screen is created; before that,
`kide_list_processors` legitimately reports none.

```bash
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"kide_list_processors","arguments":{}}}'
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"kide_get_trace","arguments":{"processor":"search","limit":"20"}}}'
```

`kide_list_processors` must report the `search` entry with all six fields:

| Field | Expected |
|---|---|
| `name` | `search` |
| `processorClass` | `org.fuusio.kide.app.feature.search.presentation.SearchProcessor` |
| `intentClass` | `org.fuusio.kide.app.feature.search.presentation.SearchIntent` |
| `currentState` | rendering of the current `SearchViewState` |
| `recordedEvents` | grows as you use the screen |
| `closed` | `false` |

`intentClass` is what `kide_dispatch_intent` type-checks against — if it reads `unknown`,
the processor was attached with the deprecated `KideDebug.attach` instead of `attach`.

Type something in the Search field on the device, re-run `kide_get_trace` — the new
`UpdateQuery` intents and state diffs must appear.

**B2a — Asynchronous reductions in the trace.** Run an actual search (type a query, then
trigger it) and re-read the trace. It must contain `StateChanged` events for the
*asynchronous* half of the work, not just the synchronous `UpdateQuery` reductions:
`isLoading` going true, then results arriving and `isLoading` going false again.

This is the fix that motivated 1.2.0 and it cannot be checked by unit tests against the
real app. Before the fix, reductions performed inside `async { }` / `useCase { }` never
reached interceptors, so this half of every search was simply absent from the trace — the
symptom being a recorded session that shows a load starting and never finishing. If the
`isLoading` transitions are missing here, that regression is back.

**B3 — Intent injection (watch the device screen).**

```bash
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"kide_dispatch_intent","arguments":{"processor":"search","intent_class":"org.fuusio.kide.app.feature.search.presentation.UpdateQuery","intent_json":"{\"query\":\"compose multiplatform\"}"}}}'
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"kide_dispatch_intent","arguments":{"processor":"search","intent_class":"org.fuusio.kide.app.feature.search.presentation.TriggerSearch","intent_json":"{}"}}}'
```

Expected: the query text changes **on screen** after the first call; results load after
the second. Then export the session:

```bash
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"kide_export_regression_test","arguments":{"processor":"search"}}}'
```

**B2b — Domain events in the trace.** Save a project from the Search screen (the star / save
control), then re-read the trace.

The `SavedProjectsProcessor` shares the application's `TraceBuffer` with the search screen's
`FlightRecorder`, so the trace must contain **both** layers for that one tap, tied together by
a single `correlationId`:

```
"source":"Presentation" "type":"Intent"        ← ToggleSave
"source":"Presentation" "type":"ActionMapped"
"source":"Domain"       "type":"Intent"        ← SaveProject
"source":"Domain"       "type":"StateChanged"  ← SavedProjectsState, projects grew
"source":"Presentation" "type":"StateChanged"
```

Check that every one of those events carries the same `correlationId`, and that a *second*
save gets a different one. This is the whole point of the domain-tracing work: before it, the
trace stopped at the first two lines and an agent could not tell a failed repository write from
a UI that never dispatched.

Events with `"correlationId":null` are expected and correct — `SavedProjectsProcessor` collects
repository flows from its `init` block, which has no originating intent.

**B3a — Rejected injections (negative cases).** Each of these must come back as a tool
result with `"isError": true` and a message that says what was wrong. Silence, or a
success message with no visible change on the device, is the failure being tested for —
that is what an agent would chase as an application bug that does not exist.

```bash
# Wrong type: SearchViewState is @Serializable and decodes from {}, but is not a SearchIntent.
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"kide_dispatch_intent","arguments":{"processor":"search","intent_class":"org.fuusio.kide.app.feature.search.presentation.SearchViewState","intent_json":"{}"}}}'
# Unknown processor name.
curl -s -X POST $MCP -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"kide_dispatch_intent","arguments":{"processor":"nope","intent_class":"org.fuusio.kide.app.feature.search.presentation.TriggerSearch","intent_json":"{}"}}}'
```

Expected messages: the first names both types — *"Processor 'search' accepts
…SearchIntent, but got …SearchViewState"*; the second is *"No processor named 'nope'"*.
The device screen must not change for either.

The class in the first call is deliberately one that is `@Serializable` and has defaults
for every field. A class that is neither would be rejected earlier, by `serializer(type)`
or by the decode, and the type check at the handle boundary would never run — the test
would pass without testing anything. If you substitute another class here, keep those two
properties.

**B3b — Closed processor.** Navigate away from Search so its destination is popped, then
re-run either `kide_dispatch_intent` call from B3. Expected: `"isError": true` with
*"Processor 'search' … is closed and cannot accept intents"*, and `kide_list_processors`
reporting `"closed": true` for the stale handle rather than presenting it as live.

**B4 — Real agent.**

```
claude mcp add --transport http kide http://localhost:8765/mcp
```

In Claude Code, ask: *"Using the kide tools, what is the current state of the search
screen, and what did the user do last?"* — the agent should call `kide_list_processors`
/ `kide_get_state` / `kide_get_trace` and answer from live data.

**B5 — Release-build guard (security check).**
Install a release (non-debuggable) build and verify logcat shows
`Refusing to start the Kide agent port: application is not debuggable`, and
`curl http://localhost:8765/mcp` gets connection refused after `adb forward`.

**Failure signatures:**
- Connection refused in B1 → server never started: check B0 log line and the guard.
- `Could not start the Kide agent port` in logcat, app otherwise running → the port is held by
  something else, usually an older build of the app still alive. `adb shell ss -ltnp | grep
  8765` to find it. The app deliberately keeps running: losing the agent port must never be
  fatal. (Before 2.0.0 this crashed the app at startup.)
- Empty processor list in B2 → Search screen not yet visited (expected), or
  `KideDebug.attach` not wired in `SearchFeature`.
- `intentClass` reads `unknown` in B2 → the processor was attached with the deprecated
  `KideDebug.attach`; injected intents are not type-checked. Switch to `attach`.
- No `isLoading` transitions in the B2a trace → reductions made inside `async { }` /
  `useCase { }` are not reaching interceptors. This is the regression 1.2.0 fixed; the
  trace-fidelity unit tests in `kide` should have caught it, so treat it as a signal that
  something bypassed `PresentationProcessor.reduceState`.
- `Serializer for class ... not found` in B3 → the intent class isn't `@Serializable`,
  or (release builds only) R8 stripped serializers — agent port is debug-only, so this
  should never occur in practice. Seeing it in **B3a** instead means the negative test is
  not reaching the type check: pick a serializable class with all-default fields.
- B3a succeeds, or fails silently with no `isError` → the handle's type check is not
  running. An agent hitting this will read the success, see unchanged state, and start
  looking for a bug in application code that does not exist.
