# Real Thread Runtime + Approval Safety Layer

## Goal

Закрыть один большой, но ограниченный slice, который переводит `Stukay` с summary-only Host Bridge MVP на реальный thread runtime поверх уже доказанного host-backed path:

- runtime-backed projects/threads read path;
- `thread/list`;
- `thread/read`;
- открытие существующего реального thread;
- `turn/start`;
- streaming ответа;
- `turn/interrupt`;
- reconnect recovery для активного thread;
- real approval lifecycle для command / file / network approvals;
- accessibility baseline для новых runtime/UI surfaces;
- diagnostics/logging/evidence для runtime + approvals.

Slice не должен превращаться в “всё приложение” и не должен переоткрывать transport foundation.

## In Scope / Out Of Scope

### In scope

- derivation runtime-backed `Projects` и `Project` surfaces из app-server thread data;
- real thread list/read/open flow через Host Bridge helper;
- active thread runtime state на Android;
- send / stream / interrupt для одного активного foreground thread;
- reconnect recovery после потери и возврата Host Bridge path;
- approve once / approve for session / decline / cancel;
- stale / cleared / interrupted approval handling;
- structured runtime diagnostics и redacted logging;
- semantics/testability baseline для runtime controls и status surfaces.

### Out of scope

- public tunnel / internet endpoint path;
- TLS / public transport hardening;
- review / diff / file-change visual surface beyond minimal approval metadata;
- notifications;
- diagnostics persistence/export;
- desktop handoff;
- shared core extraction;
- большой UI redesign;
- новый heavy DI framework;
- arbitrary dynamic tool UX, full MCP elicitation UX, generic remote-control shell beyond this thread slice.

## Stop Condition

Milestone считается завершенным только если одновременно выполняются все условия:

1. `Projects` и `Project` больше не читаются из `FakeProjectsRepository` / `FakeThreadRepository` для покрытого flow; список строится из runtime-backed thread data.
2. Пользователь может открыть существующий thread, увидеть историю, отправить новый prompt, получить streaming, остановить активный turn и пережить reconnect без ручного “сброса” thread state.
3. Command / file / network approvals доходят до Android через real Host Bridge path и отрабатывают четыре решения: `once`, `session`, `decline`, `cancel`.
4. Pending approval честно очищается по `serverRequest/resolved`, `turn/completed`, `turn/interrupt` и reconnect rehydration, без вечных “залипших” карточек.
5. Новые runtime controls и states доступны через semantics/accessibility tree, а не только через пиксели.
6. Diagnostics/logging показывают thread / turn / approval lifecycle с redaction и без утечки токенов / raw payloads.
7. На финальном состоянии в пользовательском UI больше нет fake-only affordances вроде `Start fake run` / `Complete fake run`.

Отдельно фиксируем, что final stop condition не включает review/diff UI, notifications, export, public endpoint и shared core.

## Current Repo State

- `Host Bridge MVP` уже локально закрыт и доказан; следующий активный слой по repo docs — `Real Thread Runtime`.
- `StukayRuntimeGraph` уже заводит реальный `HttpJsonHostBridgeRepository`, но `projectsRepository` и `threadRepository` по-прежнему обернуты вокруг `FakeProjectsRepository` и `FakeThreadRepository`.
- `StukayAppState` уже является activity-scoped owner для shell route state, host bridge connection state, probe loop, network callback и diagnostics.
- `RuntimeProjectsRepository` и `RuntimeThreadRepository` сейчас являются только pass-through seam к fake delegates.
- Host-side helper (`tools/hostbridge`) умеет только `initialize` / `initialized` / `app/list` и публикует один summary endpoint `/v1/runtime/summary`.
- `feature:thread` держит fake-only action contract: `startFakeTurn`, `completeFakeTurn`, `resolveApproval`.
- `TimelineItem` и `ThreadStatus` пока отражают упрощенную fake domain model, а не app-server lifecycle/status union.
- Structured logging, diagnostics summary, host connection telemetry и route context уже внедрены и пригодны как base для runtime evidence.

## Integration Points In Code

### App shell and owner state

- `app/src/main/kotlin/dev/vitalcc/stukay/StukayApp.kt`
  - текущий navigation wiring и callback surface;
  - нужно заменить fake turn callbacks на real runtime actions и добавить composer/stop wiring.
