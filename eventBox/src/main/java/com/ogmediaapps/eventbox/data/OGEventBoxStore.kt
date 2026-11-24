package com.ogmediaapps.eventbox.data

import android.content.Context
import androidx.core.content.edit
import com.ogmediaapps.eventbox.model.OGEventBoxPosition

/**
 * Persists session expiry and resting position in SharedPreferences.
 */
internal object OGEventBoxStore {

    private const val PREFS_NAME = "og_event_box_prefs"

    private const val KEY_EXPIRY_AT = "og_event_box_expiry_at"
    private const val KEY_BUBBLE_ON_RIGHT = "og_event_box_bubble_on_right"
    private const val KEY_BUBBLE_Y_RATIO = "og_event_box_bubble_y_ratio"

    // One-time onboarding flags. Kept across debug-mode sessions, so clear() leaves them alone.
    private const val KEY_GESTURE_TIP_SEEN = "og_event_box_gesture_tip_seen"
    private const val KEY_RESIZE_USED = "og_event_box_resize_used"

    // Panel preferences the tester picked. Also kept across sessions.
    private const val KEY_PANEL_HEIGHT_DP = "og_event_box_panel_height_dp"
    private const val KEY_PANEL_ALPHA = "og_event_box_panel_alpha"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Epoch millis at which debug mode ends, or 0 when it was never enabled / was closed. */
    fun expiryAt(context: Context): Long = runCatching {
        prefs(context).getLong(KEY_EXPIRY_AT, 0L)
    }.getOrDefault(0L)

    fun setExpiryAt(context: Context, expiryAt: Long) {
        runCatching { prefs(context).edit { putLong(KEY_EXPIRY_AT, expiryAt) } }
    }

    /** The bubble's last resting place, or `null` when this debug-mode session hasn't moved it. */
    fun bubblePosition(context: Context): OGEventBoxPosition? = runCatching {
        val p = prefs(context)
        if (!p.contains(KEY_BUBBLE_Y_RATIO)) return@runCatching null
        OGEventBoxPosition(
            onRightEdge = p.getBoolean(KEY_BUBBLE_ON_RIGHT, true),
            yRatio = p.getFloat(KEY_BUBBLE_Y_RATIO, 0f).coerceIn(0f, 1f),
        )
    }.getOrNull()

    fun setBubblePosition(context: Context, position: OGEventBoxPosition) {
        runCatching {
            prefs(context).edit {
                putBoolean(KEY_BUBBLE_ON_RIGHT, position.onRightEdge)
                putFloat(KEY_BUBBLE_Y_RATIO, position.yRatio.coerceIn(0f, 1f))
            }
        }
    }

    /** Whether the panel's one-time gesture tip (tap / long-press the logo) was already shown. */
    fun isGestureTipSeen(context: Context): Boolean = runCatching {
        prefs(context).getBoolean(KEY_GESTURE_TIP_SEEN, false)
    }.getOrDefault(true)

    fun setGestureTipSeen(context: Context) {
        runCatching { prefs(context).edit { putBoolean(KEY_GESTURE_TIP_SEEN, true) } }
    }

    /** Whether the user has ever dragged the bottom resize handle; until then it pulses on open. */
    fun isResizeUsed(context: Context): Boolean = runCatching {
        prefs(context).getBoolean(KEY_RESIZE_USED, false)
    }.getOrDefault(true)

    fun setResizeUsed(context: Context) {
        runCatching { prefs(context).edit { putBoolean(KEY_RESIZE_USED, true) } }
    }

    /** Event list height the tester dragged the panel to, in dp, or `null` for the default. */
    fun panelHeightDp(context: Context): Float? = runCatching {
        prefs(context).getFloat(KEY_PANEL_HEIGHT_DP, 0f).takeIf { it > 0f }
    }.getOrNull()

    /** Saves the panel height, or forgets it when [heightDp] is `null` (back to the default). */
    fun setPanelHeightDp(context: Context, heightDp: Float?) {
        runCatching {
            prefs(context).edit {
                if (heightDp == null) remove(KEY_PANEL_HEIGHT_DP) else putFloat(KEY_PANEL_HEIGHT_DP, heightDp)
            }
        }
    }

    /** Panel opacity the tester picked by long-pressing the logo, or `null` if they never did. */
    fun panelAlpha(context: Context): Float? = runCatching {
        prefs(context).getFloat(KEY_PANEL_ALPHA, 0f).takeIf { it > 0f }
    }.getOrNull()

    fun setPanelAlpha(context: Context, alpha: Float) {
        runCatching { prefs(context).edit { putFloat(KEY_PANEL_ALPHA, alpha) } }
    }

    /**
     * Ends the stored session: both the expiry and the parked position go, so the next debug-mode
     * session starts from the default corner.
     */
    fun clear(context: Context) {
        runCatching {
            prefs(context).edit {
                remove(KEY_EXPIRY_AT)
                remove(KEY_BUBBLE_ON_RIGHT)
                remove(KEY_BUBBLE_Y_RATIO)
            }
        }
    }
}
