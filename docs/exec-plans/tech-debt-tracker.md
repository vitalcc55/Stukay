# Tech Debt Tracker

## Open Items

- item: JetBrains MCP доступен как внешний stdio-config, но текущий live runtime может не видеть namespace без перезапуска.
- impact: часть IDE-aware workflows пока приходится документировать и проверять через standalone probes.
- follow-up: после перезапуска Codex App подтвердить native availability `jetbrains` tool namespace.

- item: Repo-local observability policy уже зафиксирована, но runtime implementation (`AppLogger`, log sinks, diagnostics screen) еще отсутствует.
- impact: будущие Android milestones пока нельзя расследовать через app-native logs/diagnostics.
- follow-up: отдельный implementation milestone после fake UI shell.

- item: Quality policy зафиксирована только на уровне docs; aggregated `quality` task еще не создан.
- impact: local CI equivalent пока собирается вручную из нескольких команд.
- follow-up: ввести Gradle-level quality task после выбора lint/static-analysis stack.

## Resolved Items
- item: В репозитории не было lifecycle stack для long-horizon работы.
- resolution: stack создан через `repo-harness-lifecycle` scaffold и привязан к корню репозитория.

- item: Текущий package / namespace `com.vitaly.stukay` не совпадает с целевым `dev.vitalcc.stukay`.
- resolution: foundation refactor перевел namespace, applicationId и source packages на `dev.vitalcc.stukay`.

- item: Репозиторий пока одномодульный и не разделяет `core` / `feature` boundaries.
- resolution: foundation refactor ввел multi-module каркас `:app`, `:core:*`, `:feature:*`.
