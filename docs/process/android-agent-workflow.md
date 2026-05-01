# Android Agent Workflow

## Purpose

Этот документ фиксирует, как Codex должен работать именно в Android-репозитории `Stukay`, где доступны Android Studio + JetBrains MCP Server и `android` CLI.

## Source Hierarchy

1. Repo-local docs and lifecycle artifacts.
2. Gradle Wrapper as canonical build/verify surface.
3. JetBrains MCP for IDE-aware context.
4. Android CLI for Android-specific docs and runtime/tooling surfaces.
5. General web research only when official docs or Android CLI docs search недостаточны.

## When To Use JetBrains MCP

Используй JetBrains MCP, когда нужен IDE-aware context:

- список модулей и run configurations;
- symbol/file navigation;
- inspections и file problems;
- rename refactoring;
- форматирование файла;
- проверка, какой файл реально открыт в IDE;
- понимание Android/Kotlin codebase через индексы, а не только через `rg`.

### Why It Matters Here

`Stukay` — это Kotlin/Compose/Gradle проект. Для таких репозиториев IDE индексы часто точнее plain grep в вопросах:

- symbol ownership,
- Compose usage sites,
- Gradle module boundaries,
- generated/test source sets,
- refactoring safety.

## When To Use Android CLI

Используй `android` CLI, когда нужен Android-specific workflow:

- `android docs search` для актуальных Android docs;
- `android describe --project_dir .` для project metadata and APK outputs;
- SDK/emulator/device workflows;
- later: layout, screen, runtime diagnostics and smoke helpers.

### Why It Matters Here

`android` CLI дает Android-native control plane, который не зависит от GUI IDE и не сводится к raw Gradle commands. Это особенно полезно для:

- Android 16 behavior research,
- managed emulator/device flows,
- repeatable Android diagnostics,
- project-aware artifact discovery.

## Working Loop For Stukay

1. Прочитать `Prompt.md`, `ExecPlan.md`, `Documentation.md`, `AGENTS.md`.
2. Если задача касается Android platform behavior, сначала обновить факты через official Android docs или `android docs search`.
3. Если задача касается структуры проекта, navigation, symbols или refactoring, сначала использовать JetBrains MCP.
4. Делать минимальный diff на milestone.
5. Проверять Gradle / lifecycle / Android CLI surfaces.
6. Обновлять status surfaces и commit по milestone.

## Guardrails

- JetBrains MCP и Android CLI относятся к engineering workflow, а не к runtime architecture приложения.
- Если current runtime не подхватил native `jetbrains` namespace, это не отменяет сам MCP config; просто используй fallback verification и зафиксируй limitation.
- Не документируй Android behavior как факт, если он не проверен по official source.
