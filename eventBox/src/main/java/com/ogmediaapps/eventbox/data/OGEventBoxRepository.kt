package com.ogmediaapps.eventbox.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ogmediaapps.eventbox.model.OGEventBoxBackgroundMode
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.model.OGEventBoxPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data Repository for OGEventBox: manages the log list and active session state.
 */
internal object OGEventBoxRepository {

    /** Default size of the log. */
    const val MAX_EVENTS = 200
    const val MIN_MAX_EVENTS = 10
    const val MAX_MAX_EVENTS = 1000
    const val DEFAULT_DUPLICATE_WINDOW_MS = 500L
    private const val TAG = "OGEventBox"

    private val _events = MutableStateFlow<List<OGEventBoxDebugEvent>>(emptyList())
    val events: StateFlow<List<OGEventBoxDebugEvent>> = _events.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: Boolean get() = _isActive.value
    val isActiveFlow: StateFlow<Boolean> = _isActive.asStateFlow()

    @Volatile
    var debugModeEnabled = false

    @Volatile
    var debugModeDurationHours = 0.5

    @Volatile
    var firebaseChecksEnabled = true

    @Volatile
    var panelResizingEnabled = true

    @Volatile
    var backgroundMode = OGEventBoxBackgroundMode.OFF

    /** Opacity of the panel background, 0.6..1. Text always stays fully opaque. */
    @Volatile
    var panelAlpha = 1f
        set(value) { field = value.coerceIn(MIN_PANEL_ALPHA, 1f) }

    /** How many entries the log keeps before dropping the oldest. */
    @Volatile
    var maxEvents = MAX_EVENTS
        set(value) {
            field = value.coerceIn(MIN_MAX_EVENTS, MAX_MAX_EVENTS)
            trimToMax()
        }

    /** Merge an event into the previous row when it repeats back to back (shown as ×N). */
    @Volatile
    var detectDuplicates = true

    @Volatile
    var duplicateWindowMs = DEFAULT_DUPLICATE_WINDOW_MS
        set(value) { field = value.coerceAtLeast(0L) }

    /** Entries dropped from the top of the log since the last clear, because it was full. */
    @Volatile
    var droppedCount = 0
        private set

    @Volatile
    private var appContext: Context? = null

    private var nextIndex = 1
    private var lastAppendAt = 0L
    private val lock = Any()

    private val expiryHandler by lazy { Handler(Looper.getMainLooper()) }
    private val expiryRunnable = Runnable { disable() }

    var onStateChangedListener: (() -> Unit)? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    fun getContext(): Context? = appContext

    fun restoreSession(context: Context) {
        setContext(context)
        val expiryAt = OGEventBoxStore.expiryAt(context)
        if (expiryAt <= System.currentTimeMillis()) {
            if (expiryAt != 0L) OGEventBoxStore.clear(context)
            return
        }
        if (!debugModeEnabled) {
            OGEventBoxStore.clear(context)
            return
        }
        activate(expiryAt)
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        setContext(context)
        if (!enabled) {
            disable()
            return false
        }
        if (!debugModeEnabled || debugModeDurationHours <= 0) {
            Log.d(TAG, "OGEventBox not enabled: debugModeEnabled=$debugModeEnabled, duration=$debugModeDurationHours")
            return false
        }
        val expiryAt = System.currentTimeMillis() + (debugModeDurationHours * 60 * 60 * 1000).toLong()
        OGEventBoxStore.setExpiryAt(context, expiryAt)
        activate(expiryAt)
        Log.d(TAG, "OGEventBox enabled for ${debugModeDurationHours}h")
        return true
    }

    fun updateDebugModeDurationHours(context: Context, durationHours: Double) {
        this.debugModeDurationHours = durationHours
        if (isActive) {
            val expiryAt = System.currentTimeMillis() + (durationHours * 60 * 60 * 1000).toLong()
            OGEventBoxStore.setExpiryAt(context, expiryAt)
            expiryHandler.removeCallbacks(expiryRunnable)
            expiryHandler.postDelayed(expiryRunnable, (durationHours * 60 * 60 * 1000).toLong())
            onStateChangedListener?.invoke()
        }
    }

    fun remainingMinutes(context: Context): Int {
        val remaining = OGEventBoxStore.expiryAt(context) - System.currentTimeMillis()
        return if (remaining <= 0) 0 else ((remaining + 59_999) / 60_000).toInt()
    }

    fun log(name: String, params: Map<String, Any?>? = null, source: String? = null) {
        if (!isActive || name.isBlank()) return
        // A testing aid must never crash the host app, even when a caller's map is mutated
        // concurrently or holds a value whose toString() throws.
        runCatching {
            val snapshot = params?.let { LinkedHashMap(it) }
            val warnings = if (firebaseChecksEnabled) {
                com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent(name, snapshot)
            } else {
                emptyList()
            }
            append(name, snapshot, warnings, source?.takeIf { it.isNotBlank() })
        }
    }

