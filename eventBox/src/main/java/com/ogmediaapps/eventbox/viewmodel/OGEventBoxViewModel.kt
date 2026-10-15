package com.ogmediaapps.eventbox.viewmodel

import android.content.Context
import com.ogmediaapps.eventbox.data.OGEventBoxRepository
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

    private var _isCollapsed: Boolean = true
    val isCollapsed: Boolean get() = _isCollapsed

    private var _isPinned: Boolean = false
    val isPinned: Boolean get() = _isPinned

    var lastSeenIndex: Int = 0
    var customPanelHeight: Int? = null

    private val _uiState = MutableStateFlow(OGEventBoxUiState())
    val uiState: StateFlow<OGEventBoxUiState> = _uiState.asStateFlow()

    val events: StateFlow<List<OGEventBoxDebugEvent>> get() = repository.events
    val isActive: Boolean get() = repository.isActive
    val isActiveFlow: StateFlow<Boolean> get() = repository.isActiveFlow

    fun resetSessionState() {
        anchorX = Float.NaN
        anchorY = Float.NaN
        _isCollapsed = true
        _isPinned = false
        lastSeenIndex = 0
        customPanelHeight = null
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

    fun togglePin(): Boolean {
        _isPinned = !_isPinned
        updateUiState()
        return _isPinned
    }

    fun clearEvents() {
        repository.clear()
        lastSeenIndex = 0
        updateUiState()
    }

    fun closeTargetDropped() {
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

    private fun updateUiState() {
        val currentEvents = repository.events.value
        _uiState.value = OGEventBoxUiState(
            events = currentEvents,
            isActive = repository.isActive,
            isCollapsed = _isCollapsed,
            isPinned = _isPinned,
            unreadCount = getUnreadCount(currentEvents),
            remainingMinutes = repository.getContext()?.let { repository.remainingMinutes(it) } ?: 0
        )
    }
}
