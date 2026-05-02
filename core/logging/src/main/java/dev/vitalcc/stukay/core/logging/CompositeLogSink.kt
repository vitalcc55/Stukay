package dev.vitalcc.stukay.core.logging

class CompositeLogSink(
    private val sinks: List<LogSink>,
) : LogSink {
    override fun log(event: LogEvent) {
        sinks.forEach { sink ->
            sink.log(event)
        }
    }
}
