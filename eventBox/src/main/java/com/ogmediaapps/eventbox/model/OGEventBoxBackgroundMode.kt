package com.ogmediaapps.eventbox.model

/**
 * Controls whether the box may stay visible while the app is in the background or in recents.
 *
 * Showing over other apps needs the "Display over other apps" permission
 * (see `OGEventBoxLog.hasOverlayPermission`); the library never requests it on its own.
 */
enum class OGEventBoxBackgroundMode {
    /** The box is shown only while one of the app's Activities is in front. This is the default. */
    OFF,

    /**
     * In front the box uses the Activity window (above dialogs). When the app goes to the
     * background it moves to a system overlay if the permission is granted, otherwise it hides.
     */
    AUTO,

    /** The box always uses the system overlay and is hidden whenever the permission is missing. */
    OVERLAY_ONLY
}
