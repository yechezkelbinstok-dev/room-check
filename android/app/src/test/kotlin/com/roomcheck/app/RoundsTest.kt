package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Slots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

/**
 * Setting a night's own rounds, through the view model rather than the data class - this is where
 * the marks get cleaned up, the open round gets repaired and the whole thing gets written to disk,
 * and every one of those is a way to lose a night's work.
 */
class RoundsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var home: java.io.File
    private var now = PINNED_NOW
    private fun vm(at: LocalDateTime = PINNED_NOW): AppViewModel {
        home = folder.newFolder()
        now = at
        return testVm(NightStore(home), now)
    }
    /** The same storage opened again, as a second launch of the app sees it. */
    private fun reopen(): AppViewModel = testVm(NightStore(home), now)

    private fun labels(vm: AppViewModel) = vm.rounds().map { it.label }

    /** The case it exists for, in one step from the Add dialog. */
    @Test
    fun oneCheckTonightAtItsOwnTime() {
        val vm = vm()
        vm.onlyRoundTonight("1230")
        assertEquals(listOf("12:30"), labels(vm))
        assertEquals("1230", vm.state.value.curSlot)
    }

    @Test
    fun twoChecksTonight() {
        val vm = vm()
        vm.onlyRoundTonight("1230")
        vm.addRoundTonight("0115")
        assertEquals(listOf("12:30", "1:15"), labels(vm))
    }

    /** Removing one of the usual three leaves the other two, not the three plus an exception. */
    @Test
    fun aRoundCanBeDroppedFromTheUsualThree() {
        val vm = vm()
        vm.removeRoundTonight("1115")
        assertEquals(listOf("11:30", "12:00"), labels(vm))
    }

    @Test
    fun theUsualThreeCanBePutBack() {
        val vm = vm()
        vm.onlyRoundTonight("1230")
        vm.resetRoundsTonight()
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm))
    }

    /** A night always has somewhere to mark; emptying it completely is refused. */
    @Test
    fun aNightCannotBeLeftWithNoRounds() {
        val vm = vm()
        vm.onlyRoundTonight("1230")
        vm.removeRoundTonight("1230")
        assertEquals(listOf("12:30"), labels(vm))
    }

    /** Marks in a dropped round go with it, or the round comes straight back on the next read. */
    @Test
    fun droppingARoundTakesItsMarksWithIt() {
        val vm = vm()
        vm.setMark("p1", "1115", Mark.OUT)
        vm.onlyRoundTonight("1230")
        assertEquals(listOf("12:30"), labels(vm))
        assertEquals(null, vm.state.value.night.marks["1115"])
    }

    /** ...and undo puts them back, which is what makes dropping one safe to try. */
    @Test
    fun undoBringsBackARoundAndItsMarks() {
        val vm = vm()
        vm.setMark("p1", "1115", Mark.OUT)
        vm.onlyRoundTonight("1230")
        vm.undo()
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm))
        assertEquals(Mark.OUT, vm.state.value.night.marks["1115"]?.get("p1"))
    }

    /** The screen must never be left marking into a round that is gone. */
    @Test
    fun theOpenRoundIsRepairedWhenItIsRemoved() {
        val vm = vm()
        vm.selectSlot("1115")
        vm.removeRoundTonight("1115")
        assertEquals("1130", vm.state.value.curSlot)
    }

    /** It is on the night, so it is on disk - not just in memory until the app is closed. */
    @Test
    fun aNightsRoundsAreOnDisk() {
        val vm = vm()
        vm.onlyRoundTonight("1230")
        vm.setMark("p1", "1230", Mark.OUT)
        val reopened = reopen()
        assertEquals(listOf("12:30"), reopened.rounds().map { it.label })
        assertEquals(Mark.OUT, reopened.state.value.night.marks["1230"]?.get("p1"))
    }

    /** And it is one night's: tomorrow opens on the usual three with nothing to put back. */
    @Test
    fun anotherNightIsUntouched() {
        val vm = vm()
        vm.onlyRoundTonight("1230")
        vm.shiftDay(-1)
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm))
        vm.goToday()
        assertEquals(listOf("12:30"), labels(vm))
    }

    /** Thursday night - leil shishi - opens on its own two rounds, 12:45 and 1:00, on the first of them. */
    @Test
    fun thursdayNightIsTwoLateRounds() {
        val vm = vm(LocalDateTime.parse("2026-10-29T23:00"))
        assertEquals(listOf("12:45", "1:00"), labels(vm))
        assertEquals("1245", vm.state.value.curSlot)
        vm.setMark("p1", "0100", Mark.OUT)
        assertEquals(Mark.OUT, reopen().state.value.night.marks["0100"]?.get("p1"))
        // the night before and the night after are the standing three
        vm.shiftDay(-1)
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm))
        vm.shiftDay(2)
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm))
        // and coming back to Thursday from a standing-three night lands on one of Thursday's
        vm.goToday()
        assertEquals("1245", vm.state.value.curSlot)
    }

    /** A Thursday can still be given other rounds, and put back on its usual two - not the three. */
    @Test
    fun aThursdaysRoundsCanStillBeChanged() {
        val vm = vm(LocalDateTime.parse("2026-11-05T23:00"))
        vm.onlyRoundTonight("1230")
        assertEquals(listOf("12:30"), labels(vm))
        vm.resetRoundsTonight()
        assertEquals(listOf("12:45", "1:00"), labels(vm))
        vm.removeRoundTonight("0100")
        assertEquals(listOf("12:45"), labels(vm))
    }

    /** It starts on the 29th: the Thursdays before were marked other ways, and keep what they had. */
    @Test
    fun earlierThursdaysAreAsTheyWere() {
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm(LocalDateTime.parse("2026-10-22T23:00"))))
    }

    /** The picture can be narrowed to one of the night's rounds, and that is the night's too. */
    @Test
    fun thePictureCanBeNarrowedToOneRound() {
        val vm = vm()
        vm.toggleSheetSlot("1115")
        vm.toggleSheetSlot("1130")
        assertEquals(listOf("12:00"), Slots.forSheet(vm.state.value.night, vm.state.value.dateKey).map { it.label })
        vm.shiftDay(-1)
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.forSheet(vm.state.value.night, vm.state.value.dateKey).map { it.label })
    }

    /** Ticking everything back on is the same as having said nothing, so a later round joins in. */
    @Test
    fun tickingEverythingBackOnMeansAllOfThem() {
        val vm = vm()
        vm.toggleSheetSlot("1115")
        vm.toggleSheetSlot("1115")
        assertEquals(true, vm.state.value.night.sheetSlots.isEmpty())
        vm.addRoundTonight("0130")
        assertEquals(
            listOf("11:15", "11:30", "12:00", "1:30"),
            Slots.forSheet(vm.state.value.night, vm.state.value.dateKey).map { it.label }
        )
    }

    @Test
    fun rubbishTimesAreRefused() {
        val vm = vm()
        vm.onlyRoundTonight("2599")
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm))
    }
}
