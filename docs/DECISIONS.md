# Decisions

## 001. Android 16 only

`Stukay` целенаправленно проектируется под Android 16 / API 36.

Причина:

- personal-first use case;
- нет цели поддерживать старые Android versions;
- можно проектировать edge-to-edge, predictive back и modern network behavior с первого дня.

## 002. Pixel 9 Pro XL first

UI проектируется portrait-first под большой экран Pixel 9 Pro XL.

Причина:

- приложение личное;
- нет требования оптимизировать UX под малые и старые устройства;
- можно сразу строить плотный timeline и richer control surfaces.

## 003. Compose-first UI

UI строится вокруг Jetpack Compose.

Причина:

- current repo уже Compose-based;
- target interaction model лучше выражается через state-driven Compose UI.

## 004. Material 3 stable baseline, Expressive in isolation

Stable Material 3 — основной foundation. Material 3 Expressive допускается только в изолированном слое.

Причина:

- expressive APIs в актуальных release notes продолжают меняться;
- нужно не размазывать experimental API по всему приложению.

## 005. Codex App is not the runtime API

Desktop Codex App считается optional handoff surface, а не primary runtime API.

Причина:

- canonical runtime path должен идти через Windows Host Bridge -> codex app-server;
- это лучше для typed interfaces, diagnostics и long-running control flows.

## 006. JetBrains MCP and Android CLI are mandatory workflow tools

JetBrains MCP и `android` CLI считаются частью рабочего контура Codex в этом репозитории.

Причина:

- Kotlin/Compose/Gradle проекты выигрывают от IDE-aware indexes and refactorings;
- Android-specific research и diagnostics лучше делать через Android-native tool surface, а не только через generic shell.

## 007. Harness before feature development

Сначала поднимаются lifecycle stack, docs, observability policy и commands inventory; только потом feature milestones.

Причина:

- иначе будущие Android milestones будут плохо воспроизводимы и плохо отлаживаемы для Codex.

## 008. Current state and target state must stay separate

Во всех repo docs нужно явно разделять то, что уже есть в коде, и то, что только является target direction.

Причина:

- current repo еще template-like;
- смешение fact и intent приведет к ложным assumptions в следующих milestones.

## 009. Pixel render mode policy

Основной режим проектирования — `1344×2992`, но layout не должен разваливаться на `1008×2244`.

Причина:

- это два реальных режима одного целевого устройства;
- здесь полезнее width-constrained and stable layout, чем тяжелая generalized adaptive-навигация под все классы экранов.
