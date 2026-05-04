# Host Bridge MVP Plan

## Goal

Реализовать первый реальный end-to-end путь `Android -> Host Bridge -> local codex app-server`, заменив `StubHostBridgeRepository` на host-backed transport без ухода в полный `real thread runtime`.

## Progress Tracker

- overall_status: `ready_for_final_branch_review`
- current_milestone: `final_branch_review`
- implementation_started: `yes`
- active_plan_owner: текущий execution thread `codex/host-bridge-mvp`

### Update Rules

- После старта milestone перевести его `status` в `in_progress`.
- После завершения milestone:
  - отметить чекбоксы;
  - заполнить `Stage Report`;
  - обновить `.tmp/.codex/task_state/latest.md`;
  - обновить `.tmp/.codex/task_state/latest.json`;
  - при необходимости синхронизировать `Documentation.md` и `docs/CHANGELOG.md`.
- Если реализация была прервана, продолжение начинается с первого milestone со статусом `in_progress` или первого неполного milestone со статусом `planned`.

## In Scope

- реальный `HostBridgeRepository` вместо `StubHostBridgeRepository`;
- host-side launcher/process manager для локального `codex app-server`;
- живой `connect / reconnect / disconnect / degraded` path;
- первый host-backed transport;
- ровно один обязательный реальный runtime payload path:
  - `host health/status`
  - `app/list` count;
- diagnostics, structured logging и evidence для transport path;
- security gate через `@codex-security`;
- emulator и device proof через `@test-android-apps`.

## Out Of Scope

- полный `real thread runtime`;
- public tunnel / internet endpoint path;
- camera QR scan;
- heavy DI;
- новый top-level feature module;
- большой UI redesign;
- review/diff/notifications/persistence export;
- direct Android -> app-server WebSocket path как production-ориентированный transport.

## Stop Condition

Milestone `Host Bridge MVP` считается закрытым, когда:

1. `Settings` реально подключает Android-клиент к Windows Host Bridge по host-backed transport.
2. Windows Host Bridge реально говорит с локальным `codex app-server`, а не симулирует состояние.
3. `Projects` и `Diagnostics` показывают runtime-backed host status summary плюс `app/list` count, а не stubbed signal.
4. Есть рабочие `reconnect`, `disconnect` и честный `degraded` path с retry/backoff semantics.
5. В логи и diagnostics попадают transport transitions и ошибки без утечки pairing secret / auth token.
6. Есть хотя бы один device proof на эмуляторе и один proof на реальном Pixel.
7. Нет ложного claim, что `thread/start`, `thread/read`, streaming timeline и approvals уже переведены на реальный runtime.

Следующий milestone после этого:

- `Real Thread Runtime`: `thread/list`, `thread/read`, `turn/start`, streaming, interrupt, approvals поверх реального transport.

## Definition Of Done

- `StubHostBridgeRepository` больше не используется в production wiring.
- В runtime graph собран реальный host-backed repository path.
- Первый обязательный runtime payload проходит через Host Bridge в Android shell:
  - `host health/status`
  - `app/list` count
- `Settings`, `Projects` и `Diagnostics` читают единый `HostBridgeConnectionState`.
- MVP transport ограничен `http_json`.
- Security model ограничен bounded cleartext opt-in:
  - Android получает явный cleartext opt-in;
  - strict boundedness обеспечивается прежде всего runtime-валидацией endpoint policy;
  - `network_security_config` здесь не считается механизмом, который сам по себе выражает CIDR allowlist.
- Добавлены целевые tests для transport mapping и connection transitions.
- Пройден `@codex-security` diff-scoped scan по новому transport slice.
- Пройден `@test-android-apps` QA flow для `connect -> degraded -> reconnect -> disconnect`.

## Current Repo State

- Contract slice уже закрыт.
- Pairing/local-network UX уже живет в `feature:settings`.
- `Projects` и `Diagnostics` уже показывают runtime-backed host summary из real `HostBridgeConnectionState`.
- `RuntimeProjectsRepository` и `RuntimeThreadRepository` остаются adapter seams, но сейчас делегируют в fake repositories.
- `feature:thread` все еще fake-only по action contract:
  - `startFakeTurn`
  - `completeFakeTurn`
  - `resolveApproval`
