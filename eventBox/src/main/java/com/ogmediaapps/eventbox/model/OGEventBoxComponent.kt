package com.ogmediaapps.eventbox.model

/**
 * A part of the Event Box UI the host app can show or hide with
 * `OGEventBoxLog.setComponentVisible`, `hideComponents` or `showOnly`.
 * Everything is shown by default except [DELTA_TIME_BUTTON], which is hidden until the host shows it.
 */
enum class OGEventBoxComponent {
    /** The logo in the panel header. Tapping it shrinks the panel, so hiding it also removes that. */
    LOGO,

    /** The "EVENTS" title in the panel header. It also drops out by itself on narrow screens. */
    TITLE,

    /** The event count next to the title. */
    COUNT,

    /** Header button: clear the log (with undo). */
    CLEAR_BUTTON,

    /** Header button: pin the panel open (no auto-shrink). */
    PIN_BUTTON,

    /** Header button: share the log as text or a JSON file. */
    SHARE_BUTTON,

    /** Header button: pause or resume the live list. */
    PAUSE_BUTTON,

    /** Header button: open the search bar. */
    SEARCH_BUTTON,

    /** Header button: show or hide the "+1.2s" time since the previous event. */
    DELTA_TIME_BUTTON,

    /** The drag handle at the bottom that resizes the panel (same as `setPanelResizingEnabled`). */
    RESIZE_HANDLE,

    /** The unread-count badge on the collapsed bubble. */
    UNREAD_BADGE,

    /** The one-time tip shown the first time the panel opens. */
    TIPS
}