- `app/src/main/kotlin/dev/vitalcc/stukay/StukayAppViewModel.kt`
  - lifecycle owner для `StukayAppState`; менять минимально.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - рекомендованный owner для foreground thread runtime session;
  - уже держит executor, main-thread handoff, network callbacks и diagnostics;
  - сюда естественно ложатся active thread id, active turn id, streaming projection, pending approvals, reconnect recovery.

### Runtime graph and repositories

- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayRuntimeGraph.kt`
  - final wire-up point; сюда должен прийти real runtime-backed projects/thread stack вместо fake delegates.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/RuntimeProjectsRepository.kt`
  - должен перестать делегировать в fake repo;
  - должен синтезировать `CodexProject` из `thread/list` + `cwd`/metadata grouping.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/RuntimeThreadRepository.kt`
  - должен перестать делегировать fake actions;
  - должен стать typed runtime gateway для `thread/list`, `thread/read`, `thread/resume`, `turn/start`, `turn/interrupt`, approvals.

### Host Bridge / transport boundary

- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeRepository.kt`
  - current boundary already guards private LAN / `.local`;
  - здесь нельзя строить второй transport stack, но нужно расширять host runtime contract выше summary-only.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeClient.kt`
  - current HTTP client only knows `/v1/runtime/summary`;
  - сюда лягут typed runtime endpoints and streaming client support.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgePairingParser.kt`
  - pairing boundary already validated; менять только если понадобится explicit session metadata versioning.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgePairingStore.kt`
  - storage seam for pairing payload; not a protocol source of truth.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/AndroidNetworkMonitor.kt`
  - existing `ConnectivityManager.NetworkCallback` seam for reconnect recovery.

### Domain / model / logging

- `core/model/src/main/java/dev/vitalcc/stukay/core/model/ProjectModels.kt`
  - `CodexProject` stays app-level grouping model; source becomes derived runtime data.
- `core/model/src/main/java/dev/vitalcc/stukay/core/model/ThreadModels.kt`
  - current enum is too fake-only; needs alignment with app-server thread/runtime truth.
- `core/model/src/main/java/dev/vitalcc/stukay/core/model/TimelineModels.kt`
  - current item set is a good start, but needs real lifecycle/status payloads and approval context fields.
- `core/model/src/main/java/dev/vitalcc/stukay/core/model/HostBridgeModels.kt`
  - current host summary model exists; add thread/turn/approval state only where it belongs, not by overloading host summary.
- `core/logging/src/main/java/dev/vitalcc/stukay/core/logging/*`
  - already supports structured areas and recent-log diagnostics; extend fields/events, not architecture.

### Feature/UI surfaces

- `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/data/ProjectsRepository.kt`
  - contract may stay minimal if project derivation stays read-only.
- `feature/thread/src/main/java/dev/vitalcc/stukay/feature/thread/data/ThreadRepository.kt`
  - current interface is fake-only and must be replaced or evolved in the same slice.
- `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectsRoute.kt`
  - current copy still says thread runtime is fake-only; must flip to honest runtime-backed state.
- `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectRoute.kt`
  - project thread list must render real runtime thread summaries and statuses.
- `feature/thread/src/main/java/dev/vitalcc/stukay/feature/thread/ui/ThreadRoute.kt`
  - biggest UI seam: composer, streaming, stop, approval cards, degraded/reconnect state, semantics/test tags.
- `feature/settings/src/main/java/dev/vitalcc/stukay/feature/settings/ui/SettingsRoute.kt`
  - stays pairing/connection surface, but may need runtime session diagnostics / reconnect messaging only.
- `feature/diagnostics/src/main/java/dev/vitalcc/stukay/feature/diagnostics/ui/DiagnosticsRoute.kt`
  - should expose current thread/turn/pending approval/runtime stream truth, not only host summary.

### Host-side helper

- `tools/hostbridge/server.py`
  - currently exports only `/v1/runtime/summary`;
  - must grow into narrow typed runtime proxy, not raw generic app-server passthrough.
- `tools/hostbridge/runtime_client.py`
  - already owns persistent stdio app-server process and JSON-RPC request loop;
  - needs request/response support for thread/turn methods and notification streaming fan-out.
- `tools/hostbridge/auth.py`
  - keep bearer auth model; preserve redaction discipline.
- `tools/hostbridge/models.py`
  - summary-only today; needs typed host->Android payload models for thread, turn, approval and stream events.

### Build / config

- `app/src/main/AndroidManifest.xml`
  - current permission set is enough for this slice (`INTERNET`, `ACCESS_NETWORK_STATE`, `NEARBY_WIFI_DEVICES`);
  - no new public-path permission work should leak in.
