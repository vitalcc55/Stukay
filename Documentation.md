# Documentation.md

## Current Milestone Status
- current: Milestone 2 - Android-specific docs, process surfaces и repo map enrichment.
- done: Milestone 1 завершен; канонический lifecycle stack создан и привязан к корню репозитория.
- next: Зафиксировать Android project direction, commands inventory, architecture surfaces и observability baseline.

## Decisions
- decision: Сначала поднимаем harness, docs и observability, а не меняем продуктовый код.
- why: Проект пока в template state, и без legible control plane дальнейшая Android-разработка будет плохо воспроизводимой для Codex.

## How To Run And Demo
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: validator проходит без contract failures; допустимы только осознанные `warn` до заполнения placeholder-ов.

## Latest Review Outcome
- findings: no findings по bootstrap diff; lifecycle stack самосогласован, `.tmp` policy не оставляет случайные probe artifacts под коммит.
- residual risks: Android-specific docs и command inventory еще не заполнены; Notion sync еще не заведен.

## Known Issues And Follow-ups
- item: В текущем runtime JetBrains MCP tools могут быть недоступны как native namespace до перезапуска Codex App, хотя server-side конфиг уже работает.
- item: `android describe` иногда не печатает useful stdout без controlled capture; это надо зафиксировать в commands inventory.

## Evidence Index
- artifact: `.tmp/.codex/task_state/latest.md`
- purpose: compaction-safe human-readable resume surface
- artifact: `.tmp/.codex/task_state/latest.json`
- purpose: machine-readable checkpoint surface
