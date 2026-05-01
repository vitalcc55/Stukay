# Implement.md

## Source of Truth
- `Prompt.md`
- `docs/exec-plans/active/ExecPlan.md`
- ближайшие `AGENTS.md`
- official Android / Gradle / Kotlin sources для platform-sensitive решений

## Canonical Artifact Stack
- `Prompt.md`
- `docs/exec-plans/active/ExecPlan.md`
- `Implement.md`
- `Documentation.md`
- `.tmp/.codex/task_state/latest.md`
- `.tmp/.codex/task_state/latest.json`

## Execution Loop
1. Прочитай `Prompt.md`, active `ExecPlan` и ближайшие `AGENTS.md`.
2. Возьми следующий незавершенный milestone и проверь, нужен ли factual refresh через Android docs, Android CLI или JetBrains MCP.
3. Для IDE-aware discovery предпочитай JetBrains MCP surfaces перед ad-hoc file search, если они доступны в текущем runtime.
4. Для Android-specific guidance, project describe, emulator/device и docs search предпочитай `android` CLI.
5. Сделай минимальный законченный diff в рамках milestone.
6. Сразу прогони релевантные validation commands и сохрани evidence.
7. Выполни локальный review loop.
8. Обнови `Documentation.md`, progress log, `latest.*` и `docs/CHANGELOG.md`.
9. Зафиксируй milestone отдельным commit, если acceptance criteria закрыты.

## Validation Rule
- Если validation падает, сначала чини или локализуй blocker.
- Нельзя переходить к следующему milestone с красным состоянием.
- Если Android-specific факт кажется сомнительным или недавно изменился, перепроверь его через official docs прежде чем закреплять в durable docs.

## Review Loop
- После каждого milestone сделай локальный review diff и evidence.
- Зафиксируй findings или явное `no findings` в `Documentation.md`.

## Documentation And Status Update
- Обновляй `Documentation.md` после каждого milestone.
- Обновляй `.tmp/.codex/task_state/latest.md` и `latest.json` после каждого milestone.

## Scope Control
- Не расширяй scope без явного основания.
- Смежные задачи выноси в follow-up или tech debt tracker.
- Harness, docs и process идут раньше feature-кода, если milestone не требует иного.
