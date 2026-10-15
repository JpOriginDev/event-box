package com.ogmediaapps.eventbox

import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.model.OGEventBoxPosition
import com.ogmediaapps.eventbox.model.OGEventBoxUiState
import com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OGEventBoxTest {

    @Test
    fun testEventModelCreation() {
        val event = OGEventBoxDebugEvent(1, 1000L, "test_event")
        assertEquals(1, event.index)
        assertEquals(1000L, event.timestampMillis)
        assertEquals("test_event", event.name)
    }

    @Test
    fun testPositionModel() {
        val position = OGEventBoxPosition(onRightEdge = true, yRatio = 0.5f)
        assertTrue(position.onRightEdge)
        assertEquals(0.5f, position.yRatio, 0.001f)
    }

    @Test
    fun testUiStateModel() {
        val state = OGEventBoxUiState(
            events = listOf(OGEventBoxDebugEvent(1, 1000L, "ev1")),
            isActive = true,
            isCollapsed = true,
            unreadCount = 1
        )
        assertTrue(state.isActive)
        assertTrue(state.isCollapsed)
        assertEquals(1, state.unreadCount)
        assertEquals(1, state.events.size)
    }

    @Test
    fun testViewModelStateOperations() {
        val vm = OGEventBoxViewModel()
        assertTrue(vm.isCollapsed)
        assertFalse(vm.isPinned)

        vm.expand()
        assertFalse(vm.isCollapsed)

        vm.collapse()
        assertTrue(vm.isCollapsed)

        val pinned = vm.togglePin()
        assertTrue(pinned)
        assertTrue(vm.isPinned)
    }

    @Test
    fun testFluentSetters() {
        OGEventBoxLog
            .setBubbleIconResId(999)
            .setBubbleIconScaleType(null)
            .setBubbleBackgroundColor(0xFF123456.toInt())
            .setBubbleBorderColor(0xFF654321.toInt())
            .setBubbleBorderWidthDp(2.0f)
            .setBubbleCornerRadiusDp(12f)
            .setDebugModeEnabled(true)
            .setDebugModeDurationHours(1.5)

        assertEquals(999, OGEventBoxLog.bubbleIconResId)
        assertEquals(null, OGEventBoxLog.bubbleIconScaleType)
        assertEquals(0xFF123456.toInt(), OGEventBoxLog.bubbleBackgroundColor)
        assertEquals(0xFF654321.toInt(), OGEventBoxLog.bubbleBorderColor)
        assertEquals(2.0f, OGEventBoxLog.bubbleBorderWidthDp, 0.001f)
        assertEquals(12f, OGEventBoxLog.bubbleCornerRadiusDp, 0.001f)
        assertTrue(OGEventBoxLog.debugModeEnabled)
        assertEquals(1.5, OGEventBoxLog.debugModeDurationHours, 0.001)

        // Reset
        OGEventBoxLog.setDebugModeEnabled(false).setDebugModeDurationHours(0.5)
    }
}

