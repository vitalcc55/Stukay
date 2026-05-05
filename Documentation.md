# Documentation.md

## Current Milestone Status
- current: `Real Thread Runtime + Approval Safety Layer` локально реализован поверх Host Bridge MVP; checkpoint commit `cdf3d14` и follow-up commit `8cb7dfa` уже зафиксированы, full combined Gradle gate и Python helper suite на текущем local diff зелёные, а повторный review loop тем же агентным trio локально закрыт без подтверждённых новых дефектов. До `verified` по-прежнему не хватает device proof и финального branch-wide review относительно `main`.
- done: helper больше не ограничивается `/v1/runtime/summary`: добавлены typed `thread/list` / `thread/read` / `thread/resume` / `turn/start` / `turn/interrupt` / approval-response routes и SSE event stream для активного thread. Android-side `OkHttpHostBridgeClient` перешел на nested JSON contract, `RuntimeThreadStore` стал общим runtime-backed read model для `Projects` / `Project` / `Thread`, а `RuntimeThreadRepository` и `RuntimeProjectsRepository` перестали делегировать в fake repositories. `StukayAppState` теперь держит foreground thread session с hydrate/resume, composer/send/stop, event-stream reducer, reconnect recovery, pending approvals и runtime diagnostics snapshot. `ThreadRoute` убрал fake run controls, получил composer, stop, approval actions, blocked-state banner и стабильные semantics/test tags; `Projects`, `Project` и `Diagnostics` переведены на честный runtime-backed copy и identifiers. Добавлены новые regression tests для runtime store и helper thread endpoints / SSE / approval surface.
- next: прогнать device proof для emulator и Pixel 9 Pro XL по сценарию `open existing thread -> send -> stream -> interrupt -> reconnect -> approval`, затем синхронизировать active execution docs под post-implementation state и прогнать merge-readiness review.

## Decisions
- decision: Сначала поднимаем harness, docs и observability, а не меняем продуктовый код.
- why: Проект пока в template state, и без legible control plane дальнейшая Android-разработка будет плохо воспроизводимой для Codex.
- decision: JetBrains MCP и Android CLI входят в обязательный Android workflow этого репозитория.
- why: они закрывают IDE-aware и Android-native surfaces, которые обычный shell не покрывает достаточно надежно.
- decision: Notion используется как project visibility surface, но не как замена repo-local sources of truth.
- why: active plan, status, commands и architecture должны оставаться versioned рядом с кодом.
- decision: shell ориентируется на `1344×2992` как основной режим рендера, но сохраняет стабильный layout на `1008×2244`.
- why: это два реальных режима одного Pixel 9 Pro XL, и для этого проекта важнее стабильный width-constrained layout, чем избыточный adaptive stack.
- decision: отдельный `:feature:connection` module пока не вводится; pairing/local-network UX живет в `feature:settings`.
- why: текущий slice single-host, engineering-first и не требует нового feature boundary до начала real multi-host/runtime transport.
- decision: pairing flow в этом milestone начинается с `pairing payload paste/import`, а camera QR scanning и public tunnel flow отложены.
- why: roadmap разделяет contract slice и `Host Bridge MVP`, поэтому сначала фиксируется typed seam, guard-нутый control flow и permission UX.
- decision: ложный `Instantiatable` для `MainActivity` подавляется точечно через `tools:ignore` в manifest вместо отката SDK, замены `ComponentActivity` или ослабления UI stack.
- why: это bug tooling surface для `SDK 36 Preview + AGP 9.2.0`, а не runtime bug приложения; suppression ограничен одной записью manifest и не меняет поведение app shell.
- decision: accessibility переводится в обязательный engineering baseline для следующих UI milestones.
- why: device-side QA и agent-driven Android smoke опираются на semantics/accessibility tree, а не только на pixels и координаты.

