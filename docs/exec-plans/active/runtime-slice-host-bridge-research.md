# Runtime Slice Host Bridge Research

## Purpose

Эта подложка фиксирует фактическое состояние репозитория, tool preflight, official facts и reference signals, на которых основан active `ExecPlan`.

## Scope Frame

Этот slice ограничен Android-side подготовкой к runtime:

- typed Host Bridge contract;
- pairing payload flow;
- local network permission/rationale;
- runtime-aware seam вместо прямого wiring fake repositories;
- host/connection diagnostics.

Не входит:

- real `thread/start` / `turn/start` runtime;
- review/diff;
- notifications;
- diagnostics export/persistence;
- отдельный `:feature:connection` module;
- shared core extraction.

## Stop Condition

План и реализация считаются корректно ограниченными, если:

- pairing/local-network UX живет в `Settings`;
- `Projects` и `Diagnostics` получают только необходимые host signals;
- `Thread` shell не притворяется real runtime;
- следующий milestone остается `Host Bridge MVP`, а не размывается в текущем slice.

## Canonical Repo Sources Read

Прочитаны в repo-local порядке:

1. `AGENTS.md`
2. `README.md`
3. `Documentation.md`
4. `docs/ROADMAP.md`
5. `docs/exec-plans/active/ExecPlan.md`
6. `docs/exec-plans/tech-debt-tracker.md`
7. `docs/architecture/application-architecture.md`
8. `docs/DECISIONS.md`
9. `docs/QUALITY.md`
10. `docs/observability/logging-and-diagnostics.md`
11. `docs/generated/project-interfaces.md`
12. `docs/generated/stack-inventory.md`
13. `docs/generated/commands.md`
14. `docs/generated/test-matrix.md`
15. `docs/process/android-agent-workflow.md`

## Tool Preflight

### JetBrains MCP

- `codex mcp get jetbrains` confirms configured Android Studio stdio server.
- Native `jetbrains` tool calls (`get_project_modules`, `get_run_configurations`, `list_directory_tree`, `get_file_problems`) timed out repeatedly at 120s in the current runtime.
- Decision:
  - do not block the slice on IDE namespace availability;
  - record the limitation and use `rg` / file reads as fallback.

### Android CLI

Observed facts from `android describe --project_dir .`:

- modules:
  - `:app`
  - `:core:design`
  - `:core:logging`
  - `:core:model`
  - `:feature:diagnostics`
  - `:feature:projects`
  - `:feature:settings`
  - `:feature:thread`
- `app-debug.apk` exists and describe passes successfully.

Observed facts from `android docs search`:

- `local network permission android 16` points to:
  - local network permission doc
  - Android 16 behavior changes
  - Android 16 summary
- `navigation compose routes arguments` points to Navigation/route/type-safety docs.
- `window size classes adaptive navigation compose` points to `use-window-size-classes` and `build-adaptive-navigation`.
- `state hoisting app architecture compose` points to `state-hoisting` and app architecture docs.

## Current Repo State In Code

### App shell and state owner

- `app/src/main/java/dev/vitalcc/stukay/MainActivity.kt`
  - activity already enables edge-to-edge and hosts a `ViewModel`-backed app state.
- `app/src/main/java/dev/vitalcc/stukay/StukayAppViewModel.kt`
  - keeps one `StukayAppState` instance.
- `app/src/main/java/dev/vitalcc/stukay/StukayApp.kt`
  - existing route graph:
    - `projects`
    - `project/{projectId}`
    - `thread/{threadId}`
    - `settings`
    - `diagnostics`
  - passes callbacks instead of passing `NavController` into feature composables, which already matches official Navigation Compose guidance.

### Direct fake seam

