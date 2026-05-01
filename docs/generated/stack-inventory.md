# Stack Inventory

Обновлено: `2026-05-01`

## Scope

Этот документ описывает фактический current state репозитория и рядом фиксирует target direction, если она уже принята, но еще не реализована в коде.

## Current Repository State

| Area | Current State |
| --- | --- |
| Repo type | Android app repository, single module |
| Root module graph | `:app` |
| Build system | Gradle Wrapper |
| Gradle version | `9.4.1` |
| Android Gradle Plugin | `9.2.0` |
| Kotlin | `2.2.10` |
| Compose dependency model | Compose BOM `2026.02.01` |
| Current namespace | `com.vitaly.stukay` |
| Current applicationId | `com.vitaly.stukay` |
| Current minSdk / targetSdk | `36 / 36` |
| Current compileSdk | `36.1` |
| Java source / target compatibility | `11 / 11` |
| UI stack | Jetpack Compose + Material 3 |
| Current app entry point | `app/src/main/java/com/vitaly/stukay/MainActivity.kt` |
| Current tests | template unit test + template instrumented test |

## External Tool Surfaces

| Surface | Status | Notes |
| --- | --- | --- |
| Android Studio | available | `AndroidStudio2025.3.4` |
| JetBrains MCP Server | configured | Android Studio plugin loaded, Codex side uses stdio config |
| Android CLI | available | `0.7.15326717` |
| Gradle Wrapper | available | canonical build / verify entry point |
| Notion plugin | available | workspace search worked; project page will be created in later milestone |

## Evidence Snapshot

- `android describe --project_dir .` confirms module `:app`, variants `debug` and `release`, and existing `app-debug.apk`.
- JetBrains MCP probe confirms IDE-side modules:
  - `Stukay`
  - `Stukay.app`
  - `Stukay.app.main`
  - `Stukay.app.unitTest`
  - `Stukay.app.androidTest`
- JetBrains MCP probe confirms run configuration:
  - `app` — Android launch/debug configuration
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` passed on `2026-05-01`.

## Target Direction

Принятый target direction на уровне docs и future milestones:

- Android 16 / API 36 only.
- Pixel 9 Pro XL portrait-first.
- Compose-first UI.
- Material 3 as stable base; Material 3 Expressive in isolated experimental layer.
- Host Bridge -> codex app-server as runtime API.
- Structured logging, diagnostics and exported evidence from the beginning.

## Known Gaps Between Current And Target

- `current package / namespace` не совпадает с target `dev.vitalcc.stukay`.
- Репозиторий пока одномодульный.
- Нет repo-local quality task, lint policy, observability implementation или diagnostics screen.
- Нет yet product architecture code for projects / threads / approvals / review.
