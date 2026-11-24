package com.ogmediaapps.eventbox

import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Clear with undo: clearUndoable / restore / commitClear on the repository. */
class OGEventBoxUndoClearTest {

    @Before
    fun setUp() {
        OGEventBoxRepository.setActiveForTest(true)
        OGEventBoxRepository.firebaseChecksEnabled = false
        OGEventBoxRepository.detectDuplicates = false
        OGEventBoxRepository.maxEvents = OGEventBoxRepository.MAX_EVENTS
        OGEventBoxRepository.clear()
    }

    @After
    fun tearDown() {
        OGEventBoxRepository.clear()
        OGEventBoxRepository.maxEvents = OGEventBoxRepository.MAX_EVENTS
        OGEventBoxRepository.detectDuplicates = true
        OGEventBoxRepository.firebaseChecksEnabled = true
        OGEventBoxRepository.setActiveForTest(false)
    }

    private val events get() = OGEventBoxRepository.events.value

    @Test
    fun clearUndoableEmptiesTheLogAndRestoreBringsItBack() {
        listOf("a", "b", "c").forEach { OGEventBoxRepository.log(it) }

        val cleared = OGEventBoxRepository.clearUndoable()
        assertTrue(events.isEmpty())
        assertEquals(listOf("a", "b", "c"), cleared.events.map { it.name })

        OGEventBoxRepository.restore(cleared)
        assertEquals(listOf("a", "b", "c"), events.map { it.name })
        assertEquals(listOf(1, 2, 3), events.map { it.index })
    }

    @Test
    fun eventsLoggedAfterClearAreKeptAndRenumberedOnRestore() {
        listOf("a", "b").forEach { OGEventBoxRepository.log(it) }
        val cleared = OGEventBoxRepository.clearUndoable()
        OGEventBoxRepository.log("late")

        OGEventBoxRepository.restore(cleared)
        assertEquals(listOf("a", "b", "late"), events.map { it.name })
        assertEquals(listOf(1, 2, 3), events.map { it.index })

        // Numbering carries on from the restored entries.
        OGEventBoxRepository.log("next")
        assertEquals(4, events.last().index)
    }

    @Test
    fun restoreRespectsTheLogSize() {
        OGEventBoxRepository.maxEvents = OGEventBoxRepository.MIN_MAX_EVENTS
        repeat(OGEventBoxRepository.MIN_MAX_EVENTS) { OGEventBoxRepository.log("e$it") }
        val cleared = OGEventBoxRepository.clearUndoable()
        repeat(3) { OGEventBoxRepository.log("late$it") }

        OGEventBoxRepository.restore(cleared)
        assertEquals(OGEventBoxRepository.MIN_MAX_EVENTS, events.size)
        assertEquals("late2", events.last().name)
        assertEquals(3, OGEventBoxRepository.droppedCount)
    }
}