- `app/src/main/res/xml/network_security_config.xml`
  - keep current cleartext opt-in; do not turn this slice into TLS hardening.
- `app/build.gradle.kts`
  - probable need: test/compose-testing dependencies only if accessibility proof requires them.
- `gradle/libs.versions.toml`
  - update only if required for bounded test/tooling additions.

## Official Facts To Honor

### Codex app-server

- `thread/list`, `thread/read`, `thread/start`, `thread/resume`, `turn/start`, `turn/interrupt`, `thread/status/changed`, `turn/*`, `item/*`, and `serverRequest/resolved` are documented v2 surfaces for this slice.
- `thread/read` is read-only hydration: it does not load the thread into memory and does not emit `thread/started`.
- Interactive opening of an existing thread requires `thread/resume`; otherwise later `turn/start` does not append to a loaded live session.
- `thread/list` is cursor-paginated and supports `sortKey`, `sortDirection`, `modelProviders`, `sourceKinds`, `archived`, `cwd`, `searchTerm`.
- Important nuance: when `sourceKinds` is omitted, the default is interactive sources only (`cli`, `vscode`). If Android-created threads must be visible later, the client must explicitly include `appServer`.
- `thread` runtime status is a union:
  - `notLoaded`
  - `idle`
  - `systemError`
  - `active` with `activeFlags`
- stable `activeFlags` in current schema:
  - `waitingOnApproval`
  - `waitingOnUserInput`
- `turn/start` returns an initial `turn`, then streams `turn/started`, `item/started`, deltas such as `item/agentMessage/delta`, and eventually `turn/completed`.
- `turn/interrupt` returns `{}` and the authoritative terminal state is the later `turn/completed` with `status: "interrupted"`.
- approvals are server-initiated JSON-RPC requests; request payloads include at least `threadId`, `turnId`, `itemId`, `requestId` semantics and must be cleared by `serverRequest/resolved`.
- command approvals can carry `networkApprovalContext`; network approval is not a separate protocol family and should be rendered as a network-specific variant of command approval.
- `availableDecisions` may constrain what the server wants the client to show. For this slice we must support the shared scalar subset: `accept`, `acceptForSession`, `decline`, `cancel`.
- `tool/requestUserInput`, `dynamicTools`, `thread/turns/list`, `thread/backgroundTerminals/clean`, `thread/goal/*` are experimental or beyond the bounded slice and must not silently expand scope.

### Android

- Android 16 / targetSdk 36 requires edge-to-edge support; this slice must not regress it.
- Predictive back is enabled by default for apps targeting Android 16+.
- Local network protection is opt-in / transitional on Android 16 and mandatory only from Android 17 (`ACCESS_LOCAL_NETWORK`); current slice should keep using the existing API 36 posture around `NEARBY_WIFI_DEVICES`.
- Connectivity state should be observed through `ConnectivityManager` / `NetworkCallback`, not polling-only heuristics.
- Compose semantics are a first-class contract for accessibility, autofill, and testing.
- Compose UI tests use the semantics tree; meaningful `contentDescription`, state text, and stable identifiers improve both accessibility and automation.
- Window size classes and adaptive navigation exist, but current repo policy is width-constrained shell, not a new adaptive-navigation rewrite for this slice.

## Reference Findings

### `references/openai-codex`

- Main protocol truth: use `thread/list` for history, `thread/read` for passive hydration, and `thread/resume` before real interaction.
- `thread/list` pagination + `sourceKinds` behavior is important for Stukay because “projects” must be derived from thread metadata, not from a nonexistent project API.
- `thread/status/changed.activeFlags` gives the minimal stable blocked-state signal (`waitingOnApproval`, `waitingOnUserInput`).
- Approval lifecycle is item/request-based, and `serverRequest/resolved` is the authoritative cleanup event.
- SDK examples confirm the practical lifecycle: start/resume, read with turns, archive/unarchive, then continue turning on the resumed thread.

### `references/codexdroid`

- `SessionViewModel` keeps one foreground session owner that reconciles live event stream with `thread/read` fallback; that is a strong signal not to push foreground lifecycle into random composables.
- `thread/read` is used as reconnect/drop fallback for terminal-state confirmation when event streaming is unreliable.
- Approval UI is separate from the timeline list and clears only after explicit response.
- `SessionStateReducer` keeps UI-only fields (`activeTurnId`, `isSending`, `pendingUserMessage`, `error`) separate from raw thread DTOs.
- Workspace/project grouping is effectively `cwd`-based rather than server-native “project” data.

