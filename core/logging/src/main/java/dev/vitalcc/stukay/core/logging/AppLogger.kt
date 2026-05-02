package dev.vitalcc.stukay.core.logging

interface AppLogger {
    fun verbose(event: LogEvent)

    fun debug(event: LogEvent)

    fun info(event: LogEvent)

    fun warn(event: LogEvent)

    fun error(event: LogEvent)
}
