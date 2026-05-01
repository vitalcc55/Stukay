# Prompt.md

## Goals
- Поднять в репозитории `Stukay` Android-aware lifecycle harness для долгой автономной работы Codex.
- Зафиксировать проект как personal-first Android 16 / Pixel 9 Pro XL приложение, выступающее мобильной control surface для Windows Host Bridge -> codex app-server.
- Организовать repo-local docs, plans, observability policy, command inventory и status surfaces до начала продуктовой разработки.
- Встроить в рабочий контур два обязательных инструмента: JetBrains MCP Server для Android Studio и `android` CLI.
- Завести проектный след в Notion и описать правило, как repo-local docs и Notion дополняют друг друга.

## Non-goals
- Не выполнять сейчас product-feature разработку экранов, Host Bridge, pairing, timeline или approval flow.
- Не менять на этом этапе package name, модульную структуру, dependency graph или Compose UI beyond minimal discovery.
- Не строить пока CI/CD pipeline, release automation или production backend integration.
- Не заменять repo-local sources of truth одними записями в Notion.

## Hard Constraints
- Вся работа и документация должны учитывать Android 16 / API 36 как целевую платформу, но отделять `current state` репозитория от `target state`.
- Документация должна сверяться с актуальными official Android/Gradle/Kotlin sources и не повторять неподтвержденные предположения.
- Рабочий контур должен оставаться Windows-native: Android Studio + JetBrains MCP + Android CLI + Gradle Wrapper.
- Корневой `AGENTS.md` должен оставаться короткой картой, а детали уходят в `docs/`.
- Каждый завершенный этап фиксируется отдельным Git commit.

## Deliverables
- Канонический lifecycle stack с заполненными `Prompt.md`, `Implement.md`, `Documentation.md`, `ExecPlan.md` и `latest.*`.
- Repo-local Android docs: stack inventory, commands inventory, test matrix, project interfaces, architecture direction, decisions, observability/logging policy.
- Явное описание того, когда использовать JetBrains MCP и когда использовать `android` CLI.
- Repo-local правило Notion sync и стартовая Notion page по проекту.
- Changelog и tech-debt surfaces для дальнейших milestones.

## Done When
- Lifecycle validator не падает по contract failures.
- `Documentation.md`, active `ExecPlan` и `.tmp/.codex/task_state/latest.*` отражают реальный статус работ.
- В репозитории есть достаточно legible surfaces, чтобы новый агент мог продолжить работу без чтения полного чата.
- Минимальный Android-aware control plane задокументирован и проверен фактами.
