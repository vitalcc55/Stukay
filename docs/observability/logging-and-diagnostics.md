# Logging And Diagnostics

## Purpose

Для `Stukay` observability — это не secondary feature, а рабочий канал между приложением и Codex. Пока код logging layer еще не создан, этот документ фиксирует каноническую модель, по которой следующие milestones должны его строить.

## Current State

- В приложении нет еще `AppLogger`, локального log store или diagnostics screen.
- Repo already has lifecycle status surfaces and evidence-aware docs, но это еще не runtime observability самого Android app.

## Canonical Logging Model

### Event Shape

Минимальная структура будущего runtime event:

- `timestamp_utc`
- `level`
- `area`
- `event_name`
- `message_human`
- `session_id`
- `task_id`
- `correlation_id`
- domain ids (`hostId`, `threadId`, `turnId`, `approvalId`, `requestId`) по ситуации
- `outcome`
- `error_category`

### Log Areas

- `App`
- `Ui`
- `Navigation`
- `Connection`
- `HostBridge`
- `Codex`
- `Thread`
- `Turn`
- `Approval`
- `Review`
- `Storage`
- `Security`
- `Performance`

### Log Levels

- `verbose` — high-frequency debug detail, never the main truth surface
- `debug` — useful implementation detail
- `info` — important lifecycle transitions
- `warn` — retries, degraded mode, recoverable faults
- `error` — operation failed or scenario cannot continue

## Redaction Rules

Нельзя логировать по умолчанию:

- токены
- pairing secrets
- auth headers
- private keys
- raw full prompts
- raw full assistant output
- full file diffs
- raw protocol payloads

Допустимо логировать:

- IDs и hashes
- operation type
- payload size
- relative file path
- duration
- retry count
- status transition

## Diagnostics Surface

Будущий `DiagnosticsScreen` должен показывать:

- app version
- Android version
- Host Bridge status
- active connection
- current thread / turn
- pending approvals
- recent warnings and errors
- recent logs
- export action for redacted diagnostics

## Storage And Export

Target direction:

- structured runtime events
- local store for recent logs
- exported JSONL for investigations
- separate policy by build type

## Build Type Policy

- `debug` — full diagnostics and verbose runtime detail
- `profile` — info/warn/error plus tracing
- `release` — low-noise diagnostics, redacted exports only

## Tracing

Логи не должны заменять tracing. Для expensive flows полезны отдельные trace sections:

- `decodeCodexEvent`
- `renderThreadTimeline`
- `applyThreadEvent`
- `saveLogBatch`
- `reconnectHostBridge`

## Investigation Workflows

Минимальные future workflows:

- capture by `threadId`
- capture by `turnId`
- capture by `approvalId`
- host reconnect summary
- export last errors bundle
- diagnostics snapshot before bug report or milestone handoff
