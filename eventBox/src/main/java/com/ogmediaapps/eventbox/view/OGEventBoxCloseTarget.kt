package com.ogmediaapps.eventbox.view

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.ogmediaapps.eventbox.R
import com.ogmediaapps.eventbox.safely
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.ANIMATION_MS
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.CLOSE_TARGET_ENTER_SCALE
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.CLOSE_TARGET_HIDE_SLACK
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.CLOSE_TARGET_NEAR_RADIUS
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.CLOSE_TARGET_NEAR_SCALE
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.CLOSE_TARGET_OVER_RADIUS
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.CLOSE_TARGET_OVER_SCALE
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.CLOSE_TARGET_REVEAL_HEIGHT
import com.ogmediaapps.eventbox.view.OGEventBoxViewConstants.DRAG_ANIMATION_MS
import kotlin.math.hypot

/**
 * The "drop here to close" target that appears at the bottom while the box is dragged. It owns the
 * target's window, its animation and its on/off state; [OGEventBoxView] only feeds it the drag distance.
 */
internal class OGEventBoxCloseTarget(private val box: OGEventBoxView) {
    private val context = box.context
    private val sizePx = box.resources.getDimensionPixelSize(R.dimen.og_event_box_close_size)
    private val marginPx = box.resources.getDimensionPixelSize(R.dimen.og_event_box_close_margin)
    private val slackPx = box.resources.getDimensionPixelSize(R.dimen.og_event_box_close_slack)

    private var targetView: View? = null
    private var targetIcon: ImageView? = null
    private var shown = false

    /** Centre of the target on screen, in the same coordinates as a raw touch. */
    fun centerX(): Float = box.screenWidth() / 2f
    fun centerY(): Float = box.screenHeight() - marginPx - sizePx / 2f

    /** True if a drag [distance] from the target centre is close enough to drop onto it. */
    fun isOver(distance: Float): Boolean = shown && distance <= sizePx * CLOSE_TARGET_OVER_RADIUS

    /** Removes the target and forgets that it was shown. */
    fun reset() {
        remove()
        shown = false
    }

    fun track(distance: Float) {
        val reveal = revealRadius()
        when {
            !shown && distance <= reveal -> show(true)
            shown && distance > reveal * CLOSE_TARGET_HIDE_SLACK -> show(false)
        }
        if (shown) updateCloseTarget(distance)
    }

    private fun revealRadius(): Float = hypot(box.screenWidth() * 0.5f, box.screenHeight() * CLOSE_TARGET_REVEAL_HEIGHT)

    fun show(visible: Boolean) {
        if (shown == visible) return
        shown = visible
        val icon = if (visible) addWindow() else targetIcon
        if (icon == null) return
        icon.animate().cancel()
        if (visible) {
            icon.alpha = 0f
            icon.scaleX = CLOSE_TARGET_ENTER_SCALE
            icon.scaleY = CLOSE_TARGET_ENTER_SCALE
            icon.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(ANIMATION_MS)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        } else {
            icon.animate().alpha(0f)
                .scaleX(CLOSE_TARGET_ENTER_SCALE)
                .scaleY(CLOSE_TARGET_ENTER_SCALE)
                .setDuration(ANIMATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { if (!shown) remove() }
                .start()
        }
    }

    private fun addWindow(): ImageView? {
        targetView?.let { return targetIcon }
        val view = LayoutInflater.from(context).inflate(R.layout.og_event_box_close, box, false)
        val icon = view.findViewById<ImageView>(R.id.og_event_box_close_target)
        val manager = box.boxWindowManager
        val added = if (manager != null) {
            runCatching { manager.addView(view, closeTargetWindowParams()) }.isSuccess
        } else {
            (box.parent as? ViewGroup)?.addView(
                view,
                FrameLayout.LayoutParams(windowSizePx(), windowSizePx(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = marginPx - slackPx
                },
            ) != null
        }
        if (!added) return null
        targetView = view
        targetIcon = icon
        return icon
    }

    fun remove() {
        val view = targetView ?: return
        targetView = null
        targetIcon = null
        val manager = box.boxWindowManager
        safely {
            if (manager != null) manager.removeViewImmediate(view) else (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    private fun windowSizePx() = sizePx + slackPx * 2

    private fun closeTargetWindowParams() = WindowManager.LayoutParams(
        windowSizePx(),
        windowSizePx(),
        box.boxWindowParams?.type ?: WindowManager.LayoutParams.TYPE_APPLICATION,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (box.screenWidth() - windowSizePx()) / 2
        y = box.screenHeight() - marginPx - sizePx - slackPx
        token = box.hostActivity()?.window?.attributes?.token
        windowAnimations = 0
    }

    private fun updateCloseTarget(distance: Float) {
        val icon = targetIcon ?: return
        val reach = sizePx.toFloat()
        val scale = when {
            distance <= reach * CLOSE_TARGET_OVER_RADIUS -> CLOSE_TARGET_OVER_SCALE
            distance <= reach * CLOSE_TARGET_NEAR_RADIUS -> CLOSE_TARGET_NEAR_SCALE
            else -> 1f
        }
        if (icon.scaleX == scale) return
        icon.animate().alpha(1f).scaleX(scale).scaleY(scale)
            .setDuration(DRAG_ANIMATION_MS)
            .setInterpolator(OvershootInterpolator(2.5f))
            .start()
    }
}
