# Stukay

`Stukay` — personal-first Android 16 / Pixel 9 Pro XL приложение, задуманное как мобильная control surface для локального Codex runtime на Windows.

Сейчас репозиторий находится в phase `post-foundation shell`. Это означает:

- template уже вытеснен multi-module shell;
- repo-local lifecycle stack, architecture direction, observability policy и process docs являются source of truth;
- следующий milestone должен идти в runtime slice, а не возвращаться к базовой перестройке shell.

## Current State

- multi-module graph:
  - `:app`
  - `:core:model`
  - `:core:logging`
  - `:core:design`
  - `:feature:projects`
  - `:feature:thread`
  - `:feature:settings`
  - `:feature:diagnostics`
- Gradle Wrapper `9.4.1`;
- Android Gradle Plugin `9.2.0`;
- Kotlin `2.2.10`;
- Compose BOM `2026.02.01`;
- `minSdk = 36`, `targetSdk = 36`, `compileSdk = 36.1`;
- current package / namespace: `dev.vitalcc.stukay`;
- root shell уже включает `Projects`, `Project`, `Thread`, `Settings`, `Diagnostics`;
- runtime logging и diagnostics snapshot уже работают;
- typed fake domain и approval shell уже реализованы.

## Target Direction

- Android 16 / API 36 only;
- Pixel 9 Pro XL portrait-first;
- primary display mode `1344×2992`, but layouts must remain stable on `1008×2244`;
- Jetpack Compose;
- Material 3 with isolated Material 3 Expressive experimentation;
- Windows Host Bridge -> codex app-server as canonical runtime API;
- structured timeline, approvals, review and diagnostics surfaces;
- JetBrains MCP + Android CLI as mandatory Codex workflow tools.

## Primary Docs

- [AGENTS.md](/C:/Users/v.vlasov/Desktop/Stukay/AGENTS.md)
- [Prompt.md](/C:/Users/v.vlasov/Desktop/Stukay/Prompt.md)
- [Implement.md](/C:/Users/v.vlasov/Desktop/Stukay/Implement.md)
- [Documentation.md](/C:/Users/v.vlasov/Desktop/Stukay/Documentation.md)
- [docs/architecture/index.md](/C:/Users/v.vlasov/Desktop/Stukay/docs/architecture/index.md)
- [docs/generated/stack-inventory.md](/C:/Users/v.vlasov/Desktop/Stukay/docs/generated/stack-inventory.md)
- [docs/generated/commands.md](/C:/Users/v.vlasov/Desktop/Stukay/docs/generated/commands.md)
- [docs/DECISIONS.md](/C:/Users/v.vlasov/Desktop/Stukay/docs/DECISIONS.md)
- [docs/QUALITY.md](/C:/Users/v.vlasov/Desktop/Stukay/docs/QUALITY.md)
- [docs/observability/logging-and-diagnostics.md](/C:/Users/v.vlasov/Desktop/Stukay/docs/observability/logging-and-diagnostics.md)
- [docs/notion/PROJECT_SYNC.md](/C:/Users/v.vlasov/Desktop/Stukay/docs/notion/PROJECT_SYNC.md)

## Validation Snapshot

Проверено 2026-05-02:

- lifecycle validator проходит без contract failures;
- `android describe --project_dir .` распознает проект и его APK outputs;
- `.\gradlew.bat :core:model:testDebugUnitTest :feature:projects:testDebugUnitTest :feature:thread:testDebugUnitTest :core:logging:testDebugUnitTest :app:assembleDebug :app:testDebugUnitTest` проходит успешно;
- `codex mcp get jetbrains` показывает рабочий stdio-config для Android Studio MCP.