### `references/relaydex`

- Thread/project grouping is derived from `cwd` / normalized project path.
- Bridge/runtime design keeps the local runtime warm across transient relay disconnects and preserves per-thread active turn recovery.
- Turn timeline projection is a reducer concern, not transport DTO rendering inline in the view.
- Composer / stop controls are keyed off active turn state, and reconnect recovery explicitly refreshes in-flight turn state.

### `references/PocketDex`

- A mobile client can stay simple if the host-side proxy normalizes app-server notifications and approval requests before forwarding them.
- Approval protocol should respect `availableDecisions` aliases instead of hardcoding one wire spelling.
- Proxy/auth responsibilities are distinct from runtime responsibilities: QR/session token/auth live at the proxy boundary, not inside the UI state machine.

### `references/openconnect`

- Good reminder that the phone is a controller and the computer is the runtime owner.
- Pairing/runtime boundary must stay explicit; public tunnel or fixed-domain setup is a separate concern from runtime lifecycle itself.

### `references/farfield` and `references/codexUI`

- Useful only as secondary UI signals for thread browser, live monitoring, and remote status surfacing.
- Not a source of truth for Android architecture or Host Bridge contract shape.

## Target Runtime Shape

### Core stance

- Keep `Windows Host Bridge -> codex app-server` as the canonical runtime path.
- Do not let Android talk raw app-server protocol directly.
- Do not create a second parallel runtime graph beside `StukayAppState`.

### Recommended ownership

- `StukayAppState` remains the top-level owner for:
  - current route and selected thread context;
  - foreground active thread runtime state;
  - reconnect recovery triggers;
  - diagnostics/logging integration;
  - approval presentation state.
- `RuntimeProjectsRepository` becomes the read-model builder for derived project groups.
- `RuntimeThreadRepository` becomes the typed operation gateway for thread/turn/approval calls plus cached thread snapshots.
- `ThreadRoute` remains render/action surface only.

### Host Bridge helper surface

The helper should evolve from summary-only HTTP into a narrow typed runtime proxy:

- keep `GET /v1/runtime/summary`;
- add typed thread read/list/open endpoints;
- add typed turn start/interrupt endpoints;
- add typed approval response endpoint;
- add a streaming event channel for the active loaded thread.

The helper should normalize and redact app-server data for Android instead of exposing raw generic JSON-RPC over the network.

### Project derivation

There is no canonical `project/list` in app-server.

For this slice, `CodexProject` should be derived from runtime thread metadata:

- primary grouping key: normalized `cwd`;
- project title: best-effort display name derived from `cwd` / stored thread metadata;
- project summary/status: synthesized from newest thread activity within that group;
- archived threads stay outside the main live project buckets unless explicitly requested.

### Thread opening model

- list/history surfaces use `thread/list`;
- passive hydration uses `thread/read(includeTurns=true)` when needed;
- interactive open uses `thread/resume`, because that is what subscribes the client to live turn/item events.

### Timeline model

`TimelineItem` stays typed, but must become runtime-backed:

- `UserMessage`
- `AssistantMessage` with streaming and completion semantics
- `CommandRun` with in-progress / completed / failed / declined projection
- `FileChange` only to the extent needed for approval context and minimal status
- `ApprovalRequest` with command/file/network context, requestId/itemId scope, resolved/stale state
- `StatusEvent` for reconnect, degraded, interrupted, resumed, cleared states

## Progress Tracking

### Status values

- `pending` — этап не начат;
- `in_progress` — этап активен, но acceptance ещё не закрыт;
- `blocked` — есть blocker, из-за которого этап нельзя честно закрыть;
- `done` — acceptance закрыт, verification пройден, stage report заполнен;
- `verified` — после `done` дополнительно подтверждён device/runtime proof там, где он нужен.

### Working rule

- Рекомендуемый ритм: закрывать milestone отдельным checkpoint-коммитом после выполнения acceptance и заполнения stage report.
- Если milestone слишком большой для одного коммита, делить его на 2-3 осмысленных коммита, но всё равно завершать milestone отдельным финальным checkpoint-коммитом.
- Не начинать следующий milestone, пока у текущего не заполнены `status`, checklist и stage report.

### Review loop protocol

