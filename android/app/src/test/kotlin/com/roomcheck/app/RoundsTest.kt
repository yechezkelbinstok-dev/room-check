package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Slots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Setting a night's own rounds, through the view model rather than the data class - this is where
 * the marks get cleaned up, the open round gets repaired and the whole thing gets written to disk,
 * and every one of those is a way to lose a night's work.
 */
class RoundsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var home: java.io.File
    private fun vm(): AppViewModel {
        home = folder.newFolder()
        return AppViewModel(NightStore(home))
    }
    /** The same storage opened again, as a second launch of the app sees it. */
    private fun reopen(): AppViewModel = AppViewModel(NightStore(home))

    private fun labels(vm: AppViewModel) = Slots.all(vm.state.value.night).map { it.label }

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
        assertEquals(listOf("12:30"), Slots.all(reopened.state.value.night).map { it.label })
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

    /** The picture can be narrowed to one of the night's rounds, and that is the night's too. */
    @Test
    fun thePictureCanBeNarrowedToOneRound() {
        val vm = vm()
        vm.toggleSheetSlot("1115")
        vm.toggleSheetSlot("1130")
        assertEquals(listOf("12:00"), Slots.forSheet(vm.state.value.night).map { it.label })
        vm.shiftDay(-1)
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.forSheet(vm.state.value.night).map { it.label })
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
            Slots.forSheet(vm.state.value.night).map { it.label }
        )
    }

    @Test
    fun rubbishTimesAreRefused() {
        val vm = vm()
        vm.onlyRoundTonight("2599")
        assertEquals(listOf("11:15", "11:30", "12:00"), labels(vm))
    }
}