- `StukayAppState` остается главным owner-ом app-level state и runtime wiring seam.
- Current pairing parser принимает только `http_json` success path и требует base host endpoint без route path.
- Current host policy helper в коде уже знает:
  - RFC1918
  - `.local`
  - `100.64/10`
  - `169.254/16` по-прежнему reject path
- Current host bridge state machine уже real host-backed, но full real thread runtime по-прежнему не входит в этот milestone.
- Current Android 16 permission posture уже не hard-gate-ит admissible private/local endpoint:
  - `Nearby devices` остается manual/opt-in advisory для current API 36;
  - actual boundary для connect path — endpoint policy + runtime transport result.
- Current Android client явно запрещает helper redirects как out-of-scope boundary escape.
- Current `StukayAppState` держит отдельный probe barrier, чтобы queued auto-probe не оживлял manual disconnect cycle.
- `AndroidManifest.xml` уже содержит:
  - `INTERNET`
  - `ACCESS_NETWORK_STATE`
  - `NEARBY_WIFI_DEVICES`

## Integration Points In Code

### Core runtime seam

- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeRepository.kt`
  - текущий real `HttpJsonHostBridgeRepository` plus host-state contract;
  - здесь живут `Degraded`, retry/backoff и runtime snapshot mapping;
  - следующий milestone трогает его уже не ради transport prove-out, а ради full thread runtime data path.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayRuntimeGraph.kt`
  - current runtime graph уже wired на real Host Bridge client и host-backed repository;
  - следующий шаг здесь — не новый transport seam, а расширение runtime-backed data readers поверх уже доказанного Host Bridge path.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - SSOT для `hostBridgeState`, connect actions и logs;
  - здесь нельзя смешивать transport DTO и UI directly.

### Transport and pairing seam

- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgePairingParser.kt`
  - current parser уже locked на `http_json` success path;
  - pairing payload теперь должен оставаться strict enough, чтобы не плодить ambiguous transport branches и endpoint-path drift.
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgePairingStore.kt`
  - storage seam для pairing payload;
  - не использовать как runtime API.
- `core/model/src/main/java/dev/vitalcc/stukay/core/model/HostBridgeModels.kt`
  - current model уже несет:
    - `Degraded`
    - `retryAttempt`
    - `lastRoundTripMs`
    - `lastProbeAtEpochMs`
    - `runtimeSummary`
    - `appListCount`
    - `HostRuntimeSnapshotScope`

### UI readers

- `app/src/main/kotlin/dev/vitalcc/stukay/StukayApp.kt`
  - root callbacks and route wiring stay unchanged in shape.
- `feature/settings/src/main/java/dev/vitalcc/stukay/feature/settings/ui/SettingsRoute.kt`
  - control plane for pairing and connect/reconnect/disconnect.
- `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectsRoute.kt`
  - first lightweight real host signal.
- `feature/diagnostics/src/main/java/dev/vitalcc/stukay/feature/diagnostics/ui/DiagnosticsRoute.kt`
  - detailed runtime snapshot, transitions, retry/degraded reason.

### Existing adapters that must not fork into parallel path

- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/RuntimeProjectsRepository.kt`
- `app/src/main/kotlin/dev/vitalcc/stukay/runtime/RuntimeThreadRepository.kt`

Правило:

- не строить новый parallel fake/runtime stack рядом с ними;
- использовать их как existing seam до следующего milestone.

### Build/config surface

- `app/src/main/AndroidManifest.xml`
  - network security config и permission surface.
- `app/build.gradle.kts`
  - HTTP client dependency surface при необходимости.
- `gradle/libs.versions.toml`
  - centralized dependency version pinning.

### Host-side implementation

- `tools/hostbridge/`
  - Windows-native helper/service, который уже держит локальный `codex app-server --listen stdio://`;
  - этот helper не является Android runtime API truth surface, а только host bridge process;
  - следующий milestone поверх него — real thread/runtime methods, а не заново helper scaffolding.