- Перед каждым checkpoint-коммитом milestone запускать отдельный review loop через субагентов по незакоммиченным изменениям milestone.
- Этот loop является review-only и sandbox-only: findings считаются гипотезами, а не истиной; принимать их без локальной проверки нельзя.
- Субагентам не передавать fork истории чата; каждому давать явный контекст-промпт с:
  - целью текущего milestone;
  - рамкой slice и non-goals;
  - списком затронутых файлов;
  - просьбой смотреть только diff, код, тесты, docs и official docs.
- Базовый prompt для review-субагента:

```md
Это review в sandbox-режиме: не запускай bootstrap, verify, dotnet restore, dotnet test, сборку, smoke, линтеры и любые тяжёлые проверки, если я отдельно не попрошу; считай, что такие прогоны здесь нерелевантны или ненадёжны, и делай выводы только по статическому анализу diff, кода, тестов, docs и официальной документации. Если для подтверждения замечания очень нужен runtime-сигнал, сначала коротко укажи, зачем именно он нужен, но ничего не запускай без отдельного разрешения, не запрашивай разрешения на повышение прав.
```

- Для одного pre-commit loop рекомендуется `2-3` review-субагента с разными фокусами:
  - runtime/protocol;
  - Android/UI/accessibility;
  - tests/docs/acceptance boundaries.
- После получения findings:
  - разобрать их как гипотезы;
  - подтвердить или отклонить локальным анализом;
  - исправить только подтверждённые root-cause;
  - затем отправить этих же субагентов на перепроверку и конкретных исправлений, и общего состояния milestone.
- Если loop уходит в мелкую docs-семантику, формулировки без продуктового/контрактного риска или повтор уже закрытых stylistic замечаний, loop останавливать и milestone не блокировать.
- После checkpoint-коммита milestone этих review-субагентов закрывать.
- Для следующего milestone запускать уже новых review-субагентов, а не переиспользовать старых.
- После завершения всего плана запускать ещё один branch-wide review loop относительно `main`, снова новыми субагентами и снова в review-only sandbox-режиме.

## Milestones

### Milestone 1: Runtime-backed read path

#### Status

- current: `pending`

#### Objective

Replace fake projects/threads read surfaces with runtime-derived data while staying read-focused.

#### Recommended commit split

- Commit A: host helper + Android runtime read contracts for `thread/list` / `thread/read`.
- Commit B: runtime-backed `Projects` / `Project` derivation, removal of fake read dependency.

#### Implementation checklist

- [ ] Expand host helper + Android client for typed `thread/list` and `thread/read`.
- [ ] Derive `CodexProject` groups from runtime thread metadata (`cwd`-first grouping).
- [ ] Replace `RuntimeProjectsRepository` delegate path with real runtime mapping.
- [ ] Replace `RuntimeThreadRepository.loadThreads/loadThread/loadTimeline` read path with runtime-backed snapshots.
- [ ] Keep a clear temporary branch-only boundary while migrating; final milestone must remove fake read dependency.

#### Acceptance checklist

- [ ] `Projects` renders runtime-derived project groups.
- [ ] `Project` renders runtime-derived thread summaries and statuses.
- [ ] Opening a thread no longer depends on `FakeThreadRepository`.
- [ ] No new parallel project model is introduced beside the existing `CodexProject`.

### Milestone 2: Active turn lifecycle

#### Status

- current: `pending`

#### Objective

Turn one opened thread into a real interactive runtime surface.

#### Recommended commit split

- Commit A: host helper/event stream + Android live thread subscription/recovery scaffolding.
- Commit B: `ThreadRoute` real composer/send/stop flow and runtime state projection.

#### Implementation checklist

- [ ] Add host helper support for `thread/resume`, `turn/start`, `turn/interrupt`, and event streaming.
- [ ] Add active thread runtime state to `StukayAppState`:
  - active thread id;
  - active turn id;
  - streaming assistant item projection;
  - turn terminal status;
  - reconnect / resumed marker.
- [ ] Replace `ThreadRoute` fake run controls with:
  - composer send;
  - stop/interrupt;
  - running / interrupted / failed / idle status.
- [ ] On thread open:
  - hydrate from `thread/read` or `thread/resume` result;
  - enter live subscription mode via `thread/resume`.
- [ ] On reconnect:
  - re-open stream;
  - rehydrate active thread snapshot;
  - recover `activeTurnId` and terminal state from runtime truth.

#### Acceptance checklist

- [ ] Existing thread can be opened and interacted with from Android.
- [ ] `turn/start` streams assistant output incrementally.
- [ ] `turn/interrupt` stops the active turn and the UI resolves to final interrupted state only after terminal event.
- [ ] Reconnect does not lose the active thread surface or leave `Stop` in a false state.

