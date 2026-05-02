# Application Architecture

## Product Direction

`Stukay` проектируется не как universal Android client, а как personal-first Android 16 / Pixel 9 Pro XL control surface для локального Codex runtime на Windows.

Это означает:

- backward compatibility не является проектной целью;
- UI можно проектировать portrait-first под большой экран Pixel 9 Pro XL;
- приложение не должно выглядеть как generic chat client;
- runtime architecture должна обслуживать projects, threads, approvals, review, diagnostics и desktop handoff.

## Current State

- Репозиторий уже переведен на multi-module foundation: `:app`, `:core:*`, `:feature:*`.
- `MainActivity` и `StukayApp` уже поднимают edge-to-edge root shell вместо template greeting.
- current package / namespace: `dev.vitalcc.stukay`.
- `Projects`, `Project`, `Thread`, `Settings` и `Diagnostics` уже существуют как screen shell.
- `Diagnostics` уже питается от минимального runtime logging слоя, но fake domain и typed timeline ещё не реализованы.

## Target State

### Platform

- Android 16 / API 36 only.
- Pixel 9 Pro XL as primary device.
- Compose-first UI.
- Material 3 as stable base.
- Material 3 Expressive использовать изолированно и точечно, потому что expressive APIs продолжают меняться между stable и alpha ветками.

### Runtime API

- Android app <-> Windows Host Bridge.
- Windows Host Bridge <-> codex app-server.
- Codex App desktop window — optional handoff surface, не primary API contract.

### UI Model

Ключевые surfaces:

- Projects dashboard
- Project thread lists
- Active thread timeline
- Approval actions
- Review findings
- Host status
- Diagnostics

Timeline должен быть typed surface, а не plain chat transcript. Минимальные item families:

- user prompt
- assistant response
- command execution
- file change / diff
- approval request
- review finding
- status / error

### Layering

Предпочтительная application structure для следующих milestones:

- `ui/` — screens, state rendering, callbacks
- `domain/` — use cases, orchestration
- `data/` — Host Bridge, codex app-server, persistence
- `design/` — theme, expressive layer, reusable timeline/status components
- `core/` — model, logging, network, security, util

Foundation layer уже переведен на feature-bounded modules. Следующие этапы должны наполнять эти boundaries реальными моделями, use cases и logging contracts, а не откатываться обратно к monolithic app module.

## Platform Clarifications

### Edge-to-edge

Android 16 требует корректного edge-to-edge поведения для targetSdk 36+, поэтому screen layouts, bottom composer и diagnostics panels сразу проектируются с учетом system bars и IME insets.

### Predictive Back

Back behavior должен быть частью UX contract:

- modal / sheet / diff close before screen exit
- Thread -> Project
- Project -> Projects
- Settings -> previous screen

### Local Network Access

Для `Stukay` это критично, потому что Host Bridge находится в локальной или частной сети. Repo docs должны учитывать local network protections и объяснение permission rationale. При этом важно не перепутать `target state` и `current code`: permission and discovery flow еще не реализованы.

### Notifications

Progress-centric notifications полезны для long-running Codex tasks, но относятся к future runtime milestone, а не к текущему bootstrap.

## Architecture Rule

Пока не появится real Host Bridge, сначала строим:

1. docs and process,
2. diagnostics and logging policy,
3. fake UI shell,
4. only then runtime integration.
