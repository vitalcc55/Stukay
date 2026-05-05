# Changelog

## 2026-05-05

- После review loop по commit `8cb7dfa` закрыт дополнительный runtime tail: `thread/resume` теперь заменяет same-id items более свежим runtime snapshot и не держит stale approvals вне `waitingOnApproval`.
- Foreground thread session теперь честнее переживает failure/reconnect: off-screen retain включает recoverable `Failed`, open-failure path ре-гидратирует pending approvals, а `Send`/`Stop` завязаны на runtime availability и non-reentrant session policy.
- Добавлены прямые regression tests для foreground session policy, runtime-path guard в `HostBridgeRepository` и resume-merge reconciliation в `RuntimeThreadStore`.

## 2026-05-04

- Реализован `Real Thread Runtime + Approval Safety Layer`: helper получил typed thread/turn/approval HTTP routes и SSE event stream, а Android shell переведен с fake delegates на runtime-backed `Projects` / `Project` / `Thread`.
- `StukayAppState` теперь владеет foreground thread session с hydrate/resume, composer/send/stop, reconnect recovery, pending approvals и runtime-aware diagnostics snapshot.
- Добавлены targeted regression tests для runtime store grouping/reducer и helper thread endpoints / approval / SSE surface.
- Добавлен implementation-oriented план `Real Thread Runtime + Approval Safety Layer` с зафиксированными рамками slice, stop condition, integration points, official protocol facts, accessibility baseline и validation surface.
- План `Real Thread Runtime + Approval Safety Layer` усилен execution-tracking слоем: добавлены `status` по milestone, checklist-ы, рекомендуемая разбивка по коммитам и шаблон stage report перед checkpoint-коммитом.
- В план `Real Thread Runtime + Approval Safety Layer` добавлен review loop protocol: pre-commit sandbox review через субагентов, повторная перепроверка после исправлений, остановка на low-signal docs semantics и отдельный branch-wide review loop относительно `main` после завершения всего slice.

## 2026-05-01

- Поднят канонический lifecycle stack для long-horizon работы Codex через `repo-harness-lifecycle`.
- Корневой `AGENTS.md` превращен в реальную карту Android-репозитория и рабочего контура Codex.
- Зафиксированы `Prompt.md`, `Implement.md`, `Documentation.md`, active `ExecPlan` и checkpoint policy.
- Добавлена `.gitignore` политика для канонических `.tmp/.codex/task_state/latest.*`.
- Добавлены repo-local Android docs: `README`, stack inventory, commands inventory, test matrix, interfaces map, architecture direction и Android agent workflow.
- В docs зафиксированы роли JetBrains MCP и `android` CLI как обязательных engineering surfaces.
- Добавлены `docs/QUALITY.md`, observability/logging policy, Android UI legibility surface и repo-to-Notion sync policy.
- Проект `Stukay` заведен в Notion database `Проекты` как запись `PRJ-3`.

## 2026-05-02

- Начат первый product milestone.
- Проект переведен на `dev.vitalcc.stukay`.
- Введен multi-module foundation scaffold: `:app`, `:core:*`, `:feature:*`.
- Поднят root shell для `Projects`, `Project`, `Thread`, `Settings`, `Diagnostics`.
- `:app:assembleDebug` и `:app:testDebugUnitTest` проходят после foundation refactor.
- Реализован `core:logging` с TDD: `AppLogger`, `LogEvent`, `LogLevel`, `LogArea`, `CompositeLogSink`, `InMemoryLogStore`, `DiagnosticsSummaryProvider`.
- Root shell теперь пишет runtime events, а `DiagnosticsScreen` показывает живой snapshot recent logs и summary.
- Реализован DDD-lite fake domain: typed projects, threads, timeline items, approval actions и in-memory repositories/use cases.
- `ThreadScreen` теперь поддерживает fake run start/complete и approval resolution, а shell стабилизирован под оба режима рендера Pixel 9 Pro XL.
- Поверх milestone выполнен локальный multi-agent review loop; исправлены найденные баги в lifecycle owner app state, approval semantics, route/diagnostics identity, bounded log truth surface и width-constrained shell rollout.
- Зафиксирован новый active `ExecPlan` и research-подложка для `Host Bridge contract + pairing + local network flow`.
- Добавлены typed `Host Bridge` / pairing / connection models в `core:model`.
- `StukayAppState` переведен на runtime graph вместо прямого создания fake repositories.
- `Settings` получил pairing payload save/connect/reconnect/disconnect flow и Android 16 local-network permission rationale.
- `Projects` теперь показывает lightweight host status signal, а `Diagnostics` — host/connection summary.
- В `AndroidManifest.xml` добавлены `INTERNET`, `ACCESS_NETWORK_STATE` и `NEARBY_WIFI_DEVICES` для текущего local-network slice.
- Добавлены JVM tests для pairing payload parser и host bridge repository state transitions.
- Поверх initial runtime slice fix pass убраны crash-path операции без pairing, выровнен restored permission state, введены runtime adapters поверх fake repositories, добавлен dedicated host/connection diagnostics tail и исключен pairing storage из backup/data-transfer rules.

## 2026-05-03

