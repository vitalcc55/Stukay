# Tech Debt Tracker

## Open Items
- item: Текущий package / namespace `com.vitaly.stukay` не совпадает с целевым `dev.vitalcc.stukay`.
- impact: docs уже описывают target direction, но код пока живет в template namespace.
- follow-up: отдельный refactor milestone после завершения harness/documentation phase.

- item: Репозиторий пока одномодульный и не разделяет `core` / `feature` boundaries.
- impact: архитектурные docs описывают целевую модульность, но enforcement и code boundaries еще не существуют.
- follow-up: переход к multi-module только после стабилизации fake UI shell и control plane.

- item: JetBrains MCP доступен как внешний stdio-config, но текущий live runtime может не видеть namespace без перезапуска.
- impact: часть IDE-aware workflows пока приходится документировать и проверять через standalone probes.
- follow-up: после перезапуска Codex App подтвердить native availability `jetbrains` tool namespace.

## Resolved Items
- item: В репозитории не было lifecycle stack для long-horizon работы.
- resolution: stack создан через `repo-harness-lifecycle` scaffold и привязан к корню репозитория.