- `app/src/main/java/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - current direct construction:
    - `FakeProjectsRepository`
    - `FakeThreadRepository`
  - current problem:
    - app-level state holder owns fake wiring directly;
    - no runtime-aware graph/container;
    - no host/pairing/connection state.

### Repository boundaries

- `feature/projects/.../ProjectsRepository.kt`
  - already a minimal interface seam.
- `feature/thread/.../ThreadRepository.kt`
  - already an interface seam, but polluted by fake-only commands:
    - `startFakeTurn`
    - `completeFakeTurn`
    - `resolveApproval`

### Existing entry points for this slice

- `feature/settings/.../SettingsRoute.kt`
  - best existing location for pairing/local-network control plane.
- `feature/diagnostics/.../DiagnosticsRoute.kt`
  - best existing location for host/connection diagnostics.
- `feature/projects/.../ProjectsRoute.kt`
  - suitable for lightweight status signal only.

### Diagnostics baseline

- `core/logging/.../LogArea.kt`
  - already contains `Connection`, `HostBridge`, `Codex`, `Security`, `Diagnostics`.
- `core/logging/.../DiagnosticsSummary.kt`
  - current summary has only:
    - `sessionStartedAt`
    - `totalLogs`
    - `latestWarningOrError`
    - `recentLogs`
- `feature/diagnostics/.../DiagnosticsRoute.kt`
  - current UI shows route/session/log snapshot only.

### Build and manifest surface

- `settings.gradle.kts`
  - no `:feature:connection` module exists now.
- `app/build.gradle.kts`
  - current app already depends on all `core:*` and `feature:*` modules;
  - no extra networking/runtime module is wired.
- `app/src/main/AndroidManifest.xml`
  - no `INTERNET`, `ACCESS_NETWORK_STATE`, `NEARBY_WIFI_DEVICES`, or other network permissions yet.

## Official Facts To Honor

## Android 16 / 17 local network

Sources:

- `android docs fetch kb://android/privacy-and-security/local-network-permission`
- `android docs fetch kb://android/about/versions/16/behavior-changes-16`
- Android Developers official pages for the same URLs

Key facts:

- Android 16 local network protections are still opt-in/testing-oriented for apps targeting API 36.
- During Android 16 opt-in guidance:
  - local network restrictions are enabled with `adb shell am compat enable RESTRICT_LOCAL_NETWORK <package>`;
  - restoring access uses `NEARBY_WIFI_DEVICES`;
  - framework APIs such as `NsdManager` are not impacted during the opt-in phase in the same way as raw socket traffic.
- Starting with Android 17 / targetSdk 37+:
  - local network becomes blocked by default;
  - broad access requires `ACCESS_LOCAL_NETWORK`;
  - privacy-preserving picker paths are explicitly preferred where applicable.

Implication for this repo:

- do not write the plan as if API 36 app can simply request `ACCESS_LOCAL_NETWORK` now;
- document Android 16 companion path honestly;
- keep Android 17 path visible but deferred.

## Navigation and route contract

Sources:

- `https://developer.android.com/develop/ui/compose/navigation`

Key facts:

- destinations should receive callbacks, not raw `NavController`;
- navigation arguments should carry minimal IDs, not complex objects;
- complex state should be loaded from the data layer / SSOT after navigation.

Implication for this repo:

- keep current `ProjectId` / `ThreadId` route pattern;
- do not invent a complex serialized connection object in routes;
- pairing/local-network controls should not force a new route model unless screen complexity demands it.

## App architecture and state hoisting

Sources:

- `https://developer.android.com/topic/architecture`
- `https://developer.android.com/develop/ui/compose/state-hoisting`

Key facts:

- repositories should expose data, centralize changes, and abstract data sources;
- state should be hoisted to the lowest common ancestor that reads/writes it;
- app-level or screen-level state holder should own business-relevant state.

Implication for this repo:

- `StukayAppState` or a container immediately under it is the right owner for host connection state;
- local pairing text field state can live near UI, but trusted host / connection lifecycle must live in app/runtime state;
- direct fake construction inside `StukayAppState` is the wrong long-term seam.

## Adaptive UI

Sources:

- `https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes`
- `https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation`
- `https://developer.android.com/develop/ui/compose/layouts/adaptive/app-orientation-aspect-ratio-resizability?hl=en`

Key facts:

- window size classes are for high-level layout decisions, not device-type branching;
- `NavigationSuiteScaffold` is useful when top-level navigation truly needs to switch between bar/rail/drawer;
- Android 16 ignores orientation/aspect/resizability restrictions on `sw600dp+`, but most phones remain under that threshold.