- `:app` и его tests переведены на `src/*/kotlin`, чтобы выровнять структуру исходников с built-in Kotlin workflow AGP.
- Локальный кеш компилятора `.kotlin/` исключен из репозитория через `.gitignore`.
- Для `SDK 36 Preview + AGP 9.2.0` зафиксирован manifest-scoped `tools:ignore="Instantiatable"` на `MainActivity`, потому что lint дает ложное срабатывание на цепочке наследования `ComponentActivity -> Activity`, тогда как build/test surface остаются корректными.
- `gradle.properties` обновлен до `org.gradle.jvmargs=-Xmx4g`, чтобы уменьшить нестабильность Gradle/Kotlin daemon во время local verification.
- Финальный verification gate по runtime slice теперь включает `:app:lintDebug`, `:app:testDebugUnitTest` и `:app:assembleDebug`.
- Перед merge выровнены README, AGENTS, generated commands/test-matrix/stack-inventory и Notion sync note под фактический post-runtime-contract state.
- Добавлен новый active plan `docs/exec-plans/active/host-bridge-mvp-plan.md` для следующего milestone `Host Bridge MVP` с milestone checklist, progress tracker и stage report секциями для продолжения реализации после прерываний.
- В `Documentation.md` и `.tmp/.codex/task_state/latest.*` зафиксировано, что план Host Bridge MVP сохранен, а реализация milestone еще не начата.
- По user decision до начала кодинга закрыты четыре ключевых решения Host Bridge MVP: `transport=http_json`, explicit Android cleartext opt-in + runtime endpoint validation как реальная private/local boundary, первый runtime payload = `host health/status + app/list count`, address policy = `RFC1918 + .local + 100.64/10` без public tunnel и без автоматического `169.254/16`.
- `docs/exec-plans/active/ExecPlan.md` переведен в канонический pointer на `host-bridge-mvp-plan.md`, чтобы lifecycle stack не продолжал ссылаться на старый runtime-slice plan как на active execution source of truth.
- Формулировка bounded cleartext policy уточнена: `network_security_config` здесь фиксирует explicit Android cleartext opt-in, а точная private/local allowlist boundary обеспечивается runtime-валидацией endpoint policy, а не CIDR-выражением внутри Android XML.
- Stage 1 Host Bridge MVP выполнен: Kotlin domain contract очищен от рабочего `ws`/`wss` path, parser по умолчанию переведен на `http_json`, в модель добавлены `Degraded` и `HostRuntimeSummary`, allowlist расширен до `100.64/10`, `169.254/16` оставлен reject path, а stubbed permission semantics больше не заявляют ложную transport-ready готовность для private LAN без permission.
- В Stage 1 также добавлен explicit Android cleartext opt-in через `app/src/main/res/xml/network_security_config.xml` и manifest reference, чтобы `http_json` success path не оставался только docs-level контрактом.
- Stage 2 локально поднят: добавлен stdlib-only Windows Host Bridge helper в `tools/hostbridge` с bearer auth, shared `codex app-server` stdio client, `app/list`-based runtime summary, degraded caching и auth-first reject path; helper suite проходит через `unittest` и `compileall`.
- Stage 3 локально завершен: добавлены Android-side `OkHttpHostBridgeClient` и `HttpJsonHostBridgeRepository`, runtime graph переведен на real host-backed repository, `StukayAppState` переведен на background executor + periodic probe loop + immediate probe on network change, lifecycle teardown добавлен через `AndroidNetworkMonitor.stop()` / `StukayAppState.dispose()` / `StukayAppViewModel.onCleared()`, а review-driven fix закрыл truthfulness drift в `refreshPermissionState()` после `Unauthorized`.
- Stage 4 локально завершен: `Settings`, `Projects` и `Diagnostics` выведены на общий runtime summary surface, но с разной детализацией; введен model-level `HostRuntimeSnapshotScope`, чтобы различать `live` и `last_known` snapshots, и закрыт edge case, где свежий `Unauthorized` verdict выглядел как cached runtime state.
- Stage 5 локально завершен: helper runtime path усилен для Windows spawn/error-path, Python helper suite снова green в `ResourceWarning`-strict mode, diff-scoped transport security review не подтвердил security defects, а acceptance flow `connect -> degraded -> reconnect -> disconnect` доказан и на Pixel 9 Pro XL по USB tether LAN, и на эмуляторе `medium_phone`.

## 2026-05-04

- После внешнего review pass для всей ветки и follow-up internal review loop подтверждены и закрыты восемь root-cause классов в Host Bridge MVP slice: `Nearby devices` перестал быть unconditional connect gate для admissible private/local endpoint на current API 36, `OkHttpHostBridgeClient` теперь явно запрещает redirects как boundary-escape path, `StukayAppState` получил thread-safe `HostBridgeProbeBarrier` против queued probe resurrection после manual disconnect, callback-driven recovery path теперь коалесцирует duplicate immediate probe для одного поколения, auth/protocol failures больше не публикуют stale runtime metrics как `live`, remote-controlled diagnostic strings санитизируются до попадания в state/log/UI, helper bind host ограничен loopback/private-only surface, а `CodexRuntimeClient` дочитывает paginated `app/list` до полного count.
- Для этих fixes добавлены целевые regression tests: Kotlin tests на permission posture, redirect policy и probe barrier, плюс Python test на multi-page `app/list`.
- Accessibility переведен из optional polish в обязательный cross-cutting baseline для следующих UI/runtime milestones: roadmap, active exec plan, quality policy, architecture, UI legibility surface и checkpoint state теперь требуют semantics/content descriptions/stable identifiers как часть device-side Android QA.
- После orchestration review выровнены lifecycle/control-plane артефакты: `ExecPlan.md` снова имеет канонические headings для validator-а, а `Documentation.md`, `.tmp/.codex/task_state/latest.*` и `PROJECT_SYNC.md` переведены на post-MVP state с следующим шагом `Real Thread Runtime`.
