package com.roomcheck.app

import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Merge
import com.roomcheck.app.data.Night
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** In the dorm but not in his room: a mark of its own, apart from out. */
class DormTest {

    @get:Rule val folder = TemporaryFolder()

    private val room1 = Roster.PLAN.first { it.id == "r1" }

    @Test
    fun savedAndSyncedAsItsOwnMark() {
        val n = Night()
        n.marks["1115"] = mutableMapOf("p1" to Mark.DORM)
        n.touch(Merge.markKey("1115", "p1"), 1000L)
        val back = Night.fromJson(JSONObject(n.toJson().toString()))
        assertEquals(Mark.DORM, back.marks["1115"]?.get("p1"))
        assertEquals("dorm", n.toJson().getJSONObject("marks").getJSONObject("1115").getString("p1"))

        // a later edit on another device wins, the same as any other mark
        val other = Night()
        other.marks["1115"] = mutableMapOf("p1" to Mark.OUT)
        other.touch(Merge.markKey("1115", "p1"), 2000L)
        assertEquals(Mark.OUT, Merge.merge(n, other).marks["1115"]?.get("p1"))
        assertEquals(Mark.DORM, Merge.merge(other, n.also { it.touch(Merge.markKey("1115", "p1"), 3000L) }).marks["1115"]?.get("p1"))
    }

    /**
     * A copy that went through an app from before this mark drops the word but keeps its stamp.
     * Merged back, the tie goes to the cell that has a value, so the mark is not lost on the way.
     */
    @Test
    fun anOlderAppCannotEraseIt() {
        val n = Night()
        n.marks["1115"] = mutableMapOf("p1" to Mark.DORM)
        n.touch(Merge.markKey("1115", "p1"), 1000L)
        val old = Night()
        old.touch(Merge.markKey("1115", "p1"), 1000L)
        assertEquals(Mark.DORM, Merge.merge(n, old).marks["1115"]?.get("p1"))
        assertEquals(Mark.DORM, Merge.merge(old, n).marks["1115"]?.get("p1"))
    }

    @Test
    fun countedApartAndTheRoomIsNotAllIn() {
        val vm = testVm(NightStore(folder.newFolder()))
        val sid = vm.state.value.curSlot
        vm.setRoom(room1, sid, Mark.IN)
        vm.setMark("p1", sid, Mark.DORM)
        val logic = vm.logic()
        val st = logic.stats(sid)
        assertEquals(1, st.dorm)
        assertEquals(0, st.out)
        assertFalse(logic.roomAllIn(room1, sid))
        assertTrue(logic.roomChecked(room1, sid))
        assertEquals(listOf("p1"), logic.dormIn(room1, sid))
        assertTrue(logic.missingAt(sid).isEmpty())

        // tapped again, it comes off, like the other buttons
        vm.setMark("p1", sid, Mark.DORM)
        assertEquals(null, vm.logic().statusOf("p1", sid))
    }

    @Test
    fun theTextNamesThemOnALineOfTheirOwn() {
        val vm = testVm(NightStore(folder.newFolder()))
        Roster.PEOPLE.forEach { vm.setMark(it.id, "1115", Mark.IN) }
        vm.setMark("p1", "1115", Mark.OUT)
        vm.setMark("p2", "1115", Mark.DORM)
        Roster.PEOPLE.forEach { vm.setMark(it.id, "1130", Mark.IN) }
        vm.setMark("p4", "1130", Mark.DORM)
        val lines = vm.logic().report(vm.state.value.dateKey).lines()

        val at1115 = lines.indexOf("11:15")
        assertEquals("Shlomo Altein", lines[at1115 + 1])
        assertEquals("In the dorm: Yosef Lehr", lines[at1115 + 2])
        val at1130 = lines.indexOf("11:30")
        assertEquals("Nobody out", lines[at1130 + 1])
        assertEquals("In the dorm: Dovber Pevzner", lines[at1130 + 2])
    }

    @Test
    fun whosMissingLeavesWhoeverIsInTheDormThere() {
        val vm = testVm(NightStore(folder.newFolder()))
        val sid = vm.state.value.curSlot
        vm.setMark("p1", sid, Mark.DORM)
        vm.setMark("p2", sid, Mark.DORM)
        vm.markMissing(setOf("p2", "p5"), sid)
        val logic = vm.logic()
        assertEquals(Mark.DORM, logic.statusOf("p1", sid))
        assertEquals(Mark.OUT, logic.statusOf("p2", sid))
        assertEquals(Mark.OUT, logic.statusOf("p5", sid))
        assertEquals(Mark.IN, logic.statusOf("p4", sid))
    }
}