Implication for this repo:

- current width-constrained `ScreenFrame` is still a valid baseline for this slice;
- do not drag `NavigationSuiteScaffold` into Host Bridge pairing work just because adaptive docs exist;
- keep layouts edge-to-edge and configuration-safe.

## OpenAI Codex app-server

Sources:

- `mcp__openaiDeveloperDocs__fetch_openai_doc(url="https://developers.openai.com/codex/app-server", anchor="protocol")`
- `... anchor="api-overview"`
- `... anchor="command-execution-approvals"`
- `... anchor="items"`
- `... anchor="run-a-thread-shell-command"`
- `https://developers.openai.com/codex/cli/features#connect-the-tui-to-a-remote-app-server`

Key facts:

- `codex app-server` speaks JSON-RPC 2.0 style messages over:
  - `stdio` by default;
  - `ws://IP:PORT` as experimental/unsupported WebSocket transport.
- non-loopback WebSocket exposure needs explicit auth/TLS thinking.
- API surface already distinguishes:
  - `thread/*`
  - `turn/*`
  - `command/exec`
  - `app/list`
  - approval notifications
  - item stream lifecycle.
- approval protocol explicitly exposes `networkApprovalContext` for network-specific prompts.
- `thread/shellCommand` is user-initiated and outside normal thread sandbox inheritance.

Implication for this repo:

- current slice should model Host Bridge as a typed backend surface, not as a copy of the desktop UI;
- local Android approval UI should preserve the distinction between generic command approval and network approval;
- WebSocket is acceptable as a future bridge transport, but the plan must keep its experimental status explicit.

## Reference Repo Signals

## `references/relaydex`

Used for:

- QR/bootstrap pairing shape;
- trusted reconnect model;
- host bridge vs mobile client separation;
- secure reconnect catch-up thinking.

Extracted signals:

- pairing payload can be short-lived and host-generated;
- trusted reconnect should be distinct from first-time QR bootstrap;
- approval UI and connection state belong in client state, not in thread timeline itself;
- bridge is a host-side runtime, phone is a paired remote client only.

## `references/codexdroid`

Used for:

- separation between connection selection and thread/session state;
- explicit pending-approval queue;
- reconnect/health polling UI model.

Extracted signals:

- `connection state` and `thread state` should not be conflated;
- pending approval handling benefits from dedicated queueing surface;
- reconnect health can live alongside session state without forcing a huge DI graph.

## `references/openconnect`

Used for:

- pairing link / WSS endpoint framing;
- reconnect policy examples;
- bridging host status into a diagnostics-heavy Android `ViewModel`.

Extracted signals:

- pairing flow can start from a URL/payload and still stay explicit about sensitive secrets;
- auto-reconnect needs user-visible status text and backoff state;
- logs/diagnostics become more useful when wire IN/OUT and bridge health are first-class signals.

## `references/PocketDex`

Used for:

- one-time pairing token -> session token pattern;
- proxy/bridge architecture with phone client separated from local app-server;
- approval request shapes including network approval context.

Extracted signals:

- pairing should have a first-scan bootstrap vs longer-lived reconnect credential split;
- pending server requests deserve their own explicit state, not hidden flags;
- network approval context is specific enough to justify a dedicated UI copy path.

## `references/codexUI` and `references/farfield`

Used only for:

- pending approval and waiting-state UX;
- bridge/backend separation patterns.

Extracted signals:

- waiting-for-approval indicators should be visible outside the active thread view;
- pending approvals are a cross-surface runtime concern, not just a thread detail.

## `references/openai-codex`

Used only as read-only semantic backup around thread/turn and protocol thinking after official docs.

Extracted signal:

- official docs remain the normative source; repo code is only useful to understand likely runtime semantics and client expectations.

## Non-Blocking Decisions Before Implementation

- No new top-level route is required right now.
- No new Gradle module is required right now.
- Pairing payload input is sufficient for this slice; QR camera scanning is deferred.
- Real host-backed thread runtime is deferred to the next milestone.
