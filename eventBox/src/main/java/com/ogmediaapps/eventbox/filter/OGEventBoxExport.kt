package com.ogmediaapps.eventbox.filter

import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.safeOr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns log entries into JSON for the clipboard and for "Share log".
 *
 * Hand-written instead of using `org.json`, so it also runs in plain JVM unit tests and never
 * throws on odd parameter values.
 */
internal object OGEventBoxExport {

    fun eventJson(event: OGEventBoxDebugEvent, indent: String = ""): String {
        val inner = "$indent  "
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append(inner).append("\"index\": ").append(event.index).append(",\n")
        sb.append(inner).append("\"time\": ").append(quote(isoTime(event.timestampMillis))).append(",\n")
        sb.append(inner).append("\"name\": ").append(quote(event.name)).append(",\n")
        event.source?.let { sb.append(inner).append("\"source\": ").append(quote(it)).append(",\n") }
        if (event.isRepeated) sb.append(inner).append("\"repeat_count\": ").append(event.repeatCount).append(",\n")
        sb.append(inner).append("\"params\": ")
        val params = event.params
        if (params.isNullOrEmpty()) {
            sb.append("{}")
        } else {
            sb.append("{\n")
            params.entries.forEachIndexed { i, (key, value) ->
                sb.append(inner).append("  ").append(quote(key)).append(": ").append(value(value))
                if (i < params.size - 1) sb.append(',')
                sb.append('\n')
            }
            sb.append(inner).append('}')
        }
        sb.append(",\n")
        sb.append(inner).append("\"warnings\": [")
        sb.append(event.warnings.joinToString(", ") { quote(it) })
        sb.append("]\n")
        sb.append(indent).append('}')
        return sb.toString()
    }

    fun logJson(events: List<OGEventBoxDebugEvent>): String {
        if (events.isEmpty()) return "[]"
        return events.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]") {
            "  " + eventJson(it, indent = "  ")
        }
    }

    private fun value(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Int, is Long, is Short, is Byte -> value.toString()
        is Double -> if (value.isNaN() || value.isInfinite()) quote(value.toString()) else value.toString()
        is Float -> if (value.isNaN() || value.isInfinite()) quote(value.toString()) else value.toString()
        else -> quote(safeOr("<unprintable>") { value.toString() })
    }

    fun quote(text: String): String {
        val sb = StringBuilder(text.length + 2).append('"')
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format(Locale.US, "\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun isoTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(millis))
}
