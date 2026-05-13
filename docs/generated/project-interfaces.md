# Project Interfaces

Обновлено: `2026-05-13`

## Current Interfaces

| Interface | Current State | Primary Tools |
| --- | --- | --- |
| Android app entry point | `MainActivity` + `StukayApp` multi-module shell | Android Studio, Gradle |
| Gradle build interface | multi-module `:app` root build + `:core:logging` unit tests | Gradle Wrapper |
| Android project introspection | project describe and docs search | Android CLI |
| IDE-aware introspection | modules, run configurations, file/symbol operations | JetBrains MCP |
| Runtime observability | Logcat + in-memory log store + DiagnosticsScreen snapshot | core logging layer |
| Runtime-backed thread shell | Host Bridge helper + runtime-derived projects/threads + foreground session state + paged large-thread history | app runtime + feature project/thread layers |

## Planned Runtime Interfaces

| Interface | Role | Notes |
| --- | --- | --- |
| Windows Host Bridge | canonical transport gateway between phone and local Codex runtime | real `http_json` MVP path implemented through Android client plus Windows helper |
| codex app-server | canonical desktop runtime API | reached through Windows Host Bridge helper over local stdio |
| Pairing / host registration | connect device to host and explain network permission rationale | payload import + local-network rationale implemented |
| Thread timeline event stream | active foreground SSE stream with typed turn/item/approval events plus paged older-history hydration | helper + Android client implemented for one active thread |
| Diagnostics / exported evidence | app-level status, logs, last errors, current thread/turn/approval snapshot | runtime-backed diagnostics summary implemented; export still planned |

## Interface Boundaries To Preserve

- UI layer не должна напрямую знать про raw network transport.
- Repository / data layer не должна зависеть от Compose UI components.
- Diagnostics surface должна читать structured state, а не разбирать случайные log strings.
- JetBrains MCP и Android CLI относятся к engineering workflow, а не к runtime API самого приложения.
