# Documentation.md

## Current Milestone Status
- current: `Host Bridge MVP` функционально закрыт локально; Stage 5 verification/proof gates завершены и ветка готова к финальному branch-wide review vs `main`.
- done: предыдущий runtime-contract slice закрыт; в Stage 1 зафиксированы `http_json`-only transport semantics, explicit unsupported `ws/wss` path, `Degraded` + `HostRuntimeSummary`, `100.64/10` в allowlist, reject для `169.254/16`, explicit Android cleartext opt-in через `network_security_config`, и правдивая permission-gated stub semantics без false-ready для private LAN. В Stage 2 добавлен stdlib-only helper под `tools/hostbridge`, который поднимает локальный `codex app-server` по `stdio://`, делает `initialize`/`initialized`, требует `Authorization: Bearer <sessionToken>` и отдает narrow runtime summary для `app/list` + host health. В Stage 3 добавлены Android-side `OkHttpHostBridgeClient` и `HttpJsonHostBridgeRepository`, runtime graph переведен на real host-backed repository, а `StukayAppState` получил background executor, periodic probe loop, network-change triggered immediate probe и lifecycle teardown. В Stage 4 shell surfaces переведены на честный runtime summary contract: `Settings` и `Projects` показывают summary-only signal, `Diagnostics` держит полную telemetry detail, а model-level `HostRuntimeSnapshotScope` различает `live` и `last_known` snapshots. В Stage 5 helper runtime path дополнительно усилен по итогам live proof для Windows spawn/error path, пройден diff-scoped security review, и acceptance flow подтвержден и на Pixel 9 Pro XL по USB tether LAN, и на эмуляторе `medium_phone`.
- next: Выполнить финальный static review loop по всей ветке относительно `main`, затем при отсутствии новых findings ветка будет готова к итоговому merge-readiness verdict.

## Decisions
- decision: Сначала поднимаем harness, docs и observability, а не меняем продуктовый код.
- why: Проект пока в template state, и без legible control plane дальнейшая Android-разработка будет плохо воспроизводимой для Codex.
- decision: JetBrains MCP и Android CLI входят в обязательный Android workflow этого репозитория.
- why: они закрывают IDE-aware и Android-native surfaces, которые обычный shell не покрывает достаточно надежно.
- decision: Notion используется как project visibility surface, но не как замена repo-local sources of truth.
- why: active plan, status, commands и architecture должны оставаться versioned рядом с кодом.
- decision: shell ориентируется на `1344×2992` как основной режим рендера, но сохраняет стабильный layout на `1008×2244`.
- why: это два реальных режима одного Pixel 9 Pro XL, и для этого проекта важнее стабильный width-constrained layout, чем избыточный adaptive stack.
- decision: отдельный `:feature:connection` module пока не вводится; pairing/local-network UX живет в `feature:settings`.
- why: текущий slice single-host, engineering-first и не требует нового feature boundary до начала real multi-host/runtime transport.
- decision: pairing flow в этом milestone начинается с `pairing payload paste/import`, а camera QR scanning и public tunnel flow отложены.
- why: roadmap разделяет contract slice и `Host Bridge MVP`, поэтому сначала фиксируется typed seam, guard-нутый control flow и permission UX.
- decision: ложный `Instantiatable` для `MainActivity` подавляется точечно через `tools:ignore` в manifest вместо отката SDK, замены `ComponentActivity` или ослабления UI stack.
- why: это bug tooling surface для `SDK 36 Preview + AGP 9.2.0`, а не runtime bug приложения; suppression ограничен одной записью manifest и не меняет поведение app shell.

## How To Run And Demo
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: validator проходит без contract failures; допустимы только осознанные `warn` до заполнения placeholder-ов.
- command: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- expected result: runtime slice собирается, а JVM tests для pairing/parser/runtime state и restore/guard transitions проходят.
- command: `.\gradlew.bat :app:lintDebug --console=plain`
- expected result: lint проходит; preview false positive по `Instantiatable` подавлен точечно на manifest entry `MainActivity`.
- command: `android describe --project_dir .`
- expected result: CLI распознает `:app`, варианты `debug/release` и APK output surface.
- command: `codex mcp get jetbrains`
- expected result: отображается активный stdio-config Android Studio MCP.
- command: `python C:\Users\v.vlasov\.codex\skills\repo-harness-lifecycle\scripts\validate_lifecycle_stack.py --root .`
- expected result: lifecycle validator проходит; допустимы только известные `warn`, без `fail`.

## Latest Review Outcome
- findings: Stage 5 live proof выявил и закрыл еще один реальный host-helper дефект: error path в `tools/hostbridge/server.py` мог рухнуть в `NameError`, а Windows default spawn не всегда резолвил runnable `codex` binary. После фикса helper прошел строгий Python suite, локальный live `/v1/runtime/summary` proof и device/emulator acceptance cycles. Diff-scoped security review по transport slice не дал подтвержденных security findings; итоговое sanitized evidence сведено в `docs/exec-plans/active/host-bridge-mvp-proof.md`.
- residual risks: bounded cleartext MVP intentionally остается debt до TLS/public-path milestone, public/tunnel endpoint path остается out of scope, diagnostics все еще без persistence/export, а suppression `Instantiatable` нужно будет пересмотреть после стабилизации AGP/SDK 36.

