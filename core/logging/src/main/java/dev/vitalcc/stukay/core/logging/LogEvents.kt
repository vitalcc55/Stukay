package dev.vitalcc.stukay.core.logging

object LogEvents {
    fun info(
        area: LogArea,
        eventName: String,
        messageHuman: String,
        fields: Map<String, String> = emptyMap(),
        sessionId: String? = null,
        taskId: String? = null,
        correlationId: String? = null,
        requestId: String? = null,
    ): LogEvent = LogEvent(
        timestampUtc = java.time.Instant.now(),
        level = LogLevel.Info,
        area = area,
        eventName = eventName,
        messageHuman = messageHuman,
        fields = fields,
        sessionId = sessionId,
        taskId = taskId,
        correlationId = correlationId,
        requestId = requestId,
    )
}
