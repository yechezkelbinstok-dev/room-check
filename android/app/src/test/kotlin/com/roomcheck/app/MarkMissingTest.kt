package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Naming who was missing and having everyone else come out marked in. */
class MarkMissingTest {

    @get:Rule val folder = TemporaryFolder()
    private fun vm() = AppViewModel(NightStore(folder.newFolder()))

    @Test
    fun namedPeopleAreOutAndEveryoneElseIsIn() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.markMissing(setOf("p3", "p11"), sid)

        val logic = vm.logic()
        assertEquals(Mark.OUT, logic.statusOf("p3", sid))
        assertEquals(Mark.OUT, logic.statusOf("p11", sid))
        Roster.PEOPLE.filter { it.id != "p3" && it.id != "p11" }.forEach {
            assertEquals(it.id, Mark.IN, logic.statusOf(it.id, sid))
        }
    }

    @Test
    fun nobodyMissingMarksTheWholeRosterIn() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.markMissing(emptySet(), sid)
        assertEquals(0, vm.logic().stats(sid).out)
        assertEquals(Roster.PEOPLE.size, vm.logic().stats(sid).inCount)
    }

    /** An excused person stays excused - a blanket "everyone else is in" must not overwrite that. */
    @Test
    fun excusedPeopleAreLeftAlone() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.toggleExcusedTonight("p5")
        vm.markMissing(setOf("p3"), sid)
        assertEquals(Mark.EXC, vm.logic().statusOf("p5", sid))
        assertEquals(Mark.OUT, vm.logic().statusOf("p3", sid))
    }

    /** It only speaks for the round it was given - the other rounds stay untouched. */
    @Test
    fun onlyTheOneRoundIsFilledIn() {
        val vm = vm()
        vm.markMissing(setOf("p3"), "1115")
        assertEquals(null, vm.logic().statusOf("p3", "1130"))
        assertEquals(null, vm.logic().statusOf("p1", "1130"))
    }

    @Test
    fun undoPutsTheRoundBackAsItWas() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.setMark("p1", sid, Mark.OUT)
        vm.markMissing(setOf("p3"), sid)
        vm.undo()
        assertEquals(Mark.OUT, vm.logic().statusOf("p1", sid))
        assertEquals(null, vm.logic().statusOf("p3", sid))
    }

    /** Run twice with a correction, the second answer is the one that stands. */
    @Test
    fun runningItAgainReplacesTheFirstAnswer() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.markMissing(setOf("p3", "p11"), sid)
        vm.markMissing(setOf("p11"), sid)
        assertEquals(Mark.IN, vm.logic().statusOf("p3", sid))
        assertEquals(Mark.OUT, vm.logic().statusOf("p11", sid))
    }
}
