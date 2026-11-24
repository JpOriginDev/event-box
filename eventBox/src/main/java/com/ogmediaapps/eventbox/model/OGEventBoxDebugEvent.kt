package com.ogmediaapps.eventbox.model

/**
 * One line in the on-screen OGEventBox log: a single event that was fired while debug mode was
 * active. Exposed through [com.ogmediaapps.eventbox.OGEventBoxLog.events] so a host app can
 * render its own view of the log if desired.
 */
data class OGEventBoxDebugEvent @JvmOverloads constructor(
    /** 1-based position in the log, shown in the leading column. Restarts at 1 after a clear. */
    val index: Int,
    /** Wall-clock time the event was recorded, in millis since epoch. */
    val timestampMillis: Long,
    /** The event name -- for analytics events, the same string sent to Firebase Analytics. */
    val name: String,
    /** Optional event parameters (e.g. from Firebase Analytics Bundle or Map). */
    val params: Map<String, Any?>? = null,
    /** List of Firebase limits or condition warnings for this event. */
    val warnings: List<String> = emptyList(),
    /**
     * Which SDK or code path sent the event (e.g. "firebase", "appsflyer"), or null when the
     * caller didn't say. Shown as a tag on the row and matched by the search.
     */
    val source: String? = null,
    /**
     * How many times this exact event (same name, source and parameters) fired back to back
     * within the duplicate window. 1 for a normal event; see `OGEventBoxLog.setDetectDuplicates`.
     */
    val repeatCount: Int = 1,
) {
    /** True if this event failed any Firebase limits or required conditions. */
    val hasWarning: Boolean get() = warnings.isNotEmpty()

    /** True if the same event fired more than once in a row within the duplicate window. */
    val isRepeated: Boolean get() = repeatCount > 1
}
