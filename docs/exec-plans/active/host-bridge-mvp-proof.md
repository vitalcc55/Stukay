# Host Bridge MVP Proof

## Scope

Этот документ фиксирует acceptance evidence для `Host Bridge MVP` без full real thread runtime и без public/tunnel path.

## Helper Runtime Proof

- Host Bridge helper поднят на Windows и отдал `ready` summary через `/v1/runtime/summary`.
- Ответ helper содержал:
  - `hostStatus=ready`
  - `runtimeReady=true`
  - `appListCount=50`
- Это подтвердило живой путь:
  - `Windows Host Bridge helper -> local codex app-server -> app/list`

## Physical Pixel Proof

Surface:

- device: `Pixel 9 Pro XL`
- transport topology: private USB tether LAN

Observed flow:

1. `pairing restored`
2. `Nearby devices granted`
3. `connect`
4. `degraded` after helper stop
5. `reconnect` after helper restore
6. `disconnect`

Observed UI states:

- `Live runtime · Ready · ready=true · apps=50`
- `Live runtime · Degraded · ready=false · apps=50`
- `Last known runtime · Ready · ready=true · apps=50`

Observed structured logs:

- `host_bridge_connect_started`
- `host_bridge_connect_succeeded`
- `host_bridge_degraded`
- `host_bridge_reconnect_started`
- `host_bridge_disconnected`

## Emulator Proof

Surface:

- device: `medium_phone`
- host alias path: Android emulator host alias `10.0.2.2`

Observed flow:

1. `pairing restored`
2. `Nearby devices granted`
3. `connect`
4. `degraded` after helper stop
5. `reconnect` after helper restore
6. `disconnect`

Observed UI states:

- `Live runtime · Ready · ready=true · apps=50`
- `Live runtime · Degraded · ready=false · apps=50`
- `Last known runtime · Ready · ready=true · apps=50`

Observed structured logs:

- `host_bridge_connect_started`
- `host_bridge_connect_succeeded`
- `host_bridge_degraded`
- `host_bridge_reconnect_started`
- `host_bridge_disconnected`

## Security Review Verdict

Diff-scoped transport security review не дал подтвержденных security findings по следующим классам:

- bearer token logging
- raw pairing secret leakage
- backup/data-transfer leakage
- unauthorized-before-runtime-access contract
- cleartext boundary mismatch внутри текущего private-host MVP scope

Residual accepted debt:

- bounded cleartext MVP remains intentionally scoped debt until TLS/public-path milestone

