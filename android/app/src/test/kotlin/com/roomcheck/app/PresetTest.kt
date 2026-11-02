package com.roomcheck.app

import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Merge
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Preset
import com.roomcheck.app.data.Roster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A preset's one tap: its bochurim get their marks in the round on screen, and nobody else does. */
class PresetTest {

    @get:Rule val folder = TemporaryFolder()
    private fun vm() = testVm(NightStore(folder.newFolder()))

    private val lipkindLiberow = Preset("p26" to Mark.IN, "p25" to Mark.OUT)
    private val pevznerLehr = Preset("p4" to Mark.IN, "p2" to Mark.IN)

    /** The two asked for, in the order they were asked for. */
    @Test
    fun theYeshivasPresets() {
        assertEquals(listOf(lipkindLiberow, pevznerLehr), Roster.PRESETS)
        // a preset can only mark someone who has a bed to be marked in
        Roster.PRESETS.flatMap { it.marks }.forEach { (pid, _) -> assertTrue(pid, pid in Roster.roomOf) }
    }

    @Test
    fun itMarksItsPeopleAndNobodyElse() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.setMark("p1", sid, Mark.OUT)
        vm.applyPreset(lipkindLiberow, sid)
        val logic = vm.logic()
        assertEquals(Mark.IN, logic.statusOf("p26", sid))
        assertEquals(Mark.OUT, logic.statusOf("p25", sid))
        assertEquals(Mark.OUT, logic.statusOf("p1", sid))
        assertEquals(3, vm.state.value.night.marks[sid]?.size)
    }

    /** Only the round on screen - the night's other rounds are their own taps. */
    @Test
    fun itMarksOnlyTheOneRound() {
        val vm = vm()
        vm.applyPreset(pevznerLehr, "1115")
        assertEquals(Mark.IN, vm.logic().statusOf("p4", "1115"))
        assertEquals(Mark.IN, vm.logic().statusOf("p2", "1115"))
        assertEquals(null, vm.logic().statusOf("p4", "1130"))
        assertEquals(null, vm.logic().statusOf("p2", "1200"))
    }

    /** What it says is what stands, even over a mark that said otherwise. */
    @Test
    fun itOverridesAMarkThatSaidOtherwise() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.setMark("p25", sid, Mark.IN)
        vm.applyPreset(lipkindLiberow, sid)
        assertEquals(Mark.OUT, vm.logic().statusOf("p25", sid))
    }

    /** Someone not in town, or excused tonight, is left as he is - a mark on him would never be read. */
    @Test
    fun peopleOffTheWalkAreLeftAlone() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.toggleAway("p25")
        vm.toggleExcusedTonight("p2")
        vm.applyPreset(lipkindLiberow, sid)
        vm.applyPreset(pevznerLehr, sid)
        val marks = vm.state.value.night.marks[sid].orEmpty()
        assertEquals(Mark.IN, marks["p26"])
        assertEquals(Mark.IN, marks["p4"])
        assertFalse("p25" in marks)
        assertFalse("p2" in marks)
        assertEquals(Mark.AWAY, vm.logic().statusOf("p25", sid))
        assertEquals(Mark.EXC, vm.logic().statusOf("p2", sid))
        assertTrue(vm.logic().presetDone(lipkindLiberow, sid))
    }

    /** The button turns green once its round has what it says, and not before; each round its own. */
    @Test
    fun itShowsAsDoneOnceItsRoundHasItsMarks() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        assertFalse(vm.logic().presetDone(lipkindLiberow, sid))
        vm.setMark("p26", sid, Mark.IN)
        assertFalse(vm.logic().presetDone(lipkindLiberow, sid))   // half of it is not it
        vm.applyPreset(lipkindLiberow, sid)
        assertTrue(vm.logic().presetDone(lipkindLiberow, sid))
        assertFalse(vm.logic().presetDone(lipkindLiberow, "1130"))
        assertFalse(vm.logic().presetDone(pevznerLehr, sid))
    }

    /** Every mark it sets is stamped, so it goes to the other phones like any other mark. */
    @Test
    fun itsMarksSync() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.applyPreset(lipkindLiberow, sid)
        val stamps = vm.state.value.night.stamps
        assertTrue(Merge.markKey(sid, "p26") in stamps)
        assertTrue(Merge.markKey(sid, "p25") in stamps)
    }

    /** A second tap changes nothing, so it leaves nothing to undo; the first one undoes. */
    @Test
    fun aSecondTapChangesNothingAndUndoTakesItBack() {
        val vm = vm()
        val sid = vm.state.value.curSlot
        vm.setMark("p1", sid, Mark.OUT)
        vm.applyPreset(lipkindLiberow, sid)
        vm.applyPreset(lipkindLiberow, sid)
        vm.undo()
        assertEquals(null, vm.logic().statusOf("p26", sid))
        assertEquals(null, vm.logic().statusOf("p25", sid))
        assertEquals(Mark.OUT, vm.logic().statusOf("p1", sid))
    }

    /** The button reads as what it does, names and all, in English or in Hebrew. */
    @Test
    fun itsLabelSaysWhatItDoes() {
        val logic = vm().logic()
        assertEquals("Lipkind in · Liberow out", logic.presetText(lipkindLiberow, hebrew = false))
        assertEquals("Pevzner, Lehr in", logic.presetText(pevznerLehr, hebrew = false))
        assertEquals("ליפקינד in · ליבעראוו out", logic.presetText(lipkindLiberow, hebrew = true))
        // two Alteins: a surname alone would not say which one
        assertEquals("Dovid Altein in", logic.presetText(Preset("p13" to Mark.IN), hebrew = false))
    }
}
