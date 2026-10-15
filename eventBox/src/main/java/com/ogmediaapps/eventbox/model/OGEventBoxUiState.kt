package com.ogmediaapps.eventbox.model

/**
 * Encapsulates the UI state of the OGEventBox overlay for MVVM pattern.
 */
data class OGEventBoxUiState(
    val events: List<OGEventBoxDebugEvent> = emptyList(),
    val isActive: Boolean = false,
    val isCollapsed: Boolean = true,
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val remainingMinutes: Int = 0,
)
