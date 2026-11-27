package com.ogmediaapps.eventbox

import android.app.Application
import android.content.Context
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import com.ogmediaapps.eventbox.data.OGEventBoxStore
import com.ogmediaapps.eventbox.model.OGEventBoxAvailability
import com.ogmediaapps.eventbox.model.OGEventBoxBackgroundMode
import com.ogmediaapps.eventbox.model.OGEventBoxBubbleStyle
import com.ogmediaapps.eventbox.model.OGEventBoxComponent
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
        OGEventBoxRepository.resolveAvailability(context)
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
        OGEventBoxRepository.availability = null
        this.debugModeDurationHours = debugModeDurationHours
        return install(context)
    }

    /**
     * Same as [install], and also starts a session right away, so the box shows without the tester
     * having to switch it on first (for example with the click counter). If a session from an
     * earlier launch is still running it simply continues. Nothing shows when the box is not
     * allowed in this build (see [setAvailability]).
     *
     * ```
     * OGEventBoxLog.setAvailability(OGEventBoxAvailability.DEBUG_ONLY).installAndShow(this)
     * ```
     *
     * @param durationHours how long the session lasts; defaults to [debugModeDurationHours]
     */
    @JvmStatic
    @JvmOverloads
    fun installAndShow(context: Context, durationHours: Double = this.debugModeDurationHours): OGEventBoxLog {
        this.debugModeDurationHours = durationHours
        install(context)
        // A box the tester closed stays closed until the app's data is cleared.
        val closedByUser = rememberUserChoices && OGEventBoxStore.isClosedByUser(context)
        if (!OGEventBoxRepository.isActive && !closedByUser) OGEventBoxRepository.setEnabled(context, true)
        return this
    }

    /**
     * Whether the box remembers what the tester did by hand (default true): a box they minimized
     * stays a bubble until they open it again, and a box they closed (dropped on the close target)
     * does not start again by itself, for example in [installAndShow], until the app's data is
     * cleared or [setEnabled] switches it on by hand. Turn it off to always start from the defaults.
     */
    @get:JvmStatic
    @set:JvmSynthetic
    var rememberUserChoices: Boolean
        get() = OGEventBoxRepository.rememberUserChoices
        set(value) { OGEventBoxRepository.rememberUserChoices = value }

    /** True if the tester's minimize and close choices are remembered. See [rememberUserChoices]. */
    @JvmStatic
    fun isRememberUserChoices(): Boolean = OGEventBoxRepository.rememberUserChoices

    /** Fluent setter for [rememberUserChoices]. Call it before [install]. */
    @JvmStatic
    fun setRememberUserChoices(remember: Boolean): OGEventBoxLog = apply {
        this.rememberUserChoices = remember
    }

    /** Forgets the remembered minimize and close choices, for QA. Takes effect on the next start. */
    @JvmStatic
    fun resetUserChoices(context: Context) {
        OGEventBoxStore.resetUserChoices(context)
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
     * Chooses in which builds of the app Event Box may appear. Call it before [install]; the
     * build kind is read from the host app, so no `BuildConfig.DEBUG` is needed.
     *
     * ```
     * OGEventBoxLog.setAvailability(OGEventBoxAvailability.DEBUG_ONLY).install(this)
     * ```
     *
     * Options: [OGEventBoxAvailability.DEBUG_ONLY], [OGEventBoxAvailability.RELEASE_ONLY],
     * [OGEventBoxAvailability.DEBUG_AND_RELEASE] and [OGEventBoxAvailability.NEVER]. The last call
     * wins over the deprecated [setDebugModeEnabled]. Without either, Event Box stays off.
     */
    @JvmStatic
    fun setAvailability(availability: OGEventBoxAvailability): OGEventBoxLog = apply {
        OGEventBoxRepository.availability = availability
    }

    /**
     * The choice made with [setAvailability], or null while the deprecated [setDebugModeEnabled]
     * flag is in use. Whether the box is allowed in this build is [debugModeEnabled].
     */
    @JvmStatic
    fun getAvailability(): OGEventBoxAvailability? = OGEventBoxRepository.availability

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

    // =========================================================================
    // Components and defaults: what the box shows and how it starts
    // =========================================================================
    //
    // Everything below is optional and chainable, and is best set before [install]:
    //
    //   OGEventBoxLog
    //       .setStartState(expanded = true, pinned = true, deltaTimeShown = true)
    //       .hideComponents(OGEventBoxComponent.PIN_BUTTON, OGEventBoxComponent.DELTA_TIME_BUTTON)
    //       .install(app)
    //
    // A component with a state has a default (expanded, pinned, delta time). While its control is
    // shown a tester can change it; hide the control and the default is final.

    /**
     * Shows or hides one [component] of the box. Applies live. Everything is shown by default except
     * [OGEventBoxComponent.DELTA_TIME_BUTTON], which is hidden until you turn it on.
     * [OGEventBoxComponent.RESIZE_HANDLE] is the same switch as [setPanelResizingEnabled].
     */
    @JvmStatic
    fun setComponentVisible(component: OGEventBoxComponent, visible: Boolean): OGEventBoxLog = apply {
        if (component == OGEventBoxComponent.RESIZE_HANDLE) {
            this.panelResizingEnabled = visible
            return@apply
        }
        val hidden = OGEventBoxRepository.hiddenComponents
        OGEventBoxRepository.hiddenComponents = if (visible) hidden - component else hidden + component
        OGEventBoxOverlay.updateComponents()
    }

    /** True if [component] is shown. See [setComponentVisible]. */
    @JvmStatic
    fun isComponentVisible(component: OGEventBoxComponent): Boolean =
        if (component == OGEventBoxComponent.RESIZE_HANDLE) OGEventBoxRepository.panelResizingEnabled
        else component !in OGEventBoxRepository.hiddenComponents

    /** Hides every one of [components]. Shortcut for several [setComponentVisible] calls. */
    @JvmStatic
    fun hideComponents(vararg components: OGEventBoxComponent): OGEventBoxLog = apply {
        components.forEach { setComponentVisible(it, false) }
    }

    /** Shows every one of [components]. Shortcut for several [setComponentVisible] calls. */
    @JvmStatic
    fun showComponents(vararg components: OGEventBoxComponent): OGEventBoxLog = apply {
        components.forEach { setComponentVisible(it, true) }
    }

    /** Shows only [components] and hides every other one. */
    @JvmStatic
    fun showOnly(vararg components: OGEventBoxComponent): OGEventBoxLog = apply {
        OGEventBoxComponent.values().forEach { setComponentVisible(it, it in components) }
    }

    /**
     * Sets how the box starts, in one call. A parameter you leave out keeps its current value.
     * Same as [setExpandedByDefault], [setPinnedByDefault] and [setDeltaTimeShown].
     *
     * @param expanded start as the open panel instead of the small bubble (default false)
     * @param pinned keep the open panel from auto-shrinking (default false)
     * @param deltaTimeShown show the "+1.2s" time since the previous event (default false)
     */
    @JvmStatic
    @JvmOverloads
    fun setStartState(expanded: Boolean = expandedByDefault, pinned: Boolean = pinnedByDefault, deltaTimeShown: Boolean = this.deltaTimeShown): OGEventBoxLog = apply {
        this.expandedByDefault = expanded
        this.pinnedByDefault = pinned
        this.deltaTimeShown = deltaTimeShown
    }

    /**
     * Whether the box starts as the open panel instead of the small bubble (default false). The
     * tester can still shrink it to the bubble and reopen it; hide [OGEventBoxComponent.LOGO] and
     * pin it to keep it open. Set it before [install]; it does not move a box already showing.
     */
    @get:JvmStatic
    @set:JvmSynthetic
    var expandedByDefault: Boolean
        get() = OGEventBoxRepository.expandedByDefault
        set(value) {
            OGEventBoxRepository.expandedByDefault = value
        }

    /** True if the box starts as the open panel. See [expandedByDefault]. */
    @JvmStatic
    fun isExpandedByDefault(): Boolean = OGEventBoxRepository.expandedByDefault

    /** Fluent setter for the start state. See [expandedByDefault]. */
    @JvmStatic
    fun setExpandedByDefault(expanded: Boolean): OGEventBoxLog = apply {
        this.expandedByDefault = expanded
    }

    /**
     * Whether the panel is pinned open (no auto-shrink) from the start (default false). A tester's
     * pin button changes it for the session while that button is shown; hide it
     * ([OGEventBoxComponent.PIN_BUTTON]) and this value is final.
     */
    @get:JvmStatic
    @set:JvmSynthetic
    var pinnedByDefault: Boolean
        get() = OGEventBoxRepository.pinnedByDefault
        set(value) {
            OGEventBoxRepository.pinnedByDefault = value
            OGEventBoxOverlay.updateComponents()
        }

    /** True if the panel starts pinned open. See [pinnedByDefault]. */
    @JvmStatic
    fun isPinnedByDefault(): Boolean = OGEventBoxRepository.pinnedByDefault

    /** Fluent setter for the pin default. See [pinnedByDefault]. */
    @JvmStatic
    fun setPinnedByDefault(pinned: Boolean): OGEventBoxLog = apply {
        this.pinnedByDefault = pinned
    }

    /**
     * Whether rows show the "+1.2s" time since the previous event by default (default false). A
     * tester's own choice with the clock button wins while that button is shown; hide it
     * ([OGEventBoxComponent.DELTA_TIME_BUTTON]) and this value is final.
     */
    @get:JvmStatic
    @set:JvmSynthetic
    var deltaTimeShown: Boolean
        get() = OGEventBoxRepository.deltaTimeShown
        set(value) {
            OGEventBoxRepository.deltaTimeShown = value
            OGEventBoxOverlay.updateComponents()
        }

    /** True if rows show the "+1.2s" time by default. See [deltaTimeShown]. */
    @JvmStatic
    fun isDeltaTimeShown(): Boolean = OGEventBoxRepository.deltaTimeShown

    /** Fluent setter for the default of the "+1.2s" times. See [deltaTimeShown]. */
    @JvmStatic
    fun setDeltaTimeShown(shown: Boolean): OGEventBoxLog = apply {
        this.deltaTimeShown = shown
    }

    // Shortcuts for the six header buttons: the same as [setComponentVisible] with the matching
    // [OGEventBoxComponent], in the same style as the other fluent setters.

    @get:JvmStatic
    @set:JvmSynthetic
    var clearButtonVisible: Boolean
        get() = isComponentVisible(OGEventBoxComponent.CLEAR_BUTTON)
        set(value) {
            setComponentVisible(OGEventBoxComponent.CLEAR_BUTTON, value)
        }

    /** True if the clear (trash) button is shown. See [OGEventBoxComponent.CLEAR_BUTTON]. */
    @JvmStatic
    fun isClearButtonVisible(): Boolean = clearButtonVisible

    /** Fluent setter to show or hide the clear (trash) button. */
    @JvmStatic
    fun setClearButtonVisible(visible: Boolean): OGEventBoxLog = apply {
        this.clearButtonVisible = visible
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var pinButtonVisible: Boolean
        get() = isComponentVisible(OGEventBoxComponent.PIN_BUTTON)
        set(value) {
            setComponentVisible(OGEventBoxComponent.PIN_BUTTON, value)
        }

    /** True if the pin button is shown. See [OGEventBoxComponent.PIN_BUTTON]. */
    @JvmStatic
    fun isPinButtonVisible(): Boolean = pinButtonVisible

    /** Fluent setter to show or hide the pin button. */
    @JvmStatic
    fun setPinButtonVisible(visible: Boolean): OGEventBoxLog = apply {
        this.pinButtonVisible = visible
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var shareButtonVisible: Boolean
        get() = isComponentVisible(OGEventBoxComponent.SHARE_BUTTON)
        set(value) {
            setComponentVisible(OGEventBoxComponent.SHARE_BUTTON, value)
        }

    /** True if the share button is shown. See [OGEventBoxComponent.SHARE_BUTTON]. */
    @JvmStatic
    fun isShareButtonVisible(): Boolean = shareButtonVisible

    /** Fluent setter to show or hide the share button. */
    @JvmStatic
    fun setShareButtonVisible(visible: Boolean): OGEventBoxLog = apply {
        this.shareButtonVisible = visible
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var pauseButtonVisible: Boolean
        get() = isComponentVisible(OGEventBoxComponent.PAUSE_BUTTON)
        set(value) {
            setComponentVisible(OGEventBoxComponent.PAUSE_BUTTON, value)
        }

    /** True if the pause / resume button is shown. See [OGEventBoxComponent.PAUSE_BUTTON]. */
    @JvmStatic
    fun isPauseButtonVisible(): Boolean = pauseButtonVisible

    /** Fluent setter to show or hide the pause / resume button. */
    @JvmStatic
    fun setPauseButtonVisible(visible: Boolean): OGEventBoxLog = apply {
        this.pauseButtonVisible = visible
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var searchButtonVisible: Boolean
        get() = isComponentVisible(OGEventBoxComponent.SEARCH_BUTTON)
        set(value) {
            setComponentVisible(OGEventBoxComponent.SEARCH_BUTTON, value)
        }

    /** True if the search button is shown. See [OGEventBoxComponent.SEARCH_BUTTON]. */
    @JvmStatic
    fun isSearchButtonVisible(): Boolean = searchButtonVisible

    /** Fluent setter to show or hide the search button. */
    @JvmStatic
    fun setSearchButtonVisible(visible: Boolean): OGEventBoxLog = apply {
        this.searchButtonVisible = visible
    }

    @get:JvmStatic
    @set:JvmSynthetic
    var deltaTimeButtonVisible: Boolean
        get() = isComponentVisible(OGEventBoxComponent.DELTA_TIME_BUTTON)
        set(value) {
            setComponentVisible(OGEventBoxComponent.DELTA_TIME_BUTTON, value)
        }

    /** True if the clock button is shown. See [OGEventBoxComponent.DELTA_TIME_BUTTON]. */
    @JvmStatic
    fun isDeltaTimeButtonVisible(): Boolean = deltaTimeButtonVisible

    /** Fluent setter to show or hide the clock button. */
    @JvmStatic
    fun setDeltaTimeButtonVisible(visible: Boolean): OGEventBoxLog = apply {
        this.deltaTimeButtonVisible = visible
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
        // Before Android 6 the permission is granted at install time and this screen doesn't exist.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        safely {
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
        // Switching it on by hand is a new decision, so an earlier close no longer applies.
        if (enabled) OGEventBoxStore.setClosedByUser(context, false)
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
     * Logs an event with parameters and a [source] tag (e.g. "firebase", "appsflyer"), so events
     * from different SDKs can be told apart and searched in the box.
     */
    @JvmStatic
    fun log(name: String, params: Map<String, Any?>?, source: String?) {
        OGEventBoxRepository.log(name, params, source)
    }

    /** Logs an event with a bundle of parameters and a [source] tag. */
    @JvmStatic
    fun log(name: String, bundle: android.os.Bundle?, source: String?) {
        OGEventBoxRepository.log(name, bundle, source)
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
    // 5b. Log size & duplicate detection
    // =========================================================================

    /**
     * How many entries the log keeps before dropping the oldest. Defaults to [MAX_EVENTS];
     * values are kept within 10..1000.
     */
    @get:JvmStatic
    @set:JvmSynthetic
    var maxEvents: Int
        get() = OGEventBoxRepository.maxEvents
        set(value) { OGEventBoxRepository.maxEvents = value }

    /** Fluent setter for [maxEvents]. */
    @JvmStatic
    fun setMaxEvents(count: Int): OGEventBoxLog = apply { this.maxEvents = count }

    /** Entries dropped from the top of the log since the last clear because it was full. */
    @get:JvmStatic
    val droppedEventCount: Int get() = OGEventBoxRepository.droppedCount

    /**
     * When true (the default), an event that fires again with the same name, source and
     * parameters within [duplicateWindowMs] of the previous one is counted on that row (shown as
     * ×N) instead of adding a new row. Double-firing is a common analytics bug.
     */
    @get:JvmStatic
    @set:JvmSynthetic
    var detectDuplicates: Boolean
        get() = OGEventBoxRepository.detectDuplicates
        set(value) { OGEventBoxRepository.detectDuplicates = value }

    /** Fluent setter for [detectDuplicates]. */
    @JvmStatic
    fun setDetectDuplicates(enabled: Boolean): OGEventBoxLog = apply { this.detectDuplicates = enabled }

    /** How close together (in ms) two identical events must be to count as a duplicate. Default 500. */
    @get:JvmStatic
    @set:JvmSynthetic
    var duplicateWindowMs: Long
        get() = OGEventBoxRepository.duplicateWindowMs
        set(value) { OGEventBoxRepository.duplicateWindowMs = value }

    /** Fluent setter for [duplicateWindowMs]. */
    @JvmStatic
    fun setDuplicateWindowMs(windowMs: Long): OGEventBoxLog = apply { this.duplicateWindowMs = windowMs }

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

    /**
     * Sets the whole bubble look from a ready-made [style]. Call it before [install]. Use
     * [OGEventBoxBubbleStyle.Builder] from Java, or the lambda overload from Kotlin.
     */
    @JvmStatic
    fun setBubbleStyle(style: OGEventBoxBubbleStyle): OGEventBoxLog = apply {
        this.bubbleIconResId = style.iconRes
        this.bubbleIconScaleType = style.iconScaleType
        this.bubbleBackgroundColor = style.backgroundColor
        this.bubbleBorderColor = style.borderColor
        this.bubbleBorderWidthDp = style.borderWidthDp
        this.bubbleCornerRadiusDp = style.cornerRadiusDp
    }

    /**
     * Changes the bubble look in one block. Only the values you set change; everything else keeps
     * its current value. Call it before [install].
     *
     * ```
     * OGEventBoxLog.setBubbleStyle {
     *     iconRes(R.mipmap.my_logo)
     *     iconScaleType(ImageView.ScaleType.CENTER_CROP)
     *     backgroundColor(Color.parseColor("#0D9379"))
     *     borderWidthDp(1.5f)
     *     fullyRound()
     * }
     * ```
     */
    @JvmSynthetic
    fun setBubbleStyle(configure: OGEventBoxBubbleStyle.Builder.() -> Unit): OGEventBoxLog =
        setBubbleStyle(getBubbleStyle().toBuilder().apply(configure).build())

    /** The bubble look in use right now, for reading or as the start of a [OGEventBoxBubbleStyle.toBuilder]. */
    @JvmStatic
    fun getBubbleStyle(): OGEventBoxBubbleStyle = OGEventBoxBubbleStyle.Builder()
        .iconRes(bubbleIconResId)
        .iconScaleType(bubbleIconScaleType)
        .backgroundColor(bubbleBackgroundColor)
        .borderColor(bubbleBorderColor)
        .borderWidthDp(bubbleBorderWidthDp)
        .cornerRadiusDp(bubbleCornerRadiusDp)
        .build()

    /**
     * Opacity of the open panel's background, from 0.6 (see the app through it) to 1 (solid, the
     * default). Text stays fully opaque. Testers can also cycle it by long-pressing the panel logo.
     */
    @get:JvmStatic
    @set:JvmSynthetic
    var panelAlpha: Float
        get() = OGEventBoxRepository.panelAlpha
        set(value) {
            OGEventBoxRepository.panelAlpha = value
            OGEventBoxOverlay.updatePanelAlpha()
        }

    /** Fluent setter for [panelAlpha]. */
    @JvmStatic
    fun setPanelAlpha(alpha: Float): OGEventBoxLog = apply { this.panelAlpha = alpha }

    // =========================================================================
    // Deprecated: kept so existing apps still compile; use the replacement named in each message
    // =========================================================================

    /**
     * Fluent setter for debug mode enabled flag.
     */
    @Deprecated(
        message = "Use setAvailability(OGEventBoxAvailability.DEBUG_ONLY) instead of setDebugModeEnabled(BuildConfig.DEBUG). It also supports RELEASE_ONLY, DEBUG_AND_RELEASE and NEVER.",
        replaceWith = ReplaceWith("setAvailability(OGEventBoxAvailability.DEBUG_ONLY)", "com.ogmediaapps.eventbox.model.OGEventBoxAvailability"),
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun setDebugModeEnabled(enabled: Boolean): OGEventBoxLog = apply {
        OGEventBoxRepository.availability = null
        this.debugModeEnabled = enabled
    }

    @Deprecated(
        message = "Use setBubbleStyle { iconRes(...) } or setBubbleStyle(OGEventBoxBubbleStyle) instead.",
        replaceWith = ReplaceWith("setBubbleStyle { iconRes(iconResId) }"),
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun setBubbleIconResId(@DrawableRes iconResId: Int): OGEventBoxLog = apply {
        this.bubbleIconResId = iconResId
    }

    @Deprecated(
        message = "Use setBubbleStyle { iconScaleType(...) } or setBubbleStyle(OGEventBoxBubbleStyle) instead.",
        replaceWith = ReplaceWith("setBubbleStyle { iconScaleType(scaleType) }"),
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun setBubbleIconScaleType(scaleType: ImageView.ScaleType?): OGEventBoxLog = apply {
        this.bubbleIconScaleType = scaleType
    }

    @Deprecated(
        message = "Use setBubbleStyle { backgroundColor(...) } or setBubbleStyle(OGEventBoxBubbleStyle) instead.",
        replaceWith = ReplaceWith("setBubbleStyle { backgroundColor(color) }"),
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun setBubbleBackgroundColor(@ColorInt color: Int): OGEventBoxLog = apply {
        this.bubbleBackgroundColor = color
    }

    @Deprecated(
        message = "Use setBubbleStyle { borderColor(...) } or setBubbleStyle(OGEventBoxBubbleStyle) instead.",
        replaceWith = ReplaceWith("setBubbleStyle { borderColor(color) }"),
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun setBubbleBorderColor(@ColorInt color: Int): OGEventBoxLog = apply {
        this.bubbleBorderColor = color
    }

    @Deprecated(
        message = "Use setBubbleStyle { borderWidthDp(...) } or setBubbleStyle(OGEventBoxBubbleStyle) instead.",
        replaceWith = ReplaceWith("setBubbleStyle { borderWidthDp(widthDp) }"),
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun setBubbleBorderWidthDp(widthDp: Float): OGEventBoxLog = apply {
        this.bubbleBorderWidthDp = widthDp
    }

    @Deprecated(
        message = "Use setBubbleStyle { cornerRadiusDp(...) } or setBubbleStyle(OGEventBoxBubbleStyle) instead.",
        replaceWith = ReplaceWith("setBubbleStyle { cornerRadiusDp(radiusDp) }"),
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun setBubbleCornerRadiusDp(radiusDp: Float): OGEventBoxLog = apply {
        this.bubbleCornerRadiusDp = radiusDp
    }
}
