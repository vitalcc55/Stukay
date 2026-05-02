# ExecPlan

## Goal

Реализовать первый Android-side runtime slice для `Host Bridge contract + pairing + local network flow`, не уезжая в полный real thread runtime.

## Constraints

- не уезжать в полный real `thread/start` / `turn/start` / streaming runtime;
- не добавлять heavy DI, shared core extraction или новый `:feature:connection` module;
- не смешивать Android 16 opt-in local-network facts с Android 17 mandatory `ACCESS_LOCAL_NETWORK` path;
- не притворяться, что experimental `codex app-server` WebSocket transport уже production-safe для non-loopback exposure;
- сохранить текущий top-level route graph и existing width-constrained shell.

## In Scope

- typed contract между Android app и Windows Host Bridge;
- pairing flow для одного локального host через pairing payload;
- local network flow, permission rationale и connection UX вокруг него;
- runtime-aware seam, который убирает прямое создание `FakeProjectsRepository` / `FakeThreadRepository` из `StukayAppState`;
- host/connection diagnostics и structured logging для pairing / connect / reconnect / permission paths.

## Out Of Scope

- полный `thread/start` / `turn/start` / streaming runtime;
- review/diff surfaces;
- notifications;
- persistence/export для diagnostics;
- heavy DI framework;
- shared core extraction;
- новый Gradle module `:feature:connection` только ради этого milestone.

## Stop Condition

Slice считается завершенным, когда выполнены все условия ниже:

- в коде есть typed Android-side `Host Bridge` contract и runtime package для pairing / connection state;
- `StukayAppState` больше не создает fake repositories напрямую, а получает их через runtime-aware graph/container;
- `Settings` предоставляет рабочий pairing/connect/reconnect/disconnect flow для pairing payload input и local-network rationale;
- `Diagnostics` показывает host/connection state и recent host-bridge events;
- `Projects` shell получает хотя бы минимальный connection-aware status signal;
- граница между `connection state` и `thread state` отделена в моделях и app state;
- следующий milestone после этого slice явно остается `Host Bridge MVP + real app-server-backed thread runtime`, а не маскируется как уже сделанный.

## Current Repo State

- `MainActivity` и `StukayAppViewModel` уже держат app-level state через activity-scoped `ViewModel`, так что app shell готов к долгоживущему runtime state.
- `StukayAppState` вручную собирает logger, diagnostics store, fake repositories и use cases в одном месте.
- `Projects -> Project -> Thread -> Settings -> Diagnostics` уже существуют как navigation shell.
- `ProjectsRepository` и `ThreadRepository` уже являются interface seams, но текущий wiring прибит к fake implementations.
- `ThreadRepository` содержит fake-specific commands (`startFakeTurn`, `completeFakeTurn`, `resolveApproval`), поэтому прямой swap на real runtime пока невозможен без промежуточного adapter/container layer.
- `Diagnostics` уже показывает route/session/log snapshot, но не знает ничего про host, pairing, network readiness или reconnect state.
- `AndroidManifest.xml` пока не содержит network permissions.

## Integration Points In Code

- `app/src/main/java/dev/vitalcc/stukay/runtime/StukayAppState.kt`
  - текущая точка ручного wiring;
  - здесь будет главный runtime seam и connection-aware state owner.
- `app/src/main/java/dev/vitalcc/stukay/StukayApp.kt`
  - текущий `NavHost`;
  - сюда встраиваются новые callbacks/state для pairing/local-network UX без раздувания route model.
- `app/src/main/java/dev/vitalcc/stukay/navigation/StukayDestination.kt`
  - текущий route contract;
  - для slice сохраняем текущую top-level graph shape.
- `feature/settings/src/main/java/dev/vitalcc/stukay/feature/settings/ui/SettingsRoute.kt`
  - основной entry point для pairing/local-network flow.
- `feature/diagnostics/src/main/java/dev/vitalcc/stukay/feature/diagnostics/ui/DiagnosticsRoute.kt`
  - основной entry point для host/connection diagnostics.
- `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectsRoute.kt`
  - место для lightweight host status signal, без ввода нового top-level destination.
- `feature/projects/.../ProjectsRepository.kt` и `feature/thread/.../ThreadRepository.kt`
  - seam для runtime-aware repository layer.
- `core/model/src/main/java/dev/vitalcc/stukay/core/model`
  - место для typed host/pairing/connection models.
- `core/logging/src/main/java/dev/vitalcc/stukay/core/logging`
  - `LogArea.Connection`, `LogArea.HostBridge`, `LogArea.Security` уже есть и должны начать использоваться.
- `app/src/main/AndroidManifest.xml`
  - permission surface для local network readiness.

## Official Facts To Honor

- Android 16 local-network behavior сейчас переходный:
  - opt-in testing path использует `RESTRICT_LOCAL_NETWORK` compat flag;
  - для targetSdk 36 восстановление доступа в opt-in path завязано на `NEARBY_WIFI_DEVICES`;
  - mandatory blocked-by-default path с `ACCESS_LOCAL_NETWORK` начинается с Android 17 / targetSdk 37+.