## Official Facts To Honor

### Android

- Android 16 local-network protection сейчас opt-in only.
- Android 17 / targetSdk 37+ делает local-network protection mandatory.
- Ограничения local-network path применяются ко всем networking APIs, включая OkHttp/Cronet.
- `.local` resolution подпадает под local-network protection.
- `ConnectivityManager` callback-based tracking нужен для live connectivity UX; snapshot alone недостаточен.
- Cleartext traffic disabled by default since Android 9 / API 28; если MVP идет по `http://`, нужен explicit opt-in через network security config.
- `INTERNET` и `ACCESS_NETWORK_STATE` не требуют runtime request.
- `100.64.0.0/10` входит в Android local network definition и может считаться local/private path.
- `169.254.0.0/16` тоже относится к local network definition, но не должен автоматически входить в MVP allowlist без реальной необходимости.
- Navigation Compose best practice:
  - передавать в routes minimal IDs и callbacks;
  - не тащить `NavController` и complex objects в feature composables.
- State hoisting best practice:
  - owner должен быть lowest common ancestor;
  - в текущем repo это уже `StukayAppState` за `AndroidViewModel`.

### OpenAI / Codex

- `codex app-server` поддерживает bidirectional JSON-RPC 2.0.
- Stable local transport для host launcher path: `stdio://`.
- WebSocket app-server transport остается `experimental / unsupported`.
- `thread/start`, `thread/resume`, `thread/read`, `thread/list`, `turn/start`, `turn/interrupt`, `app/list` и approvals существуют как canonical API surface.
- Experimental methods/fields требуют `initialize.capabilities.experimentalApi = true`.
- Approvals несут `threadId` и `turnId`; network approvals приходят через `networkApprovalContext` и не должны рендериться как generic command approval.

## Closed Technical Decisions

### D1. Android -> Host Bridge transport

- status: `closed`
- decision: `Host Bridge MVP` использует только `http_json`.
- why:
  - меньше риск, чем direct Android WebSocket;
  - проще health/probe/retry;
  - не опирается на experimental app-server websocket.
- implementation_note:
  - `ws` / `wss` остаются только explicit fast-fail unsupported case в pairing parser/repository path;
  - они не должны рендериться как почти поддерживаемый режим.

### D2. Cleartext vs TLS

- status: `closed`
- decision:
  - для MVP допустим bounded cleartext opt-in по `http://`;
  - без глобального `usesCleartextTraffic=true`;
  - через явный Android cleartext opt-in;
  - strict allowlist enforcement делается runtime-логикой репозитория/client path;
  - без public tunnel path.
- why:
  - это personal-first private-host MVP;
  - текущий ключевой риск в отсутствии real transport path, а не в недоведенном TLS hardening.
- clarification:
  - `network_security_config` нужен как явный cleartext opt-in surface;
  - он не должен описываться как точный CIDR allowlist-механизм для `RFC1918` / `100.64/10`.
- follow_up:
  - TLS / HTTPS / public path hardening остается отдельным milestone;
  - если в ходе реализации HTTPS на host side почти бесплатен по сложности, это можно отдельно переоценить, но стартовый contract не меняется.

### D3. First real data path

- status: `closed`
- decision:
  - первый обязательный runtime payload = `host health/status + app/list count`.
- why:
  - это уже real app-server round-trip;
  - риск ниже, чем `thread/list` / `thread/read`;
  - позволяет показать живой runtime-backed status в shell.
- rule:
  - `thread/loaded/list` не входит в acceptance этого MVP;
  - если нужен второй probe, он может быть только diagnostics-only surface.

### D4. Local host policy

- status: `closed`
- decision:
  - поддерживаемые адреса для MVP:
    - `10.0.0.0/8`
    - `172.16.0.0/12`
    - `192.168.0.0/16`
    - `100.64.0.0/10`
    - `.local`
  - явно не поддерживать:
    - public tunnel / internet endpoint path
    - автоматическое включение `169.254.0.0/16` без доказанной необходимости
- why:
  - `100.64/10` полезен для private overlay scenarios и остается в private-host intent;
  - `169.254/16` пока расширяет поверхность без подтвержденной пользы.

