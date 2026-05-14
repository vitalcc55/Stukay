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
- Любой новый UI/runtime milestone обязан одновременно усиливать accessibility surface: не только pixels/screenshots, но и понятную semantics tree для device-side automation и реального пользователя.

## Accessibility Baseline

Начиная со следующего активного слоя accessibility больше не считается optional polish.

Обязательный baseline для новых экранов и существенно меняемых surfaces:

- icon-only actions имеют `contentDescription`;
- status, loading, approval и error states имеют semantics/state text;
- критичные controls получают стабильные `testTag` или эквивалентные semantics identifiers;
- validation errors привязываются к конкретному input, а не остаются неструктурным текстом;
- device-side smoke и `android layout` должны уметь находить ключевые элементы не только по координатам.

## Current State

Сейчас у проекта уже есть:

- multi-module Android shell;
- `Navigation Compose`;
- `core:logging` с runtime events и diagnostics snapshot;
- DDD-lite fake domain для projects / threads / timeline / approvals;
- проверяемый build/test baseline;
- repo-local lifecycle/docs/control-plane surfaces.

Это значит, что foundation больше не является будущим этапом. `Host Bridge MVP`, `Real Thread Runtime` и `Approval safety layer` уже закрыты на `main` через живой Android -> Host Bridge -> local Codex runtime path; следующий реальный шаг — richer review/file-change surface, а не очередная перестройка transport или shell.

## Now

### 3. Review / diff / file-change surface

Задача:

- file-change cards;
- diff preview;
- richer command/result surface;
- review findings и severity;
- связь review/file changes с thread/turn context;
- более полный approval context, чем короткая карточка metadata.

Почему сейчас:

- runtime-backed thread lifecycle уже живой, значит следующий product-value слой лежит не в transport, а в понимании того, что именно сделал Codex;
- approvals уже доходят до телефона, но оператору всё ещё не хватает контекста изменений и результатов команд;
- это естественное продолжение живого thread surface, а не новая transport-фаза.

Exit criteria:

- оператор видит file-change и review context рядом с реальным thread/turn lifecycle;
- review/diff surface работает поверх уже живого runtime path, а не через fake data;
- новые review/file-change/status controls получают automation-grade accessibility baseline.

## Next

### 4. Diagnostics persistence and export

- persistence between restarts
- redacted export
- health summary
- error bundle
- capture by thread / turn / approval
- trace integration

## Later

### 5. Notifications and long-running work

- running task notifications
- pending approval notifications
- open thread / stop / open approval actions
- low-noise policy

### 6. Device validation and UX hardening

- emulator smoke
- physical Pixel 9 Pro XL smoke
- both render modes `1344×2992` and `1008×2244`
- IME / edge-to-edge / predictive back validation
- accessibility tree / semantics validation for critical controls and status surfaces

### 7. Desktop handoff

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
