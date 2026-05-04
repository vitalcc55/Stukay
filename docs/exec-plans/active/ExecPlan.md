# ExecPlan

## Goal

Зафиксировать завершение `Host Bridge MVP`, синхронизировать lifecycle/control-plane артефакты после branch-wide review и подготовить репозиторий к следующему активному slice `Real Thread Runtime`.

## Constraints

- не переоткрывать `Host Bridge MVP` под видом нового transport refactor;
- не смешивать post-merge docs sync с началом `Real Thread Runtime` реализации;
- сохранить `host-bridge-mvp-plan.md` как completed execution ledger, а не потерять evidence milestone;
- source of truth для следующего активного слоя должен оставаться в `ExecPlan.md`, а не только в `ROADMAP.md`.

## Milestones

1. Подтвердить merge-readiness `Host Bridge MVP` по branch-wide review, code verification и lifecycle/doc sync.
2. Синхронизировать `Documentation.md`, `.tmp/.codex/task_state/latest.*`, `PROJECT_SYNC.md` и changelog на post-MVP state.
3. Передать следующий активный слой как `Real Thread Runtime`, не начиная его реализацию в этом же цикле.

## Acceptance Criteria

- `Host Bridge MVP` принят как завершенный milestone без новых branch-blocking findings.
- `Documentation.md` больше не описывает branch-wide review как pending.
- `.tmp/.codex/task_state/latest.*` больше не держат `next = final review loop`; следующий шаг в них — `Real Thread Runtime`.
- `PROJECT_SYNC.md` и repo-local docs согласованы по следующему milestone.
- `host-bridge-mvp-plan.md` и `host-bridge-mvp-proof.md` остаются доступными как evidence для завершенного milestone.

## Validation Commands

```text
git diff --check
python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .
android describe --project_dir .
codex mcp get jetbrains
.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p "test_*.py"
```

## Review Loop

- Проверить, что `Host Bridge MVP` действительно закрывает только transport/runtime-summary слой и не делает ложных claim про `real thread runtime`.
- Проверить, что lifecycle validator проходит без `fail`.
- Проверить, что next-step surfaces согласованы между `ROADMAP`, `Documentation`, `latest.*` и `PROJECT_SYNC`.

## Decision Log

- `Host Bridge MVP` считается текущим завершенным milestone и после merge уходит в history/evidence, а не остается active implementation target.
- Следующий обязательный слой — `Real Thread Runtime`.
- Security hardening по TLS/public path и at-rest credential storage остается follow-up, а не merge blocker текущего milestone.

## Progress Log

- status: `host_bridge_mvp_merge_ready`
- done:
  - `Host Bridge MVP` реализован и локально доказан по runtime, device и emulator surfaces.
  - branch-wide review по коду завершен без новых branch-blocking findings.
  - code verification green: Gradle gate, Python helper suite, Android CLI describe и JetBrains MCP config probe.
  - lifecycle/docs drift закрыт, следующий milestone переведен на `Real Thread Runtime`.
- next:
  - открыть новый detailed active plan под `Real Thread Runtime`
  - после этого начать следующий implementation branch/slice

## Recovery / Rollback

- Если после финальной sync-правки lifecycle validator или branch-wide verification ломаются, `Host Bridge MVP` не считается готовым к merge и ветка остается открытой до исправления.
- Если blocker окажется только в control-plane/docs, исправлять его без расширения runtime scope.