## How To Run And Demo
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: validator проходит без contract failures; допустимы только осознанные `warn` до заполнения placeholder-ов.
- command: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- expected result: runtime slice собирается, а JVM tests для pairing/parser/runtime state и restore/guard transitions проходят.
- command: `.\gradlew.bat :app:lintDebug --console=plain`
- expected result: lint проходит; preview false positive по `Instantiatable` подавлен точечно на manifest entry `MainActivity`.
- command: `android describe --project_dir .`
- expected result: CLI распознает `:app`, варианты `debug/release` и APK output surface.
- command: `codex mcp get jetbrains`
- expected result: отображается активный stdio-config Android Studio MCP.
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: lifecycle validator проходит; допустимы только известные `warn`, без `fail`.

## Latest Review Outcome
- findings: после checkpoint `cdf3d14` был создан follow-up commit `8cb7dfa Fix runtime review follow-up issues`, затем два дополнительных re-review круга тем же trio локально закрыли подтверждённые хвосты по `thread/resume` merge semantics, runtime-path action gating, recoverable `Failed` retention, failed-state approval hydration, non-reentrant composer policy, blocked-turn `Stop`, rehydrate failure context и error surfacing на thread screen. В последнем re-review один агент оставил только гипотезу про stale multi-approval reconnect merge, но локально она не подтверждена как дефект этого slice: текущий helper/runtime contract ре-гидратирует blocked-state только через thread status + server-request replay, а approval inventory не является частью `thread/read` / `thread/resume` snapshot truth и уже зафиксирован в research как client-owned queue concern.
- residual risks: bounded cleartext/local-only runtime path остается сознательным ограничением до TLS/public-path milestone; device proof по emulator и физическому Pixel 9 Pro XL для нового runtime slice еще не прогонялся; полноценный `waitingOnUserInput` dialog UX все еще out of scope; если будущий helper начнет отдавать authoritative approval inventory в read/resume payload, reconnect cleanup для multi-approval edge-case нужно будет пересмотреть.

## Known Issues And Follow-ups
- item: В текущем runtime JetBrains MCP tools могут быть недоступны как native namespace до перезапуска Codex App, хотя server-side конфиг уже работает.
- item: `android describe` иногда не печатает useful stdout без controlled capture; это надо зафиксировать в commands inventory.
- item: shell уже использует `Navigation Compose`, но adaptive-навигация под более широкие классы экранов пока не вводилась сознательно.
- item: Notion workspace запрещает standalone private page creation; проект привязан к базе `Проекты`, и этот parent constraint надо учитывать дальше.
- item: adaptive UI для двух режимов рендера Pixel (`1008×2244` и `1344×2992`) пока решён через width-constrained shell, а не через `NavigationSuiteScaffold` или отдельный adaptive toolkit layer.
- item: основные runtime controls и status surfaces уже получили semantics/test tags, но device-side proof через `android layout` и физический Pixel в этом цикле еще не перепроверялся.
- item: `waitingOnUserInput` сейчас surfacing-only: banner + diagnostics есть, но полноценного dialog/input UX в этом slice нет намеренно.
- item: local network permission path для current API 36 intentionally подается как manual/opt-in path вокруг `NEARBY_WIFI_DEVICES`, а не как unconditional blocker; `ACCESS_LOCAL_NETWORK` остается Android 17+ follow-up.
- item: текущий host bridge repository уже real host-backed и принимает только private LAN / `.local` / `100.64/10` endpoints; public tunnel path по-прежнему считается out of scope.
- item: pairing payload хранится raw в `SharedPreferences`, но backup/data-transfer для `stukay_host_bridge.xml` теперь исключены; полноценный at-rest hardening остается follow-up.
- item: `tools:ignore="Instantiatable"` на `MainActivity` считается допустимым tooling debt для `SDK 36 Preview + AGP 9.2.0`; его надо снять, когда стабильный lint снова начнет корректно видеть `ComponentActivity -> Activity`.
- item: Host Bridge helper по умолчанию стартует на loopback и для physical LAN proof сейчас поднимался явным bind на USB tether IP; автоматизированный host launcher UX для non-loopback bind остается follow-up поверх этого MVP.

