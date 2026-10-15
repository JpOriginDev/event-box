package com.ogmediaapps.eventbox.model

/**
 * One line in the on-screen OGEventBox log: a single event that was fired while debug mode was
 * active. Exposed through [com.ogmediaapps.eventbox.OGEventBoxLog.events] so a host app can
 * render its own view of the log if desired.
 */
data class OGEventBoxDebugEvent(
    /** 1-based position in the log, shown in the leading column. Restarts at 1 after a clear. */
    val index: Int,
    /** Wall-clock time the event was recorded, in millis since epoch. */
    val timestampMillis: Long,
    /** The event name -- for analytics events, the same string sent to Firebase Analytics. */
    val name: String,
)