## Remaining Implementation Choices

- host-side helper exact package/layout inside `tools/hostbridge/`;
- Android HTTP client choice;
- diagnostics-only secondary probe, если он действительно нужен;
- launcher/process supervisor details on Windows host.

## Reference Findings

### `references/openai-codex`

- protocol truth surface;
- использовать как источник формы методов и approval flows;
- не использовать reference repo как замену official docs.

Key takeaways:

- `app/list` годится как первый low-risk runtime payload.
- `thread/read` useful as fallback/poll surface, но это уже следующий milestone.
- approvals и `networkApprovalContext` надо уважать как отдельный contract.

### `references/relaydex`

- bridge и transport должны быть отдельными слоями;
- local Codex runtime стоит держать warm across reconnects;
- pairing bootstrap и trusted reconnect - разные режимы.

### `references/codexdroid`

- connection state и thread/session state должны быть разведены;
- reducer/state-holder shape помогает не смешивать transport/UI;
- `thread/read` можно использовать как fallback when live events drop.

### `references/openconnect`

- local/private path и public/tunnel path надо держать раздельно;
- pairing bootstrap не должен автоматически тянуть remote endpoint policy.

### `references/PocketDex`

- proxy/bridge layer должен владеть Codex transport и approval normalization;
- stdio-first path предпочтительнее debug WebSocket path.

### `.codex` host-side context

- `host runtime context`:
  - `C:\Users\v.vlasov\.codex\config.toml`
  - `codex --version`
  - `codex app-server --help`
- `diagnostics context`:
  - `C:\Users\v.vlasov\.codex\sessions\`
  - `C:\Users\v.vlasov\.codex\memories\`
- `not for direct client coupling`:
  - `.codex\sessions`
  - `.codex\memories`
  - plugin caches

## Target Runtime Shape

### Android side

- `RealHostBridgeRepository`
- `HostBridgeClient`
- `AndroidNetworkMonitor`
- `HostBridgeConnectionState` with:
  - `NotPaired`
  - `Paired`
  - `Connecting`
  - `Connected`
  - `Degraded`
  - `Disconnected`
  - `Failed`

### Host side

- Windows-native helper in `tools/hostbridge/`
- helper responsibilities:
  - spawn or reuse local `codex app-server --listen stdio://`
  - do `initialize`
  - call `app/list`
  - map health/probe/status into narrow Android-facing JSON
  - own retry/restart policy for app-server child process

### Auth contract

- Android -> Host Bridge helper:
  - `Authorization: Bearer <sessionToken>`
- token source:
  - pairing payload `sessionToken`
- helper behavior:
  - rejects unauthorized requests before any call to local `codex app-server`
  - does not forward raw bearer token into diagnostics payloads
  - does not log raw bearer token
  - maps auth failure to explicit unauthorized/error status for Android repository
- scope:
  - auth contract applies to `GET /v1/runtime/summary`
  - diagnostics-only helper routes stay auth-protected in MVP

### First runtime snapshot to expose into UI

- `runtimeReady`
- `hostStatus`
- `availableAppsCount`
- `lastRoundTripMs`
- `lastProbeAtEpochMs`
- `retryAttempt`
- `degradedReason`
- `lastTransportError`

Rule:

- `Projects` and `Diagnostics` read this summary;
- `Thread` stays fake-only until next milestone.

## Milestones

### M1. Implement locked transport and state contract

- status: `completed`
- owner_surface:
  - `HostBridgeModels.kt`
  - `HostBridgePairingParser.kt`
  - `HostBridgeRepository.kt`
  - `AndroidManifest.xml`
  - `app/build.gradle.kts`

- [x] Зафиксировать `http_json` как единственный рабочий success path в code/docs.
- [x] Перевести `ws` / `wss` в explicit fast-fail unsupported case.
- [x] Зафиксировать bounded cleartext opt-in в code/docs:
  - Android cleartext opt-in explicit
  - runtime endpoint validation is the real allowlist boundary
