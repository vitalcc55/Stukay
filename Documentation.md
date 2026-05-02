# Documentation.md

## Current Milestone Status
- current: Первый runtime slice для `Host Bridge contract + pairing + local network flow` реализован и локально перепроверен.
- done: typed `Host Bridge` models, runtime graph для app state, pairing payload save/connect/reconnect/disconnect flow, Android 16 local-network permission rationale, host summary в `Projects`, host diagnostics в `Diagnostics`, JVM tests для parser/repository transitions.
- next: Перейти к `Host Bridge MVP` и real `codex app-server` backed thread/runtime transport, не возвращаясь к foundation shell.

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
- why: roadmap разделяет contract slice и `Host Bridge MVP`, поэтому сначала фиксируется typed seam и permission UX.

## How To Run And Demo
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: validator проходит без contract failures; допустимы только осознанные `warn` до заполнения placeholder-ов.
- command: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- expected result: runtime slice собирается, а JVM tests для pairing/parser/runtime state проходят.
- command: `android describe --project_dir .`
- expected result: CLI распознает `:app`, варианты `debug/release` и APK output surface.
- command: `codex mcp get jetbrains`
- expected result: отображается активный stdio-config Android Studio MCP.
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: lifecycle validator проходит; допустимы только известные `warn`, без `fail`.

## Latest Review Outcome
- findings: локальный compile/test loop для runtime slice чист; blocker regressions по `app:testDebugUnitTest` и `app:assembleDebug` не обнаружены.
- residual risks: transport остается stubbed, camera QR pairing не реализован, public/tunnel endpoint path вынесен в следующий milestone, diagnostics все еще без persistence/export.

## Known Issues And Follow-ups
- item: В текущем runtime JetBrains MCP tools могут быть недоступны как native namespace до перезапуска Codex App, хотя server-side конфиг уже работает.
- item: `android describe` иногда не печатает useful stdout без controlled capture; это надо зафиксировать в commands inventory.
- item: shell уже использует `Navigation Compose`, но adaptive-навигация под более широкие классы экранов пока не вводилась сознательно.
- item: Notion workspace запрещает standalone private page creation; проект привязан к базе `Проекты`, и этот parent constraint надо учитывать дальше.
- item: adaptive UI для двух режимов рендера Pixel (`1008×2244` и `1344×2992`) пока решён через width-constrained shell, а не через `NavigationSuiteScaffold` или отдельный adaptive toolkit layer.
- item: `feature:thread` по-прежнему использует fake-only action contract (`startFakeTurn`, `completeFakeTurn`, `resolveApproval`); его real runtime замена вынесена в `Host Bridge MVP`.
- item: local network permission path для current API 36 intentionally опирается на `NEARBY_WIFI_DEVICES` rationale; `ACCESS_LOCAL_NETWORK` остается Android 17+ follow-up.
- item: текущий host bridge repository остается stubbed и принимает только private LAN / `.local` endpoints; public tunnel path пока считается out of scope.

## Evidence Index
- artifact: `.tmp/.codex/task_state/latest.md`
- purpose: compaction-safe human-readable resume surface
- artifact: `.tmp/.codex/task_state/latest.json`
- purpose: machine-readable checkpoint surface
- artifact: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- purpose: verified current build/test surface
- artifact: `android describe --project_dir .`
- purpose: verified Android CLI project surface
- artifact: `codex mcp get jetbrains`
- purpose: verified Codex-side JetBrains MCP config
- artifact: `https://app.notion.com/p/353f585cf06881c682d5ccb7437ada86`
- purpose: active Notion project record for cross-project visibility
- artifact: `.\gradlew.bat :core:model:testDebugUnitTest :feature:projects:testDebugUnitTest :feature:thread:testDebugUnitTest :core:logging:testDebugUnitTest :app:assembleDebug :app:testDebugUnitTest --console=plain`
- purpose: verified domain, logging and app shell surfaces after stage 3
- artifact: `docs/exec-plans/active/runtime-slice-host-bridge-research.md`
- purpose: evidence-backed research substrate for runtime slice scope, official facts and reference signals
- artifact: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
- purpose: verified pairing parser and host bridge repository state transitions
- artifact: `.\gradlew.bat :app:assembleDebug --console=plain`
- purpose: verified compile/build surface after runtime slice UI and manifest changes
