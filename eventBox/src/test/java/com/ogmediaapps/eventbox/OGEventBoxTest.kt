package com.ogmediaapps.eventbox

import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.model.OGEventBoxPosition
import com.ogmediaapps.eventbox.model.OGEventBoxUiState
import com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OGEventBoxTest {

    // The options live in a singleton, so every test starts from (and leaves) the documented defaults.
    @Before
    @After
    fun restoreDefaultOptions() {
        OGEventBoxLog.showComponents(*com.ogmediaapps.eventbox.model.OGEventBoxComponent.values())
            .hideComponents(com.ogmediaapps.eventbox.model.OGEventBoxComponent.DELTA_TIME_BUTTON)
            .setStartState(expanded = false, pinned = false, deltaTimeShown = false)
    }

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
    fun testSystemEventNameWarning() {
        val v = com.ogmediaapps.eventbox.validator.OGEventBoxValidator
        assertTrue(v.validateEvent("first_open").any { it.contains("automatically collected") })
        assertTrue(v.validateEvent("session_start").any { it.contains("automatically collected") })
        // Manually logging screen_view is the documented way to track screens, so it stays quiet.
        assertTrue(v.validateEvent("screen_view").none { it.contains("automatically collected") })
        assertTrue(v.validateEvent("valid_purchase_event").isEmpty())
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
    fun testHeaderButtonVisibilityOptions() {
        // All shown by default, except the clock button, which starts hidden.
        assertTrue(OGEventBoxLog.isClearButtonVisible())
        assertTrue(OGEventBoxLog.isPinButtonVisible())
        assertTrue(OGEventBoxLog.isShareButtonVisible())
        assertTrue(OGEventBoxLog.isPauseButtonVisible())
        assertTrue(OGEventBoxLog.isSearchButtonVisible())
        assertFalse(OGEventBoxLog.isDeltaTimeButtonVisible())

        OGEventBoxLog.setShareButtonVisible(false).setDeltaTimeButtonVisible(false)
        assertFalse(OGEventBoxLog.isShareButtonVisible())
        assertFalse(OGEventBoxLog.shareButtonVisible)
        assertFalse(OGEventBoxLog.isDeltaTimeButtonVisible())
        assertTrue(OGEventBoxLog.isClearButtonVisible())

        OGEventBoxLog.setShareButtonVisible(true).setDeltaTimeButtonVisible(true)
        assertTrue(OGEventBoxLog.isShareButtonVisible())
        assertTrue(OGEventBoxLog.isDeltaTimeButtonVisible())
    }

    @Test
    fun testDeltaTimeDefaultOption() {
        // Off by default.
        assertFalse(OGEventBoxLog.isDeltaTimeShown())
        OGEventBoxLog.setDeltaTimeShown(true)
        assertTrue(OGEventBoxLog.isDeltaTimeShown())
        assertTrue(OGEventBoxLog.deltaTimeShown)
        OGEventBoxLog.setDeltaTimeShown(false)
        assertFalse(OGEventBoxLog.isDeltaTimeShown())
    }

    @Test
    fun testPinnedByDefaultOption() {
        assertFalse(OGEventBoxLog.isPinnedByDefault())
        OGEventBoxLog.setPinnedByDefault(true)
        assertTrue(OGEventBoxLog.isPinnedByDefault())
        assertTrue(com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel().isPinned)
        OGEventBoxLog.setPinnedByDefault(false)
        assertFalse(com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel().isPinned)
    }

    @Test
    fun testExpandedByDefaultOption() {
        assertFalse(OGEventBoxLog.isExpandedByDefault())
        assertTrue(com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel().isCollapsed)
        OGEventBoxLog.setExpandedByDefault(true)
        assertTrue(OGEventBoxLog.isExpandedByDefault())
        assertFalse(com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel().isCollapsed)
        OGEventBoxLog.setExpandedByDefault(false)
        assertTrue(com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel().isCollapsed)
    }

    @Test
    fun testComponentVisibilityApi() {
        val c = com.ogmediaapps.eventbox.model.OGEventBoxComponent.values()
        // Everything is shown by default, except the clock button.
        c.filter { it != com.ogmediaapps.eventbox.model.OGEventBoxComponent.DELTA_TIME_BUTTON }
            .forEach { assertTrue(OGEventBoxLog.isComponentVisible(it)) }
        assertFalse(OGEventBoxLog.isComponentVisible(com.ogmediaapps.eventbox.model.OGEventBoxComponent.DELTA_TIME_BUTTON))

        OGEventBoxLog.hideComponents(
            com.ogmediaapps.eventbox.model.OGEventBoxComponent.TITLE,
            com.ogmediaapps.eventbox.model.OGEventBoxComponent.UNREAD_BADGE,
        )
        assertFalse(OGEventBoxLog.isComponentVisible(com.ogmediaapps.eventbox.model.OGEventBoxComponent.TITLE))
        assertFalse(OGEventBoxLog.isComponentVisible(com.ogmediaapps.eventbox.model.OGEventBoxComponent.UNREAD_BADGE))
        assertTrue(OGEventBoxLog.isComponentVisible(com.ogmediaapps.eventbox.model.OGEventBoxComponent.LOGO))

        // The six button shortcuts are the same switch as the generic API.
        OGEventBoxLog.setPinButtonVisible(false)
        assertFalse(OGEventBoxLog.isComponentVisible(com.ogmediaapps.eventbox.model.OGEventBoxComponent.PIN_BUTTON))

        // RESIZE_HANDLE is the same switch as setPanelResizingEnabled.
        OGEventBoxLog.setComponentVisible(com.ogmediaapps.eventbox.model.OGEventBoxComponent.RESIZE_HANDLE, false)
        assertFalse(OGEventBoxLog.isPanelResizingEnabled())

        OGEventBoxLog.showOnly(com.ogmediaapps.eventbox.model.OGEventBoxComponent.CLEAR_BUTTON)
        assertTrue(OGEventBoxLog.isClearButtonVisible())
        assertFalse(OGEventBoxLog.isShareButtonVisible())
        assertFalse(OGEventBoxLog.isPanelResizingEnabled())

        OGEventBoxLog.showComponents(*c)
        c.forEach { assertTrue(OGEventBoxLog.isComponentVisible(it)) }
        assertTrue(OGEventBoxLog.isPanelResizingEnabled())
    }

    @Test
    fun testSetStartStateKeepsUnspecifiedValues() {
        OGEventBoxLog.setStartState(expanded = true, pinned = true, deltaTimeShown = false)
        assertTrue(OGEventBoxLog.isExpandedByDefault())
        assertTrue(OGEventBoxLog.isPinnedByDefault())
        assertFalse(OGEventBoxLog.isDeltaTimeShown())

        OGEventBoxLog.setStartState(pinned = false)
        assertTrue(OGEventBoxLog.isExpandedByDefault())
        assertFalse(OGEventBoxLog.isPinnedByDefault())
        assertFalse(OGEventBoxLog.isDeltaTimeShown())

        OGEventBoxLog.setStartState(expanded = false, deltaTimeShown = true)
        assertFalse(OGEventBoxLog.isExpandedByDefault())
        assertTrue(OGEventBoxLog.isDeltaTimeShown())
    }

    @Test
    fun testAvailabilityAllowsMatrix() {
        val a = com.ogmediaapps.eventbox.model.OGEventBoxAvailability.values().associateWith { it }
        val debug = true
        val release = false
        assertTrue(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.DEBUG_ONLY).allows(debug))
        assertFalse(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.DEBUG_ONLY).allows(release))
        assertFalse(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.RELEASE_ONLY).allows(debug))
        assertTrue(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.RELEASE_ONLY).allows(release))
        assertTrue(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.DEBUG_AND_RELEASE).allows(debug))
        assertTrue(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.DEBUG_AND_RELEASE).allows(release))
        assertFalse(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.NEVER).allows(debug))
        assertFalse(a.getValue(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.NEVER).allows(release))
    }

    @Test
    fun testSetAvailabilityAndDeprecatedFlagStayCompatible() {
        OGEventBoxLog.setAvailability(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.DEBUG_ONLY)
        assertEquals(com.ogmediaapps.eventbox.model.OGEventBoxAvailability.DEBUG_ONLY, OGEventBoxLog.getAvailability())

        // The old flag still works and, being called last, wins.
        @Suppress("DEPRECATION")
        OGEventBoxLog.setDebugModeEnabled(true)
        assertNull(OGEventBoxLog.getAvailability())
        assertTrue(OGEventBoxLog.debugModeEnabled)

        @Suppress("DEPRECATION")
        OGEventBoxLog.setDebugModeEnabled(false)
        assertFalse(OGEventBoxLog.debugModeEnabled)
    }

    @Test
    fun testBubbleStyleBuilderAndLambda() {
        OGEventBoxLog.setBubbleStyle(
            com.ogmediaapps.eventbox.model.OGEventBoxBubbleStyle.Builder()
                .iconRes(7)
                .backgroundColor(0xFF112233.toInt())
                .borderColor(0xFF445566.toInt())
                .borderWidthDp(2f)
                .cornerRadiusDp(10f)
                .build()
        )
        var s = OGEventBoxLog.getBubbleStyle()
        assertEquals(7, s.iconRes)
        assertEquals(0xFF112233.toInt(), s.backgroundColor)
        assertEquals(2f, s.borderWidthDp, 0.001f)
        assertEquals(10f, s.cornerRadiusDp, 0.001f)

        // The lambda form changes only what it sets.
        OGEventBoxLog.setBubbleStyle {
            fullyRound()
            noBorder()
        }
        s = OGEventBoxLog.getBubbleStyle()
        assertEquals(7, s.iconRes)
        assertEquals(0xFF112233.toInt(), s.backgroundColor)
        assertEquals(0f, s.borderWidthDp, 0.001f)
        assertEquals(-1f, s.cornerRadiusDp, 0.001f)

        OGEventBoxLog.setBubbleStyle(com.ogmediaapps.eventbox.model.OGEventBoxBubbleStyle.DEFAULT)
        s = OGEventBoxLog.getBubbleStyle()
        assertEquals(0, s.iconRes)
        assertEquals(0, s.backgroundColor)
        assertEquals(-1f, s.borderWidthDp, 0.001f)
    }

    @Test
    fun testDeprecatedBubbleSettersStillWriteTheSameStyle() {
        @Suppress("DEPRECATION")
        OGEventBoxLog
            .setBubbleIconResId(5)
            .setBubbleBorderWidthDp(3f)
            .setBubbleCornerRadiusDp(4f)
        val s = OGEventBoxLog.getBubbleStyle()
        assertEquals(5, s.iconRes)
        assertEquals(3f, s.borderWidthDp, 0.001f)
        assertEquals(4f, s.cornerRadiusDp, 0.001f)
        OGEventBoxLog.setBubbleStyle(com.ogmediaapps.eventbox.model.OGEventBoxBubbleStyle.DEFAULT)
    }

    @Test
    fun testRememberUserChoicesOptionDefaultsOn() {
        assertTrue(OGEventBoxLog.isRememberUserChoices())
        OGEventBoxLog.setRememberUserChoices(false)
        assertFalse(OGEventBoxLog.isRememberUserChoices())
        assertFalse(OGEventBoxLog.rememberUserChoices)
        OGEventBoxLog.setRememberUserChoices(true)
        assertTrue(OGEventBoxLog.isRememberUserChoices())
    }

    @Test
    fun testResetSessionStateWithoutContextUsesHostDefaults() {
        OGEventBoxLog.setExpandedByDefault(true).setPinnedByDefault(true)
        val vm = com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel()
        vm.collapse()
        vm.togglePin()
        vm.resetSessionState()
        assertFalse(vm.isCollapsed)
        assertTrue(vm.isPinned)
        OGEventBoxLog.setExpandedByDefault(false).setPinnedByDefault(false)
        vm.resetSessionState()
        assertTrue(vm.isCollapsed)
        assertFalse(vm.isPinned)
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

