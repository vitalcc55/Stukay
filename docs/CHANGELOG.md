# Changelog

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
