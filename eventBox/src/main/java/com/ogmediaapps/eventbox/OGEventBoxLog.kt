package com.ogmediaapps.eventbox

import android.app.Application
import android.content.Context
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import com.ogmediaapps.eventbox.model.OGEventBoxBackgroundMode
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.view.OGEventBoxOverlay
import kotlinx.coroutines.flow.StateFlow

/**
 * Public entry point for OGEventBox: on-device event log testing overlay.
 */
object OGEventBoxLog {

    // =========================================================================
    // 1. Constants
    // =========================================================================

    const val MAX_EVENTS = OGEventBoxRepository.MAX_EVENTS

    // =========================================================================
    // 2. Installation & Initialization
    // =========================================================================

    /**
     * Installs OGEventBox in the app process using the configured debug mode settings.
     * Call from [Application.onCreate].
     */
    @JvmStatic
    fun install(context: Context): OGEventBoxLog {
        (context.applicationContext as? Application)?.let { OGEventBoxOverlay.install(it) }
        OGEventBoxRepository.restoreSession(context)
        return this
    }

    /**
     * Installs OGEventBox in the app process with explicit parameters.
     * Call from [Application.onCreate].
     */
    @JvmStatic
    @JvmOverloads
    fun install(
        context: Context,
        debugModeEnabled: Boolean,
        debugModeDurationHours: Double = this.debugModeDurationHours
    ): OGEventBoxLog {
        this.debugModeEnabled = debugModeEnabled
        this.debugModeDurationHours = debugModeDurationHours
        return install(context)
    }

    // =========================================================================
    // 3. Debug Mode & Duration Configuration
    // =========================================================================

    @get:JvmStatic
    @set:JvmSynthetic
    var debugModeEnabled: Boolean
        get() = OGEventBoxRepository.debugModeEnabled
        set(value) { OGEventBoxRepository.debugModeEnabled = value }

    @get:JvmStatic
    @set:JvmSynthetic
    var debugModeDurationHours: Double
        get() = OGEventBoxRepository.debugModeDurationHours
        set(value) { OGEventBoxRepository.debugModeDurationHours = value }

    /**
     * Fluent setter for debug mode enabled flag.
     */
    @JvmStatic
    fun setDebugModeEnabled(enabled: Boolean): OGEventBoxLog = apply {
        this.debugModeEnabled = enabled
    }

    /**
     * Fluent setter for default debug mode testing duration in hours.
     */
    @JvmStatic
    fun setDebugModeDurationHours(durationHours: Double): OGEventBoxLog = apply {
        this.debugModeDurationHours = durationHours
    }

