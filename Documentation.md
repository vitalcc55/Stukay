# Documentation.md

## Current Milestone Status
- current: Первый product milestone завершен.
- done: multi-module shell, logging core, typed fake domain, timeline items и approval shell уже реализованы и проверены локально.
- next: Следующий milestone — runtime slice для Host Bridge / pairing / local network flow.

## Decisions
- decision: Сначала поднимаем harness, docs и observability, а не меняем продуктовый код.
- why: Проект пока в template state, и без legible control plane дальнейшая Android-разработка будет плохо воспроизводимой для Codex.
- decision: JetBrains MCP и Android CLI входят в обязательный Android workflow этого репозитория.
- why: они закрывают IDE-aware и Android-native surfaces, которые обычный shell не покрывает достаточно надежно.
- decision: Notion используется как project visibility surface, но не как замена repo-local sources of truth.
- why: active plan, status, commands и architecture должны оставаться versioned рядом с кодом.
- decision: shell ориентируется на `1344×2992` как основной режим рендера, но сохраняет стабильный layout на `1008×2244`.
- why: это два реальных режима одного Pixel 9 Pro XL, и для этого проекта важнее стабильный width-constrained layout, чем избыточный adaptive stack.

## How To Run And Demo
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: validator проходит без contract failures; допустимы только осознанные `warn` до заполнения placeholder-ов.
- command: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- expected result: текущий template app собирается и unit tests проходят.
- command: `android describe --project_dir .`
- expected result: CLI распознает `:app`, варианты `debug/release` и APK output surface.
- command: `codex mcp get jetbrains`
- expected result: отображается активный stdio-config Android Studio MCP.

## Latest Review Outcome
- findings: no findings по product milestone diff; shell уже использует typed models, fake repositories, runtime logging и diagnostics snapshot.
- residual risks: Host Bridge/runtime network slice ещё не начат; diagnostics пока без persistence/export.

## Known Issues And Follow-ups
- item: В текущем runtime JetBrains MCP tools могут быть недоступны как native namespace до перезапуска Codex App, хотя server-side конфиг уже работает.
- item: `android describe` иногда не печатает useful stdout без controlled capture; это надо зафиксировать в commands inventory.
- item: shell уже использует `Navigation Compose`, но adaptive-навигация под более широкие классы экранов пока не вводилась сознательно.
- item: Notion workspace запрещает standalone private page creation; проект привязан к базе `Проекты`, и этот parent constraint надо учитывать дальше.
- item: adaptive UI для двух режимов рендера Pixel (`1008×2244` и `1344×2992`) пока решён через width-constrained shell, а не через `NavigationSuiteScaffold` или отдельный adaptive toolkit layer.

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
