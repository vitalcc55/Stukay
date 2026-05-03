# Changelog

## 2026-05-01

- Поднят канонический lifecycle stack для long-horizon работы Codex через `repo-harness-lifecycle`.
- Корневой `AGENTS.md` превращен в реальную карту Android-репозитория и рабочего контура Codex.
- Зафиксированы `Prompt.md`, `Implement.md`, `Documentation.md`, active `ExecPlan` и checkpoint policy.
- Добавлена `.gitignore` политика для канонических `.tmp/.codex/task_state/latest.*`.
- Добавлены repo-local Android docs: `README`, stack inventory, commands inventory, test matrix, interfaces map, architecture direction и Android agent workflow.
- В docs зафиксированы роли JetBrains MCP и `android` CLI как обязательных engineering surfaces.
- Добавлены `docs/QUALITY.md`, observability/logging policy, Android UI legibility surface и repo-to-Notion sync policy.
- Проект `Stukay` заведен в Notion database `Проекты` как запись `PRJ-3`.

## 2026-05-02

- Начат первый product milestone.
- Проект переведен на `dev.vitalcc.stukay`.
- Введен multi-module foundation scaffold: `:app`, `:core:*`, `:feature:*`.
- Поднят root shell для `Projects`, `Project`, `Thread`, `Settings`, `Diagnostics`.
- `:app:assembleDebug` и `:app:testDebugUnitTest` проходят после foundation refactor.
- Реализован `core:logging` с TDD: `AppLogger`, `LogEvent`, `LogLevel`, `LogArea`, `CompositeLogSink`, `InMemoryLogStore`, `DiagnosticsSummaryProvider`.
- Root shell теперь пишет runtime events, а `DiagnosticsScreen` показывает живой snapshot recent logs и summary.
- Реализован DDD-lite fake domain: typed projects, threads, timeline items, approval actions и in-memory repositories/use cases.
- `ThreadScreen` теперь поддерживает fake run start/complete и approval resolution, а shell стабилизирован под оба режима рендера Pixel 9 Pro XL.
- Поверх milestone выполнен локальный multi-agent review loop; исправлены найденные баги в lifecycle owner app state, approval semantics, route/diagnostics identity, bounded log truth surface и width-constrained shell rollout.
- Зафиксирован новый active `ExecPlan` и research-подложка для `Host Bridge contract + pairing + local network flow`.
- Добавлены typed `Host Bridge` / pairing / connection models в `core:model`.
- `StukayAppState` переведен на runtime graph вместо прямого создания fake repositories.
- `Settings` получил pairing payload save/connect/reconnect/disconnect flow и Android 16 local-network permission rationale.
- `Projects` теперь показывает lightweight host status signal, а `Diagnostics` — host/connection summary.
- В `AndroidManifest.xml` добавлены `INTERNET`, `ACCESS_NETWORK_STATE` и `NEARBY_WIFI_DEVICES` для текущего local-network slice.
- Добавлены JVM tests для pairing payload parser и host bridge repository state transitions.
- Поверх initial runtime slice fix pass убраны crash-path операции без pairing, выровнен restored permission state, введены runtime adapters поверх fake repositories, добавлен dedicated host/connection diagnostics tail и исключен pairing storage из backup/data-transfer rules.

## 2026-05-03

- `:app` и его tests переведены на `src/*/kotlin`, чтобы выровнять структуру исходников с built-in Kotlin workflow AGP.
- Локальный кеш компилятора `.kotlin/` исключен из репозитория через `.gitignore`.
- Для `SDK 36 Preview + AGP 9.2.0` зафиксирован manifest-scoped `tools:ignore="Instantiatable"` на `MainActivity`, потому что lint дает ложное срабатывание на цепочке наследования `ComponentActivity -> Activity`, тогда как build/test surface остаются корректными.
- `gradle.properties` обновлен до `org.gradle.jvmargs=-Xmx4g`, чтобы уменьшить нестабильность Gradle/Kotlin daemon во время local verification.
- Финальный verification gate по runtime slice теперь включает `:app:lintDebug`, `:app:testDebugUnitTest` и `:app:assembleDebug`.
- Перед merge выровнены README, AGENTS, generated commands/test-matrix/stack-inventory и Notion sync note под фактический post-runtime-contract state.
