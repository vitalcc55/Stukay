package dev.vitalcc.stukay.core.logging

fun interface LogSink {
    fun log(event: LogEvent)
}
