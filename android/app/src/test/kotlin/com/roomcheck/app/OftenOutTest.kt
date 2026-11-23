package com.roomcheck.app

import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The box beside the plan of the bochurim often out of their rooms. */
class OftenOutTest {

    @get:Rule val folder = TemporaryFolder()

    /** The ones asked for, in the order asked for - both Alteins - each with a bed of his own. */
    @Test
    fun theYeshivasList() {
        assertEquals(listOf("p25", "p6", "p1", "p13", "p31", "p24", "p3", "p11"), Roster.OFTEN_OUT)
        assertEquals(Roster.OFTEN_OUT.size, Roster.OFTEN_OUT.toSet().size)
        Roster.OFTEN_OUT.forEach { assertTrue(it, it in Roster.roomOf) }
    }

    /** Surnames, but the two Alteins each with his initial, so the box says which is which. */
    @Test
    fun itsNamesAreSurnamesAndTheAlteinsInitials() {
        val logic = testVm(NightStore(folder.newFolder())).logic()
        assertEquals(
            listOf("Liberow", "Stolik", "S. Altein", "D. Altein", "Smith", "November", "Piekarski", "Wolfe"),
            Roster.OFTEN_OUT.map { logic.shortName(it, hebrew = false) }
        )
        assertEquals("ש׳ אלטיין", logic.shortName("p1", hebrew = true))
        assertEquals("ד׳ אלטיין", logic.shortName("p13", hebrew = true))
        assertEquals("ליבעראוו", logic.shortName("p25", hebrew = true))
    }

    /** A tap in the box is a mark like any other: on him, in the round on screen, in his own room's count. */
    @Test
    fun aMarkFromTheBoxIsHisMarkInHisRoom() {
        val vm = testVm(NightStore(folder.newFolder()))
        val sid = vm.state.value.curSlot
        vm.jumpRoom(0)
        vm.setMark("p25", sid, Mark.DORM)
        val logic = vm.logic()
        assertEquals(Mark.DORM, logic.statusOf("p25", sid))
        val room7 = Roster.roomOf.getValue("p25")
        assertEquals(listOf("p25"), logic.dormIn(room7, sid))
        assertEquals(1, vm.state.value.night.marks[sid]?.size)
    }
}
