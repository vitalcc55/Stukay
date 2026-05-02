# Documentation.md

## Current Milestone Status
- current: Второй этап product milestone завершен; logging core и diagnostics foundation уже работают внутри shell.
- done: `core:logging` покрыт unit tests, app shell пишет runtime events, DiagnosticsScreen читает live in-memory log snapshot.
- next: Перейти к fake domain, typed timeline items, approval shell и richer thread state.

## Decisions
- decision: Сначала поднимаем harness, docs и observability, а не меняем продуктовый код.
- why: Проект пока в template state, и без legible control plane дальнейшая Android-разработка будет плохо воспроизводимой для Codex.
- decision: JetBrains MCP и Android CLI входят в обязательный Android workflow этого репозитория.
- why: они закрывают IDE-aware и Android-native surfaces, которые обычный shell не покрывает достаточно надежно.
- decision: Notion используется как project visibility surface, но не как замена repo-local sources of truth.
- why: active plan, status, commands и architecture должны оставаться versioned рядом с кодом.

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
- findings: logging core проходит через красный-зелёный цикл, а shell уже не только собирается, но и пишет/показывает runtime events.
- residual risks: fake domain orchestration, timeline typing и approval behavior ещё не реализованы.

## Known Issues And Follow-ups
- item: В текущем runtime JetBrains MCP tools могут быть недоступны как native namespace до перезапуска Codex App, хотя server-side конфиг уже работает.
- item: `android describe` иногда не печатает useful stdout без controlled capture; это надо зафиксировать в commands inventory.
- item: root shell пока использует локальный navigation state machine вместо `navigation-compose`; это осознанное решение первого этапа, пока зависимость и масштабы UI не требуют большего.
- item: Notion workspace запрещает standalone private page creation; проект привязан к базе `Проекты`, и этот parent constraint надо учитывать дальше.
- item: adaptive UI для двух режимов рендера Pixel (`1008×2244` и `1344×2992`) пока закрыт width-constrained shell-логикой, но не через отдельный adaptive toolkit слой.

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
