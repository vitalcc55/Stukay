# Official Sources Review

Обновлено: `2026-05-01`

Этот документ фиксирует official/primary sources, на которые опираются repo-local Android decisions и workflow guidance.

## Android Platform

- Android 16 summary:
  - <https://developer.android.com/about/versions/16/summary>
- Android 16 behavior changes:
  - <https://developer.android.com/about/versions/16/behavior-changes-16>
- Local network permission / protections:
  - <https://developer.android.com/privacy-and-security/local-network-permission>
- Progress-centric notifications:
  - <https://developer.android.com/about/versions/16/features/progress-centric-notifications>
- Predictive back navigation:
  - <https://developer.android.com/guide/navigation/navigation-event/handle-back>
- App architecture guide:
  - <https://developer.android.com/topic/architecture>
- State hoisting in Compose:
  - <https://developer.android.com/develop/ui/compose/state-hoisting>
- Compose UI testing:
  - <https://developer.android.com/develop/ui/compose/testing>
- Gradle-managed devices:
  - <https://developer.android.com/studio/test/managed-devices>
- Logcat:
  - <https://developer.android.com/studio/debug/logcat>
- AndroidX Tracing:
  - <https://developer.android.com/jetpack/androidx/releases/tracing>

## Compose / Material

- Material Design 3 in Compose:
  - <https://developer.android.com/develop/ui/compose/designsystems/material3>
- Compose Material 3 release notes:
  - <https://developer.android.com/jetpack/androidx/releases/compose-material3>
- `ExperimentalMaterial3ExpressiveApi` reference:
  - <https://developer.android.com/reference/kotlin/androidx/compose/material3/ExperimentalMaterial3ExpressiveApi>

## Build / Tooling

- Android Gradle Plugin 9.2.0 release notes:
  - <https://developer.android.com/build/releases/agp-9-2-0-release-notes>
- Android Gradle Plugin 9.0.x release notes:
  - <https://developer.android.com/build/releases/agp-9-0-0-release-notes>
- Gradle version catalogs:
  - <https://docs.gradle.org/current/userguide/version_catalogs.html>
- Gradle dependency locking:
  - <https://docs.gradle.org/current/userguide/dependency_locking.html>
- Gradle convention plugins:
  - <https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html>

## Repo-specific Working Surfaces

- Android CLI docs search and project describe were used as Android-native verification surfaces.
- JetBrains MCP Server was used as IDE-aware verification surface for modules and run configuration.