### Milestone 3: Real approvals

#### Status

- current: `pending`

#### Objective

Close the approval safety layer in the same slice, not as a distant follow-up.

#### Recommended commit split

- Commit A: host helper + Android client approval request/response contracts.
- Commit B: Android pending approval state, UI decisions, stale/cleared handling, audit logging.

#### Implementation checklist

- [ ] Add host helper + Android client support for:
  - `item/commandExecution/requestApproval`
  - `item/fileChange/requestApproval`
  - command approvals with `networkApprovalContext`
  - `serverRequest/resolved`
- [ ] Introduce typed pending approval state keyed by:
  - `requestId`
  - `itemId`
  - `threadId`
  - `turnId`
- [ ] Support decision set:
  - approve once
  - approve for session
  - decline
  - cancel
- [ ] Prefer scalar values from `availableDecisions` when present; ignore policy-amendment variants in this slice.
- [ ] Handle stale/cleared cases when:
  - `serverRequest/resolved` arrives;
  - the owning turn completes/interruption happens;
  - reconnect rehydration shows the item already terminal;
  - active thread changes away from the request owner.
- [ ] Add audit logging for approval requested / answered / cleared / stale.

#### Acceptance checklist

- [ ] Command, file, and network approvals can all be decided from Android.
- [ ] Pending approval is scoped to the correct thread/turn.
- [ ] Cleared or stale approvals disappear deterministically.
- [ ] Logs/diagnostics preserve redacted audit trail without raw secrets or raw full payloads.

### Milestone 4: Accessibility, diagnostics, and proof

#### Status

- current: `pending`

#### Objective

Make the runtime slice testable and reviewable as an engineering surface, not only as pixels.

#### Recommended commit split

- Commit A: semantics/test identifiers + diagnostics enrichment.
- Commit B: tests, emulator proof, physical Pixel proof, final cleanup of fake affordances and docs sync.

#### Implementation checklist

- [ ] Add semantics/test identifiers for all critical runtime controls and states.
- [ ] Enrich diagnostics with:
  - current thread id;
  - active turn id;
  - pending approval summary;
  - stream status;
  - reconnect generation / last recover attempt;
  - last requestId/itemId for approval and turn actions.
- [ ] Add targeted unit tests for:
  - runtime thread->project grouping;
  - active turn state transitions;
  - approval lifecycle reducer/mapper;
  - stale/cleared approval handling;
  - host helper request/response + stream normalization.
- [ ] Add device proof surfaces:
  - emulator flow;
  - physical Pixel flow;
  - `android layout` / semantics-aware inspection.

#### Acceptance checklist

- [ ] Key runtime flows are discoverable via semantics tree.
- [ ] Diagnostics surface can explain what thread/turn/approval is currently blocked or active.
- [ ] Proof covers read path, send, stream, interrupt, reconnect, and approvals.

## Stage Report Template

Заполнять перед финальным checkpoint-коммитом milestone.

```md
### Milestone <N> Report

- milestone: `<name>`
- status: `pending | in_progress | blocked | done | verified`
- branch: `<branch-name>`
- commits:
  - `<sha> <message>`
  - `<sha> <message>`
- scope closed:
  - `...`
- files touched:
  - `...`
- acceptance:
  - `[x] ...`
  - `[ ] ...`
- verification run:
  - `[x] <command> -> <result>`
  - `[ ] <command> -> not run / blocked`
- review loop:
  - subagents launched: `...`
  - findings raised: `...`
  - confirmed hypotheses: `...`
  - rejected hypotheses: `...`
  - re-review result after fixes: `...`
- accessibility proof:
  - `...`
- diagnostics / evidence:
  - `...`
- open issues / debt kept out:
  - `...`
- blocker for next milestone:
  - `none | ...`
```

### Commit policy recommendation

- Да, для этого slice рекомендуется фиксировать изменения по этапам.
- Причина: здесь пересекаются host helper, Android state, UI, approvals и proof surfaces; без checkpoint-коммитов diff быстро станет слишком широким и плохо проверяемым.
- Каждый checkpoint-коммит milestone должен идти только после закрытого pre-commit review loop и заполненного `Stage Report`.
- Review-субагенты живут только внутри одного milestone loop; после коммита закрываются, на следующий milestone создаются новые.
- После полного исполнения плана нужен отдельный branch-wide review loop относительно `main`; только после него ветка считается готовой к финальному merge-readiness review.
- Практическое правило:
  - milestone 1-3: обычно `2` рабочих коммита + `1` checkpoint-коммит максимум;
  - milestone 4: `1-2` коммита, если proof и docs sync действительно отделимы;
  - не дробить на мелкие “шумовые” коммиты без законченной подзадачи.

