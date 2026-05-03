from __future__ import annotations

from dataclasses import dataclass, replace
import time


@dataclass(frozen=True)
class RuntimeSummary:
    host_status: str = "unknown"
    runtime_ready: bool = False
    app_list_count: int | None = None
    last_round_trip_ms: int | None = None
    probe_at_epoch_ms: int | None = None
    retry_attempt: int = 0
    degraded_reason: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    last_transport_error: str | None = None

    def to_wire_dict(self) -> dict[str, object | None]:
        return {
            "hostStatus": self.host_status,
            "runtimeReady": self.runtime_ready,
            "appListCount": self.app_list_count,
            "lastRoundTripMs": self.last_round_trip_ms,
            "probeAtEpochMs": self.probe_at_epoch_ms,
            "retryAttempt": self.retry_attempt,
            "degradedReason": self.degraded_reason,
            "errorCode": self.error_code,
            "errorMessage": self.error_message,
            "lastTransportError": self.last_transport_error,
        }

    @classmethod
    def ready(cls, *, app_list_count: int, last_round_trip_ms: int) -> "RuntimeSummary":
        return cls(
            host_status="ready",
            runtime_ready=True,
            app_list_count=app_list_count,
            last_round_trip_ms=last_round_trip_ms,
            probe_at_epoch_ms=_now_epoch_ms(),
            retry_attempt=0,
        )

    def degraded(self, *, retry_attempt: int, degraded_reason: str, error_code: str, error_message: str) -> "RuntimeSummary":
        return replace(
            self,
            host_status="degraded",
            runtime_ready=False,
            probe_at_epoch_ms=_now_epoch_ms(),
            retry_attempt=retry_attempt,
            degraded_reason=degraded_reason,
            error_code=error_code,
            error_message=error_message,
            last_transport_error=error_message,
        )


def _now_epoch_ms() -> int:
    return int(time.time() * 1000)
