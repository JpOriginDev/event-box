package com.ogmediaapps.eventbox.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.ogmediaapps.eventbox.OGEventBoxLog
import com.ogmediaapps.eventbox.R
import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import com.ogmediaapps.eventbox.data.OGEventBoxStore
import com.ogmediaapps.eventbox.filter.OGEventBoxExport
import com.ogmediaapps.eventbox.filter.OGEventBoxQuery
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
import kotlin.math.abs
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
    private val clearButton: ImageButton
    private val pinButton: ImageButton
    private val recycler: RecyclerView
    private val layoutManager: LinearLayoutManager
    private val adapter: OGEventBoxAdapter
    private val contentContainer: FrameLayout
    private val resizeHandle: View
    private val resizeHandleBar: View
    private val emptyView: TextView
    private val droppedView: TextView
    private val tipView: TextView
    private val undoPill: TextView
    private val jumpPill: TextView
    private val pauseButton: ImageButton
    private val bubble: View
    private val bubbleIcon: ImageView
    private val bubbleBadge: TextView
    private val searchButton: ImageButton
    private val searchBar: View
    private val searchInput: EditText
    private val searchClearButton: ImageButton
    private val shareButton: ImageButton

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val headerButtonHeight = resources.getDimensionPixelSize(R.dimen.og_event_box_header_button_height)
    private val maxPanelWidthPx = resources.getDimensionPixelSize(R.dimen.og_event_box_max_panel_width)
    private val marginPx = resources.getDimensionPixelSize(R.dimen.og_event_box_margin)
    private val closeSizePx = resources.getDimensionPixelSize(R.dimen.og_event_box_close_size)
    private val closeMarginPx = resources.getDimensionPixelSize(R.dimen.og_event_box_close_margin)
    private val closeSlackPx = resources.getDimensionPixelSize(R.dimen.og_event_box_close_slack)
    private val headerButtonWidth = resources.getDimensionPixelSize(R.dimen.og_event_box_header_button_width)
    private val headerButtonGap = resources.getDimensionPixelSize(R.dimen.og_event_box_header_button_gap)
    private val headerIconSize = resources.getDimensionPixelSize(R.dimen.og_event_box_header_icon_size)
    private val headerTitleMargin = resources.getDimensionPixelSize(R.dimen.og_event_box_header_title_margin)
    private val headerCountMargin = resources.getDimensionPixelSize(R.dimen.og_event_box_header_count_margin)
    private val pivotInset = resources.getDimensionPixelSize(R.dimen.og_event_box_panel_pivot_inset)
    private val defaultListHeight = resources.getDimensionPixelSize(R.dimen.og_event_box_list_height)
    private val minListHeight = resources.getDimensionPixelSize(R.dimen.og_event_box_min_list_height)
    private val indexColor = ContextCompat.getColor(context, R.color.og_event_box_index)
    private val nameColor = ContextCompat.getColor(context, R.color.og_event_box_name)
    private val pinnedTint = ContextCompat.getColor(context, R.color.og_event_box_accent)
    private val iconTint = ContextCompat.getColor(context, R.color.og_event_box_name)

    private var boxWindowManager: WindowManager? = null
    private var boxWindowParams: WindowManager.LayoutParams? = null

    private var closeTargetView: View? = null
    private var closeTargetIcon: ImageView? = null
    private var closeTargetShown = false

    private var dragInProgress = false
    private var settleAnimator: ValueAnimator? = null
    private var handleHighlightAnimator: ValueAnimator? = null
    private var resizeResetAnimator: ValueAnimator? = null
    private var isHandleHighlighted = false
    private var isHeaderMirrored: Boolean? = null

    /** Declared before init: init builds the header, and a later initializer would reset this. */
    private var headerFitWatched = false
    private var expandedEventIndex: Int? = null

    /** True while the box window has dropped FLAG_NOT_FOCUSABLE so the search field can type. */
    private var windowFocusGranted = false

    /** True while the list's last row is on screen, so new events keep it pinned to the bottom. */
    private var stickToBottom = true

    /** New entries that arrived while the reader was scrolled up (for the "↓ N new" pill). */
    private var unseenBelow = 0
    private var lastRenderedLatestIndex = 0

    private var scope: CoroutineScope? = null
    private val autoShrinkRunnable = Runnable {
        when {
            viewModel.isPinned -> Unit
            // Never shrink under someone who is typing; look again later.
            isSearchInputFocused -> scheduleAutoShrink()
            // Let the one-time tip and the undo offer finish before the panel goes away.
            tipView.isVisible || undoPill.isVisible -> scheduleAutoShrink()
            else -> collapse(animate = true)
        }
    }

    /** What the last Clear removed, while its undo pill is still offered. */
    private var pendingClear: OGEventBoxRepository.ClearedLog? = null
    private val commitClearRunnable = Runnable { commitPendingClear() }
    private val applyQueryRunnable = Runnable {
        viewModel.setQuery(searchInput.text?.toString().orEmpty())
        renderEvents(viewModel.events.value)
    }
    private val bubbleFadeRunnable = Runnable {
        bubble.animate().alpha(BUBBLE_IDLE_ALPHA).setDuration(BUBBLE_FADE_MS).start()
    }
    private val hideTipRunnable = Runnable { hideGestureTip() }

    /** True while a finger is on the resize handle, so the open-time pulse never fights a drag. */
    private var isHandleDragging = false
    private val handlePulseOnRunnable = Runnable {
        if (!isHandleDragging) setHandleHighlighted(true)
    }
    private val handlePulseOffRunnable = Runnable {
        if (!isHandleDragging) setHandleHighlighted(false)
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
        recycler = findViewById(R.id.og_event_box_list)
        contentContainer = findViewById(R.id.og_event_box_content_container)
        resizeHandle = findViewById(R.id.og_event_box_resize_handle)
        resizeHandleBar = findViewById(R.id.og_event_box_resize_handle_bar)
        resizeHandleBar.alpha = 0.75f
        resizeHandleBar.backgroundTintList = ColorStateList.valueOf(indexColor)
        emptyView = findViewById(R.id.og_event_box_empty)
        droppedView = findViewById(R.id.og_event_box_dropped)
        tipView = findViewById(R.id.og_event_box_tip)
        tipView.setOnClickListener { hideGestureTip() }
        undoPill = findViewById(R.id.og_event_box_undo)
        undoPill.setOnClickListener { undoClear() }
        ViewCompat.setAccessibilityDelegate(undoPill, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        context.getString(R.string.og_event_box_undo),
                    )
                )
            }
        })
        jumpPill = findViewById(R.id.og_event_box_jump)
        pauseButton = findViewById(R.id.og_event_box_pause)

        layoutManager = LinearLayoutManager(context)
        adapter = OGEventBoxAdapter(
            onRowClick = { event -> toggleExpanded(event) },
            onRowLongClick = { event ->
                copyEvent(event)
                scheduleAutoShrink()
            },
        )
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        // Row content changes (highlight, expand) re-bind in place instead of cross-fading.
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                stickToBottom = !recyclerView.canScrollVertically(1)
                if (stickToBottom) unseenBelow = 0
                updateJumpPill()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) scheduleAutoShrink()
            }
        })
        jumpPill.setOnClickListener {
            if (viewModel.isPaused) togglePause() else scrollToLatest(smooth = true)
            scheduleAutoShrink()
        }
        bubble = findViewById(R.id.og_event_box_bubble)
        bubbleIcon = findViewById(R.id.og_event_box_bubble_icon)
        bubbleBadge = findViewById(R.id.og_event_box_bubble_badge)
        searchButton = findViewById(R.id.og_event_box_search)
        searchBar = findViewById(R.id.og_event_box_search_bar)
        searchInput = findViewById(R.id.og_event_box_search_input)
        searchClearButton = findViewById(R.id.og_event_box_search_clear)
        shareButton = findViewById(R.id.og_event_box_share)

        clearButton.setOnClickListener { clearWithUndo() }
        setUpSearch()
        resizeHandle.setOnTouchListener(ResizeTouchListener())
        // TalkBack users cannot drag the handle, so expose the reset as its click action.
        ViewCompat.replaceAccessibilityAction(
            resizeHandle,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.og_event_box_resize_reset),
        ) { _, _ ->
            resetPanelHeight()
            true
        }
        headerIcon.setOnClickListener { collapse(animate = true) }
        headerIcon.setOnLongClickListener {
            cyclePanelAlpha()
            scheduleAutoShrink()
            true
        }
        headerIcon.contentDescription = context.getString(R.string.og_event_box_minimize)
        // Name both logo gestures for TalkBack, so "double-tap and hold" announces what it does.
        ViewCompat.setAccessibilityDelegate(headerIcon, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        context.getString(R.string.og_event_box_minimize),
                    )
                )
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                        context.getString(R.string.og_event_box_opacity_action),
                    )
                )
            }
        })
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
        syncSearchUi()
        // The opacity the tester picked last time wins over the app's default.
        OGEventBoxStore.panelAlpha(context)?.let { OGEventBoxRepository.panelAlpha = it }
        applyPanelAlpha()
    }

    // =========================================================================
    // Clear with undo
    // =========================================================================

    private fun clearWithUndo() {
        val before = viewModel.events.value
        if (before.isEmpty() && pendingClear == null) return
        // A second Clear while the first can still be undone makes the first one final.
        commitPendingClear()
        expandedEventIndex = null
        releaseSearchFocus()
        val cleared = viewModel.clearEventsUndoable()
        syncSearchUi()
        renderEvents(viewModel.events.value)
        clearButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val count = cleared.events.sumOf { it.repeatCount }
        if (count == 0) {
            viewModel.commitClear()
        } else {
            pendingClear = cleared
            undoPill.text = resources.getQuantityString(R.plurals.og_event_box_cleared, count, count)
            undoPill.isVisible = true
            removeCallbacks(commitClearRunnable)
            postDelayed(commitClearRunnable, UNDO_DURATION_MS)
        }
        scheduleAutoShrink()
    }

    private fun undoClear() {
        val cleared = pendingClear ?: return
        pendingClear = null
        removeCallbacks(commitClearRunnable)
        undoPill.isVisible = false
        viewModel.undoClear(cleared)
        stickToBottom = true
        unseenBelow = 0
        syncSearchUi()
        renderEvents(viewModel.events.value)
        scrollToLatest(smooth = false)
        undoPill.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scheduleAutoShrink()
    }

    /** Ends the undo offer: the cleared entries are gone for good. */
    private fun commitPendingClear() {
        removeCallbacks(commitClearRunnable)
        undoPill.isVisible = false
        if (pendingClear == null) return
        pendingClear = null
        viewModel.commitClear()
    }

    // =========================================================================
    // Search, copy and share
    // =========================================================================

    /** True while the search field holds focus, i.e. someone may be typing. */
    val isSearchInputFocused: Boolean get() = searchInput.hasFocus() || windowFocusGranted

    private fun setUpSearch() {
        searchButton.setOnClickListener {
            if (viewModel.isSearchOpen) closeSearch() else openSearch()
            scheduleAutoShrink()
        }
        searchInput.setText(viewModel.query)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchClearButton.isVisible = !s.isNullOrEmpty()
                removeCallbacks(applyQueryRunnable)
                postDelayed(applyQueryRunnable, QUERY_DEBOUNCE_MS)
                scheduleAutoShrink()
            }
        })
        // A touch on the field is the moment the box window must start taking key input.
        searchInput.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) grantWindowFocus()
            false
        }
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                grantWindowFocus()
                showKeyboard()
            }
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                removeCallbacks(applyQueryRunnable)
                applyQueryRunnable.run()
                releaseSearchFocus()
                true
            } else {
                false
            }
        }
        searchClearButton.setOnClickListener {
            searchInput.setText("")
            removeCallbacks(applyQueryRunnable)
            applyQueryRunnable.run()
            scheduleAutoShrink()
        }
        shareButton.setOnClickListener {
            shareVisibleEvents()
            scheduleAutoShrink()
        }
        pauseButton.setOnClickListener {
            togglePause()
            scheduleAutoShrink()
        }
    }

    private fun togglePause() {
        if (viewModel.isPaused) viewModel.resume() else viewModel.pause()
        updatePauseButton()
        renderEvents(viewModel.events.value)
        if (!viewModel.isPaused) scrollToLatest(smooth = false)
    }

    private fun updatePauseButton() {
        val paused = viewModel.isPaused
        pauseButton.setImageResource(if (paused) R.drawable.og_event_box_ic_play else R.drawable.og_event_box_ic_pause)
        pauseButton.setColorFilter(if (paused) pinnedTint else iconTint)
        pauseButton.contentDescription = context.getString(
            if (paused) R.string.og_event_box_resume_description else R.string.og_event_box_pause
        )
    }

    private fun openSearch() {
        viewModel.openSearch()
        syncSearchUi()
        // The bar is only a text field now, so go straight to typing.
        grantWindowFocus()
        searchInput.requestFocus()
    }

    private fun closeSearch() {
        releaseSearchFocus()
        removeCallbacks(applyQueryRunnable)
        viewModel.closeSearch()
        syncSearchUi()
        renderEvents(viewModel.events.value)
    }

    /** Makes the search bar and field match the view model (also after re-creation). */
    private fun syncSearchUi() {
        val open = viewModel.isSearchOpen
        searchBar.isVisible = open
        searchButton.setColorFilter(if (open || viewModel.isFiltering) pinnedTint else iconTint)
        searchButton.contentDescription = context.getString(
            if (open) R.string.og_event_box_search_close else R.string.og_event_box_search
        )
        if (searchInput.text?.toString() != viewModel.query) searchInput.setText(viewModel.query)
        searchClearButton.isVisible = viewModel.query.isNotEmpty()
        updatePauseButton()
    }

    /**
     * Lets the box window take key input. Both window types are created with FLAG_NOT_FOCUSABLE
     * so the host app keeps its keyboard and back button; it is lifted only while searching.
     */
    private fun grantWindowFocus() {
        if (windowFocusGranted) return
        val params = boxWindowParams ?: return // inside the Activity's content: already focusable
        val manager = boxWindowManager ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        if (runCatching { manager.updateViewLayout(this, params) }.isSuccess) {
            windowFocusGranted = true
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    /** Gives key input back to the host app. Must run whenever search stops being used. */
    private fun revokeWindowFocus() {
        if (!windowFocusGranted) return
        windowFocusGranted = false
        val params = boxWindowParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        runCatching { boxWindowManager?.updateViewLayout(this, params) }
    }

    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        // Right after the flag change the window may not have focus yet; onWindowFocusChanged
        // retries once it does.
        searchInput.post { runCatching { imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT) } }
    }

    /** Hides the keyboard, drops the field's focus and returns key input to the host app. */
    fun releaseSearchFocus() {
        if (searchInput.hasFocus()) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            runCatching { imm?.hideSoftInputFromWindow(searchInput.windowToken, 0) }
            // Turning focusability off clears focus without touch mode handing it straight back.
            searchInput.isFocusableInTouchMode = false
            searchInput.isFocusable = false
            searchInput.isFocusable = true
            searchInput.isFocusableInTouchMode = true
        }
        revokeWindowFocus()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            if (searchInput.hasFocus()) showKeyboard()
        } else if (windowFocusGranted) {
            // The user touched the app (focus follows touch) or another window took over.
            releaseSearchFocus()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && (searchInput.hasFocus() || windowFocusGranted)) {
            if (event.action == KeyEvent.ACTION_UP) releaseSearchFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun copyEvent(event: OGEventBoxDebugEvent) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.og_event_box_copy_label), OGEventBoxExport.eventJson(event))
            )
            // Android 13+ shows its own clipboard confirmation.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context.applicationContext, R.string.og_event_box_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareVisibleEvents() {
        val visible = viewModel.visibleEvents(viewModel.events.value)
        runCatching {
            if (visible.isEmpty()) {
                Toast.makeText(context.applicationContext, R.string.og_event_box_share_empty, Toast.LENGTH_SHORT).show()
                return
            }
            releaseSearchFocus()
            val json = OGEventBoxExport.logJson(visible)
            val subject = context.resources.getQuantityString(R.plurals.og_event_box_share_subject, visible.size, visible.size)
            // Long sessions don't fit in an intent extra (the binder limit is ~1 MB for the whole
            // transaction), so they go out as a JSON file from the cache instead.
            val fileUri = if (json.length > MAX_SHARE_TEXT_CHARS) writeExportFile(json) else null
            val send = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                if (fileUri != null) {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    clipData = ClipData.newRawUri(subject, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, json)
                }
            }
            val chooser = Intent.createChooser(send, context.getString(R.string.og_event_box_share_title))
            // The overlay window has an application context, which needs a new task.
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    /**
     * Writes [json] to `cache/og_event_box/` (replacing the previous export) and returns a
     * content URI for it, or null when that fails so the caller falls back to plain text.
     */
    private fun writeExportFile(json: String): android.net.Uri? = runCatching {
        val dir = java.io.File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = java.io.File(dir, "event-box-$stamp.json")
        file.writeText(json, Charsets.UTF_8)
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}$EXPORT_AUTHORITY_SUFFIX", file)
    }.getOrNull()

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

        val border = if (borderWidth <= 0) null else GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(borderWidth, borderColor)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bubbleIcon.foreground = border
        } else if (border != null) {
            // View#setForeground is API 23+. Draw the ring on the disc itself instead; the logo
            // is inset by the border width (applyLogoTo), so it stays visible around it. Only a
            // CENTER_CROP / FIT_XY logo covers it, and only on Android 5.x.
            (bubbleIcon.background as? GradientDrawable)?.setStroke(borderWidth, borderColor)
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
        removeCallbacks(applyQueryRunnable)
        removeCallbacks(hideTipRunnable)
        cancelHandlePulse()
        commitPendingClear()
        resizeResetAnimator?.cancel()
        windowFocusGranted = false
        settleAnimator?.cancel()
        handleHighlightAnimator?.cancel()
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    fun onDetaching() {
        // Keep whatever was typed: the view model holds it for the next window.
        removeCallbacks(applyQueryRunnable)
        viewModel.setQuery(searchInput.text?.toString().orEmpty())
        releaseSearchFocus()
        removeCallbacks(autoShrinkRunnable)
        removeCallbacks(bubbleFadeRunnable)
        settleAnimator?.cancel()
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
        val filtering = viewModel.isFiltering
        val paused = viewModel.isPaused
        val visible = viewModel.visibleEvents(events)
        val latestIndex = events.lastOrNull()?.index ?: 0
        val words = OGEventBoxQuery.highlightWords(viewModel.query)

        countView.text = if (filtering) {
            context.getString(R.string.og_event_box_count_filtered, visible.size, events.size)
        } else {
            String.format(Locale.US, "%d", events.size)
        }
        emptyView.isVisible = visible.isEmpty()
        emptyView.setText(if (events.isNotEmpty() && filtering) R.string.og_event_box_no_match else R.string.og_event_box_empty)
        renderDropped()

        if (expandedEventIndex != null && visible.none { it.index == expandedEventIndex }) {
            expandedEventIndex = null
        }

        // Numbering restarts after a clear; start counting "new" rows again from there.
        if (latestIndex < lastRenderedLatestIndex) {
            lastRenderedLatestIndex = 0
            unseenBelow = 0
            stickToBottom = true
        }
        val arrived = if (lastRenderedLatestIndex == 0) 0 else visible.count { it.index > lastRenderedLatestIndex }
        lastRenderedLatestIndex = latestIndex

        // Time since the previous entry in the whole log, not the filtered list.
        val previousTime = HashMap<Int, Long>(events.size)
        for (i in 1 until events.size) previousTime[events[i].index] = events[i - 1].timestampMillis
        val rows = visible.mapIndexed { position, event ->
            OGEventBoxRow(
                event = event,
                deltaMs = previousTime[event.index]?.let { event.timestampMillis - it },
                isLatest = event.index == latestIndex,
                isLastRow = position == visible.lastIndex,
                isExpanded = event.index == expandedEventIndex,
                highlight = words,
            )
        }

        // Follow new entries only when the reader is at the bottom and nothing narrows the list.
        val follow = !viewModel.isCollapsed && stickToBottom && !filtering && !paused
        if (!follow && !paused && arrived > 0 && !stickToBottom) unseenBelow += arrived
        adapter.submitList(rows) {
            if (follow) scrollToLatest(smooth = false)
            updateJumpPill()
        }

        // The badge always counts every new entry, whatever is filtered.
        if (viewModel.isCollapsed) {
            updateBadge(events)
        } else {
            viewModel.lastSeenIndex = latestIndex
        }
    }

    private fun renderDropped() {
        val dropped = viewModel.droppedCount
        droppedView.isVisible = dropped > 0
        if (dropped > 0) {
            droppedView.text = resources.getQuantityString(R.plurals.og_event_box_dropped, dropped, dropped, viewModel.maxEvents)
        }
    }

    private fun toggleExpanded(event: OGEventBoxDebugEvent) {
        scheduleAutoShrink()
        val expanding = expandedEventIndex != event.index
        expandedEventIndex = if (expanding) event.index else null
        renderEvents(viewModel.events.value)
        if (expanding) recycler.post { revealRow(event.index) }
    }

    /** Scrolls just enough to show the whole of an expanded row, including its details. */
    private fun revealRow(eventIndex: Int) {
        val position = adapter.currentList.indexOfFirst { it.event.index == eventIndex }
        if (position < 0) return
        val rowView = layoutManager.findViewByPosition(position)
        if (rowView == null) {
            recycler.smoothScrollToPosition(position)
            return
        }
        val overflowBottom = rowView.bottom - (recycler.height - recycler.paddingBottom)
        val overflowTop = rowView.top - recycler.paddingTop
        val dy = when {
            overflowTop < 0 -> overflowTop
            overflowBottom > 0 -> min(overflowBottom, overflowTop)
            else -> 0
        }
        if (dy != 0) recycler.smoothScrollBy(0, dy)
    }

    private fun scrollToLatest(smooth: Boolean) {
        val last = adapter.itemCount - 1
        stickToBottom = true
        unseenBelow = 0
        if (last >= 0) {
            if (smooth) recycler.smoothScrollToPosition(last) else recycler.scrollToPosition(last)
        }
        updateJumpPill()
    }

    /** "▶ Resume (+N)" while paused, "↓ N new" when scrolled up past new entries, else hidden. */
    private fun updateJumpPill() {
        when {
            viewModel.isPaused -> {
                val newCount = viewModel.newSincePause(viewModel.events.value)
                jumpPill.text = if (newCount > 0) {
                    context.getString(R.string.og_event_box_resume_new, newCount)
                } else {
                    context.getString(R.string.og_event_box_resume)
                }
                jumpPill.isVisible = true
            }
            !stickToBottom && unseenBelow > 0 -> {
                jumpPill.text = resources.getQuantityString(R.plurals.og_event_box_jump_new, unseenBelow, unseenBelow)
                jumpPill.isVisible = true
            }
            else -> jumpPill.isVisible = false
        }
    }

    /** Applies [OGEventBoxLog.panelAlpha] to the panel backgrounds only; text stays opaque. */
    fun applyPanelAlpha() {
        val alpha = (OGEventBoxLog.panelAlpha * 255).roundToInt().coerceIn(0, 255)
        panel.background?.mutate()?.alpha = alpha
        header.background?.mutate()?.alpha = alpha
    }

    /** Long-press on the panel logo: 100% → 85% → 70% → 60% → 100%. */
    private fun cyclePanelAlpha() {
        val current = OGEventBoxLog.panelAlpha
        val next = PANEL_ALPHA_STEPS.firstOrNull { it < current - 0.01f } ?: PANEL_ALPHA_STEPS.first()
        OGEventBoxLog.panelAlpha = next
        OGEventBoxStore.setPanelAlpha(context, next)
        applyPanelAlpha()
        runCatching {
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.og_event_box_opacity, (next * 100).roundToInt()),
                Toast.LENGTH_SHORT,
            ).show()
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

        // Sizes come from dimens.xml (og_event_box_header_*), the same values the layout uses.
        header.removeAllViews()
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        fun lp(width: Int, height: Int, start: Int = 0, weight: Float = 0f) =
            LinearLayout.LayoutParams(width, height, weight).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = start
            }
        fun button(start: Int) = lp(headerButtonWidth, headerButtonHeight, start)
        val spacer = lp(0, 1, weight = 1f)

        if (isRightSide) {
            // Mirrored for a right-side bubble:
            // [Clear] [Pin] [Share] [Pause] [Search] ---Spacer--- [Title] [Count] [Logo]
            header.addView(clearButton, button(0))
            header.addView(pinButton, button(headerButtonGap))
            header.addView(shareButton, button(headerButtonGap))
            header.addView(pauseButton, button(headerButtonGap))
            header.addView(searchButton, button(headerButtonGap))
            header.addView(headerSpacer, spacer)
            header.addView(titleView, lp(wrap, wrap))
            header.addView(countView, lp(wrap, wrap, headerCountMargin))
            header.addView(headerIcon, lp(headerIconSize, headerIconSize, headerTitleMargin))
        } else {
            // [Logo] [Title] [Count] ---Spacer--- [Search] [Pause] [Share] [Pin] [Clear]
            header.addView(headerIcon, lp(headerIconSize, headerIconSize))
            header.addView(titleView, lp(wrap, wrap, headerTitleMargin))
            header.addView(countView, lp(wrap, wrap, headerCountMargin))
            header.addView(headerSpacer, spacer)
            header.addView(searchButton, button(0))
            header.addView(pauseButton, button(headerButtonGap))
            header.addView(shareButton, button(headerButtonGap))
            header.addView(pinButton, button(headerButtonGap))
            header.addView(clearButton, button(headerButtonGap))
        }

        if (!headerFitWatched) {
            headerFitWatched = true
            header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                fitHeaderButtons()
                fitHeaderTitle()
            }
        }
    }

    /**
     * Narrow phones can't fit every header button next to the "EVENTS" title, so the title is
     * dropped when the row overflows and brought back once there is room. The count stays.
     */
    private fun fitHeaderTitle() {
        val available = header.width - header.paddingLeft - header.paddingRight
        if (available <= 0) return
        val titleLp = titleView.layoutParams as? LinearLayout.LayoutParams ?: return
        val titleNeed = titleView.paint.measureText(titleView.text.toString()).roundToInt() +
                titleView.paddingLeft + titleView.paddingRight + titleLp.marginStart + titleLp.marginEnd
        var used = 0
        for (i in 0 until header.childCount) {
            val child = header.getChildAt(i)
            if (child.visibility == View.GONE || child === headerSpacer) continue
            val lp = child.layoutParams as? LinearLayout.LayoutParams
            // The natural width, not the measured one: an overflowing row squeezes its last children.
            // Buttons count at full size here so the title decision doesn't flip as they shrink.
            val natural = if (child in headerButtons) headerButtonWidth else if ((lp?.width ?: 0) > 0) lp!!.width else {
                val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                child.measure(unspecified, unspecified)
                child.measuredWidth
            }
            used += natural + (lp?.marginStart ?: 0) + (lp?.marginEnd ?: 0)
        }
        val fits = if (titleView.isVisible) used <= available else used + titleNeed <= available
        if (titleView.isVisible != fits) post { titleView.isVisible = fits }
    }

    private val headerButtons get() = listOf(searchButton, pauseButton, shareButton, pinButton, clearButton)

    /**
     * On very narrow panels (small screens, large font or display size) the five header buttons
     * share whatever room is left instead of running off the edge. They never grow past
     * [headerButtonWidth] and never shrink below [HEADER_BUTTON_MIN_WIDTH_DP].
     */
    private fun fitHeaderButtons() {
        val available = header.width - header.paddingLeft - header.paddingRight
        if (available <= 0) return
        val buttons = headerButtons
        var fixed = 0
        for (i in 0 until header.childCount) {
            val child = header.getChildAt(i)
            if (child.visibility == View.GONE || child === headerSpacer || child === titleView || child in buttons) continue
            val lp = child.layoutParams as? LinearLayout.LayoutParams
            val natural = if ((lp?.width ?: 0) > 0) lp!!.width else {
                val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                child.measure(unspecified, unspecified)
                child.measuredWidth
            }
            fixed += natural + (lp?.marginStart ?: 0) + (lp?.marginEnd ?: 0)
        }
        val gaps = buttons.sumOf { (it.layoutParams as? LinearLayout.LayoutParams)?.marginStart ?: 0 }
        val minWidth = (HEADER_BUTTON_MIN_WIDTH_DP * resources.displayMetrics.density).roundToInt()
        val target = ((available - fixed - gaps) / buttons.size).coerceIn(minWidth, headerButtonWidth)
        // Icon is 48dp tall minus 14dp padding top and bottom; keep side padding from squeezing it.
        val iconSide = headerButtonHeight - buttons.first().paddingTop * 2
        val sidePad = ((target - iconSide) / 2).coerceIn(0, buttons.first().paddingTop)
        for (b in buttons) {
            val lp = b.layoutParams as? LinearLayout.LayoutParams ?: continue
            if (lp.width != target) {
                lp.width = target
                b.layoutParams = lp
            }
            if (b.paddingLeft != sidePad || b.paddingRight != sidePad) {
                b.setPadding(sidePad, b.paddingTop, sidePad, b.paddingBottom)
            }
        }
    }

    private fun collapse(animate: Boolean) {
        if (viewModel.isCollapsed) return
        releaseSearchFocus()
        viewModel.collapse()
        removeCallbacks(autoShrinkRunnable)
        cancelHandlePulse()
        hideGestureTip()
        commitPendingClear()

        val isRight = isBubbleOnRightSide()
        val panelWidth = (if (panel.width > 0) panel.width else (screenWidth() - marginPx * 2)).toFloat()
        val pivotX = if (isRight) panelWidth - pivotInset else pivotInset.toFloat()
        val pivotY = pivotInset.toFloat()
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
        // Opening the panel always starts at the newest entry.
        stickToBottom = true
        unseenBelow = 0
        renderEvents(viewModel.events.value)
        if (!viewModel.isPaused) scrollToLatest(smooth = false)

        val panelWidth = (if (panel.width > 0) panel.width else min(screenWidth() - marginPx * 2, maxPanelWidthPx)).toFloat()
        val pivotX = if (isRight) panelWidth - pivotInset else pivotInset.toFloat()
        val pivotY = pivotInset.toFloat()
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
        showGestureTipOnce()
        pulseResizeHandleIfUnused()
        scheduleAutoShrink()
    }

    /** The first time the panel ever opens, explain the logo gestures (and the resize handle). */
    private fun showGestureTipOnce() {
        if (OGEventBoxStore.isGestureTipSeen(context)) return
        OGEventBoxStore.setGestureTipSeen(context)
        tipView.setText(
            if (OGEventBoxLog.isPanelResizingEnabled()) R.string.og_event_box_tip_with_resize
            else R.string.og_event_box_tip
        )
        tipView.isVisible = true
        removeCallbacks(hideTipRunnable)
        postDelayed(hideTipRunnable, TIP_DURATION_MS)
    }

    private fun hideGestureTip() {
        removeCallbacks(hideTipRunnable)
        tipView.isVisible = false
    }

    /**
     * Until the user has dragged the bottom handle once, light it up briefly each time the panel
     * opens, so it is clear the panel can be resized from there.
     */
    private fun pulseResizeHandleIfUnused() {
        cancelHandlePulse()
        if (!OGEventBoxLog.isPanelResizingEnabled() || OGEventBoxStore.isResizeUsed(context)) return
        postDelayed(handlePulseOnRunnable, HANDLE_PULSE_DELAY_MS)
        postDelayed(handlePulseOffRunnable, HANDLE_PULSE_DELAY_MS + HANDLE_PULSE_HOLD_MS)
    }

    private fun cancelHandlePulse() {
        removeCallbacks(handlePulseOnRunnable)
        removeCallbacks(handlePulseOffRunnable)
        if (!isHandleDragging) setHandleHighlighted(false, animate = false)
    }

    private fun applyCollapsedState(collapsed: Boolean) {
        // Full width on phones, capped on tablets and in landscape and kept on the bubble's side.
        val barInsets = systemBarInsets()
        val panelWidth = min(screenWidth() - barInsets.left - barInsets.right - marginPx * 2, maxPanelWidthPx)
        val panelLp = panel.layoutParams as LayoutParams
        val panelGravity = Gravity.TOP or (if (isBubbleOnRightSide()) Gravity.END else Gravity.START)
        if (panelWidth > 0 && (panelLp.width != panelWidth || panelLp.gravity != panelGravity)) {
            panelLp.width = panelWidth
            panelLp.gravity = panelGravity
            panel.layoutParams = panelLp
        }
        if (!collapsed && OGEventBoxLog.isPanelResizingEnabled()) {
            // This session's height, else the one the tester saved last time.
            val custom = viewModel.customPanelHeight ?: savedPanelHeightPx()?.also { viewModel.customPanelHeight = it }
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
        panel.pivotX = if (isRight) (pw - pivotInset).toFloat() else pivotInset.toFloat()
        panel.pivotY = pivotInset.toFloat()
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

    /**
     * Drag the bottom handle to resize the event list; double-tap it to go back to the default
     * height. A drag only starts past the touch slop, so taps never nudge the height.
     */
    private inner class ResizeTouchListener : OnTouchListener {
        private var downRawY = 0f
        private var initialHeight = 0
        private var isResizing = false
        private var dragStarted = false
        private var atLimit = false
        private var lastTapUpTime = 0L

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (!OGEventBoxLog.isPanelResizingEnabled()) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    resizeResetAnimator?.cancel()
                    downRawY = event.rawY
                    initialHeight = contentContainer.height.takeIf { it > 0 }
                        ?: contentContainer.measuredHeight.takeIf { it > 0 }
                        ?: defaultListHeight
                    isResizing = true
                    dragStarted = false
                    atLimit = false
                    isHandleDragging = true
                    removeCallbacks(handlePulseOnRunnable)
                    removeCallbacks(handlePulseOffRunnable)
                    removeCallbacks(autoShrinkRunnable)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isResizing) return false
                    if (!dragStarted) {
                        if (abs(event.rawY - downRawY) < touchSlop) return true
                        // Measure from here, so the panel doesn't jump by the slop distance.
                        dragStarted = true
                        downRawY = event.rawY
                        lastTapUpTime = 0L
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (!OGEventBoxStore.isResizeUsed(context)) OGEventBoxStore.setResizeUsed(context)
                    }
                    val dy = (event.rawY - downRawY).roundToInt()
                    val maxHeight = maxListHeight()
                    val wanted = initialHeight + dy
                    val targetHeight = wanted.coerceIn(minListHeight, maxHeight)
                    if (contentContainer.layoutParams.height != targetHeight) {
                        contentContainer.layoutParams.height = targetHeight
                        contentContainer.requestLayout()
                        viewModel.customPanelHeight = targetHeight
                    }
                    // One tick when the drag runs into the smallest or largest height.
                    val clamped = wanted != targetHeight
                    if (clamped && !atLimit) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    atLimit = clamped

                    // Widen and highlight the bar while dragging, whether growing or shrinking the panel
                    setHandleHighlighted(true)
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isResizing) return false
                    isResizing = false
                    isHandleDragging = false
                    setHandleHighlighted(false)
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (dragStarted) {
                        savePanelHeight(viewModel.customPanelHeight)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (event.eventTime - lastTapUpTime <= ViewConfiguration.getDoubleTapTimeout()) {
                            lastTapUpTime = 0L
                            resetPanelHeight()
                        } else {
                            lastTapUpTime = event.eventTime
                        }
                    }
                    scheduleAutoShrink()
                    return true
                }
            }
            return false
        }
    }

    /** Tallest the event list may get: what fits below the panel's top, and at most 85% of the screen. */
    private fun maxListHeight(): Int {
        val insets = systemBarInsets()
        val otherHeight = height - contentContainer.height
        val maxAvailableBelow = screenHeight() - insets.bottom - positionY().toInt() - otherHeight
        val maxScreenHeight = (screenHeight() * 0.85f).roundToInt()
        return max(minListHeight, min(maxScreenHeight, maxAvailableBelow))
    }

    private fun savePanelHeight(heightPx: Int?) {
        val density = resources.displayMetrics.density
        OGEventBoxStore.setPanelHeightDp(context, heightPx?.let { it / density })
    }

    /** The height saved by an earlier drag, in px for this screen, or `null` for the default. */
    private fun savedPanelHeightPx(): Int? {
        val dp = OGEventBoxStore.panelHeightDp(context) ?: return null
        val px = (dp * resources.displayMetrics.density).roundToInt()
        return px.coerceIn(minListHeight, max(minListHeight, (screenHeight() * 0.85f).roundToInt()))
    }

    /** Double-tap on the handle: animate back to the default height and forget the saved one. */
    private fun resetPanelHeight() {
        viewModel.customPanelHeight = null
        savePanelHeight(null)
        resizeHandle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val from = contentContainer.height.takeIf { it > 0 } ?: contentContainer.layoutParams.height
        resizeResetAnimator?.cancel()
        if (from == defaultListHeight || from <= 0) {
            contentContainer.layoutParams.height = defaultListHeight
            contentContainer.requestLayout()
            return
        }
        resizeResetAnimator = ValueAnimator.ofInt(from, defaultListHeight).apply {
            duration = RESIZE_RESET_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                contentContainer.layoutParams.height = it.animatedValue as Int
                contentContainer.requestLayout()
            }
            start()
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
        boxWindowParams?.type ?: WindowManager.LayoutParams.TYPE_APPLICATION,
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
        // Open panel: the window spans the area between the system bars (in landscape the
        // navigation bar sits on a side), so the panel never slides under them.
        val barInsets = systemBarInsets()
        val newX = if (viewModel.isCollapsed) x.roundToInt() else barInsets.left
        val newY = y.roundToInt()
        // Resize the window in the same update as the move, so a right-side bubble expanding
        // doesn't first jump across the screen at bubble width and then grow (reads as a slide).
        // Collapsed uses an exact bubble width (not WRAP_CONTENT) so the shrink needs no layout pass.
        val newWidth = if (viewModel.isCollapsed) bubbleTotalWidth()
        else (screenWidth() - barInsets.left - barInsets.right).takeIf { it > 0 } ?: WindowManager.LayoutParams.WRAP_CONTENT
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
        (hostDecor() ?: this).let { ViewCompat.getRootWindowInsets(it) }?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE

    companion object {
        private const val AUTO_SHRINK_DELAY_MS = 6_000L
        private const val TIP_DURATION_MS = 8_000L
        private const val UNDO_DURATION_MS = 4_000L
        private const val RESIZE_RESET_MS = 200L
        private const val HANDLE_PULSE_DELAY_MS = 350L
        private const val HANDLE_PULSE_HOLD_MS = 700L
        private const val QUERY_DEBOUNCE_MS = 150L
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
        private const val HEADER_BUTTON_MIN_WIDTH_DP = 28f
        private const val DEFAULT_Y_RATIO = 0f
        private val PANEL_ALPHA_STEPS = floatArrayOf(1f, 0.85f, 0.7f, 0.6f)
        private const val MAX_SHARE_TEXT_CHARS = 100_000
        private const val EXPORT_DIR = "og_event_box"
        private const val EXPORT_AUTHORITY_SUFFIX = ".ogeventbox.files"

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

        /** Window for showing over other apps; needs the "Display over other apps" permission. */
        @Suppress("DEPRECATION")
        fun overlayWindowParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
        }
    }
}