## Known Issues And Follow-ups
- item: В текущем runtime JetBrains MCP tools могут быть недоступны как native namespace до перезапуска Codex App, хотя server-side конфиг уже работает.
- item: `android describe` иногда не печатает useful stdout без controlled capture; это надо зафиксировать в commands inventory.
- item: shell уже использует `Navigation Compose`, но adaptive-навигация под более широкие классы экранов пока не вводилась сознательно.
- item: Notion workspace запрещает standalone private page creation; проект привязан к базе `Проекты`, и этот parent constraint надо учитывать дальше.
- item: adaptive UI для двух режимов рендера Pixel (`1008×2244` и `1344×2992`) пока решён через width-constrained shell, а не через `NavigationSuiteScaffold` или отдельный adaptive toolkit layer.
- item: `feature:thread` по-прежнему использует fake-only action contract (`startFakeTurn`, `completeFakeTurn`, `resolveApproval`); его real runtime замена вынесена в `Host Bridge MVP`.
- item: local network permission path для current API 36 intentionally подается как manual/opt-in path вокруг `NEARBY_WIFI_DEVICES`, а не как unconditional blocker; `ACCESS_LOCAL_NETWORK` остается Android 17+ follow-up.
- item: текущий host bridge repository уже real host-backed и принимает только private LAN / `.local` / `100.64/10` endpoints; public tunnel path по-прежнему считается out of scope.
- item: pairing payload хранится raw в `SharedPreferences`, но backup/data-transfer для `stukay_host_bridge.xml` теперь исключены; полноценный at-rest hardening остается follow-up.
- item: `tools:ignore="Instantiatable"` на `MainActivity` считается допустимым tooling debt для `SDK 36 Preview + AGP 9.2.0`; его надо снять, когда стабильный lint снова начнет корректно видеть `ComponentActivity -> Activity`.
- item: Host Bridge helper по умолчанию стартует на loopback и для physical LAN proof сейчас поднимался явным bind на USB tether IP; автоматизированный host launcher UX для non-loopback bind остается follow-up поверх этого MVP.

## Evidence Index
- artifact: `.tmp/.codex/task_state/latest.md`
- purpose: compaction-safe human-readable resume surface
- artifact: `.tmp/.codex/task_state/latest.json`
- purpose: machine-readable checkpoint surface
- artifact: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- purpose: verified current build/test surface
- artifact: `mcp__jetbrains__.build_project(filesToRebuild=...)`
- purpose: IDE-backed sync-preflight and file-level compilation proof before Android builds/tests
- artifact: `android describe --project_dir .`
- purpose: verified Android CLI project surface
- artifact: `codex mcp get jetbrains`
- purpose: verified Codex-side JetBrains MCP config
- artifact: `https://app.notion.com/p/353f585cf06881c682d5ccb7437ada86`
- purpose: active Notion project record for cross-project visibility
- artifact: `.\gradlew.bat :core:model:testDebugUnitTest :feature:projects:testDebugUnitTest :feature:thread:testDebugUnitTest :core:logging:testDebugUnitTest :app:assembleDebug :app:testDebugUnitTest --console=plain`
- purpose: verified domain, logging and app shell surfaces after stage 3
- artifact: `docs/exec-plans/active/runtime-slice-host-bridge-research.md`
- purpose: evidence-backed research substrate for runtime slice scope, official facts and reference signals
- artifact: `docs/exec-plans/active/host-bridge-mvp-plan.md`
- purpose: active implementation-oriented plan for the next Host Bridge MVP milestone with progress checklist and stage reports
- artifact: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
- purpose: verified pairing parser and host bridge repository state transitions
- artifact: `.\gradlew.bat :app:assembleDebug --console=plain`
- purpose: verified compile/build surface after runtime slice UI and manifest changes
- artifact: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HostBridgeClientTest" --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest" --console=plain`
- purpose: verified Stage 3 client/repository contract, unauthorized mapping, degraded snapshot preservation and disconnect semantics
- artifact: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.vitalcc.stukay.runtime.hostbridge.HttpJsonHostBridgeRepositoryTest.refreshPermissionStateDoesNotMaskUnauthorizedFailure" --console=plain`
- purpose: verified Stage 3 review-driven fix for permission refresh truthfulness after unauthorized failure
- artifact: `.\gradlew.bat :core:model:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain`
- purpose: verified Stage 4 model-level freshness contract plus shell runtime-summary surfaces after Settings/Projects/Diagnostics update
- artifact: `python -W error::ResourceWarning -m unittest discover -s tools/hostbridge/tests -p 'test_*.py'`
- purpose: verified Stage 5 helper runtime hardening, Windows codex spawn resolution and module-entry degraded HTTP path
- artifact: `http://127.0.0.1:8421/v1/runtime/summary`
- purpose: verified local live helper -> codex app-server round-trip after Stage 5 runtime hardening
- artifact: `docs/exec-plans/active/host-bridge-mvp-proof.md`
- purpose: sanitized acceptance evidence for helper live proof, physical Pixel flow, emulator flow and diff-scoped security verdict
- artifact: `.\gradlew.bat :app:lintDebug --console=plain`
- purpose: verified final lint gate after manifest-scoped suppression of preview false positive
