package com.ogmediaapps.eventbox

import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import com.ogmediaapps.eventbox.filter.OGEventBoxFormat
import com.ogmediaapps.eventbox.filter.OGEventBoxQuery
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** v1.2–v1.4 logic: duplicates, log size, pause, sources and format helpers. */
class OGEventBoxResultsTest {

    @Before
    fun setUp() {
        OGEventBoxRepository.setActiveForTest(true)
        OGEventBoxRepository.firebaseChecksEnabled = false
        OGEventBoxRepository.detectDuplicates = true
        OGEventBoxRepository.duplicateWindowMs = 60_000L
        OGEventBoxRepository.maxEvents = OGEventBoxRepository.MAX_EVENTS
        OGEventBoxRepository.clear()
    }

    @After
    fun tearDown() {
        OGEventBoxRepository.clear()
        OGEventBoxRepository.maxEvents = OGEventBoxRepository.MAX_EVENTS
        OGEventBoxRepository.duplicateWindowMs = OGEventBoxRepository.DEFAULT_DUPLICATE_WINDOW_MS
        OGEventBoxRepository.firebaseChecksEnabled = true
        OGEventBoxRepository.setActiveForTest(false)
    }

    private val events get() = OGEventBoxRepository.events.value

    @Test
    fun identicalEventsBackToBackAreMerged() {
        repeat(3) { OGEventBoxRepository.log("tap", mapOf("id" to 1)) }
        assertEquals(1, events.size)
        assertEquals(3, events[0].repeatCount)
        assertTrue(events[0].isRepeated)
    }

    @Test
    fun differentParamsOrSourceAreNotMerged() {
        OGEventBoxRepository.log("tap", mapOf("id" to 1))
        OGEventBoxRepository.log("tap", mapOf("id" to 2))
        OGEventBoxRepository.log("tap", mapOf("id" to 2), source = "appsflyer")
        assertEquals(3, events.size)
        assertTrue(events.all { it.repeatCount == 1 })
    }

    @Test
    fun duplicateDetectionCanBeTurnedOff() {
        OGEventBoxRepository.detectDuplicates = false
        repeat(2) { OGEventBoxRepository.log("tap") }
        assertEquals(2, events.size)
    }

    @Test
    fun maxEventsDropsOldestAndCountsThem() {
        OGEventBoxRepository.detectDuplicates = false
        OGEventBoxRepository.maxEvents = 10
        repeat(15) { OGEventBoxRepository.log("e$it") }
        assertEquals(10, events.size)
        assertEquals("e5", events.first().name)
        assertEquals(5, OGEventBoxRepository.droppedCount)

        OGEventBoxRepository.clear()
        assertEquals(0, OGEventBoxRepository.droppedCount)
    }

    @Test
    fun maxEventsIsClamped() {
        OGEventBoxRepository.maxEvents = 1
        assertEquals(OGEventBoxRepository.MIN_MAX_EVENTS, OGEventBoxRepository.maxEvents)
        OGEventBoxRepository.maxEvents = 1_000_000
        assertEquals(OGEventBoxRepository.MAX_MAX_EVENTS, OGEventBoxRepository.maxEvents)
    }

    @Test
    fun pauseFreezesListAndCountsNewEntries() {
        val vm = OGEventBoxViewModel()
        OGEventBoxRepository.log("a", null as Map<String, Any?>?)
        vm.pause()
        OGEventBoxRepository.log("b", null as Map<String, Any?>?)
        OGEventBoxRepository.log("b") // merged repeat, still counts as a new firing
        OGEventBoxRepository.log("c", null as Map<String, Any?>?)
        assertEquals(listOf("a"), vm.visibleEvents(events).map { it.name })
        assertEquals(3, vm.newSincePause(events))

        vm.resume()
        assertEquals(listOf("a", "b", "c"), vm.visibleEvents(events).map { it.name })
        assertEquals(0, vm.newSincePause(events))
    }

    @Test
    fun closingSearchKeepsPauseButClearingResumes() {
        val vm = OGEventBoxViewModel()
        OGEventBoxRepository.log("a", null as Map<String, Any?>?)
        vm.openSearch()
        vm.pause()
        vm.closeSearch()
        assertTrue(vm.isPaused) // Pause is a header button now, independent of the search bar.

        vm.clearEventsUndoable()
        assertFalse(vm.isPaused) // Otherwise the frozen list would still show the cleared entries.
        assertTrue(vm.visibleEvents(events).isEmpty())

        vm.pause()
        vm.clearEvents()
        assertFalse(vm.isPaused)
    }

    @Test
    fun sourceTagsAreSearchable() {
        OGEventBoxRepository.log("a", null as Map<String, Any?>?, source = "firebase")
        OGEventBoxRepository.log("b", null as Map<String, Any?>?, source = "mixpanel")
        OGEventBoxRepository.log("c", null as Map<String, Any?>?, source = "firebase")
        OGEventBoxRepository.log("d", null as Map<String, Any?>?)
        val vm = OGEventBoxViewModel()
        vm.setQuery("firebase")
        assertEquals(listOf("a", "c"), vm.visibleEvents(events).map { it.name })
        assertTrue(OGEventBoxQuery.matches(events[1], "mixpanel"))
        vm.setQuery("!mixpanel")
        assertEquals(listOf("a", "c", "d"), vm.visibleEvents(events).map { it.name })
    }

    @Test
    fun relativeTimeFormats() {
        assertEquals("", OGEventBoxFormat.relativeTime(null))
        assertEquals("", OGEventBoxFormat.relativeTime(-5))
        assertEquals("+120ms", OGEventBoxFormat.relativeTime(120))
        assertEquals("+1.2s", OGEventBoxFormat.relativeTime(1_234))
        assertEquals("+75s", OGEventBoxFormat.relativeTime(75_000))
        assertEquals("+12m", OGEventBoxFormat.relativeTime(12 * 60_000L))
    }

    @Test
    fun typeTagsTellStringsFromNumbers() {
        assertEquals("S", OGEventBoxFormat.typeTag("5"))
        assertEquals("L", OGEventBoxFormat.typeTag(5L))
        assertEquals("L", OGEventBoxFormat.typeTag(5))
        assertEquals("D", OGEventBoxFormat.typeTag(5.0))
        assertEquals("B", OGEventBoxFormat.typeTag(true))
        assertEquals("∅", OGEventBoxFormat.typeTag(null))
    }

    @Test
    fun paramLinesPadKeysAndSurviveBadValues() {
        val bad = object {
            override fun toString(): String = throw IllegalStateException("boom")
        }
        val lines = OGEventBoxFormat.paramLines(mapOf("id" to 1L, "screen" to "home", "x" to bad))
        assertEquals(3, lines.size)
        assertEquals("id    ", lines[0].paddedKey)
        assertEquals("L", lines[0].type)
        assertEquals("<unprintable>", lines[2].value)
        assertTrue(OGEventBoxFormat.paramLines(null).isEmpty())
    }

    @Test
    fun repeatCountDefaultsKeepOldConstructorWorking() {
        val event = OGEventBoxDebugEvent(1, 0L, "x")
        assertEquals(1, event.repeatCount)
        assertEquals(null, event.source)
        assertFalse(event.isRepeated)
    }
}
