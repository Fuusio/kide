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

Type something in the Search field on the device, re-run `kide_get_trace` — the new
`UpdateQuery` intents and state diffs must appear.

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
- Empty processor list in B2 → Search screen not yet visited (expected), or
  `KideDebug.attach` not wired in `SearchFeature`.
- `Serializer for class ... not found` in B3 → the intent class isn't `@Serializable`,
  or (release builds only) R8 stripped serializers — agent port is debug-only, so this
  should never occur in practice.