## Validation

### Static / local gates

- `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- `android describe --project_dir .`
- `codex mcp get jetbrains`
- `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain`
- `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p "test_*.py"`

### New tests expected in this slice

- JVM tests for:
  - thread list -> project grouping;
  - thread/read and thread/resume mapping;
  - turn start / interrupt state transitions;
  - approval request / response mapping;
  - stale approval clearing;
  - reconnect recovery heuristics.
- Host helper Python tests for:
  - typed thread endpoints;
  - typed turn endpoints;
  - approval response path;
  - event stream cleanup and reconnect-safe lifecycle.

### Device proof

- Emulator:
  - open runtime-backed project
  - open existing thread
  - send prompt
  - observe streaming
  - interrupt
  - reconnect recovery
  - resolve at least one command/file/network approval
- Physical Pixel 9 Pro XL:
  - same scenario over the proven Host Bridge path
  - verify semantics via `android layout` and not only by screenshot tapping

## Accessibility Baseline

Accessibility is mandatory for this slice because it is both user-facing and automation-facing.

### Required semantics

- icon-only controls must have `contentDescription`:
  - drawer/back
  - settings
  - diagnostics
  - send
  - stop/interrupt
  - approval actions when represented iconically
- critical controls must have stable identifiers (`testTag` or equivalent):
  - project open
  - thread open
  - composer input
  - send
  - stop
  - approval once/session/decline/cancel
  - reconnect banner / retry action if shown
- state surfaces must expose readable state text:
  - loading
  - streaming
  - waiting for approval
  - interrupted
  - degraded
  - reconnecting
  - failed
- input validation errors must attach to the concrete field:
  - pairing payload (already present path)
  - composer input if empty-submit is blocked

### Suggested naming discipline

- `projects.settings`
- `projects.project_open.<projectId>`
- `project.thread_open.<threadId>`
- `thread.composer.input`
- `thread.turn.send`
- `thread.turn.stop`
- `thread.approval.once.<approvalId>`
- `thread.approval.session.<approvalId>`
- `thread.approval.decline.<approvalId>`
- `thread.approval.cancel.<approvalId>`
- `thread.status.banner`
- `diagnostics.runtime.summary`

### Proof expectation

- device-side smoke must be able to drive the key flow through semantics/layout tree;
- coordinate-only tapping is fallback only.

## Risks / Deferred

### Main risks

- app-server has no server-native project object, so project derivation must stay honest and minimal.
- current helper is summary-only; stream-capable typed proxying is the main implementation risk of the slice.
- final UI must not mix fake and real affordances, otherwise the milestone becomes ambiguous and hard to verify.
- protocol can surface `waitingOnUserInput` even if full `tool/requestUserInput` UX is not in scope.

### Deferred explicitly

- execpolicy/network policy amendment approvals beyond the shared scalar decision set;
- full `tool/requestUserInput` dialog UX unless it becomes unavoidable for the covered flow;
- diff/review/file preview surfaces richer than minimal approval context;
- notifications;
- exported diagnostics persistence;
- public endpoint / tunnel path;
- shared core extraction.

### Open technical decisions

1. Host helper streaming shape:
   - recommended: SSE or chunked event stream over existing HTTP helper;
   - reject: direct Android -> app-server raw transport for this slice.
2. `Projects` derivation policy:
   - recommended: group threads by normalized `cwd`, with explicit “No Project” fallback;
   - still decide how to title threads with blank/unknown cwd.
3. `sourceKinds` set for `thread/list`:
   - recommended: explicitly include at least `cli`, `vscode`, `appServer`;
   - decide whether `subAgent*` threads are visible in the first slice or deferred.
4. `waitingOnUserInput` handling:
   - recommended minimum: diagnostics + blocked-state surfacing;
   - full dialog UX can remain deferred if not required by acceptance flow.
5. Thread runtime owner boundary:
   - recommended: foreground active thread session stays in `StukayAppState`;
   - repositories stay typed gateways and cached read-model providers.

## Evidence Sources

### Repo-local

- `AGENTS.md`
- `README.md`
- `Documentation.md`
- `docs/ROADMAP.md`
- `docs/exec-plans/active/ExecPlan.md`
- `docs/exec-plans/tech-debt-tracker.md`
- `docs/architecture/application-architecture.md`
- `docs/DECISIONS.md`
- `docs/QUALITY.md`
- `docs/observability/logging-and-diagnostics.md`
- `docs/generated/project-interfaces.md`
- `docs/generated/stack-inventory.md`
- `docs/generated/commands.md`
- `docs/generated/test-matrix.md`
- `docs/generated/ui-legibility-surface.md`
- `docs/process/android-agent-workflow.md`

### Official Android docs read

- `kb://android/about/versions/16/summary`
- `kb://android/about/versions/16/behavior-changes-16`
- `kb://android/privacy-and-security/local-network-permission`
- `kb://android/privacy-and-security/local-network-definition`
- `kb://android/develop/connectivity/network-ops/reading-network-state`
- `kb://android/develop/connectivity/network-ops/connecting`
- `kb://android/privacy-and-security/security-config`
- `kb://android/privacy-and-security/risks/cleartext-communications`
- `kb://android/develop/ui/compose/navigation`
- `kb://android/topic/architecture/index`
- `kb://android/develop/ui/compose/state-hoisting`
- `kb://android/develop/ui/compose/designsystems/material3`
- `kb://android/develop/ui/compose/accessibility/semantics`
- `kb://android/develop/ui/compose/testing/semantics`
- `kb://android/develop/ui/compose/layouts/adaptive/use-window-size-classes`
- `kb://android/develop/ui/compose/layouts/adaptive/build-adaptive-navigation`