## Evidence Index
- artifact: `.tmp/.codex/task_state/latest.md`
- purpose: compaction-safe human-readable resume surface
- artifact: `.tmp/.codex/task_state/latest.json`
- purpose: machine-readable checkpoint surface
- artifact: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- purpose: verified current build/test surface
- artifact: `mcp__jetbrains__.build_project(filesToRebuild=...)`
- purpose: IDE-backed sync-preflight and file-level compilation proof before Android builds/tests
- artifact: `android describe --project_dir .`
- purpose: verified Android CLI project surface
- artifact: `codex mcp get jetbrains`
- purpose: verified Codex-side JetBrains MCP config
- artifact: commit `cdf3d14`
- purpose: checkpoint commit for `Real Thread Runtime + Approval Safety Layer` before review loop
- artifact: commit `8cb7dfa`
- purpose: follow-up commit with the first wave of review-driven runtime fixes before the second review loop
- artifact: `.\gradlew.bat :core:model:testDebugUnitTest :feature:thread:testDebugUnitTest :app:testDebugUnitTest --console=plain`
- purpose: verified second-wave fixes for foreground session policy, runtime-path guard and resume-merge reconciliation
- artifact: `.\gradlew.bat :core:model:testDebugUnitTest :feature:projects:testDebugUnitTest :feature:thread:testDebugUnitTest :core:logging:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain`
- purpose: verified runtime-backed Android domain/store/UI slice after real thread runtime migration
- artifact: `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
- purpose: verified helper thread routes, approval response path and SSE event normalization in addition to legacy runtime summary path
- artifact: `docs/exec-plans/active/host-bridge-mvp-proof.md`
- purpose: archived evidence from the previous Host Bridge MVP milestone; not the primary proof source for `cdf3d14`
- artifact: `https://app.notion.com/p/353f585cf06881c682d5ccb7437ada86`
- purpose: active Notion project record for cross-project visibility
- artifact: `.\gradlew.bat :core:model:testDebugUnitTest :feature:projects:testDebugUnitTest :feature:thread:testDebugUnitTest :core:logging:testDebugUnitTest :app:assembleDebug :app:testDebugUnitTest --console=plain`
- purpose: verified domain, logging and app shell surfaces after stage 3
- artifact: `docs/exec-plans/active/runtime-slice-host-bridge-research.md`
- purpose: evidence-backed research substrate for runtime slice scope, official facts and reference signals
- artifact: `docs/exec-plans/active/host-bridge-mvp-plan.md`
- purpose: completed implementation-oriented plan and stage ledger for the locally proven Host Bridge MVP milestone
- artifact: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
- purpose: verified pairing parser and host bridge repository state transitions
- artifact: `.\gradlew.bat :app:assembleDebug --console=plain`
- purpose: verified compile/build surface after runtime slice UI and manifest changes
- artifact: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HostBridgeClientTest" --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest" --console=plain`
- purpose: verified Stage 3 client/repository contract, unauthorized mapping, degraded snapshot preservation and disconnect semantics
- artifact: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest.refreshPermissionStateDoesNotMaskUnauthorizedFailure" --console=plain`
- purpose: verified Stage 3 review-driven fix for permission refresh truthfulness after unauthorized failure
- artifact: `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain`
- purpose: verified Stage 4 model-level freshness contract plus shell runtime-summary surfaces after Settings/Projects/Diagnostics update
- artifact: `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
- purpose: verified Stage 5 helper runtime hardening, Windows codex spawn resolution and module-entry degraded HTTP path
- artifact: `http://127.0.0.1:8421/v1/runtime/summary`
- purpose: verified local live helper -> codex app-server round-trip after Stage 5 runtime hardening
- artifact: `docs/exec-plans/active/host-bridge-mvp-proof.md`
- purpose: sanitized acceptance evidence for helper live proof, physical Pixel flow, emulator flow and diff-scoped security verdict
- artifact: `.\gradlew.bat :app:lintDebug --console=plain`
- purpose: verified final lint gate after manifest-scoped suppression of preview false positive