- [x] Добавить `Degraded` и retry/probe metadata в model contract.
- [x] Описать exact pairing payload fields и address policy:
  - RFC1918
  - `.local`
  - `100.64/10`
- [x] Зафиксировать reject policy для:
  - public endpoint
  - tunnel endpoint
  - automatic `169.254/16`

#### Stage Report

- summary: Stage 1 завершен. Kotlin Host Bridge contract очищен от `ws` success path, добавлен `Degraded` phase и `HostRuntimeSummary`, parser переведен на `http_json`, allowlist расширен до `100.64/10`, а `169.254/16` остался reject path.
- evidence:
  - `:core:model:testDebugUnitTest`
  - `:app:testDebugUnitTest`
  - `:app:assembleDebug`
  - JetBrains file rebuild for touched files
- commands_run:
  - `mcp__jetbrains__.build_project(filesToRebuild=...)`
  - `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest --console=plain`
  - `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain`
- files_changed:
  - `core/model/src/main/java/dev/vitalcc/stukay/core/model/HostBridgeModels.kt`
  - `core/model/src/test/java/dev/vitalcc/stukay/core/model/HostBridgeModelsTest.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgePairingParser.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeRepository.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/xml/network_security_config.xml`
  - `feature/settings/src/main/java/dev/vitalcc/stukay/feature/settings/ui/SettingsRoute.kt`
  - `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectsRoute.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgePairingParserTest.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/StubHostBridgeRepositoryTest.kt`
  - `Documentation.md`
  - `docs/exec-plans/active/host-bridge-mvp-plan.md`
  - `.tmp/.codex/task_state/latest.md`
  - `.tmp/.codex/task_state/latest.json`
  - `docs/CHANGELOG.md`
- residual_risks:
  - helper auth and real runtime summary still not implemented
  - current repository remains stubbed and cannot yet prove real transport truth
- next_milestone: `M2`

### M2. Add Windows Host Bridge helper

- status: `completed`
- owner_surface:
  - `tools/hostbridge/`
  - host-side tests

- [x] Добавить Windows-native helper/service.
- [x] Реализовать lifecycle `spawn / probe / restart / shutdown` для локального `codex app-server`.
- [x] Реализовать `initialize` handshake.
- [x] Реализовать первый real request path к `app/list`.
- [x] Реализовать host health/status snapshot.
- [x] Зафиксировать auth contract:
  - `Authorization: Bearer <sessionToken>`
  - reject unauthorized before any local `codex app-server` access
  - never log raw token
- [x] Ограничить наружу narrow JSON API без проброса raw protocol payloads.

#### Stage Report

