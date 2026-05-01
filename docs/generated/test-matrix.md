# Test Matrix

Обновлено: `2026-05-01`

## Current Matrix

| Layer | Current Surface | Command | Status |
| --- | --- | --- | --- |
| Lifecycle harness | lifecycle stack validation | `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .` | verified |
| Build | debug assemble | `.\gradlew.bat :app:assembleDebug --console=plain` | verified |
| JVM tests | template unit tests | `.\gradlew.bat :app:testDebugUnitTest --console=plain` | verified |
| Android instrumented tests | template `androidTest` exists | `.\gradlew.bat :app:connectedDebugAndroidTest --console=plain` | not_yet_verified |
| Lint | debug lint | `.\gradlew.bat :app:lintDebug --console=plain` | not_yet_verified |

## Target Matrix

| Layer | Purpose | Planned Surface |
| --- | --- | --- |
| JVM unit tests | reducers, mappers, parsers, protocol helpers | `src/test` |
| Compose UI tests | screen semantics, state rendering, actions | `src/androidTest` and selected JVM-friendly UI tests |
| Instrumented tests | permissions, notifications, lifecycle, network constraints | Gradle-managed device + physical Pixel 9 Pro XL |
| Contract tests | Host Bridge and codex app-server protocol | JVM test layer |
| Manual smoke | end-to-end with real device and Windows Host Bridge | Pixel 9 Pro XL |
| Diagnostics validation | logs export, status surfaces, evidence capture | app diagnostics screen + exported JSONL |

## Working Rule

- Не объявлять новый test surface `supported`, пока для него нет documented command и ожидаемого artifact path.
- Для Android-specific behavior сначала выбирай наименьший достаточный слой:
  - JVM test для pure logic,
  - Compose/instrumented test для Android framework behavior,
  - manual smoke только там, где нужен реальный device/runtime.
