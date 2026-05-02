# AGENTS.md

Короткая карта проекта, Android-специфичные инварианты и локальный lifecycle-контур.

## Repo Map

- `app/` — Android entrypoint, `Application`, `Activity`, root navigation и shell wiring.
- `core/model/` — typed domain identifiers, thread/project/timeline models и route context.
- `core/logging/` — runtime logging, sinks, diagnostics summary provider и in-memory log store.
- `core/design/` — stable theme baseline, expressive layer и shared layout primitives.
- `feature/projects/`, `feature/thread/`, `feature/settings/`, `feature/diagnostics/` — feature-bounded shell and fake workflow surfaces.
- `docs/` — каноническая проектная документация, decisions, architecture, generated inventory и exec plans.
- `Prompt.md` — зафиксированная постановка текущего long-horizon направления.
- `Implement.md` — runbook того, как Codex должен вести работу в этом репозитории.
- `Documentation.md` — human-readable status, review outcome и evidence index.
- `.tmp/.codex/task_state/latest.*` — resume surface после compaction и между milestones.

## Local Contracts

- Репозиторий сейчас находится в phase `harness + documentation`, а не в phase активной продуктовой разработки. Сначала поднимаем управляемый контур, затем UI/backend milestones.
- `Stukay` проектируется как personal-first Android 16 / Pixel 9 Pro XL control surface для локального Codex runtime на Windows.
- JetBrains MCP Server для Android Studio считается частью штатного рабочего контура Codex. При наличии MCP tools сначала используй IDE-aware surfaces: project modules, run configurations, inspections, symbol/file navigation, rename refactoring, formatter.
- `android` CLI считается обязательным Android-specific control plane. Используй его для Android docs search, project describe, SDK/emulator/device flows и Android runtime diagnostics.
- Источник истины для сборки и проверки — Gradle Wrapper (`gradlew.bat` / `gradlew`), а не IDE-only действия.
- Multi-module foundation shell, runtime logging и DDD-lite fake domain уже внедрены; следующий шаг — runtime slice для Host Bridge/pairing/local network flow, а не возврат к template cleanup.

<!-- repo-harness-lifecycle:start -->
## Lifecycle Artifacts

- Канонический long-horizon stack:
  - `Prompt.md`
  - `docs/exec-plans/active/ExecPlan.md`
  - `Implement.md`
  - `Documentation.md`
  - `.tmp/.codex/task_state/latest.md`
  - `.tmp/.codex/task_state/latest.json`
- Active `ExecPlan` — source of truth для milestones, validation commands и progress log.
- После каждого milestone обновляй `Documentation.md` и `.tmp/.codex/task_state/latest.*`.
- Для длинной фоновой или конфликтующей Git-работы предпочитай worktree; короткий локальный milestone можно вести в текущем checkout.
- Если lifecycle stack отсутствует или дрейфует, используй `$repo-harness-lifecycle`.
<!-- repo-harness-lifecycle:end -->

## Primary Docs

- `docs/architecture/index.md`
- `docs/generated/stack-inventory.md`
- `docs/generated/commands.md`
- `docs/generated/test-matrix.md`
- `docs/generated/project-interfaces.md`
- `docs/generated/ui-legibility-surface.md`
- `docs/ROADMAP.md`
- `docs/QUALITY.md`
- `docs/observability/logging-and-diagnostics.md`
- `docs/notion/PROJECT_SYNC.md`
- `docs/DECISIONS.md`
- `docs/CHANGELOG.md`
