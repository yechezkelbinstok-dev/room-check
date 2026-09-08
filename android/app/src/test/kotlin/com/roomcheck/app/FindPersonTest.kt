package com.roomcheck.app

import com.roomcheck.app.data.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Searching a name has to land on the bed, not just name the room. */
class FindPersonTest {
    @get:Rule val folder = TemporaryFolder()
    private fun vm() = AppViewModel(NightStore(folder.newFolder()))

    @Test
    fun jumpsToTheRoomThePersonSleepsIn() {
        val vm = vm()
        // p28 (Levitansky) is in Room 8, the last one - nothing like the room it starts on
        vm.findPerson("p28")
        assertEquals(7, vm.state.value.room)
        assertEquals("p28", vm.state.value.highlight)
    }

    /** A ring buried in a scrolling list of eight plans is not showing someone where a bed is. */
    @Test
    fun dropsScrollModeSoTheRingIsActuallyOnScreen() {
        val vm = vm()
        vm.setMode(RoomMode.SCROLL)
        vm.findPerson("p17")
        assertEquals(RoomMode.ONE, vm.state.value.mode)
    }

    @Test
    fun comesBackToTheMarkingScreenFromWhereverYouWere() {
        val vm = vm()
        vm.setTab(Tab.SETTINGS)
        vm.findPerson("p1")
        assertEquals(Tab.CHECK, vm.state.value.tab)
    }

    /** Moving on by hand means the ring has done its job. */
    @Test
    fun movingRoomsClearsTheRing() {
        val vm = vm()
        vm.findPerson("p1")
        vm.goRoom(1)
        assertEquals(null, vm.state.value.highlight)
    }

    @Test
    fun jumpingByRoomChipClearsTheRing() {
        val vm = vm()
        vm.findPerson("p1")
        vm.jumpRoom(5)
        assertEquals(null, vm.state.value.highlight)
    }

    @Test
    fun everyPersonCanBeFound() {
        val vm = vm()
        Roster.PEOPLE.forEach { p ->
            vm.findPerson(p.id)
            assertEquals(p.id, p.id, vm.state.value.highlight)
        }
    }
}
