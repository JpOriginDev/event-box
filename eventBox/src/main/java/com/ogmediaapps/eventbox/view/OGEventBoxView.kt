package com.ogmediaapps.eventbox.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.ogmediaapps.eventbox.OGEventBoxLog
import com.ogmediaapps.eventbox.R
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The floating OGEventBox overlay view component (testing overlay).
 *
 * Implements the MVVM View layer: binds to [OGEventBoxViewModel] for reactive state
 * and dispatches UI events (drag, expand, collapse, clear, pin, and drop-to-close).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class OGEventBoxView(context: Context, private val viewModel: OGEventBoxViewModel) : FrameLayout(context) {

    private val panel: LinearLayout
    private val header: View
    private val countView: TextView
    private val clearButton: TextView
    private val pinButton: ImageButton
    private val minimizeButton: ImageButton
    private val scrollView: ScrollView
    private val listContainer: LinearLayout
    private val emptyView: TextView
    private val bubble: View
    private val headerIcon: ImageView
    private val bubbleIcon: ImageView
    private val bubbleBadge: TextView

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val rowSpacing = resources.getDimensionPixelSize(R.dimen.og_event_box_row_spacing)
    private val marginPx = resources.getDimensionPixelSize(R.dimen.og_event_box_margin)
    private val closeSizePx = resources.getDimensionPixelSize(R.dimen.og_event_box_close_size)
    private val closeMarginPx = resources.getDimensionPixelSize(R.dimen.og_event_box_close_margin)
    private val closeSlackPx = resources.getDimensionPixelSize(R.dimen.og_event_box_close_slack)
    private val indexColor = ContextCompat.getColor(context, R.color.og_event_box_index)
    private val timeColor = ContextCompat.getColor(context, R.color.og_event_box_time)
    private val nameColor = ContextCompat.getColor(context, R.color.og_event_box_name)
    private val latestColor = ContextCompat.getColor(context, R.color.og_event_box_name_latest)
    private val pinnedTint = ContextCompat.getColor(context, R.color.og_event_box_accent)
    private val iconTint = ContextCompat.getColor(context, R.color.og_event_box_name)

    private var boxWindowManager: WindowManager? = null
    private var boxWindowParams: WindowManager.LayoutParams? = null

    private var closeTargetView: View? = null
    private var closeTargetIcon: ImageView? = null
    private var closeTargetShown = false

    private var dragInProgress = false
    private var settleAnimator: ValueAnimator? = null

    private var scope: CoroutineScope? = null
    private val autoShrinkRunnable = Runnable { if (!viewModel.isPinned) collapse(animate = true) }
    private val bubbleFadeRunnable = Runnable {
        bubble.animate().alpha(BUBBLE_IDLE_ALPHA).setDuration(BUBBLE_FADE_MS).start()
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.og_event_box, this, true)
        panel = findViewById(R.id.og_event_box_panel)
        header = findViewById(R.id.og_event_box_header)
        headerIcon = findViewById(R.id.og_event_box_header_icon)
        countView = findViewById(R.id.og_event_box_count)
        clearButton = findViewById(R.id.og_event_box_clear)
        pinButton = findViewById(R.id.og_event_box_pin)
        minimizeButton = findViewById(R.id.og_event_box_minimize)
        scrollView = findViewById(R.id.og_event_box_scroll)
        listContainer = findViewById(R.id.og_event_box_list)
        emptyView = findViewById(R.id.og_event_box_empty)
        bubble = findViewById(R.id.og_event_box_bubble)
        bubbleIcon = findViewById(R.id.og_event_box_bubble_icon)
        bubbleBadge = findViewById(R.id.og_event_box_bubble_badge)

        clearButton.setOnClickListener {
            viewModel.clearEvents()
            scheduleAutoShrink()
        }
        minimizeButton.setOnClickListener { collapse(animate = true) }
        headerIcon.setOnClickListener { collapse(animate = true) }
        headerIcon.contentDescription = context.getString(R.string.og_event_box_minimize)
        pinButton.setOnClickListener {
            viewModel.togglePin()
            updatePinButton()
            scheduleAutoShrink()
        }
        header.setOnTouchListener(DragTouchListener(fromBubble = false))
        bubble.setOnTouchListener(DragTouchListener(fromBubble = true))
        bubble.setOnClickListener { expand(animate = true) }

        applyLogo()
        updatePinButton()
        applyCollapsedState(viewModel.isCollapsed)
    }

    private fun applyLogo() {
        styleBubble()
        applyLogoTo(bubbleIcon)
        applyLogoTo(headerIcon)
    }

    private fun styleBubble() {
        val borderWidth = bubbleBorderWidthPx()
        val borderColor = OGEventBoxLog.bubbleBorderColor
            .takeIf { it != 0 }
            ?: ContextCompat.getColor(context, R.color.og_event_box_on_accent)
        val radius = bubbleCornerRadiusPx()
        val discColor = OGEventBoxLog.bubbleBackgroundColor
            .takeIf { it != 0 }
            ?: ContextCompat.getColor(context, R.color.og_event_box_accent)

        bubbleIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(discColor)
        }
        headerIcon.backgroundTintList = OGEventBoxLog.bubbleBackgroundColor
            .takeIf { it != 0 }
            ?.let { ColorStateList.valueOf(it) }

        bubbleIcon.foreground = if (borderWidth <= 0) null else GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(borderWidth, borderColor)
        }
    }

    private fun bubbleBorderWidthPx(): Int = OGEventBoxLog.bubbleBorderWidthDp
        .takeIf { it >= 0f }?.let { dpToPx(it) }
        ?: dpToPx(DEFAULT_BORDER_WIDTH_DP)

    private fun bubbleCornerRadiusPx(): Float {
        val size = resources.getDimensionPixelSize(R.dimen.og_event_box_bubble_icon_size).toFloat()
        val requested = OGEventBoxLog.bubbleCornerRadiusDp
        return if (requested < 0f) size / 2f else dpToPx(requested).toFloat().coerceAtMost(size / 2f)
    }

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun applyLogoTo(icon: ImageView) {
        icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = if (view === bubbleIcon) bubbleCornerRadiusPx() else min(view.width, view.height) / 2f
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        icon.clipToOutline = true
        val logo = OGEventBoxLog.bubbleIconResId
        if (logo == 0) return
        runCatching { icon.setImageResource(logo) }.onFailure { return }
        icon.imageTintList = null
        val scaleType = OGEventBoxLog.bubbleIconScaleType ?: ImageView.ScaleType.FIT_CENTER
        icon.scaleType = scaleType
        val fillsCircle = scaleType == ImageView.ScaleType.CENTER_CROP || scaleType == ImageView.ScaleType.FIT_XY
        val inset = if (icon === bubbleIcon && !fillsCircle) bubbleBorderWidthPx() + dpToPx(LOGO_INSET_DP) else 0
        icon.setPadding(inset, inset, inset, inset)
    }

    fun bindWindow(manager: WindowManager?, params: WindowManager.LayoutParams?) {
        boxWindowManager = manager
        boxWindowParams = params
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        newScope.launch {
            viewModel.events.collect { renderEvents(it) }
        }
        if (viewModel.isCollapsed) highlightBubble(scheduleFade = true) else scheduleAutoShrink()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(autoShrinkRunnable)
        removeCallbacks(bubbleFadeRunnable)
        settleAnimator?.cancel()
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    fun onDetaching() {
        removeCallbacks(autoShrinkRunnable)
        removeCallbacks(bubbleFadeRunnable)
        settleAnimator?.cancel()
        removeCloseTargetWindow()
        closeTargetShown = false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && panel.isVisible) scheduleAutoShrink()
        return false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!dragInProgress) applyPosition()
    }

    private fun renderEvents(events: List<OGEventBoxDebugEvent>) {
        countView.text = String.format(Locale.US, "%d", events.size)
        emptyView.isVisible = events.isEmpty()

        while (listContainer.childCount > events.size) {
            listContainer.removeViewAt(listContainer.childCount - 1)
        }
        while (listContainer.childCount < events.size) {
            listContainer.addView(createRow())
        }
        events.forEachIndexed { position, event ->
            bindRow(listContainer.getChildAt(position) as TextView, event, isLatest = position == events.lastIndex)
        }

        if (viewModel.isCollapsed) {
            updateBadge(events)
        } else {
            viewModel.lastSeenIndex = events.lastOrNull()?.index ?: 0
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun createRow(): TextView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setPadding(0, rowSpacing, 0, rowSpacing)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(nameColor)
        typeface = Typeface.MONOSPACE
        maxLines = 2
    }

    private fun bindRow(row: TextView, event: OGEventBoxDebugEvent, isLatest: Boolean) {
        val key = "${event.index}|$isLatest"
        if (row.tag == key) return
        row.tag = key
        row.text = SpannableStringBuilder().apply {
            val indexText = String.format(Locale.US, "%02d  ", event.index)
            append(indexText)
            setSpan(ForegroundColorSpan(indexColor), 0, indexText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val timeStart = length
            append(timeFormat.format(Date(event.timestampMillis))).append("  ")
            setSpan(ForegroundColorSpan(timeColor), timeStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val nameStart = length
            append(event.name)
            setSpan(ForegroundColorSpan(if (isLatest) latestColor else nameColor), nameStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (isLatest) setSpan(StyleSpan(Typeface.BOLD), nameStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun updateBadge(events: List<OGEventBoxDebugEvent>) {
        val unread = viewModel.getUnreadCount(events)
        bubbleBadge.isVisible = unread > 0
        if (unread <= 0) return
        bubbleBadge.text = if (unread > 99) "99+" else String.format(Locale.US, "%d", unread)
        val size = dpToPx(
            when {
                unread > 99 -> BADGE_SIZE_OVERFLOW_DP
                unread > 9 -> BADGE_SIZE_TWO_DIGIT_DP
                else -> BADGE_SIZE_ONE_DIGIT_DP
            }
        )
        val params = bubbleBadge.layoutParams
        if (params.width != size || params.height != size) {
            params.width = size
            params.height = size
            bubbleBadge.layoutParams = params
        }
    }

    private fun collapse(animate: Boolean) {
        if (viewModel.isCollapsed) return
        viewModel.collapse()
        removeCallbacks(autoShrinkRunnable)
        applyCollapsedState(collapsed = true)
        updateBadge(viewModel.events.value)
        if (animate) {
            panel.animate().cancel()
            bubble.alpha = 0f
            bubble.scaleX = 0.5f
            bubble.scaleY = 0.5f
            bubble.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(ANIMATION_MS)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }
        highlightBubble(scheduleFade = true)
        val resting = snapPosition(positionX(), positionY())
        persistBubblePosition(resting.x, resting.y)
    }

    private fun expand(animate: Boolean) {
        if (!viewModel.isCollapsed) return
        viewModel.expand()
        removeCallbacks(bubbleFadeRunnable)
        applyCollapsedState(collapsed = false)
        renderEvents(viewModel.events.value)
        if (animate) {
            bubble.animate().cancel()
            panel.alpha = 0f
            panel.scaleX = 0.8f
            panel.scaleY = 0.8f
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(ANIMATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        scheduleAutoShrink()
    }

    private fun applyCollapsedState(collapsed: Boolean) {
        val panelWidth = screenWidth() - marginPx * 2
        if (panelWidth > 0 && panel.layoutParams.width != panelWidth) {
            panel.layoutParams = panel.layoutParams.apply { width = panelWidth }
        }
        panel.isVisible = !collapsed
        bubble.isVisible = collapsed
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        bubble.alpha = 1f
        bubble.scaleX = 1f
        bubble.scaleY = 1f
        panel.pivotX = 0f
        panel.pivotY = 0f
        requestLayout()
    }

    private fun scheduleAutoShrink() {
        removeCallbacks(autoShrinkRunnable)
        if (viewModel.isPinned || viewModel.isCollapsed) return
        postDelayed(autoShrinkRunnable, AUTO_SHRINK_DELAY_MS)
    }

    private fun highlightBubble(scheduleFade: Boolean) {
        removeCallbacks(bubbleFadeRunnable)
        if (bubble.alpha != 1f) {
            bubble.animate().alpha(1f).setDuration(DRAG_ANIMATION_MS).start()
        }
        if (scheduleFade) postDelayed(bubbleFadeRunnable, BUBBLE_HIGHLIGHT_MS)
    }

    private fun updatePinButton() {
        val pinned = viewModel.isPinned
        pinButton.setImageResource(if (pinned) R.drawable.og_event_box_ic_unpin else R.drawable.og_event_box_ic_pin)
        pinButton.setColorFilter(if (pinned) pinnedTint else iconTint)
        pinButton.contentDescription = context.getString(
            if (pinned) R.string.og_event_box_auto_shrink_off else R.string.og_event_box_auto_shrink_on
        )
    }

    private inner class DragTouchListener(private val fromBubble: Boolean) : OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0f
        private var startY = 0f
        private var dragging = false
        private var gestureActive = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    settleAnimator?.cancel()
                    startX = positionX()
                    startY = positionY()
                    dragging = false
                    gestureActive = true
                    removeCallbacks(autoShrinkRunnable)
                    if (fromBubble) highlightBubble(scheduleFade = false)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        dragInProgress = true
                        if (fromBubble) {
                            bubble.animate().scaleX(BUBBLE_DRAG_SCALE).scaleY(BUBBLE_DRAG_SCALE)
                                .setDuration(DRAG_ANIMATION_MS)
                                .setInterpolator(OvershootInterpolator(2f))
                                .start()
                        }
                    }
                    if (dragging) {
                        val position = clampPosition(startX + dx, startY + dy)
                        moveTo(position.x, position.y)
                        trackCloseTarget(closeTargetDistance(event.rawX, event.rawY))
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!gestureActive) return true
                    gestureActive = false
                    dragInProgress = false
                    val wasDragging = dragging
                    dragging = false
                    if (!wasDragging) {
                        showCloseTarget(false)
                        view.performClick()
                        scheduleAutoShrink()
                        if (fromBubble && viewModel.isCollapsed) highlightBubble(scheduleFade = true)
                        return true
                    }
                    val dropOnClose = event.actionMasked == MotionEvent.ACTION_UP &&
                            isOverCloseTarget(event.rawX, event.rawY)
                    showCloseTarget(false)
                    if (dropOnClose) {
                        viewModel.closeTargetDropped()
                    } else {
                        settle(fromBubble)
                        scheduleAutoShrink()
                        if (fromBubble) highlightBubble(scheduleFade = true)
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun settle(fromBubble: Boolean) {
        val resting = snapPosition(positionX(), positionY())
        viewModel.anchorX = resting.x
        viewModel.anchorY = resting.y
        if (viewModel.isCollapsed) persistBubblePosition(resting.x, resting.y)
        if (fromBubble) {
            bubble.animate().scaleX(1f).scaleY(1f).setDuration(DRAG_ANIMATION_MS).start()
        }
        animateTo(resting.x, resting.y)
    }

    private fun animateTo(x: Float, y: Float) {
        val fromX = positionX()
        val fromY = positionY()
        settleAnimator?.cancel()
        settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_ANIMATION_MS
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                moveTo(fromX + (x - fromX) * fraction, fromY + (y - fromY) * fraction)
            }
            start()
        }
    }

    private fun trackCloseTarget(distance: Float) {
        val reveal = revealRadius()
        when {
            !closeTargetShown && distance <= reveal -> showCloseTarget(true)
            closeTargetShown && distance > reveal * CLOSE_TARGET_HIDE_SLACK -> showCloseTarget(false)
        }
        if (closeTargetShown) updateCloseTarget(distance)
    }

    private fun revealRadius(): Float = hypot(screenWidth() * 0.5f, screenHeight() * CLOSE_TARGET_REVEAL_HEIGHT)

    private fun showCloseTarget(visible: Boolean) {
        if (closeTargetShown == visible) return
        closeTargetShown = visible
        val icon = if (visible) addCloseTargetWindow() else closeTargetIcon
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
                .withEndAction { if (!closeTargetShown) removeCloseTargetWindow() }
                .start()
        }
    }

    private fun addCloseTargetWindow(): ImageView? {
        closeTargetView?.let { return closeTargetIcon }
        val view = LayoutInflater.from(context).inflate(R.layout.og_event_box_close, this, false)
        val icon = view.findViewById<ImageView>(R.id.og_event_box_close_target)
        val manager = boxWindowManager
        val added = if (manager != null) {
            runCatching { manager.addView(view, closeTargetWindowParams()) }.isSuccess
        } else {
            (parent as? ViewGroup)?.addView(
                view,
                LayoutParams(closeWindowSizePx(), closeWindowSizePx(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = closeMarginPx - closeSlackPx
                },
            ) != null
        }
        if (!added) return null
        closeTargetView = view
        closeTargetIcon = icon
        return icon
    }

    private fun removeCloseTargetWindow() {
        val view = closeTargetView ?: return
        closeTargetView = null
        closeTargetIcon = null
        val manager = boxWindowManager
        runCatching {
            if (manager != null) manager.removeViewImmediate(view) else (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    private fun closeWindowSizePx() = closeSizePx + closeSlackPx * 2

    private fun closeTargetWindowParams() = WindowManager.LayoutParams(
        closeWindowSizePx(),
        closeWindowSizePx(),
        WindowManager.LayoutParams.TYPE_APPLICATION,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (screenWidth() - closeWindowSizePx()) / 2
        y = screenHeight() - closeMarginPx - closeSizePx - closeSlackPx
        token = hostActivity()?.window?.attributes?.token
        windowAnimations = 0
    }

    private fun updateCloseTarget(distance: Float) {
        val icon = closeTargetIcon ?: return
        val reach = closeSizePx.toFloat()
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

    private fun isOverCloseTarget(rawX: Float, rawY: Float): Boolean =
        closeTargetShown && closeTargetDistance(rawX, rawY) <= closeSizePx * CLOSE_TARGET_OVER_RADIUS

    private fun closeTargetDistance(rawX: Float, rawY: Float): Float {
        val closeX = screenWidth() / 2f
        val closeY = screenHeight() - closeMarginPx - closeSizePx / 2f
        val fromX: Float
        val fromY: Float
        if (viewModel.isCollapsed) {
            fromX = positionX() + width / 2f
            fromY = positionY() + height / 2f
        } else {
            fromX = rawX
            fromY = rawY
        }
        return hypot(fromX - closeX, fromY - closeY)
    }

    private fun hostActivity(): Activity? = context as? Activity
    private fun hostDecor(): View? = hostActivity()?.window?.decorView
    private fun screenWidth(): Int = hostDecor()?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = hostDecor()?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

    private fun positionX(): Float = boxWindowParams?.x?.toFloat() ?: translationX
    private fun positionY(): Float = boxWindowParams?.y?.toFloat() ?: translationY

    private fun moveTo(x: Float, y: Float) {
        val params = boxWindowParams
        if (params == null) {
            translationX = x
            translationY = y
            return
        }
        val newX = x.roundToInt()
        val newY = y.roundToInt()
        if (params.x == newX && params.y == newY) return
        params.x = newX
        params.y = newY
        runCatching { boxWindowManager?.updateViewLayout(this, params) }
    }

    private fun clampPosition(x: Float, y: Float): PointF {
        val insets = systemBarInsets()
        val minX = insets.left.toFloat()
        val maxX = (screenWidth() - insets.right - width).toFloat()
        val minY = insets.top.toFloat()
        val maxY = (screenHeight() - insets.bottom - height).toFloat()
        return PointF(
            x.coerceIn(min(minX, maxX), max(minX, maxX)),
            y.coerceIn(min(minY, maxY), max(minY, maxY)),
        )
    }

    private fun snapPosition(x: Float, y: Float): PointF {
        val clamped = clampPosition(x, y)
        if (!viewModel.isCollapsed) return clamped
        val insets = systemBarInsets()
        val leftX = insets.left.toFloat()
        val rightX = (screenWidth() - insets.right - width).toFloat()
        val centerX = clamped.x + width / 2f
        clamped.x = if (centerX <= screenWidth() / 2f) min(leftX, rightX) else max(leftX, rightX)
        return clamped
    }

    private fun applyPosition() {
        if (width == 0 || screenWidth() == 0) return
        if (viewModel.anchorX.isNaN() || viewModel.anchorY.isNaN()) restorePosition()
        val position = snapPosition(viewModel.anchorX, viewModel.anchorY)
        moveTo(position.x, position.y)
    }

    private fun restorePosition() {
        val saved = viewModel.getSavedPosition(context)
        val onRightEdge = saved?.onRightEdge ?: DEFAULT_ON_RIGHT_EDGE
        val yRatio = saved?.yRatio ?: DEFAULT_Y_RATIO
        viewModel.anchorX = if (onRightEdge) screenWidth().toFloat() else -screenWidth().toFloat()
        viewModel.anchorY = screenHeight() * yRatio
    }

    private fun persistBubblePosition(x: Float, y: Float) {
        if (width == 0 || screenHeight() == 0 || x.isNaN() || y.isNaN()) return
        viewModel.persistPosition(
            context,
            onRightEdge = x + width / 2f > screenWidth() / 2f,
            yRatio = y / screenHeight()
        )
    }

    private fun systemBarInsets(): Insets =
        hostDecor()?.let { ViewCompat.getRootWindowInsets(it) }?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE

    companion object {
        private const val AUTO_SHRINK_DELAY_MS = 6_000L
        private const val BUBBLE_HIGHLIGHT_MS = 2_500L
        private const val ANIMATION_MS = 150L
        private const val DRAG_ANIMATION_MS = 120L
        private const val SNAP_ANIMATION_MS = 240L
        private const val BUBBLE_FADE_MS = 300L
        private const val DEFAULT_BORDER_WIDTH_DP = 1f
        private const val BADGE_SIZE_ONE_DIGIT_DP = 16f
        private const val BADGE_SIZE_TWO_DIGIT_DP = 18f
        private const val BADGE_SIZE_OVERFLOW_DP = 20f
        private const val LOGO_INSET_DP = 3f
        private const val BUBBLE_IDLE_ALPHA = 0.55f
        private const val BUBBLE_DRAG_SCALE = 1.15f
        private const val CLOSE_TARGET_ENTER_SCALE = 0.7f
        private const val CLOSE_TARGET_NEAR_SCALE = 1.2f
        private const val CLOSE_TARGET_OVER_SCALE = 1.45f
        private const val CLOSE_TARGET_NEAR_RADIUS = 1.8f
        private const val CLOSE_TARGET_OVER_RADIUS = 1f
        private const val CLOSE_TARGET_REVEAL_HEIGHT = 0.22f
        private const val CLOSE_TARGET_HIDE_SLACK = 1.15f
        private const val DEFAULT_ON_RIGHT_EDGE = true
        private const val DEFAULT_Y_RATIO = 0f

        fun windowParams(activity: Activity): WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            token = activity.window.attributes.token
            windowAnimations = 0
        }
    }
}
