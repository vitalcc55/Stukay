# ExecPlan

## Goal

Зафиксировать завершение и merge `Real Thread Runtime + Approval Safety Layer`, синхронизировать lifecycle/control-plane после принятого milestone и подготовить репозиторий к следующему активному slice `Review / diff / file-change surface`.

## Constraints

- не переоткрывать `Real Thread Runtime + Approval Safety Layer` под видом дополнительного runtime refactor;
- не смешивать post-merge control-plane sync с началом `Review / diff / file-change surface` реализации;
- сохранить `real-thread-runtime-approval-layer-plan.md` как completed execution ledger и evidence milestone;
- source of truth для следующего активного слоя должен оставаться в `ExecPlan.md`, а не только в `ROADMAP.md` или `Documentation.md`;
- accessibility baseline остаётся обязательной частью следующего UI/runtime slice, а не deferred polish;
- dedicated live approval device repro остаётся узким verification follow-up и не должен раздуваться в новый transport/runtime milestone.

## Milestones

1. Подтвердить принятие и merge `Real Thread Runtime + Approval Safety Layer` по diff-scoped review, verification и device proof.
2. Синхронизировать `Documentation.md`, `ROADMAP.md`, `.tmp/.codex/task_state/latest.*`, `PROJECT_SYNC.md` и changelog на post-merge state.
3. Передать следующий активный слой как `Review / diff / file-change surface`, не начиная его реализацию в этом же цикле.

## Acceptance Criteria

- `Real Thread Runtime + Approval Safety Layer` принят как завершенный milestone без новых merge-blocking findings.
- `Documentation.md` больше не описывает merge-flow как pending.
- `ROADMAP.md`, `PROJECT_SYNC.md` и `.tmp/.codex/task_state/latest.*` больше не указывают `Real Thread Runtime` как ещё не начатый шаг.
- `real-thread-runtime-approval-layer-plan.md` остаётся доступным как completed execution ledger для завершенного milestone.
- следующий активный слой явно сформулирован как `Review / diff / file-change surface`.
- accessibility baseline остаётся обязательной частью следующего active slice: semantics/testTag/state-description baseline для review/diff/thread-status surfaces.

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

- Проверить, что merged milestone действительно закрывает runtime-backed thread/approval слой, а не только host summary transport.
- Проверить, что lifecycle validator проходит без `fail`.
- Проверить, что next-step surfaces согласованы между `ROADMAP`, `Documentation`, `latest.*` и `PROJECT_SYNC`.

## Decision Log

- `Real Thread Runtime + Approval Safety Layer` считается завершенным milestone и после merge уходит в history/evidence, а не остаётся active implementation target.
- Следующий обязательный слой — `Review / diff / file-change surface`.
- Security hardening по TLS/public path и at-rest credential storage остаётся follow-up, а не blocker завершенного runtime milestone.
- Accessibility остаётся обязательным engineering baseline для всех следующих UI/runtime milestones.

## Progress Log

- status: `real_thread_runtime_approval_layer_merged`
- done:
  - `Real Thread Runtime + Approval Safety Layer` реализован, проверен и принят по diff-scoped review, emulator и physical Pixel surfaces.
  - `main` получил runtime-backed projects/thread/turn/approval path и large-thread history model поверх Host Bridge helper.
  - code verification green: Gradle gate, Python helper suite, Android CLI describe, lifecycle validator и JetBrains MCP config probe.
  - lifecycle/docs drift закрыт, следующий milestone переведен на `Review / diff / file-change surface`.
  - dedicated live approval repro оставлен как честный неблокирующий follow-up.
- next:
  - открыть новый detailed active plan под `Review / diff / file-change surface`
  - после этого начать следующий implementation branch/slice
  - встроить accessibility baseline в scope, acceptance criteria и verification нового active plan

## Recovery / Rollback

- Если после финальной sync-правки lifecycle validator или post-merge verification ломаются, milestone не считается закрытым до исправления control-plane drift.
- Если blocker окажется только в docs/control-plane, исправлять его без расширения review/runtime scope.
