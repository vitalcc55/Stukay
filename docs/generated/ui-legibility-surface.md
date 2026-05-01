# UI Legibility Surface

Обновлено: `2026-05-01`

`Stukay` — Android app, поэтому primary UI legibility surface здесь не browser/playwright, а Android-native stack:

- Android Studio preview and inspections
- JetBrains MCP for IDE-aware file/symbol/context access
- emulator / physical device runs
- Android CLI project/docs/runtime helpers
- future diagnostics exports and screenshots

## Current Surfaces

- `MainActivity` and Compose theme can be inspected in Android Studio.
- JetBrains MCP confirms run configuration `app`.
- Gradle assemble/test surface is verified.

## Planned Surfaces

- Android 16 emulator smoke route
- Pixel 9 Pro XL manual smoke route
- diagnostics export bundle
- future screenshot / layout capture workflow

## Rule

Не пытаться механически переносить browser-oriented UI harness practices в Android repo. Здесь legibility строится через Android Studio, Gradle, Android CLI, device/emulator и structured diagnostics.
