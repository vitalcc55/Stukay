# Documentation.md

## Current Milestone Status
- current: Milestone 3 - observability, diagnostics policy и Notion sync.
- done: Milestone 1 и Milestone 2 завершены; current stack, commands, architecture direction и workflow surfaces зафиксированы в repo-local docs.
- next: Добавить observability/notion layer, обновить tech debt and status surfaces, затем сделать следующий stage commit.

## Decisions
- decision: Сначала поднимаем harness, docs и observability, а не меняем продуктовый код.
- why: Проект пока в template state, и без legible control plane дальнейшая Android-разработка будет плохо воспроизводимой для Codex.
- decision: JetBrains MCP и Android CLI входят в обязательный Android workflow этого репозитория.
- why: они закрывают IDE-aware и Android-native surfaces, которые обычный shell не покрывает достаточно надежно.

## How To Run And Demo
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: validator проходит без contract failures; допустимы только осознанные `warn` до заполнения placeholder-ов.
- command: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- expected result: текущий template app собирается и unit tests проходят.
- command: `android describe --project_dir .`
- expected result: CLI распознает `:app`, варианты `debug/release` и APK output surface.

## Latest Review Outcome
- findings: no findings по documentation diff; repo now has a durable Android-aware map, and current state remains separated from target direction.
- residual risks: observability/notion layer еще не добавлены; native `jetbrains` namespace в live runtime still depends on session reload.

## Known Issues And Follow-ups
- item: В текущем runtime JetBrains MCP tools могут быть недоступны как native namespace до перезапуска Codex App, хотя server-side конфиг уже работает.
- item: `android describe` иногда не печатает useful stdout без controlled capture; это надо зафиксировать в commands inventory.
- item: current repo still uses template namespace and single-module structure.

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
