package com.ogmediaapps.eventbox.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.transition.AutoTransition
import android.transition.Transition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
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
    private val header: LinearLayout
    private val headerIcon: ImageView
    private val titleView: TextView
    private val countView: TextView
    private val headerSpacer: View
    private val clearButton: TextView
    private val pinButton: ImageButton
    private val resizeButton: ImageButton
    private val minimizeButton: ImageButton
    private val scrollView: ScrollView
    private val contentContainer: FrameLayout
    private val resizeHandle: View
    private val resizeHandleBar: View
    private val listContainer: LinearLayout
    private val emptyView: TextView
    private val bubble: View
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
    private var blinkAnimator: AnimatorSet? = null
    private var handleHighlightAnimator: ValueAnimator? = null
    private var isHandleHighlighted = false
    private var isHeaderMirrored: Boolean? = null
    private var expandedEventIndex: Int? = null

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
        titleView = findViewById(R.id.og_event_box_title)
        countView = findViewById(R.id.og_event_box_count)
        headerSpacer = findViewById(R.id.og_event_box_spacer)
        clearButton = findViewById(R.id.og_event_box_clear)
        pinButton = findViewById(R.id.og_event_box_pin)
        resizeButton = findViewById(R.id.og_event_box_resize)
        minimizeButton = findViewById(R.id.og_event_box_minimize)
        scrollView = findViewById(R.id.og_event_box_scroll)
        contentContainer = findViewById(R.id.og_event_box_content_container)
        resizeHandle = findViewById(R.id.og_event_box_resize_handle)
        resizeHandleBar = findViewById(R.id.og_event_box_resize_handle_bar)
        resizeHandleBar.alpha = 0.75f
        resizeHandleBar.backgroundTintList = ColorStateList.valueOf(indexColor)
        listContainer = findViewById(R.id.og_event_box_list)
        emptyView = findViewById(R.id.og_event_box_empty)
        bubble = findViewById(R.id.og_event_box_bubble)
        bubbleIcon = findViewById(R.id.og_event_box_bubble_icon)
        bubbleBadge = findViewById(R.id.og_event_box_bubble_badge)

        clearButton.setOnClickListener {
            expandedEventIndex = null
            viewModel.clearEvents()
            scheduleAutoShrink()
        }
        resizeButton.setOnClickListener {
            blinkResizeHandle()
            scheduleAutoShrink()
        }
        resizeHandle.setOnTouchListener(ResizeTouchListener())
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

        applyHeaderOrientation(isBubbleOnRightSide())
        updateResizingState()
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
        blinkAnimator?.cancel()
        handleHighlightAnimator?.cancel()
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    fun onDetaching() {
        removeCallbacks(autoShrinkRunnable)
        removeCallbacks(bubbleFadeRunnable)
        settleAnimator?.cancel()
        blinkAnimator?.cancel()
        handleHighlightAnimator?.cancel()
        removeCloseTargetWindow()
        closeTargetShown = false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && panel.isVisible) scheduleAutoShrink()
        return false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!dragInProgress && settleAnimator?.isRunning != true) applyPosition()
    }

    private fun renderEvents(events: List<OGEventBoxDebugEvent>) {
        countView.text = String.format(Locale.US, "%d", events.size)
        emptyView.isVisible = events.isEmpty()

        if (expandedEventIndex != null && events.none { it.index == expandedEventIndex }) {
            expandedEventIndex = null
        }

        while (listContainer.childCount > events.size) {
            listContainer.removeViewAt(listContainer.childCount - 1)
        }
        while (listContainer.childCount < events.size) {
            listContainer.addView(createRow())
        }
        events.forEachIndexed { position, event ->
            bindRow(listContainer.getChildAt(position), event, isLatest = position == events.lastIndex)
        }

        if (viewModel.isCollapsed) {
            updateBadge(events)
        } else {
            viewModel.lastSeenIndex = events.lastOrNull()?.index ?: 0
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private class RowViewHolder(
        val root: View,
        val header: View,
        val text: TextView,
        val warningIcon: ImageView,
        val details: View,
        val warningTitle: TextView,
        val warningEventName: TextView,
        val warningMessage: TextView,
        val divider: View,
    ) {
        var eventIndex: Int = -1
        var bindKey: String? = null
    }

    private fun createRow(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.og_event_box_item, listContainer, false)
        val holder = RowViewHolder(
            root = view,
            header = view.findViewById(R.id.og_event_box_item_header),
            text = view.findViewById(R.id.og_event_box_item_text),
            warningIcon = view.findViewById(R.id.og_event_box_item_warning),
            details = view.findViewById(R.id.og_event_box_item_details),
            warningTitle = view.findViewById(R.id.og_event_box_item_warning_title),
            warningEventName = view.findViewById(R.id.og_event_box_item_warning_event_name),
            warningMessage = view.findViewById(R.id.og_event_box_item_warning_message),
            divider = view.findViewById(R.id.og_event_box_item_divider)
        )
        holder.header.setPadding(0, rowSpacing / 2, 0, rowSpacing / 2)
        view.tag = holder
        return view
    }

    private fun bindRow(rowView: View, event: OGEventBoxDebugEvent, isLatest: Boolean) {
        val holder = rowView.tag as? RowViewHolder ?: return
        val isExpanded = (expandedEventIndex == event.index)
        val key = "${event.index}|$isLatest|${event.hasWarning}|$isExpanded"
        holder.eventIndex = event.index
        if (holder.bindKey == key) return
        holder.bindKey = key

        holder.text.text = SpannableStringBuilder().apply {
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

        holder.divider.isVisible = !isLatest

        holder.warningIcon.isVisible = event.hasWarning
        if (event.hasWarning) {
            holder.warningEventName.text = "Event: ${event.name}"
            holder.warningMessage.text = event.warnings.joinToString("\n") { "• $it" }
            holder.details.isVisible = isExpanded
            holder.warningIcon.setOnClickListener {
                handleWarningIconClicked(event, holder)
            }
        } else {
            holder.details.isVisible = false
            holder.warningIcon.setOnClickListener(null)
        }
    }

    private fun handleWarningIconClicked(event: OGEventBoxDebugEvent, holder: RowViewHolder) {
        scheduleAutoShrink()
        if (expandedEventIndex == event.index) {
            expandedEventIndex = null
            holder.bindKey = null
            val collapseTransition = AutoTransition().apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
            }
            TransitionManager.beginDelayedTransition(listContainer, collapseTransition)
            holder.details.visibility = View.GONE
        } else {
            val prevIndex = expandedEventIndex
            expandedEventIndex = event.index
            holder.bindKey = null

            val prevRow = if (prevIndex != null) findRowByEventIndex(prevIndex) else null
            val prevHolder = prevRow?.tag as? RowViewHolder
            val prevDetails = prevHolder?.details

            if (prevDetails != null && prevDetails.visibility == View.VISIBLE) {
                prevHolder.bindKey = null
                val collapseTransition = AutoTransition().apply {
                    duration = 180
                    interpolator = AccelerateDecelerateInterpolator()
                    addListener(object : Transition.TransitionListener {
                        override fun onTransitionEnd(transition: Transition) {
                            if (expandedEventIndex == event.index) {
                                val expandTransition = AutoTransition().apply {
                                    duration = 200
                                    interpolator = AccelerateDecelerateInterpolator()
                                }
                                TransitionManager.beginDelayedTransition(listContainer, expandTransition)
                                holder.details.visibility = View.VISIBLE
                                scrollToRow(holder.root)
                            }
                        }
                        override fun onTransitionStart(transition: Transition) {}
                        override fun onTransitionCancel(transition: Transition) {}
                        override fun onTransitionPause(transition: Transition) {}
                        override fun onTransitionResume(transition: Transition) {}
                    })
                }
                TransitionManager.beginDelayedTransition(listContainer, collapseTransition)
                prevDetails.visibility = View.GONE
            } else {
                val expandTransition = AutoTransition().apply {
                    duration = 200
                    interpolator = AccelerateDecelerateInterpolator()
                }
                TransitionManager.beginDelayedTransition(listContainer, expandTransition)
                holder.details.visibility = View.VISIBLE
                scrollToRow(holder.root)
            }
        }
    }

    private fun findRowByEventIndex(index: Int): View? {
        for (i in 0 until listContainer.childCount) {
            val child = listContainer.getChildAt(i)
            val h = child.tag as? RowViewHolder
            if (h?.eventIndex == index) return child
        }
        return null
    }

    private fun scrollToRow(view: View) {
        scrollView.post {
            val rect = Rect()
            view.getDrawingRect(rect)
            scrollView.offsetDescendantRectToMyCoords(view, rect)
            scrollView.smoothScrollTo(0, rect.top)
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

    private fun isBubbleOnRightSide(): Boolean {
        if (!viewModel.anchorX.isNaN()) {
            return viewModel.anchorX > screenWidth() / 2f
        }
        if (viewModel.isCollapsed && positionX() > 0f) {
            return (positionX() + width / 2f) > screenWidth() / 2f
        }
        val saved = viewModel.getSavedPosition(context)
        return saved?.onRightEdge ?: DEFAULT_ON_RIGHT_EDGE
    }

    private fun applyHeaderOrientation(isRightSide: Boolean) {
        val bubbleLp = bubble.layoutParams as? LayoutParams
        val targetBubbleGravity = Gravity.TOP or (if (isRightSide) Gravity.END else Gravity.START)
        if (bubbleLp != null && bubbleLp.gravity != targetBubbleGravity) {
            bubbleLp.gravity = targetBubbleGravity
            bubble.layoutParams = bubbleLp
        }

        if (isHeaderMirrored == isRightSide) return
        isHeaderMirrored = isRightSide

        header.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).roundToInt()

        if (isRightSide) {
            // Mirrored layout for right-side bubble:
            // [Clear] [Minimize] [Resize] [Pin] ---Spacer--- [Title] [Count] [Logo]
            val clearLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32f)
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 0
            }
            val minLp = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(6f)
            }
            val resizeLp = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(4f)
            }
            val pinLp = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(4f)
            }
            val spacerLp = LinearLayout.LayoutParams(0, dp(1f), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 0
            }
            val countLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(8f)
            }
            val iconLp = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(10f)
            }

            header.addView(clearButton, clearLp)
            header.addView(minimizeButton, minLp)
            header.addView(resizeButton, resizeLp)
            header.addView(pinButton, pinLp)
            header.addView(headerSpacer, spacerLp)
            header.addView(titleView, titleLp)
            header.addView(countView, countLp)
            header.addView(headerIcon, iconLp)
        } else {
            // Standard layout for left-side bubble:
            // [Logo] [Title] [Count] ---Spacer--- [Pin] [Resize] [Minimize] [Clear]
            val iconLp = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 0
            }
            val titleLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(10f)
            }
            val countLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(8f)
            }
            val spacerLp = LinearLayout.LayoutParams(0, dp(1f), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val pinLp = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 0
            }
            val resizeLp = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(4f)
            }
            val minLp = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(4f)
            }
            val clearLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32f)
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(6f)
            }

            header.addView(headerIcon, iconLp)
            header.addView(titleView, titleLp)
            header.addView(countView, countLp)
            header.addView(headerSpacer, spacerLp)
            header.addView(pinButton, pinLp)
            header.addView(resizeButton, resizeLp)
            header.addView(minimizeButton, minLp)
            header.addView(clearButton, clearLp)
        }

        resizeButton.isVisible = OGEventBoxLog.isPanelResizingEnabled()
    }

    private fun collapse(animate: Boolean) {
        if (viewModel.isCollapsed) return
        viewModel.collapse()
        removeCallbacks(autoShrinkRunnable)

        val isRight = isBubbleOnRightSide()
        val panelWidth = (if (panel.width > 0) panel.width else (screenWidth() - marginPx * 2)).toFloat()
        val pivotX = if (isRight) panelWidth - dpToPx(27f) else dpToPx(27f).toFloat()
        val pivotY = dpToPx(27f).toFloat()
        panel.pivotX = pivotX
        panel.pivotY = pivotY

        if (animate) {
            panel.animate().cancel()
            bubble.animate().cancel()
            panel.animate()
                .alpha(0f)
                .scaleX(0.65f)
                .scaleY(0.65f)
                .setDuration(130L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .withEndAction {
                    applyCollapsedState(collapsed = true)
                    updateBadge(viewModel.events.value)
                    if (isRight) {
                        // The window shrinks and moves to the right edge; keep the bubble hidden
                        // until that has been laid out, so it never shows mid-move (a slide).
                        bubble.visibility = View.INVISIBLE
                        applyBubbleRestingPosition(isRight)
                        animateBubbleEntryWhenSettled()
                    } else {
                        applyBubbleRestingPosition(isRight)
                        animateBubbleEntry()
                    }
                }
                .start()
        } else {
            applyCollapsedState(collapsed = true)
            updateBadge(viewModel.events.value)
            applyBubbleRestingPosition(isRight)
        }
    }

    private fun animateBubbleEntryWhenSettled() {
        var started = false
        val listener = object : OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int,
            ) {
                if (started) return
                started = true
                removeOnLayoutChangeListener(this)
                postDelayed({ showBubbleAfterMove() }, 60L)
            }
        }
        addOnLayoutChangeListener(listener)
        postDelayed({
            if (!started) {
                started = true
                removeOnLayoutChangeListener(listener)
                showBubbleAfterMove()
            }
        }, 150L)
    }

    private fun showBubbleAfterMove() {
        if (!viewModel.isCollapsed) return
        bubble.alpha = 0f
        bubble.visibility = View.VISIBLE
        animateBubbleEntry()
    }

    private fun animateBubbleEntry() {
        bubble.alpha = 0f
        bubble.scaleX = 0.5f
        bubble.scaleY = 0.5f
        bubble.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ANIMATION_MS)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
        highlightBubble(scheduleFade = true)
    }

    private fun bubbleTotalWidth(): Int =
        resources.getDimensionPixelSize(R.dimen.og_event_box_bubble_size) + marginPx * 2

    private fun bubbleTotalHeight(): Int =
        resources.getDimensionPixelSize(R.dimen.og_event_box_bubble_size) + marginPx * 2

    private fun applyBubbleRestingPosition(isRight: Boolean) {
        val insets = systemBarInsets()
        val totalWidth = bubbleTotalWidth()
        val totalHeight = bubbleTotalHeight()
        val leftX = insets.left.toFloat()
        val rightX = (screenWidth() - insets.right - totalWidth).toFloat()
        val restingX = if (isRight) max(leftX, rightX) else min(leftX, rightX)
        val minY = insets.top.toFloat()
        val maxY = (screenHeight() - insets.bottom - totalHeight).toFloat()
        val clampedY = positionY().coerceIn(min(minY, maxY), max(minY, maxY))
        viewModel.anchorX = restingX
        viewModel.anchorY = clampedY
        persistBubblePosition(restingX, clampedY)
        moveTo(restingX, clampedY)
    }

    private fun expand(animate: Boolean) {
        if (!viewModel.isCollapsed) return
        viewModel.expand()
        removeCallbacks(bubbleFadeRunnable)

        val isRight = isBubbleOnRightSide()
        applyHeaderOrientation(isRight)

        val insets = systemBarInsets()
        moveTo(insets.left.toFloat(), positionY())

        applyCollapsedState(collapsed = false)
        renderEvents(viewModel.events.value)

        val panelWidth = (if (panel.width > 0) panel.width else (screenWidth() - marginPx * 2)).toFloat()
        val pivotX = if (isRight) panelWidth - dpToPx(27f) else dpToPx(27f).toFloat()
        val pivotY = dpToPx(27f).toFloat()
        panel.pivotX = pivotX
        panel.pivotY = pivotY

        if (animate) {
            bubble.animate().cancel()
            panel.animate().cancel()
            panel.alpha = 0f
            panel.scaleX = 0.65f
            panel.scaleY = 0.65f
            panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
        scheduleAutoShrink()
    }

    private fun applyCollapsedState(collapsed: Boolean) {
        val panelWidth = screenWidth() - marginPx * 2
        if (panelWidth > 0 && panel.layoutParams.width != panelWidth) {
            panel.layoutParams = panel.layoutParams.apply { width = panelWidth }
        }
        if (!collapsed && OGEventBoxLog.isPanelResizingEnabled()) {
            val custom = viewModel.customPanelHeight
            if (custom != null && custom > 0 && contentContainer.layoutParams.height != custom) {
                contentContainer.layoutParams.height = custom
            }
        }
        panel.isVisible = !collapsed
        bubble.isVisible = collapsed
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        bubble.alpha = 1f
        bubble.scaleX = 1f
        bubble.scaleY = 1f
        val isRight = isBubbleOnRightSide()
        val bubbleLp = bubble.layoutParams as? LayoutParams
        val targetBubbleGravity = Gravity.TOP or (if (isRight) Gravity.END else Gravity.START)
        if (bubbleLp != null && bubbleLp.gravity != targetBubbleGravity) {
            bubbleLp.gravity = targetBubbleGravity
            bubble.layoutParams = bubbleLp
        }
        val pw = if (panel.width > 0) panel.width else panelWidth
        panel.pivotX = if (isRight) (pw - dpToPx(27f)).toFloat() else dpToPx(27f).toFloat()
        panel.pivotY = dpToPx(27f).toFloat()
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

    fun updateResizingState() {
        val enabled = OGEventBoxLog.isPanelResizingEnabled()
        resizeButton.isVisible = enabled
        resizeHandle.isVisible = enabled
        if (!enabled) {
            setHandleHighlighted(false, animate = false)
            contentContainer.layoutParams.height = resources.getDimensionPixelSize(R.dimen.og_event_box_list_height)
            contentContainer.requestLayout()
            viewModel.customPanelHeight = null
        }
    }

    private fun setHandleHighlighted(highlighted: Boolean, animate: Boolean = true) {
        if (isHandleHighlighted == highlighted) return
        isHandleHighlighted = highlighted

        handleHighlightAnimator?.cancel()
        resizeHandleBar.animate().cancel()

        val targetScaleX = if (highlighted) 1.55f else 1.0f
        val targetScaleY = if (highlighted) 1.25f else 1.0f
        val targetAlpha = if (highlighted) 1.0f else 0.75f
        val targetColor = if (highlighted) {
            OGEventBoxLog.bubbleBackgroundColor.takeIf { it != 0 }
                ?: ContextCompat.getColor(context, R.color.og_event_box_name_latest)
        } else {
            indexColor
        }

        if (!animate) {
            resizeHandleBar.scaleX = targetScaleX
            resizeHandleBar.scaleY = targetScaleY
            resizeHandleBar.alpha = targetAlpha
            resizeHandleBar.backgroundTintList = ColorStateList.valueOf(targetColor)
            return
        }

        resizeHandleBar.animate()
            .scaleX(targetScaleX)
            .scaleY(targetScaleY)
            .alpha(targetAlpha)
            .setDuration(160L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val startColor = resizeHandleBar.backgroundTintList?.defaultColor ?: indexColor
        handleHighlightAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, targetColor).apply {
            duration = 160L
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                resizeHandleBar.backgroundTintList = ColorStateList.valueOf(color)
            }
            start()
        }
    }

    private fun blinkResizeHandle() {
        blinkAnimator?.cancel()
        handleHighlightAnimator?.cancel()
        resizeHandleBar.animate().cancel()
        isHandleHighlighted = false

        val highlightColor = OGEventBoxLog.bubbleBackgroundColor.takeIf { it != 0 }
            ?: ContextCompat.getColor(context, R.color.og_event_box_name_latest)
        resizeHandleBar.backgroundTintList = ColorStateList.valueOf(highlightColor)

        // Two complete blinks: 1f -> 0.15f -> 1f -> 0.15f -> 0.75f
        val alphaAnim = ObjectAnimator.ofFloat(resizeHandleBar, View.ALPHA, 1f, 0.15f, 1f, 0.15f, 0.75f)
        val scaleXAnim = ObjectAnimator.ofFloat(resizeHandleBar, View.SCALE_X, 1.4f, 1f, 1.4f, 1f, 1.0f)
        val scaleYAnim = ObjectAnimator.ofFloat(resizeHandleBar, View.SCALE_Y, 1.25f, 1f, 1.25f, 1f, 1.0f)

        blinkAnimator = AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim)
            duration = 750L
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setHandleHighlighted(false, animate = false)
                }
                override fun onAnimationCancel(animation: Animator) {
                    setHandleHighlighted(false, animate = false)
                }
            })
            start()
        }
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

    private inner class ResizeTouchListener : OnTouchListener {
        private var downRawY = 0f
        private var initialHeight = 0
        private var isResizing = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (!OGEventBoxLog.isPanelResizingEnabled()) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    downRawY = event.rawY
                    initialHeight = contentContainer.height.takeIf { it > 0 }
                        ?: contentContainer.measuredHeight.takeIf { it > 0 }
                        ?: resources.getDimensionPixelSize(R.dimen.og_event_box_list_height)
                    isResizing = true
                    removeCallbacks(autoShrinkRunnable)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isResizing) return false
                    val dy = (event.rawY - downRawY).roundToInt()
                    val minHeight = resources.getDimensionPixelSize(R.dimen.og_event_box_min_list_height)
                    val insets = systemBarInsets()
                    val otherHeight = height - contentContainer.height
                    val maxAvailableBelow = screenHeight() - insets.bottom - positionY().toInt() - otherHeight
                    val maxScreenHeight = (screenHeight() * 0.85f).roundToInt()
                    val maxHeight = max(minHeight, min(maxScreenHeight, maxAvailableBelow))

                    val targetHeight = (initialHeight + dy).coerceIn(minHeight, maxHeight)
                    if (contentContainer.layoutParams.height != targetHeight) {
                        contentContainer.layoutParams.height = targetHeight
                        contentContainer.requestLayout()
                        viewModel.customPanelHeight = targetHeight
                    }

                    // Widen and highlight the bar while dragging, whether growing or shrinking the panel
                    setHandleHighlighted(dy != 0)

                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isResizing) {
                        isResizing = false
                        setHandleHighlighted(false)
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        scheduleAutoShrink()
                        return true
                    }
                }
            }
            return false
        }
    }

    private fun settle(fromBubble: Boolean) {
        if (fromBubble) {
            val resting = snapPosition(positionX(), positionY())
            val effectiveWidth = bubbleTotalWidth()
            val onRight = (resting.x + effectiveWidth / 2f) > (screenWidth() / 2f)
            viewModel.anchorX = resting.x
            viewModel.anchorY = resting.y
            persistBubblePosition(resting.x, resting.y)
            applyHeaderOrientation(onRight)
            bubble.animate().scaleX(1f).scaleY(1f).setDuration(DRAG_ANIMATION_MS).start()
            animateTo(resting.x, resting.y)
        } else {
            val clamped = clampPosition(positionX(), positionY())
            viewModel.anchorY = clamped.y
            animateTo(positionX(), clamped.y)
        }
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
            fromX = positionX() + bubbleTotalWidth() / 2f
            fromY = positionY() + bubbleTotalHeight() / 2f
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
        // Resize the window in the same update as the move, so a right-side bubble expanding
        // doesn't first jump across the screen at bubble width and then grow (reads as a slide).
        // Collapsed uses an exact bubble width (not WRAP_CONTENT) so the shrink needs no layout pass.
        val newWidth = if (viewModel.isCollapsed) bubbleTotalWidth()
        else screenWidth().takeIf { it > 0 } ?: WindowManager.LayoutParams.WRAP_CONTENT
        if (params.x == newX && params.y == newY && params.width == newWidth) return
        params.width = newWidth
        params.x = newX
        params.y = newY
        runCatching { boxWindowManager?.updateViewLayout(this, params) }
    }

    private fun clampPosition(x: Float, y: Float): PointF {
        val insets = systemBarInsets()
        val effectiveWidth = if (viewModel.isCollapsed) bubbleTotalWidth() else width
        val effectiveHeight = if (viewModel.isCollapsed) bubbleTotalHeight() else height
        val minX = insets.left.toFloat()
        val maxX = (screenWidth() - insets.right - effectiveWidth).toFloat()
        val minY = insets.top.toFloat()
        val maxY = (screenHeight() - insets.bottom - effectiveHeight).toFloat()
        return PointF(
            x.coerceIn(min(minX, maxX), max(minX, maxX)),
            y.coerceIn(min(minY, maxY), max(minY, maxY)),
        )
    }

    private fun snapPosition(x: Float, y: Float): PointF {
        val clamped = clampPosition(x, y)
        if (!viewModel.isCollapsed) return clamped
        val insets = systemBarInsets()
        val effectiveWidth = bubbleTotalWidth()
        val leftX = insets.left.toFloat()
        val rightX = (screenWidth() - insets.right - effectiveWidth).toFloat()
        val centerX = clamped.x + effectiveWidth / 2f
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
        val effectiveWidth = if (viewModel.isCollapsed) bubbleTotalWidth() else width
        if (effectiveWidth == 0 || screenHeight() == 0 || x.isNaN() || y.isNaN()) return
        viewModel.persistPosition(
            context,
            onRightEdge = x + effectiveWidth / 2f > screenWidth() / 2f,
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
