package com.ogmediaapps.eventbox.view

/**
 * Timings (ms), scales, sizes (dp) and defaults used by [OGEventBoxView]. Kept in one place so a
 * value can be tuned without searching a 1,900-line class.
 */
internal object OGEventBoxViewConstants {
    const val AUTO_SHRINK_DELAY_MS = 6_000L
    const val TIP_DURATION_MS = 8_000L
    const val UNDO_DURATION_MS = 4_000L
    const val RESIZE_RESET_MS = 200L
    const val HANDLE_PULSE_DELAY_MS = 350L
    const val HANDLE_PULSE_HOLD_MS = 700L
    const val QUERY_DEBOUNCE_MS = 150L
    const val BUBBLE_HIGHLIGHT_MS = 2_500L
    const val ANIMATION_MS = 150L
    const val DRAG_ANIMATION_MS = 120L
    const val SNAP_ANIMATION_MS = 240L
    const val BUBBLE_FADE_MS = 300L
    const val DEFAULT_BORDER_WIDTH_DP = 1f
    const val BADGE_SIZE_ONE_DIGIT_DP = 16f
    const val BADGE_SIZE_TWO_DIGIT_DP = 18f
    const val BADGE_SIZE_OVERFLOW_DP = 20f
    const val LOGO_INSET_DP = 3f
    const val BUBBLE_IDLE_ALPHA = 0.55f
    const val BUBBLE_DRAG_SCALE = 1.15f
    const val CLOSE_TARGET_ENTER_SCALE = 0.7f
    const val CLOSE_TARGET_NEAR_SCALE = 1.2f
    const val CLOSE_TARGET_OVER_SCALE = 1.45f
    const val CLOSE_TARGET_NEAR_RADIUS = 1.8f
    const val CLOSE_TARGET_OVER_RADIUS = 1f
    const val CLOSE_TARGET_REVEAL_HEIGHT = 0.22f
    const val CLOSE_TARGET_HIDE_SLACK = 1.15f
    const val DEFAULT_ON_RIGHT_EDGE = true
    const val DEFAULT_Y_RATIO = 0f
    val PANEL_ALPHA_STEPS = floatArrayOf(1f, 0.85f, 0.7f, 0.6f)
}
