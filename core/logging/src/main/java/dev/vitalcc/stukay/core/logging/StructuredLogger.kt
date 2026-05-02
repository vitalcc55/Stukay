package dev.vitalcc.stukay.core.logging

class StructuredLogger(
    private val sink: LogSink,
) : AppLogger {
    override fun verbose(event: LogEvent) {
        sink.log(event.copy(level = LogLevel.Verbose))
    }

    override fun debug(event: LogEvent) {
        sink.log(event.copy(level = LogLevel.Debug))
    }

    override fun info(event: LogEvent) {
        sink.log(event.copy(level = LogLevel.Info))
    }

    override fun warn(event: LogEvent) {
        sink.log(event.copy(level = LogLevel.Warn))
    }

    override fun error(event: LogEvent) {
        sink.log(event.copy(level = LogLevel.Error))
    }
}
