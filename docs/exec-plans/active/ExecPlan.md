# ExecPlan

## Active Milestone

Текущий активный milestone исполнения:

- [Host Bridge MVP Plan](/C:/Users/v.vlasov/Desktop/Stukay/docs/exec-plans/active/host-bridge-mvp-plan.md)

Этот файл остается каноническим lifecycle pointer для репозитория. Детализированный исполняемый план, checklist по этапам и `Stage Report` ведутся в `host-bridge-mvp-plan.md`.

## Why This File Exists

- `AGENTS.md`, `ROADMAP.md` и lifecycle stack по-прежнему ссылаются на `docs/exec-plans/active/ExecPlan.md` как на source of truth для активной работы.
- Чтобы не ломать этот контракт, `ExecPlan.md` теперь указывает на фактический active milestone document вместо хранения устаревшего slice plan.

## Current Execution Contract

- milestone: `Host Bridge MVP`
- branch: `codex/host-bridge-mvp`
- implementation_status: `not_started`
- progress_tracking:
  - checklist by milestone
  - `Stage Report` after each completed stage
  - checkpoint sync in `.tmp/.codex/task_state/latest.*`

## Locked Decisions

- `transport = http_json`
- `ws` / `wss` = explicit unsupported fast-fail paths
- cleartext for MVP is allowed only through explicit Android opt-in plus strict runtime endpoint validation
- first required runtime payload = `host health/status + app/list count`
- allowed host classes = `RFC1918 + .local + 100.64/10`
- `169.254/16` excluded unless separately justified
- no public tunnel / internet success path in this milestone
- no full real thread runtime in this milestone

## Research Substrate

Исследовательская подложка для этого milestone:

- [Runtime Slice Host Bridge Research](/C:/Users/v.vlasov/Desktop/Stukay/docs/exec-plans/active/runtime-slice-host-bridge-research.md)

## Operator Rule

Если milestone был прерван, продолжение начинается из:

1. `docs/exec-plans/active/host-bridge-mvp-plan.md`
2. `.tmp/.codex/task_state/latest.md`
3. `.tmp/.codex/task_state/latest.json`
