package com.roomcheck.app

import com.roomcheck.app.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Changing a round's time after it has been walked. The whole point is that the marks come with
 * it - a round you have to walk again is no better than removing it and adding the new time.
 */
class MoveRoundTest {
    @get:Rule val folder = TemporaryFolder()
    private fun vm() = testVm(NightStore(folder.newFolder()))

    @Test
    fun marksMoveToTheNewTime() {
        val vm = vm()
        vm.setMark("p3", "1200", Mark.OUT)
        vm.setMark("p4", "1200", Mark.IN)
        vm.moveRoundTonight("1200", "1230")

        val night = vm.state.value.night
        assertNull(night.marks["1200"])
        assertEquals(Mark.OUT, night.marks["1230"]?.get("p3"))
        assertEquals(Mark.IN, night.marks["1230"]?.get("p4"))
    }

    /** The night's columns are the three it always had, with that one at its new time. */
    @Test
    fun theOtherRoundsAreLeftAlone() {
        val vm = vm()
        vm.setMark("p1", "1115", Mark.IN)
        vm.moveRoundTonight("1200", "1230")

        assertEquals(listOf("1115", "1130", "1230"), vm.rounds().map { it.id })
        assertEquals(Mark.IN, vm.state.value.night.marks["1115"]?.get("p1"))
    }

    /** Standing on the round you just moved should leave you standing on it, not somewhere else. */
    @Test
    fun followsTheRoundYouWereMarkingInto() {
        val vm = vm()
        vm.selectSlot("1200")
        vm.moveRoundTonight("1200", "1230")
        assertEquals("1230", vm.state.value.curSlot)
    }

    @Test
    fun undoPutsTheTimeBack() {
        val vm = vm()
        vm.setMark("p3", "1200", Mark.OUT)
        vm.moveRoundTonight("1200", "1230")
        vm.undo()

        val night = vm.state.value.night
        assertEquals(Mark.OUT, night.marks["1200"]?.get("p3"))
        assertEquals(listOf("1115", "1130", "1200"), vm.rounds().map { it.id })
    }

    /** Moving onto a time the night already has would silently swallow one round's marks. */
    @Test
    fun refusesToLandOnATimeAlreadyThere() {
        val vm = vm()
        vm.setMark("p3", "1200", Mark.OUT)
        vm.moveRoundTonight("1200", "1130")

        val night = vm.state.value.night
        assertEquals(Mark.OUT, night.marks["1200"]?.get("p3"))
        assertEquals(listOf("1115", "1130", "1200"), vm.rounds().map { it.id })
    }

    /** A narrowed picture should still name the round after it has moved. */
    @Test
    fun theSentPictureFollowsTheRound() {
        val vm = vm()
        vm.toggleSheetSlot("1115")   // narrows the sheet to 11:30 and 12:00
        vm.moveRoundTonight("1200", "1230")

        val sheet = Slots.forSheet(vm.state.value.night, vm.state.value.dateKey).map { it.id }
        assertTrue("1230" in sheet)
        assertFalse("1115" in sheet)
    }

    /** Every moved cell has to be stamped, or the old time comes back on the next sync. */
    @Test
    fun bothSidesOfTheMoveAreStampedForSync()  {
        val vm = vm()
        vm.setMark("p3", "1200", Mark.OUT)
        vm.moveRoundTonight("1200", "1230")

        val ts = vm.state.value.night.stamps
        listOf(
            Merge.markKey("1200", "p3"), Merge.markKey("1230", "p3"),
            Merge.slotKey("1200"), Merge.slotKey("1230")
        ).forEach { assertTrue("$it not stamped", (ts[it] ?: 0L) > 0L) }
    }
}

/** The clock the Change dialog opens on has to be the round's own time, midnight included. */
class ClockSeedTest {
    @Test
    fun seedsTheClockAtTheRoundsOwnTime() {
        listOf(
            "1115" to (23 to 15),   // quarter past eleven at night
            "1130" to (23 to 30),
            "1200" to (0 to 0),     // midnight, hour zero
            "1230" to (0 to 30),
            "0130" to (1 to 30),    // the small hours
            "0600" to (18 to 0)     // six is evening, not morning
        ).forEach { (id, want) ->
            assertEquals("hour of $id", want.first, Slots.clockHourOf(id))
            assertEquals("minute of $id", want.second, Slots.clockMinuteOf(id))
            assertEquals("$id round trip", id, Slots.idForClock(Slots.clockHourOf(id), Slots.clockMinuteOf(id)))
        }
    }
}
