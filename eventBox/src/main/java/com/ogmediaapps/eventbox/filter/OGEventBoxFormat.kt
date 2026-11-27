package com.ogmediaapps.eventbox.filter

import java.util.Locale
import com.ogmediaapps.eventbox.safeOr

/**
 * Text helpers for event rows. Pure Kotlin so they can be unit tested; they never throw.
 */
internal object OGEventBoxFormat {

    /** Longest key width used for aligning the params table; longer keys just push their value. */
    const val MAX_KEY_PAD = 20

    /**
     * One-letter type tag for a parameter value. Firebase treats "5" and 5L differently, which
     * `toString()` hides: S = String, L = whole number, D = decimal, B = Boolean, ∅ = null.
     */
    fun typeTag(value: Any?): String = when (value) {
        null -> "∅"
        is String, is CharSequence -> "S"
        is Long, is Int, is Short, is Byte -> "L"
        is Double, is Float -> "D"
        is Boolean -> "B"
        is Array<*>, is Collection<*> -> "[]"
        else -> value.javaClass.simpleName.take(8).ifEmpty { "?" }
    }

    /** Time since the previous entry: "+120ms", "+1.2s", "+75s", "+12m". Empty for the first one. */
    fun relativeTime(deltaMs: Long?): String {
        if (deltaMs == null || deltaMs < 0) return ""
        return when {
            deltaMs < 1_000 -> "+${deltaMs}ms"
            deltaMs < 10_000 -> String.format(Locale.US, "+%.1fs", deltaMs / 1000.0)
            deltaMs < 120_000 -> "+${deltaMs / 1000}s"
            else -> "+${deltaMs / 60_000}m"
        }
    }

    data class ParamLine(val key: String, val paddedKey: String, val value: String, val type: String)

    /** Parameter rows with keys padded to a common width, for a monospace key/value table. */
    fun paramLines(params: Map<String, Any?>?): List<ParamLine> {
        if (params.isNullOrEmpty()) return emptyList()
        val pad = params.keys.maxOf { it.length }.coerceAtMost(MAX_KEY_PAD)
        return params.entries.map { (key, value) ->
            ParamLine(
                key = key,
                paddedKey = key.padEnd(pad),
                value = safeOr("<unprintable>") { valueText(value) },
                type = typeTag(value),
            )
        }
    }

    private fun valueText(value: Any?): String = when (value) {
        null -> "null"
        is Array<*> -> value.contentToString()
        is IntArray -> value.contentToString()
        is LongArray -> value.contentToString()
        is DoubleArray -> value.contentToString()
        is FloatArray -> value.contentToString()
        is BooleanArray -> value.contentToString()
        else -> value.toString()
    }
}
