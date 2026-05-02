package dev.vitalcc.stukay.core.logging

import android.util.Log

class AndroidLogcatSink : LogSink {
    override fun log(event: LogEvent) {
        val tag = "Stukay/${event.area.name}"
        val payload = buildString {
            append(event.eventName)
            append(": ")
            append(event.messageHuman)
            if (event.fields.isNotEmpty()) {
                append(" | ")
                append(
                    event.fields.entries.joinToString(separator = ", ") { (key, value) ->
                        "$key=$value"
                    },
                )
            }
        }

        when (event.level) {
            LogLevel.Verbose -> Log.v(tag, payload)
            LogLevel.Debug -> Log.d(tag, payload)
            LogLevel.Info -> Log.i(tag, payload)
            LogLevel.Warn -> Log.w(tag, payload)
            LogLevel.Error -> Log.e(tag, payload)
        }
    }
}
