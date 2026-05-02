package dev.vitalcc.stukay.core.logging

import java.time.Instant

data class LogEvent(
    val timestampUtc: Instant,
    val level: LogLevel,
    val area: LogArea,
    val eventName: String,
    val messageHuman: String,
    val fields: Map<String, String> = emptyMap(),
    val sessionId: String? = null,
    val taskId: String? = null,
    val correlationId: String? = null,
    val requestId: String? = null,
)