### Official OpenAI / protocol surfaces read

- `https://developers.openai.com/codex/app-server#api-overview`
- `https://developers.openai.com/codex/app-server#lifecycle-overview`
- `https://developers.openai.com/codex/app-server#threads`
- `https://developers.openai.com/codex/app-server#start-or-resume-a-thread`
- `https://developers.openai.com/codex/app-server#list-threads-with-pagination--filters`
- `https://developers.openai.com/codex/app-server#turns`
- `https://developers.openai.com/codex/app-server#events`
- `https://developers.openai.com/codex/app-server#approvals`

### Reference repos read

- `references/openai-codex/codex-rs/app-server/README.md`
- `references/openai-codex/codex-rs/app-server-protocol/schema/json/v2/*`
- `references/openai-codex/sdk/python/examples/05_existing_thread/sync.py`
- `references/openai-codex/sdk/python/examples/06_thread_lifecycle_and_controls/sync.py`
- `references/openai-codex/sdk/python/examples/06_thread_lifecycle_and_controls/async.py`
- `references/codexdroid/app/src/main/java/me/siddheshkothadi/codexdroid/feature/session/ui/SessionViewModel.kt`
- `references/codexdroid/app/src/main/java/me/siddheshkothadi/codexdroid/feature/session/ui/SessionScreen.kt`
- `references/codexdroid/app/src/main/java/me/siddheshkothadi/codexdroid/feature/session/ui/contract/SessionStateReducer.kt`
- `references/codexdroid/docs/harness/specs/thread_start_resume.md`
- `references/codexdroid/docs/harness/specs/approval_request_response.md`
- `references/relaydex/phodex-bridge/src/codex-transport.js`
- `references/relaydex/phodex-bridge/src/session-state.js`
- `references/relaydex/phodex-bridge/src/bridge.js`
- `references/relaydex/CodexMobile/CodexMobile/Views/SidebarView.swift`
- `references/relaydex/CodexMobile/CodexMobile/Views/Sidebar/SidebarThreadGrouping.swift`
- `references/relaydex/CodexMobile/CodexMobile/Views/Turn/TurnViewModel.swift`
- `references/relaydex/CodexMobile/CodexMobile/Views/Turn/TurnTimelineReducer.swift`
- `references/relaydex/CodexMobile/CodexMobile/Views/Turn/TurnTimelineView.swift`
- `references/relaydex/CodexMobile/CodexMobile/Views/Turn/TurnComposerView.swift`
- `references/PocketDex/docs/ARCHITECTURE.md`
- `references/PocketDex/shared/approval-protocol.js`
- `references/PocketDex/src/socket-server.js`
- `references/openconnect/README.md`
- `references/openconnect/docs/android-release-and-cloudflare.md`
- `references/openconnect/scripts/openconnect_pair_up.sh`
- `references/farfield/README.md`
- `references/codexUI/README.md`
