package com.ogmediaapps.eventbox.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.model.OGEventBoxPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data Repository for OGEventBox: manages the log list and active session state.
 */
internal object OGEventBoxRepository {

    const val MAX_EVENTS = 200
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
    private var appContext: Context? = null

    private var nextIndex = 1
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

    fun log(name: String) {
        if (!isActive || name.isBlank()) return
        synchronized(lock) {
            val event = OGEventBoxDebugEvent(
                index = nextIndex++,
                timestampMillis = System.currentTimeMillis(),
                name = name
            )
            _events.value = (_events.value + event).takeLast(MAX_EVENTS)
        }
    }

    fun clear() {
        synchronized(lock) {
            nextIndex = 1
            _events.value = emptyList()
        }
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
}
