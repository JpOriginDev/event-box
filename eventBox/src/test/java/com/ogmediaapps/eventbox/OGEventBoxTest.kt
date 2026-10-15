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

    @Test
    fun testValidEventValidation() {
        val warnings = com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent(
            "valid_purchase_event",
            mapOf("item_id" to "sku_123", "price" to 19.99)
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun testEventNameLengthLimit() {
        val longName = "this_is_an_extremely_long_event_name_that_exceeds_forty_characters"
        val warnings = com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent(longName)
        assertTrue(warnings.any { it.contains("exceeds max limit of 40") })
    }

    @Test
    fun testEventNameCharactersLimit() {
        val invalidName = "invalid-name with spaces!"
        val warnings = com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent(invalidName)
        assertTrue(warnings.any { it.contains("invalid characters") })
    }

    @Test
    fun testParametersPerEventLimit() {
        val heavyParams = mutableMapOf<String, Any?>()
        for (i in 1..28) {
            heavyParams["param_$i"] = "val_$i"
        }
        val warnings = com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent("heavy_event", heavyParams)
        assertTrue(warnings.any { it.contains("max 25 unique parameters") })
    }

    @Test
    fun testParameterNameAndValueLengthLimit() {
        val longParamName = "parameter_name_that_is_so_long_that_it_exceeds_forty_characters_limit"
        val longParamValue = "V".repeat(120)
        val warnings = com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent(
            "param_test_event",
            mapOf(longParamName to longParamValue)
        )
        assertTrue(warnings.any { it.contains("exceeds max limit of 40") })
        assertTrue(warnings.any { it.contains("exceeds max limit of 100") })
    }

    @Test
    fun testUserPropertyLimits() {
        val longPropName = "user_property_name_too_long" // 27 chars > 24
        val longPropValue = "V".repeat(40) // 40 chars > 36
        val warnings = com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateUserProperty(
            longPropName,
            longPropValue
        )
        assertTrue(warnings.any { it.contains("exceeds max limit of 24") })
        assertTrue(warnings.any { it.contains("exceeds max limit of 36") })
    }

    @Test
    fun testDistinctCustomEventsLimit() {
        com.ogmediaapps.eventbox.validator.OGEventBoxValidator.reset()
        for (i in 1..500) {
            com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent("custom_event_$i")
        }
        // 501st event should trigger warning
        val warnings = com.ogmediaapps.eventbox.validator.OGEventBoxValidator.validateEvent("custom_event_501")
        assertTrue(warnings.any { it.contains("max 500 custom event types") })

        com.ogmediaapps.eventbox.validator.OGEventBoxValidator.reset()
        assertEquals(0, com.ogmediaapps.eventbox.validator.OGEventBoxValidator.getDistinctCustomEventsCount())
    }

    @Test
    fun testFirebaseChecksEnabledOption() {
        OGEventBoxLog.setFirebaseChecksEnabled(true)
        assertTrue(OGEventBoxLog.isFirebaseChecksEnabled())

        OGEventBoxLog.setFirebaseChecksEnabled(false)
        assertFalse(OGEventBoxLog.isFirebaseChecksEnabled())

        // Reset
        OGEventBoxLog.setFirebaseChecksEnabled(true)
        assertTrue(OGEventBoxLog.isFirebaseChecksEnabled())
    }

    @Test
    fun testPanelResizingEnabledOption() {
        OGEventBoxLog.setPanelResizingEnabled(true)
        assertTrue(OGEventBoxLog.isPanelResizingEnabled())
        assertTrue(OGEventBoxLog.panelResizingEnabled)

        OGEventBoxLog.setPanelResizingEnabled(false)
        assertFalse(OGEventBoxLog.isPanelResizingEnabled())
        assertFalse(OGEventBoxLog.panelResizingEnabled)

        // Reset
        OGEventBoxLog.setPanelResizingEnabled(true)
        assertTrue(OGEventBoxLog.isPanelResizingEnabled())
    }
}

