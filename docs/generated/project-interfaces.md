# Project Interfaces

Обновлено: `2026-05-01`

## Current Interfaces

| Interface | Current State | Primary Tools |
| --- | --- | --- |
| Android app entry point | `MainActivity` template Compose screen | Android Studio, Gradle |
| Gradle build interface | `:app` single-module build | Gradle Wrapper |
| Android project introspection | project describe and docs search | Android CLI |
| IDE-aware introspection | modules, run configurations, file/symbol operations | JetBrains MCP |

## Planned Runtime Interfaces

| Interface | Role | Notes |
| --- | --- | --- |
| Windows Host Bridge | canonical transport gateway between phone and local Codex runtime | planned |
| codex app-server | canonical desktop runtime API | planned |
| Pairing / host registration | connect device to host and explain network permission rationale | planned |
| Thread timeline event stream | structured events for message, command, diff, approval, review | planned |
| Diagnostics / exported evidence | app-level status, logs, last errors, recent requests | planned |

## Interface Boundaries To Preserve

- UI layer не должна напрямую знать про raw network transport.
- Repository / data layer не должна зависеть от Compose UI components.
- Diagnostics surface должна читать structured state, а не разбирать случайные log strings.
- JetBrains MCP и Android CLI относятся к engineering workflow, а не к runtime API самого приложения.
