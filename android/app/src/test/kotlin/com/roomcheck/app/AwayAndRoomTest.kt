package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Not in town as a standing switch, and marking a whole room in one tap. */
class AwayAndRoomTest {

    @get:Rule val folder = TemporaryFolder()

    private val room8 = Roster.PLAN.first { it.id == "r8" }   // Fehler / Smith bunk, Levitansky

    @Test
    fun notInTownCarriesToEveryRoundAndEveryNightUntilTurnedOff() {
        val dir = folder.newFolder()
        val vm = AppViewModel(NightStore(dir))
        vm.toggleAway("p31")
        vm.state.value.night.let { n -> assertTrue(n.marks.values.none { it.containsKey("p31") }) }
        Roster.SIDS.forEach { assertEquals(Mark.AWAY, vm.logic().statusOf("p31", it)) }

        vm.shiftDay(1)
        assertEquals(Mark.AWAY, vm.logic().statusOf("p31", vm.state.value.curSlot))

        // a fresh start off the same files still has him away
        assertTrue(AppViewModel(NightStore(dir)).logic().isAway("p31"))

        vm.backInTown("p31")
        assertEquals(null, vm.logic().statusOf("p31", vm.state.value.curSlot))
    }

    @Test
    fun notInTownAndExcusedIndefinitelyAreOneOrTheOther() {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        vm.toggleAway("p31")
        vm.toggleAlwaysExcused("p31")
        assertFalse(vm.logic().isAway("p31"))
        assertEquals(Mark.EXC, vm.logic().statusOf("p31", vm.state.value.curSlot))
        vm.toggleAway("p31")
        assertFalse(vm.logic().isAlways("p31"))
    }

    @Test
    fun roomButtonsLeaveWhoeverIsNotInTownAlone() {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        val sid = vm.state.value.curSlot
        vm.toggleAway("p31")

        vm.setRoom(room8, sid, Mark.IN)
        assertEquals(Mark.IN, vm.logic().statusOf("p29", sid))
        assertEquals(Mark.IN, vm.logic().statusOf("p28", sid))
        assertEquals(Mark.AWAY, vm.logic().statusOf("p31", sid))
        assertTrue(vm.logic().roomAllIn(room8, sid))

        vm.setRoom(room8, sid, Mark.OUT)
        assertEquals(listOf("p29", "p28"), vm.logic().outIn(room8, sid))

        vm.setRoom(room8, sid, null)
        assertFalse(vm.logic().roomChecked(room8, sid))
    }

    @Test
    fun onlyOneInTheRoomMarksTheRestOut() {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        val sid = vm.state.value.curSlot
        vm.setRoom(room8, sid, null, only = "p28")
        assertEquals(Mark.IN, vm.logic().statusOf("p28", sid))
        assertEquals(Mark.OUT, vm.logic().statusOf("p29", sid))
        assertEquals(Mark.OUT, vm.logic().statusOf("p31", sid))
    }

    @Test
    fun reportNamesWhoIsNotInTownApartFromWhoIsMissing() {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        vm.toggleAway("p31")
        val report = vm.logic().report(vm.state.value.dateKey)
        assertTrue(report, report.endsWith("Not in town\nMordechai Shlomo Smith"))
    }

    @Test
    fun whoeverLeftIsOffThePlanButStillHasAName() {
        assertFalse(Roster.PEOPLE.any { it.id == "p27" || it.id == "p30" })
        assertEquals("Fridman", Roster.byId.getValue("p27").last)
        assertEquals(Roster.PEOPLE.map { it.id }.toSet(), Roster.PLAN.flatMap { r -> r.beds.flatMap { it.slots } }.toSet())
    }
}