- summary: Stage 2 завершен локально. Добавлен stdlib-only Windows Host Bridge helper с bearer auth, общим runtime client к `codex app-server` по `stdio://`, narrow `/v1/runtime/summary` API, degraded caching и auth-first reject path.
- evidence:
  - `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
  - `python -m compileall tools/hostbridge`
- commands_run:
  - `python -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
  - `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
  - `python -m compileall tools/hostbridge`
- files_changed:
  - `tools/__init__.py`
  - `tools/hostbridge/__init__.py`
  - `tools/hostbridge/auth.py`
  - `tools/hostbridge/models.py`
  - `tools/hostbridge/runtime_client.py`
  - `tools/hostbridge/server.py`
  - `tools/hostbridge/tests/test_auth.py`
  - `tools/hostbridge/tests/test_runtime_client.py`
  - `tools/hostbridge/tests/test_server.py`
  - `docs/exec-plans/active/host-bridge-mvp-plan.md`
  - `Documentation.md`
  - `docs/CHANGELOG.md`
  - `.tmp/.codex/task_state/latest.md`
  - `.tmp/.codex/task_state/latest.json`
- residual_risks:
  - helper пока не интегрирован с Android repository path
  - JSON summary contract еще не отображается в Compose shell
  - request path пока ограничен `app/list` and health summary only
- next_milestone: `M3`

### M3. Build Android-side real client and repository

- status: `completed`
- owner_surface:
  - `app/runtime/hostbridge/*`
  - `StukayRuntimeGraph.kt`
  - `StukayAppState.kt`

- [x] Добавить Android-side client к Host Bridge.
- [x] Добавить timeout handling.
- [x] Добавить retry/backoff mapping.
- [x] Добавить `ConnectivityManager`-backed connectivity monitor.
- [x] Перевести `HostBridgeRepository` с stub на real transport path.
- [x] Оставить single source of truth в `StukayAppState`.

#### Stage Report

- summary: Stage 3 завершен. Android-side `OkHttpHostBridgeClient` подключен к helper `GET /v1/runtime/summary`, stub repository заменен на `HttpJsonHostBridgeRepository`, runtime graph переведен на real host-backed transport, а `StukayAppState` получил background executor, periodic probe loop, network-change triggered immediate probe и lifecycle teardown через `dispose()` / `ViewModel.onCleared()`.
- evidence:
  - `mcp__jetbrains__.build_project(filesToRebuild=...)`
  - `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HostBridgeClientTest" --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest" --console=plain`
  - `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest.refreshPermissionStateDoesNotMaskUnauthorizedFailure" --console=plain`
  - `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`
- commands_run:
  - `mcp__jetbrains__.build_project(filesToRebuild=...)`
  - `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HostBridgeClientTest" --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest" --console=plain`
  - `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest.refreshPermissionStateDoesNotMaskUnauthorizedFailure" --console=plain`
  - `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`
- files_changed:
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `app/src/main/kotlin/dev/vitalcc/stukay/StukayAppViewModel.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayRuntimeGraph.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/AndroidNetworkMonitor.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeClient.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeRepository.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeClientTest.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HttpJsonHostBridgeRepositoryTest.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/StubHostBridgeRepositoryTest.kt`
- residual_risks:
  - shell routes еще не показывают весь runtime summary contract и probe telemetry
  - `Projects` и `Diagnostics` still need deliberate Stage 4 wording/data-surface pass
  - security scan и device proofs еще не пройдены
- next_milestone: `M4`

### M4. Surface real runtime summary into shell

- status: `completed`
- owner_surface:
  - `SettingsRoute.kt`
  - `ProjectsRoute.kt`
  - `DiagnosticsRoute.kt`
  - `StukayApp.kt`

- [x] `Settings` показывает реальный connect/reconnect/degraded status.
- [x] `Projects` показывает runtime-backed host summary и `app/list` count.
- [x] `Diagnostics` показывает transport transitions, retry state и last error.
- [x] Не затрагивать current fake thread action contract beyond honest labeling.

#### Stage Report

- summary: Stage 4 завершен. Shell surface теперь честно читает `HostBridgeConnectionState`: `Settings` и `Projects` показывают только summary-level runtime signal, `Diagnostics` держит полную telemetry detail, а freshness contract `HostRuntimeSnapshotScope` различает `live` и `last_known` snapshot без ложных claims про full thread runtime.
- evidence:
  - `mcp__jetbrains__.build_project(filesToRebuild=...)`
  - `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain`
- commands_run:
  - `mcp__jetbrains__.build_project(filesToRebuild=...)`
  - `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`
  - `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain`
- files_changed:
  - `core/model/src/main/java/dev/vitalcc/stukay/core/model/HostBridgeModels.kt`
  - `core/model/src/test/java/dev/vitalcc/stukay/core/model/HostBridgeModelsTest.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - `feature/settings/src/main/java/dev/vitalcc/stukay/feature/settings/ui/SettingsRoute.kt`
  - `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectsRoute.kt`
  - `feature/diagnostics/src/main/java/dev/vitalcc/stukay/feature/diagnostics/ui/DiagnosticsRoute.kt`
- residual_risks:
  - security scan и Android QA/device proof еще не пройдены
  - thread runtime по-прежнему fake-only и сознательно не входит в acceptance Stage 4
- next_milestone: `M5`

### M5. Verification and proof gates

- status: `completed`
- owner_surface:
  - tests
  - docs
  - checkpoint

- [x] Пройти unit/build/lint gates.
- [x] Пройти `@codex-security` scan по новому transport slice.
- [x] Пройти `@test-android-apps` emulator QA flow.
- [x] Пройти physical Pixel proof.
- [x] Обновить `Documentation.md`, `docs/CHANGELOG.md`, `.tmp/.codex/task_state/latest.*`.

#### Stage Report

- summary: Stage 5 завершен. Repo-local gates зелёные, host helper runtime path дополнительно усилен по итогам live proof (`_sanitize_runtime_error` больше не валит HTTP handler, Windows spawn резолвит runnable `codex.cmd/.exe`), diff-scoped security review по transport slice не дал подтвержденных security findings, а acceptance flow `connect -> degraded -> reconnect -> disconnect` подтвержден и на физическом Pixel по USB tether LAN, и на эмуляторе `medium_phone` через `10.0.2.2`.
- evidence:
  - `mcp__jetbrains__.build_project(filesToRebuild=...)`
  - `.\gradlew.bat :app:lintDebug --console=plain`
  - `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain`
  - `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
  - local helper proof `http://127.0.0.1:8421/v1/runtime/summary`
  - sanitized acceptance proof in `docs/exec-plans/active/host-bridge-mvp-proof.md`
- commands_run:
  - `android describe --project_dir .`
  - `adb devices`
  - `adb -s <physical-device> install -r app\build\outputs\apk\debug\app-debug.apk`
  - `adb -e install -r app\build\outputs\apk\debug\app-debug.apk`
  - `android emulator create --profile medium_phone`
  - `android emulator start medium_phone --cold`
  - `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
  - `.\gradlew.bat :app:lintDebug --console=plain`
  - `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain`
- files_changed:
  - `tools/hostbridge/runtime_client.py`
  - `tools/hostbridge/server.py`
  - `tools/hostbridge/tests/test_runtime_client.py`
  - `tools/hostbridge/tests/test_server.py`
  - `docs/exec-plans/active/host-bridge-mvp-proof.md`
  - `docs/generated/project-interfaces.md`
  - `docs/notion/PROJECT_SYNC.md`
  - `Documentation.md`
  - `docs/exec-plans/active/host-bridge-mvp-plan.md`
  - `docs/CHANGELOG.md`
  - `.tmp/.codex/task_state/latest.md`
  - `.tmp/.codex/task_state/latest.json`
- residual_risks:
  - security model по-прежнему bounded cleartext MVP, TLS/public path остается следующим milestone
  - `feature:thread` все еще fake-only и сознательно не входит в acceptance этого milestone
- next_milestone: `M6`

### M6. External review fix pass

- status: `completed_locally`
- owner_surface:
  - android runtime client
  - app state lifecycle
  - helper adapter
  - docs

- [x] Проверить внешние review findings как гипотезы, а не как истину.
- [x] Подтвердить или опровергнуть каждый пункт по коду и primary sources.
- [x] Добавить red-тесты для подтвержденных классов дефектов.
- [x] Закрыть корневые причины без symptom-only fixes.
- [x] Обновить docs и checkpoint под фактическое состояние после fix pass.

#### Stage Report

- summary: Post-proof external review pass и follow-up internal review loop подтвердили семь реальных классов дефектов и закрыли их в общем слое, а не локальными заплатками. `Nearby devices` больше не является unconditional blocker для admissible private/local endpoint на current API 36; `OkHttpHostBridgeClient` теперь явно запрещает redirects и не делегирует boundary library defaults; `StukayAppState` получил thread-safe `HostBridgeProbeBarrier`, который отсекает queued probe resurrection после manual disconnect; auth/protocol failures больше не показывают stale runtime metrics как `live`; remote-controlled diagnostic strings санитизируются до попадания в state/log/UI; helper bind host ограничен loopback/private-only surface; helper `CodexRuntimeClient` дочитывает paginated `app/list` до полного inventory count.
- evidence:
  - `mcp__jetbrains__.build_project(filesToRebuild=...)`
  - `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest" --tests "dev.vitalcc.stukay.runtime.hostbridge.HostBridgeClientTest" --tests "dev.vitalcc.stukay.runtime.HostBridgeProbeBarrierTest" --console=plain`
  - `python -W error::ResourceWarning -m unittest tools.hostbridge.tests.test_runtime_client tools.hostbridge.tests.test_server`
  - Android Developers `Local network permission` guidance
  - OkHttp `followRedirects` reference
  - official `codex app-server` `app/list` pagination docs
- files_changed:
  - `core/model/src/main/java/dev/vitalcc/stukay/core/model/HostBridgeModels.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/HostBridgeProbeBarrier.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeClient.kt`
  - `app/src/main/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeRepository.kt`
  - `tools/hostbridge/server.py`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeClientTest.kt`
  - `feature/settings/src/main/java/dev/vitalcc/stukay/feature/settings/ui/SettingsRoute.kt`
  - `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectsRoute.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/HostBridgeProbeBarrierTest.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HostBridgeClientTest.kt`
  - `app/src/test/kotlin/dev/vitalcc/stukay/runtime/hostbridge/HttpJsonHostBridgeRepositoryTest.kt`
  - `tools/hostbridge/runtime_client.py`
  - `tools/hostbridge/tests/test_runtime_client.py`
  - `Documentation.md`
  - `docs/CHANGELOG.md`
  - `.tmp/.codex/task_state/latest.md`
  - `.tmp/.codex/task_state/latest.json`
- residual_risks:
  - финальный branch-wide review vs `main` еще не перепрогнан после этого fix pass
  - TLS/public path по-прежнему сознательно не входит в scope этого milestone
- next_milestone: `Final branch-wide review vs main`

## Validation

### Required repo-local gates

- `.\gradlew.bat :app:testDebugUnitTest --console=plain`
- `.\gradlew.bat :app:assembleDebug --console=plain`
- `.\gradlew.bat :app:lintDebug --console=plain`
- `android describe --project_dir .`

### Security gate

Skill to use:

- `@codex-security`

Required scope:

- diff-scoped transport slice after implementation.

Required focus:

- cleartext LAN exposure;
- mismatch between documented cleartext boundary and actual runtime enforcement;
- auth header / token leakage;
- pairing secret leakage in logs;
- host launcher command injection;
- non-loopback listener exposure;
- overly broad cleartext policy;
- raw protocol payload leakage in diagnostics.

### Android QA gate

Skill to use:

- `@test-android-apps`

Required proofs:

- emulator proof:
  - pair
  - connect
  - `host health/status + app/list count` visible
  - host bridge stop
  - degraded state visible
  - reconnect after host restore
  - disconnect
- physical Pixel proof:
  - same flow on real device
  - screenshot/UI-tree evidence
  - logcat capture for transport errors/reconnect transitions

## Risks / Deferred

- Full real thread runtime remains next milestone.
- Direct app-server websocket client from Android remains deferred.
- Public tunnel / Cloudflare / internet transport remains deferred.
- Real approvals UI over app-server remains deferred after MVP.
- TLS/WSS hardening remains deferred unless implementation reveals a near-zero-cost HTTPS path on the host side.

## Evidence Sources

### Repo-local

- `AGENTS.md`
- `README.md`
- `Documentation.md`
- `docs/ROADMAP.md`
- `docs/exec-plans/active/ExecPlan.md`
- `docs/exec-plans/tech-debt-tracker.md`
- `docs/architecture/application-architecture.md`
- `docs/generated/project-interfaces.md`
- `docs/generated/stack-inventory.md`
- `docs/generated/commands.md`
- `docs/QUALITY.md`
- `docs/observability/logging-and-diagnostics.md`

### Official Android

- Android 16 summary
- Android 16 behavior changes
- Local network permission
- Local network definition
- Network security configuration
- Cleartext communications risks
- Reading network state
- Connect to the network
- Navigation Compose
- Guide to app architecture
- State hoisting
- Use window size classes
- Build adaptive navigation
- Material 3 in Compose

### Official OpenAI

- Codex app-server
- Codex CLI reference for `codex app-server`
- Codex remote app-server flow
- Docs MCP

### Reference repos

- `references/openai-codex`
- `references/relaydex`
- `references/codexdroid`
- `references/openconnect`
- `references/PocketDex`