- `Navigation Compose` рекомендует передавать в destination composables callbacks и минимальные ID arguments, а не `NavController` или complex objects.
- `State hoisting` для connection/pairing state должен оставаться в lowest common ancestor; для этого repo таким owner уже является app-level state holder за `ViewModel`.
- `Window size classes` и `NavigationSuiteScaffold` полезны для adaptive top-level navigation, но этот slice не требует немедленного перевода существующего width-constrained shell на adaptive navigation suite.
- Android 16 уже принудительно требует edge-to-edge и predictive-back compatibility для targetSdk 36.
- `codex app-server` WebSocket transport официально существует, но помечен как experimental/unsupported; non-loopback exposure без auth нельзя считать production-safe.
- `codex app-server` approval protocol уже различает обычный command approval и network approval через `networkApprovalContext`; Android UI должен сохранить эту границу и не сводить всё к одной generic approval карточке.

## Open Questions Before Implementation

Неблокирующих вопросов осталось немного; консервативные решения для этого slice такие:

- Pairing entry:
  - в этом slice делаем `pairing payload paste/import` как обязательный path;
  - camera QR scanning и отдельный deep-link scanner UI откладываются.
- New feature context:
  - отдельный `:feature:connection` сейчас не вводим;
  - pairing/local-network UX живет в `feature:settings`, а runtime logic живет в `app/runtime`.
- Real transport:
  - этот slice не притворяется `Host Bridge MVP`;
  - Android-клиент получает typed contract + stub/runtime-aware transport seam, а реальный host-backed transport остается следующим milestone.

## Proposed Target Shape

### Modules

- не добавлять новый Gradle module на этом этапе;
- расширить:
  - `core:model` новыми host/pairing/connection models;
  - `app/runtime` runtime-aware graph и host bridge state/repository;
  - `feature:settings` pairing/local-network UI;
  - `feature:diagnostics` host diagnostics;
  - `feature:projects` lightweight host status banner/signal.

### Data / Domain / UI Seams

- `app/runtime/hostbridge/*`
  - `PairingPayload` parsing/validation;
  - `HostBridgeConnectionState`;
  - `HostBridgeRepository`;
  - stub/in-memory implementation для этого milestone.
- `app/runtime/*`
  - runtime graph/container, который строит repositories и connection state вместо прямого `Fake*Repository()`.
- `feature:projects` / `feature:thread`
  - existing interfaces сохраняются;
  - runtime-aware wrappers/adapters делегируют в fake layer там, где real runtime еще out of scope.

### Route / Screen Changes

- top-level route graph сохраняется:
  - `Projects`
  - `Project`
  - `Thread`
  - `Settings`
  - `Diagnostics`
- pairing/local-network UX появляется в `Settings`, а не как новый top-level destination;
- `Projects` получает connection summary;
- `Diagnostics` получает отдельный host/connection block;
- `predictive back` behavior сохраняется существующим:
  - `Diagnostics -> Settings`
  - `Settings -> previous`
  - `Thread -> Project`
  - `Project -> Projects`

### Logging / Diagnostics Additions

- добавить structured events:
  - `pairing_payload_updated`
  - `pairing_saved`
  - `pairing_cleared`
  - `local_network_permission_requested`
  - `local_network_permission_result`
  - `host_bridge_connect_started`
  - `host_bridge_connect_succeeded`
  - `host_bridge_connect_failed`
  - `host_bridge_reconnect_scheduled`
  - `host_bridge_disconnected`
- расширить diagnostics surface данными:
  - pairing presence
  - host label / endpoint
  - connection phase
  - last connection error
  - permission readiness summary

## Milestones

### 1. Add typed host bridge models and runtime graph

Files:

- create `core/model/src/main/java/dev/vitalcc/stukay/core/model/HostBridgeModels.kt`
- create `app/src/main/java/dev/vitalcc/stukay/runtime/hostbridge/*`
- modify `app/src/main/java/dev/vitalcc/stukay/runtime/StukayAppState.kt`

Deliverable:

- typed pairing / connection models exist;
- `StukayAppState` uses runtime-aware container instead of direct fake construction.

### 2. Implement pairing payload flow and local-network readiness state

Files:

- modify `app/src/main/AndroidManifest.xml`
- modify `app/src/main/java/dev/vitalcc/stukay/runtime/StukayAppState.kt`
- modify `feature/settings/src/main/java/dev/vitalcc/stukay/feature/settings/ui/SettingsRoute.kt`

Deliverable:

- app can store/update/clear pairing payload;
- app can request/reflect Android 16 local-network companion permission path;
- app exposes connect / reconnect / disconnect state transitions even if transport remains stubbed.

### 3. Surface host status into shell and diagnostics

Files:

- modify `app/src/main/java/dev/vitalcc/stukay/StukayApp.kt`
- modify `feature/projects/src/main/java/dev/vitalcc/stukay/feature/projects/ui/ProjectsRoute.kt`
- modify `feature/diagnostics/src/main/java/dev/vitalcc/stukay/feature/diagnostics/ui/DiagnosticsRoute.kt`

