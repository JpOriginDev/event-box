package com.ogmediaapps.eventbox.model

/**
 * Where the bubble was left: which side rail, and how far down the screen as a fraction of its
 * height. Stored as a fraction rather than in pixels so it still means the same thing after a
 * rotation, a fold, or a restore onto a different screen.
 */
data class OGEventBoxPosition(
    val onRightEdge: Boolean,
    val yRatio: Float,
)
