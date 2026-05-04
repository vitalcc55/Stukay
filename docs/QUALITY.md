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
- accessibility semantics and device-side UI discoverability

## Accessibility Baseline

Для `Stukay` accessibility — это не только user-facing nicety, но и часть engineering quality.

Обязательные минимумы для новых или существенно меняемых UI surfaces:

- `contentDescription` на icon-only controls;
- semantics/state text для status, error, loading и approval states;
- стабильные `testTag` или эквивалентные semantics identifiers на критичных controls;
- input validation errors, связанные с конкретным полем;
- device-side smoke, который может пройти ключевой сценарий через accessibility/layout tree, а не только через координаты.

## Working Rule

- Новый milestone не считается завершенным без минимум одного соответствующего verify gate.
- Repo-local docs должны обновляться вместе с quality expectations, а не задним числом.
- Если planned quality surface еще не реализован, это фиксируется как gap, а не маскируется под существующую проверку.
- Если milestone меняет UI, verification должен включать хотя бы один accessibility-aware proof path: Compose semantics, `android layout`, device smoke или их сочетание.
