# ExecPlan

## Goal
- Превратить `Stukay` из минимального Android template в хорошо документированный Android-aware Codex work repo, где lifecycle stack, process, architecture direction, observability и external tool surfaces описаны до начала feature development.

## Constraints
- Не начинать сейчас разработку продуктовых экранов, Host Bridge integration или крупную перестройку кода приложения.
- Отделять `current state` шаблонного проекта от `target state` Android 16 / Pixel 9 Pro XL direction.
- Все Android platform claims сверять с official docs и Android CLI docs search.
- JetBrains MCP Server и `android` CLI считать частью обязательного рабочего контура Codex.
- Каждый milestone закрывать отдельным commit.

## Milestones
1. Bootstrap lifecycle stack, root `AGENTS.md`, repo-local runbook и canonical checkpoint policy.
2. Зафиксировать Android-specific project direction, current stack inventory, command inventory, test matrix и interfaces map.
3. Описать observability/logging/diagnostics policy, Notion sync policy, review/status surfaces и tech debt tracker.

## Acceptance Criteria
- Milestone 1: lifecycle validator проходит без contract failures, корневой `AGENTS.md` отражает реальный repo contract, `.tmp` policy не загрязняет Git случайными probe artifacts.
- Milestone 2: в `docs/` есть Android-aware stack inventory, commands, test matrix, interfaces map, architecture direction и decisions с явным разделением current/target state.
- Milestone 3: есть docs по logging/diagnostics, Notion-sync правилу, changelog, обновленные status surfaces и evidence-backed validation.

## Validation Commands
```text
python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .
android describe --project_dir .
codex mcp get jetbrains
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Review Loop
- Проверить, что новые docs не смешивают current state и target direction.
- Проверить, что команды и tool surfaces реально существуют и не являются wishful thinking.
- Проверить, что AGENTS остается короткой картой и не дублирует `docs/`.

## Decision Log
- decision: использовать канонический lifecycle stack из skill вместо ad-hoc набора файлов.
- why: это даст предсказуемый resume path и validator surface для будущих длинных задач.

## Progress Log
- status: product_milestone_complete
- done: scaffold lifecycle stack, initial validator pass, current repo discovery, Android CLI and JetBrains MCP evidence collection, bootstrap repo-local runbook and checkpoint policy, verified current Gradle build/test surface, documented stack inventory, commands, architecture direction and workflow guidance, added observability policy, quality policy, Android UI legibility notes and Notion sync layer, completed foundation refactor with multi-module root shell, completed `core:logging` TDD cycle and live diagnostics foundation wiring, completed fake domain, typed timeline items, approval shell and richer thread state
- next: start runtime slice for Host Bridge, pairing and local network flow

## Recovery / Rollback
- Если milestone не проходит validation, вернуть repo-local docs в self-consistent state до следующего commit.
- Если platform claim не подтверждается official source, перевести его в tech debt / open question вместо фиксации как decision.
