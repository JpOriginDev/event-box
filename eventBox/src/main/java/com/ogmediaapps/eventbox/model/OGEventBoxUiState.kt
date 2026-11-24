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
    /** Text typed into the search bar, or empty. */
    val query: String = "",
    /** [events] after [query] is applied; equals [events] when the query is empty. */
    val visibleEvents: List<OGEventBoxDebugEvent> = events,
    /** True while the search bar is open. */
    val isSearchOpen: Boolean = false,
    /** True while the list is frozen. */
    val isPaused: Boolean = false,
) {
    /** True when the search query is hiding some entries. */
    val isFiltering: Boolean get() = query.isNotBlank()
}
