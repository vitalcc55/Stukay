# Logging And Diagnostics

## Purpose

Для `Stukay` observability — это не secondary feature, а рабочий канал между приложением и Codex. Сейчас в репозитории уже есть минимальный runtime logging слой; этот документ фиксирует и текущую реализацию, и target direction следующих этапов.

## Current State

- В приложении уже есть:
  - `AppLogger`
  - `StructuredLogger`
  - `CompositeLogSink`
  - `AndroidLogcatSink`
  - bounded `InMemoryLogStore`
  - `DiagnosticsSummaryProvider`
- Root shell уже пишет runtime events:
  - `app_started`
  - `navigation_changed`
  - `screen_opened`
  - `thread_opened`
  - `diagnostics_opened`
- `DiagnosticsScreen` уже показывает:
  - Android version
  - current route
  - session start
  - total log count
  - latest warning/error
  - recent logs
- Пока нет:
  - persistence
  - export
  - redaction pipeline
  - Room/DataStore storage
  - trace integration

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

Текущий `DiagnosticsScreen` уже показывает:

- app session summary
- Android version
- current route
- recent logs
- latest warning/error

Следующий уровень diagnostics должен добавить:

- app version
- Host Bridge status
- active connection
- current thread / turn
- pending approvals
- recent warnings and errors
- recent logs
- export action for redacted diagnostics

## Storage And Export

Current direction:

- in-memory bounded store only
- no persistence across app restarts
- no export yet

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

Уже доступны:

- recent shell events through DiagnosticsScreen
- latest warning/error summary

Минимальные future workflows:

- capture by `threadId`
- capture by `turnId`
- capture by `approvalId`
- host reconnect summary
- export last errors bundle
- diagnostics snapshot before bug report or milestone handoff
