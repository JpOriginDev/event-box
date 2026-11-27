package com.ogmediaapps.eventbox.viewmodel

import android.content.Context
import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import com.ogmediaapps.eventbox.data.OGEventBoxStore
import com.ogmediaapps.eventbox.filter.OGEventBoxQuery
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.model.OGEventBoxPosition
import com.ogmediaapps.eventbox.model.OGEventBoxUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing presentation logic and UI state for OGEventBox.
 */
internal class OGEventBoxViewModel(private val repository: OGEventBoxRepository = OGEventBoxRepository) {

    var anchorX: Float = Float.NaN
    var anchorY: Float = Float.NaN

    private var _isCollapsed: Boolean = !repository.expandedByDefault
    val isCollapsed: Boolean get() = _isCollapsed

    private var _isPinned: Boolean = repository.pinnedByDefault
    val isPinned: Boolean get() = _isPinned

    var lastSeenIndex: Int = 0
    var customPanelHeight: Int? = null

    private var _query: String = ""
    /** Text typed into the search bar. Kept here so it survives the view being re-created. */
    val query: String get() = _query

    private var _isSearchOpen: Boolean = false
    val isSearchOpen: Boolean get() = _isSearchOpen

    private var pausedSnapshot: List<OGEventBoxDebugEvent>? = null
    /** True while the list is frozen; the repository keeps recording underneath. */
    val isPaused: Boolean get() = pausedSnapshot != null

    /** True when the search query is hiding some entries. */
    val isFiltering: Boolean get() = _query.isNotBlank()

    private val _uiState = MutableStateFlow(OGEventBoxUiState())
    val uiState: StateFlow<OGEventBoxUiState> = _uiState.asStateFlow()

    val events: StateFlow<List<OGEventBoxDebugEvent>> get() = repository.events
    val isActive: Boolean get() = repository.isActive
    val isActiveFlow: StateFlow<Boolean> get() = repository.isActiveFlow

    /** Entries dropped from the top of the log because it was full. */
    val droppedCount: Int get() = repository.droppedCount
    val maxEvents: Int get() = repository.maxEvents

    /**
     * Back to the start state. With a [context], the tester's remembered minimize / open choice
     * wins over the host's `expandedByDefault`.
     */
    fun resetSessionState(context: Context? = null) {
        anchorX = Float.NaN
        anchorY = Float.NaN
        val remembered = if (context != null && repository.rememberUserChoices) OGEventBoxStore.userCollapsed(context) else null
        _isCollapsed = remembered ?: !repository.expandedByDefault
        _isPinned = repository.pinnedByDefault
        lastSeenIndex = 0
        customPanelHeight = null
        resetSearch()
        pausedSnapshot = null
        updateUiState()
    }

    fun collapse() {
        if (_isCollapsed) return
        _isCollapsed = true
        lastSeenIndex = repository.events.value.lastOrNull()?.index ?: 0
        updateUiState()
    }

    fun expand() {
        if (!_isCollapsed) return
        _isCollapsed = false
        lastSeenIndex = repository.events.value.lastOrNull()?.index ?: 0
        updateUiState()
    }

    /** Sets the pin directly, for the host app's default when the pin button is hidden. */
    fun setPinned(pinned: Boolean) {
        if (_isPinned == pinned) return
        _isPinned = pinned
        updateUiState()
    }

    fun togglePin(): Boolean {
        _isPinned = !_isPinned
        updateUiState()
        return _isPinned
    }

    fun clearEvents() {
        repository.clear()
        lastSeenIndex = 0
        resetSearch()
        // A paused list would keep showing the cleared entries, so clearing also resumes.
        pausedSnapshot = null
        updateUiState()
    }

    /** Clears the log and returns what was removed, for [undoClear]. Call [commitClear] once the undo expires. */
    fun clearEventsUndoable(): OGEventBoxRepository.ClearedLog {
        val cleared = repository.clearUndoable()
        lastSeenIndex = 0
        resetSearch()
        pausedSnapshot = null
        updateUiState()
        return cleared
    }

    fun undoClear(cleared: OGEventBoxRepository.ClearedLog) {
        repository.restore(cleared)
        lastSeenIndex = repository.events.value.lastOrNull()?.index ?: 0
        updateUiState()
    }

    fun commitClear() = repository.commitClear()

    fun setQuery(text: String) {
        if (_query == text) return
        _query = text
        updateUiState()
    }

    fun openSearch() {
        if (_isSearchOpen) return
        _isSearchOpen = true
        updateUiState()
    }

    /** Closes the search bar and drops the query, so nothing stays hidden unseen. */
    fun closeSearch() {
        if (!_isSearchOpen && !isFiltering) return
        resetSearch()
        updateUiState()
    }

    /** Freezes the list on what is shown now. New events keep being recorded. */
    fun pause() {
        if (pausedSnapshot != null) return
        pausedSnapshot = repository.events.value
        updateUiState()
    }

    fun resume() {
        if (pausedSnapshot == null) return
        pausedSnapshot = null
        updateUiState()
    }

    /** Entries recorded since [pause]; 0 while not paused. */
    fun newSincePause(events: List<OGEventBoxDebugEvent>): Int {
        val snapshot = pausedSnapshot ?: return 0
        val lastIndex = snapshot.lastOrNull()?.index ?: 0
        val repeatsBefore = snapshot.lastOrNull()?.repeatCount ?: 0
        return events.sumOf { event ->
            when {
                event.index > lastIndex -> event.repeatCount
                event.index == lastIndex -> (event.repeatCount - repeatsBefore).coerceAtLeast(0)
                else -> 0
            }
        }
    }

    /**
     * What the list shows: the paused snapshot (or the live [events]) narrowed by the search
     * query. The unread badge never uses this.
     */
    fun visibleEvents(events: List<OGEventBoxDebugEvent>): List<OGEventBoxDebugEvent> =
        OGEventBoxQuery.apply(pausedSnapshot ?: events, _query)

    fun closeTargetDropped(context: Context? = null) {
        // Remembered so the box does not open again by itself until the app's data is cleared.
        if (context != null && repository.rememberUserChoices) OGEventBoxStore.setClosedByUser(context, true)
        repository.disable()
        resetSessionState()
    }

    fun getSavedPosition(context: Context): OGEventBoxPosition? =
        repository.getBubblePosition(context)

    fun persistPosition(context: Context, onRightEdge: Boolean, yRatio: Float) {
        if (!repository.isActive) return
        repository.saveBubblePosition(context, OGEventBoxPosition(onRightEdge, yRatio))
    }

    fun getUnreadCount(eventsList: List<OGEventBoxDebugEvent>): Int {
        return eventsList.count { it.index > lastSeenIndex }
    }

    /** Pause lives in the header, so closing search leaves it alone. */
    private fun resetSearch() {
        _query = ""
        _isSearchOpen = false
    }

    private fun updateUiState() {
        val currentEvents = repository.events.value
        _uiState.value = OGEventBoxUiState(
            events = currentEvents,
            isActive = repository.isActive,
            isCollapsed = _isCollapsed,
            isPinned = _isPinned,
            unreadCount = getUnreadCount(currentEvents),
            remainingMinutes = repository.getContext()?.let { repository.remainingMinutes(it) } ?: 0,
            query = _query,
            visibleEvents = visibleEvents(currentEvents),
            isSearchOpen = _isSearchOpen,
            isPaused = isPaused,
        )
    }
}