    fun log(name: String, bundle: android.os.Bundle?, source: String? = null) {
        val paramsMap = bundle?.let { b ->
            val map = mutableMapOf<String, Any?>()
            try {
                for (key in b.keySet()) {
                    @Suppress("DEPRECATION")
                    map[key] = b.get(key)
                }
            } catch (_: Throwable) {
                // Safely handle unmocked environments
            }
            map
        }
        log(name, paramsMap, source)
    }

    fun setUserProperty(name: String, value: String?) {
        if (!isActive || name.isBlank()) return
        runCatching {
            val warnings = if (firebaseChecksEnabled) {
                com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateUserProperty(name, value)
            } else {
                emptyList()
            }
            val params = mapOf("property_name" to name, "property_value" to (value ?: "null"))
            append("user_property: $name", params, warnings, null)
        }
    }

    private fun append(name: String, params: Map<String, Any?>?, warnings: List<String>, source: String?) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val current = _events.value
            val last = current.lastOrNull()
            if (detectDuplicates && last != null &&
                now - lastAppendAt <= duplicateWindowMs &&
                last.name == name && last.source == source && sameParams(last.params, params)
            ) {
                // Same event again straight away: a double-fire. Count it on the existing row.
                lastAppendAt = now
                _events.value = current.dropLast(1) + last.copy(
                    repeatCount = last.repeatCount + 1,
                    warnings = (last.warnings + warnings).distinct(),
                )
                return
            }
            lastAppendAt = now
            val event = OGEventBoxDebugEvent(
                index = nextIndex++,
                timestampMillis = now,
                name = name,
                params = params,
                warnings = warnings,
                source = source,
            )
            val appended = current + event
            val overflow = appended.size - maxEvents
            if (overflow > 0) droppedCount += overflow
            _events.value = if (overflow > 0) appended.drop(overflow) else appended
        }
    }

    private fun sameParams(a: Map<String, Any?>?, b: Map<String, Any?>?): Boolean {
        if (a.isNullOrEmpty() && b.isNullOrEmpty()) return true
        return runCatching { a == b }.getOrDefault(false)
    }

    private fun trimToMax() {
        synchronized(lock) {
            val current = _events.value
            val overflow = current.size - maxEvents
            if (overflow <= 0) return
            droppedCount += overflow
            _events.value = current.drop(overflow)
        }
    }

    fun clear() {
        synchronized(lock) {
            clearEntries()
            com.ogmediaapps.eventbox.validator.OGEventBoxValidator.reset()
        }
    }

    /** What [clearUndoable] removed, so [restore] can bring it back. */
    class ClearedLog internal constructor(
        val events: List<OGEventBoxDebugEvent>,
        internal val nextIndex: Int,
        internal val droppedCount: Int,
    )

    /**
     * Empties the log but keeps what was there, for an undo. The Firebase-limit validator is only
     * reset by [commitClear], once the undo is no longer offered, so undoing loses nothing.
     */
    fun clearUndoable(): ClearedLog = synchronized(lock) {
        ClearedLog(_events.value, nextIndex, droppedCount).also { clearEntries() }
    }

    /** Makes a [clearUndoable] final. */
    fun commitClear() {
        com.ogmediaapps.eventbox.validator.OGEventBoxValidator.reset()
    }

    /**
     * Undoes [clearUndoable]. Entries logged since the clear are kept after the restored ones and
     * renumbered to follow them, so the numbering stays continuous.
     */
    fun restore(cleared: ClearedLog) {
        synchronized(lock) {
            var index = cleared.nextIndex
            val since = _events.value.map { it.copy(index = index++) }
            val merged = cleared.events + since
            val overflow = merged.size - maxEvents
            nextIndex = index
            droppedCount = cleared.droppedCount + droppedCount + overflow.coerceAtLeast(0)
            _events.value = if (overflow > 0) merged.drop(overflow) else merged
        }
    }

    private fun clearEntries() {
        nextIndex = 1
        droppedCount = 0
        lastAppendAt = 0L
        _events.value = emptyList()
    }

    fun disable() {
        expiryHandler.removeCallbacks(expiryRunnable)
        appContext?.let { OGEventBoxStore.clear(it) }
        val wasActive = _isActive.value
        _isActive.value = false
        clear()
        if (wasActive) Log.d(TAG, "OGEventBox disabled")
        onStateChangedListener?.invoke()
    }

    fun getBubblePosition(context: Context): OGEventBoxPosition? =
        OGEventBoxStore.bubblePosition(context)

    fun saveBubblePosition(context: Context, position: OGEventBoxPosition) {
        if (!isActive) return
        OGEventBoxStore.setBubblePosition(context, position)
    }

    private fun activate(expiryAt: Long) {
        if (appContext == null) return
        val remaining = expiryAt - System.currentTimeMillis()
        if (remaining <= 0) {
            disable()
            return
        }
        _isActive.value = true
        expiryHandler.removeCallbacks(expiryRunnable)
        expiryHandler.postDelayed(expiryRunnable, remaining)
        onStateChangedListener?.invoke()
    }

    /** JVM unit tests only: flips the session on without a Context, timer or SharedPreferences. */
    @androidx.annotation.VisibleForTesting
    internal fun setActiveForTest(active: Boolean) {
        _isActive.value = active
    }

    const val MIN_PANEL_ALPHA = 0.6f
}
