# Roadmap

## Purpose

Этот документ держит продуктовую и инженерную очередность на уровне выше active `ExecPlan`.

- `ExecPlan` — текущий исполняемый slice.
- `Documentation.md` — live status и evidence.
- `tech-debt-tracker.md` — открытые gaps и follow-up.
- `ROADMAP.md` — последовательность следующих этапов и их смысл.

## Linked Sources

- [ExecPlan](/C:/Users/v.vlasov/Desktop/Stukay/docs/exec-plans/active/ExecPlan.md)
- [Documentation](/C:/Users/v.vlasov/Desktop/Stukay/Documentation.md)
- [Tech Debt Tracker](/C:/Users/v.vlasov/Desktop/Stukay/docs/exec-plans/tech-debt-tracker.md)
- [Architecture](/C:/Users/v.vlasov/Desktop/Stukay/docs/architecture/application-architecture.md)
- [Quality Policy](/C:/Users/v.vlasov/Desktop/Stukay/docs/QUALITY.md)

## Working Rules

- Roadmap не дублирует текущий `ExecPlan`; он показывает, что идет сразу после него.
- Current state и target state не смешиваются.
- Новый этап не считается начатым только потому, что он описан здесь; source of truth для активной работы — `ExecPlan`.
- Любой runtime milestone обязан усиливать diagnostics и evidence surfaces, а не только добавлять поведение.

## Current State

Сейчас у проекта уже есть:

- multi-module Android shell;
- `Navigation Compose`;
- `core:logging` с runtime events и diagnostics snapshot;
- DDD-lite fake domain для projects / threads / timeline / approvals;
- проверяемый build/test baseline;
- repo-local lifecycle/docs/control-plane surfaces.

Это значит, что foundation больше не является будущим этапом. `Host Bridge MVP` уже локально доказан через живой Android -> Host Bridge -> local Codex runtime path; следующий реальный шаг — `Real thread runtime`, а не очередная перестройка shell.

## Now

### 1. Real thread runtime

Задача:

- список проектов и тредов из runtime;
- чтение истории;
- создание треда;
- отправка prompt;
- streaming ответа;
- interrupt;
- восстановление после reconnect.

Почему сейчас:

- `Host Bridge MVP` уже закрыл transport, pairing, reconnect/degraded path и первый real data path;
- следующий product-value step теперь лежит в real thread lifecycle, а не в transport scaffolding;
- именно здесь transport prove-out начинает окупаться как пользовательский runtime, а не как только host status surface.

Exit criteria:

- Android клиент читает runtime-backed projects/threads;
- shell умеет создавать и открывать реальные треды;
- появляется real prompt/response lifecycle поверх уже доказанного Host Bridge transport.

### 2. Approval safety layer

Задача:

- full pending/resolved approval lifecycle;
- approve once / session / decline / cancel;
- stale/timeout handling;
- audit trail;
- redacted logging.

Почему сейчас:

- approval уже смоделирован fake-доменом, но реальная ценность появляется теперь, когда transport и reconnect/degraded path уже живые;
- после real thread runtime approval surface станет следующим safety-critical слоем.

Exit criteria:

- approvals проходят через реальный runtime path;
- stale/timeout paths честно видны оператору;
- logs и diagnostics сохраняют redacted audit trail.

## Next

### 3. Review / diff / file-change surface

Reference emphasis:

- `farfield`
- `codexUI`
- `PocketDex`

## Later

### 6. Review / diff / file-change surface

- file-change cards
- diff preview
- review findings
- severity / category
- связь review с thread/turn context

Reference emphasis:

- `farfield`
- `codexUI`
- `PocketDex`

### 7. Diagnostics persistence and export

- persistence between restarts
- redacted export
- health summary
- error bundle
- capture by thread / turn / approval
- trace integration

### 8. Notifications and long-running work

- running task notifications
- pending approval notifications
- open thread / stop / open approval actions
- low-noise policy

### 9. Device validation and UX hardening

- emulator smoke
- physical Pixel 9 Pro XL smoke
- both render modes `1344×2992` and `1008×2244`
- IME / edge-to-edge / predictive back validation

### 10. Desktop handoff

- open corresponding desktop context
- focus desktop UI
- optional screenshot/visual confirmation
- handoff without turning desktop UI into runtime API

## Deferred

### Shared core or cross-platform core extraction

`litter` показывает сильный путь через shared Rust core и UniFFI. Для `Stukay` это пока отложено.

Почему deferred:

- сейчас это усложнит delivery;
- текущий продукт ещё не стабилизировал даже Android-side runtime transport;
- без доказанной боли от Kotlin-only implementation такой шаг преждевременен.

### Heavy DI / large infrastructure layer

- Hilt / сложный DI graph
- protocol mapping supersystem
- generalized adaptive-navigation stack

Почему deferred:

- пока manual wiring и локальные seams дешевле и понятнее;
- нужно не усложнять agent-first поддержку раньше реальной необходимости.

### Advanced personal workflow features

- presets
- pinned threads
- history search
- task queue
- reusable prompt actions
- deeper Notion automation
- desktop screenshot workflows

Почему deferred:

- сначала нужно стабилизировать runtime, approvals, diagnostics и host connection.

## Reference Signals

Reference repos использовались не как source of truth, а как направление:

- `relaydex` — local-first host bridge, QR pairing, approvals, stream
- `codexdroid` — layered Android architecture, workspace-first grouping, multiple connections
- `openconnect` — pairing + WSS/tunnel path + real-time events
- `PocketDex` — минимальный remote-control MVP и launcher-oriented workflow
- `farfield` / `codexUI` — monitoring/review/web surface ideas
- `litter` — deferred example для shared core, не для ближайшего этапа

## Progress Model

Как читать roadmap:

- `Now` — следующий обязательный слой
- `Next` — то, что логично идёт сразу после `Now`
- `Later` — важные, но не блокирующие ближайший runtime slice этапы
- `Deferred` — сознательно не берём сейчас, чтобы не раздувать архитектуру