    /**
     * Updates the testing duration in hours.
     * If currently active, recalculates expiry and restarts the timer.
     */
    @JvmStatic
    fun updateDebugModeDurationHours(context: Context, durationHours: Double) {
        OGEventBoxRepository.updateDebugModeDurationHours(context, durationHours)
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var firebaseChecksEnabled: Boolean
        get() = OGEventBoxRepository.firebaseChecksEnabled
        set(value) { OGEventBoxRepository.firebaseChecksEnabled = value }

    /**
     * True if Firebase analytics limits checks and warnings are enabled.
     */
    @JvmStatic
    fun isFirebaseChecksEnabled(): Boolean = OGEventBoxRepository.firebaseChecksEnabled

    /**
     * Fluent setter to enable or disable Firebase analytics limits checks and warnings.
     */
    @JvmStatic
    fun setFirebaseChecksEnabled(enabled: Boolean): OGEventBoxLog = apply {
        this.firebaseChecksEnabled = enabled
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var panelResizingEnabled: Boolean
        get() = OGEventBoxRepository.panelResizingEnabled
        set(value) {
            OGEventBoxRepository.panelResizingEnabled = value
            OGEventBoxOverlay.updateResizingState()
        }

    /**
     * True if panel height resizing is enabled.
     */
    @JvmStatic
    fun isPanelResizingEnabled(): Boolean = OGEventBoxRepository.panelResizingEnabled

    /**
     * Fluent setter to enable or disable panel height resizing.
     */
    @JvmStatic
    fun setPanelResizingEnabled(enabled: Boolean): OGEventBoxLog = apply {
        this.panelResizingEnabled = enabled
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var backgroundMode: OGEventBoxBackgroundMode
        get() = OGEventBoxRepository.backgroundMode
        set(value) {
            OGEventBoxRepository.backgroundMode = value
            OGEventBoxOverlay.sync()
        }

    /**
     * Fluent setter for whether the box stays visible when the app is in the background.
     * Defaults to [OGEventBoxBackgroundMode.OFF]. The overlay is only used while
     * [hasOverlayPermission] is true; otherwise the box behaves as with `OFF`.
     */
    @JvmStatic
    fun setBackgroundMode(mode: OGEventBoxBackgroundMode): OGEventBoxLog = apply {
        this.backgroundMode = mode
    }

    /**
     * True if the app may draw over other apps ("Display over other apps").
     */
    @JvmStatic
    fun hasOverlayPermission(context: Context): Boolean = OGEventBoxOverlay.canDrawOverlays(context)

    /**
     * Opens the system screen where the user can grant "Display over other apps" to this app.
     * The library never calls this itself; the host app decides when to ask.
     */
    @JvmStatic
    fun requestOverlayPermission(context: Context) {
        runCatching {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
            if (context !is android.app.Activity) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // =========================================================================
    // 4. Session State & Activation Controls
    // =========================================================================

    /**
     * True if a debug session is currently active and the overlay is visible.
     */
    @get:JvmStatic
    val isActive: Boolean get() = OGEventBoxRepository.isActive

    /**
     * StateFlow emitting whether a debug session is currently active.
     */
    @get:JvmStatic
    val isActiveFlow: StateFlow<Boolean> get() = OGEventBoxRepository.isActiveFlow

    /**
     * Enables or disables debug mode.
     */
    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        return OGEventBoxRepository.setEnabled(context, enabled)
    }

    /**
     * Remaining minutes in the current active session, or 0 when inactive.
     */
    @JvmStatic
    fun remainingMinutes(context: Context): Int {
        return OGEventBoxRepository.remainingMinutes(context)
    }

    /**
     * Ends the current debug session and dismisses the box overlay.
     */
    @JvmStatic
    fun disable() {
        OGEventBoxRepository.disable()
        OGEventBoxOverlay.resetPosition()
        OGEventBoxOverlay.sync()
    }

    // =========================================================================
    // 5. Event Logging & Stream
    // =========================================================================

    /**
     * Observable event list flow (capped at [MAX_EVENTS]).
     */
    @get:JvmStatic
    val events: StateFlow<List<OGEventBoxDebugEvent>> get() = OGEventBoxRepository.events

    /**
     * Logs an event into the OGEventBox log.
     */
    @JvmStatic
    fun log(name: String) {
        OGEventBoxRepository.log(name, null as Map<String, Any?>?)
    }

    /**
     * Logs an event with a bundle of parameters into the OGEventBox log.
     */
    @JvmStatic
    fun log(name: String, bundle: android.os.Bundle?) {
        OGEventBoxRepository.log(name, bundle)
    }

    /**
     * Logs an event with a parameters map into the OGEventBox log.
     */
    @JvmStatic
    fun log(name: String, params: Map<String, Any?>?) {
        OGEventBoxRepository.log(name, params)
    }

    /**
     * Records a user property into the OGEventBox log and validates against Firebase limits.
     */
    @JvmStatic
    fun setUserProperty(name: String, value: String?) {
        OGEventBoxRepository.setUserProperty(name, value)
    }

    /**
     * Empties the event log and restarts numbering at 1.
     */
    @JvmStatic
    fun clear() {
        OGEventBoxRepository.clear()
        OGEventBoxOverlay.viewModel.lastSeenIndex = 0
    }

    // =========================================================================
    // 6. Bubble Styling & Appearance
    // =========================================================================

    @get:JvmStatic
    @set:JvmSynthetic
    @DrawableRes
    var bubbleIconResId: Int = 0

    @get:JvmStatic
    @set:JvmSynthetic
    var bubbleIconScaleType: ImageView.ScaleType? = null

    @get:JvmStatic
    @set:JvmSynthetic
    @ColorInt
    var bubbleBackgroundColor: Int = 0

    @get:JvmStatic
    @set:JvmSynthetic
    @ColorInt
    var bubbleBorderColor: Int = 0

    @get:JvmStatic
    @set:JvmSynthetic
    var bubbleBorderWidthDp: Float = -1f

    @get:JvmStatic
    @set:JvmSynthetic
    var bubbleCornerRadiusDp: Float = -1f

    @JvmStatic
    fun setBubbleIconResId(@DrawableRes iconResId: Int): OGEventBoxLog = apply {
        this.bubbleIconResId = iconResId
    }

    @JvmStatic
    fun setBubbleIconScaleType(scaleType: ImageView.ScaleType?): OGEventBoxLog = apply {
        this.bubbleIconScaleType = scaleType
    }

    @JvmStatic
    fun setBubbleBackgroundColor(@ColorInt color: Int): OGEventBoxLog = apply {
        this.bubbleBackgroundColor = color
    }

    @JvmStatic
    fun setBubbleBorderColor(@ColorInt color: Int): OGEventBoxLog = apply {
        this.bubbleBorderColor = color
    }

    @JvmStatic
    fun setBubbleBorderWidthDp(widthDp: Float): OGEventBoxLog = apply {
        this.bubbleBorderWidthDp = widthDp
    }

    @JvmStatic
    fun setBubbleCornerRadiusDp(radiusDp: Float): OGEventBoxLog = apply {
        this.bubbleCornerRadiusDp = radiusDp
    }
}