Deliverable:

- `Projects` shows host status signal;
- `Diagnostics` shows host bridge summary and recent connection events;
- `Settings` remains the primary control plane for pairing.

### 4. Add focused tests and update repo status surfaces

Files:

- create `app/src/test/java/dev/vitalcc/stukay/runtime/hostbridge/*`
- modify `Documentation.md`
- modify `.tmp/.codex/task_state/latest.md`
- modify `.tmp/.codex/task_state/latest.json`
- modify `docs/CHANGELOG.md`

Deliverable:

- critical parser/state transitions are covered by JVM tests;
- lifecycle/docs/checkpoint surfaces reflect the new milestone state.

## Acceptance Criteria

- `StukayAppState` больше не создает fake repositories напрямую и получает runtime dependencies через отдельный graph/container.
- В `core:model` есть typed host/pairing/connection models, достаточные для Android-side contract surface.
- `Settings` поддерживает pairing payload save/connect/reconnect/disconnect flow и честно объясняет Android 16 / 17 local-network difference.
- `Projects` и `Diagnostics` получают connection-aware signals без ввода нового top-level route.
- `app:testDebugUnitTest` и `app:assembleDebug` проходят после изменений.
- Active `ExecPlan`, `Documentation`, `docs/CHANGELOG.md` и `.tmp/.codex/task_state/latest.*` синхронизированы с новым milestone.

## Validation

Validation surface summary:

- milestone-level JVM validation идет через `:app:testDebugUnitTest`;
- compile/build validation идет через `:app:assembleDebug`;
- lifecycle/docs contract валидируется repo harness validator;
- Android CLI и JetBrains MCP config surface подтверждаются отдельными probes.

## Validation Commands

After milestone 1:

```text
.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest --console=plain
```

After milestone 2:

```text
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

After milestone 3:

```text
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

Final verification:

```text
python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .
android describe --project_dir .
codex mcp get jetbrains
.\gradlew.bat :core:model:testDebugUnitTest :app:assembleDebug :app:testDebugUnitTest --console=plain
```

## Review Loop

- проверить, что pairing/local-network flow явно остается Android-side contract slice, а не маскируется под real host runtime;
- проверить, что новый host state не смешан с existing fake thread state;
- проверить, что new logs и diagnostics не выводят pairing secret/token;
- проверить, что `Settings` остается control plane, а route graph не разрастается ad hoc.

## Decision Log

- decision: pairing flow в этом milestone начинается с `pairing payload paste/import`, без camera QR scanning.
- why: сначала фиксируем contract и runtime seam, а не UI around camera/device permissions.
- decision: отдельный `:feature:connection` module пока не вводится.
- why: single-host engineering surface достаточно естественно ложится в existing `Settings`.
- decision: transport implementation в этом milestone остается stubbed/local-slice-only.
- why: roadmap уже отделяет current runtime slice от следующего `Host Bridge MVP`.

## Progress Log

- status: runtime_slice_host_bridge_complete
- done: собран active implementation plan и research подложка, добавлены typed `Host Bridge` models, введен runtime graph для app state, добавлен pairing payload flow, manifest/network permission surface, connection-aware `Settings`, host summary в `Projects`, host diagnostics в `Diagnostics`, JVM tests для parser/repository transitions
- next: перейти к `Host Bridge MVP` и real `codex app-server` backed thread/runtime transport

## Risks / Non-Goals / Deferred

- JetBrains MCP live namespace currently times out from this Codex runtime; use shell fallback and record the limitation rather than blocking implementation.
- `ACCESS_LOCAL_NETWORK` cannot be treated as current app permission policy for this repo while compile/target remain API 36; document the Android 17 path, but do not pretend it is implementable in this milestone.
- Camera QR scanning, full host discovery, real `codex app-server` thread transport, review surfaces, notifications, and diagnostics export remain deferred.
- Existing fake thread controls may still exist visually after this slice, but they must be clearly separated from host connection state rather than silently presented as real runtime operations.

## Recovery / Rollback

- если runtime slice ломает shell compile/test surface, вернуть app-level wiring к последней self-consistent fake-only state before next commit;
- если future host transport потребует другой repository contract, менять это через runtime graph seam, а не через прямой возврат fake wiring в `StukayAppState`;
- если official Android/OpenAI docs противоречат принятому решению, переводить решение в deferred/open question и не расширять scope эвристически.

## Evidence Sources

- detailed research notes: `docs/exec-plans/active/runtime-slice-host-bridge-research.md`
- repo-local sources:
  - `AGENTS.md`
  - `README.md`
  - `Documentation.md`
  - `docs/ROADMAP.md`
  - `docs/architecture/application-architecture.md`
  - `docs/observability/logging-and-diagnostics.md`
- official sources:
  - Android 16 summary / behavior changes
  - local network permission guidance
  - Navigation Compose
  - app architecture
  - state hoisting
  - adaptive navigation/window-size docs
  - OpenAI Codex app-server docs
