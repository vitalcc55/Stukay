# UI Legibility Surface

Обновлено: `2026-05-04`

`Stukay` — Android app, поэтому primary UI legibility surface здесь не browser/playwright, а Android-native stack:

- Android Studio preview and inspections
- JetBrains MCP for IDE-aware file/symbol/context access
- emulator / physical device runs
- Android CLI project/docs/runtime helpers
- future diagnostics exports and screenshots
- accessibility / semantics tree via Compose + `android layout`

## Current Surfaces

- `MainActivity` and Compose theme can be inspected in Android Studio.
- JetBrains MCP confirms run configuration `app`.
- Gradle assemble/test surface is verified.
- device-side smoke уже использует screenshots плюс `android layout` tree.
- базовые icon-only navigation controls уже имеют `contentDescription`.

## Planned Surfaces

- Android 16 emulator smoke route
- Pixel 9 Pro XL manual smoke route
- diagnostics export bundle
- future screenshot / layout capture workflow
- stable `testTag` / semantics identifiers for critical controls
- status/error semantics for connection, approval, degraded and runtime states
- accessibility-aware Compose/instrumented proof for primary flows

## Rule

Не пытаться механически переносить browser-oriented UI harness practices в Android repo. Здесь legibility строится через Android Studio, Gradle, Android CLI, device/emulator, structured diagnostics и accessibility/layout tree.

Coordinate-only tapping допускается только как fallback. Для основных flows проект должен постепенно переходить к automation-grade discoverability через semantics tree.
