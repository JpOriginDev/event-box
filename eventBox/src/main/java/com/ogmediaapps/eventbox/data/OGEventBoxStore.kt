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
