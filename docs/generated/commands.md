# Commands Inventory

Обновлено: `2026-05-02`

Этот документ фиксирует только реальные и желательные для Codex entry points. Если команда здесь указана, она должна быть воспроизводимой в текущем repo state или явно помеченной как `planned`.

## Lifecycle / Harness

| Command | Purpose | Status |
| --- | --- | --- |
| `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .` | Проверка канонического lifecycle stack | verified |
| `git status --short` | Проверка чистоты milestone перед commit | verified |

## Gradle / Build

| Command | Purpose | Status |
| --- | --- | --- |
| `.\gradlew.bat :app:assembleDebug --console=plain` | Сборка debug APK | verified |
| `.\gradlew.bat :app:testDebugUnitTest --console=plain` | Запуск JVM unit tests | verified |
| `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain` | Быстрый local confidence loop | verified |
| `.\gradlew.bat :core:logging:testDebugUnitTest --console=plain` | TDD verification для logging core | verified |
| `.\gradlew.bat :app:lintDebug --console=plain` | Android Lint для debug variant | not_yet_verified |

## Android CLI

| Command | Purpose | Status |
| --- | --- | --- |
| `android --version` | Проверка версии Android CLI | verified |
| `android describe --project_dir .` | Описание Android project surface и output artifacts | verified |
| `android docs search "<query>"` | Поиск актуальной Android documentation | verified |

### Android CLI Notes

- `android describe --project_dir .` может быть полезнее запускать с контролируемым capture stdout, если console output выглядит пустым.
- Для platform-sensitive решений сначала используй `android docs search`, а уже потом general web search.

## JetBrains MCP / Android Studio

| Command / Surface | Purpose | Status |
| --- | --- | --- |
| `codex mcp get jetbrains` | Проверка Codex-side MCP config | verified |
| native `jetbrains` MCP tools in live session | IDE-aware modules, run configs, symbol/file navigation, inspections, refactoring | runtime-dependent |

### JetBrains MCP Notes

- Каноническая схема: Codex = MCP client, Android Studio = MCP server.
- Для live Codex workflow предпочитай native `jetbrains` tools, если runtime уже перезапущен и подхватил MCP namespace.
- Если namespace еще не появился в текущем runtime, факт конфигурации все равно проверяется через `codex mcp get jetbrains` и standalone probe.

## Planned Commands

| Command | Purpose | Status |
| --- | --- | --- |
| `.\gradlew.bat quality --console=plain` | Агрегированный local CI equivalent | planned |
| `.\gradlew.bat :app:pixelApi36DebugAndroidTest --console=plain` | managed-device instrumented verification | planned |
| `android emulator ...` | Android 16 emulator automation | planned |
