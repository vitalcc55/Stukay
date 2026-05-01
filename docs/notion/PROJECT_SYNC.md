# Notion Project Sync

## Current Project Record

- Database: `Проекты`
- Page title: `Stukay`
- Notion URL: <https://app.notion.com/p/353f585cf06881c682d5ccb7437ada86>
- Record code: `PRJ-3`

## Sync Rule

Repo-local docs остаются primary source of truth. Notion нужен как operational/project surface, а не как замена `Prompt.md`, `ExecPlan.md` или `Documentation.md`.

Обновлять запись в Notion нужно, когда:

- закрыт milestone commit;
- materially changed `Prompt.md`;
- materially changed `docs/DECISIONS.md`;
- materially changed current phase / next step проекта.

## Minimum Fields To Keep Fresh

- `Состояние`
- `Приоритет`
- `Следующий шаг`

## What Stays In The Repo

- full architecture direction
- command inventory
- observability policy
- active exec plan
- review/status history

## What Goes To Notion

- short project summary
- current phase
- next step
- high-level state for cross-project visibility

## Failure Rule

Если Notion update blocked by workspace policy or connector issue:

- repo-local docs still move forward;
- blocker фиксируется в `Documentation.md`;
- next successful Notion sync must reconcile the gap.
