# Quality Policy

## Purpose

`Stukay` должен получать quality gates раньше feature complexity. Пока проект еще template-like, policy фиксируется docs-first, а затем превращается в реальные Gradle tasks и enforcement.

## Current Verified Surfaces

- lifecycle stack validator
- `.\gradlew.bat :app:assembleDebug --console=plain`
- `.\gradlew.bat :app:testDebugUnitTest --console=plain`
- `android describe --project_dir .`
- `codex mcp get jetbrains`

## Target Quality Stack

- Android Lint
- Detekt
- formatting / style policy
- unit tests
- Compose UI tests
- instrumented tests on managed device and physical Pixel 9 Pro XL
- dependency hygiene
- diagnostics and exported evidence

## Working Rule

- Новый milestone не считается завершенным без минимум одного соответствующего verify gate.
- Repo-local docs должны обновляться вместе с quality expectations, а не задним числом.
- Если planned quality surface еще не реализован, это фиксируется как gap, а не маскируется под существующую проверку.
