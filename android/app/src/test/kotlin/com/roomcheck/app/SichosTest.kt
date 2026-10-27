package com.roomcheck.app

import com.roomcheck.app.data.Merge
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.NightStore.Companion.PEOPLE_KEY
import com.roomcheck.app.data.Sichos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import kotlin.random.Random

/** Seder Sichos: sikumim a week at a time, in rounds of four weeks, and each round's raffle. */
class SichosTest {

    @get:Rule val folder = TemporaryFolder()

    /** A hat that always gives the same ticket, so a draw can be checked. */
    private class Ticket(private val n: Int) : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int) = n
    }

    private fun day(s: String) = LocalDate.parse(s)

    @Test
    fun roundsAreFourWeeksFromTheStart() {
        val book = Sichos(mapOf("sichos.start" to "2026-10-25"))
        assertEquals(0, book.roundOf(day("2026-10-25")))
        assertEquals(0, book.weekOf(day("2026-10-25")))
        assertEquals(0, book.roundOf(day("2026-11-21")))
        assertEquals(3, book.weekOf(day("2026-11-21")))
        assertEquals(1, book.roundOf(day("2026-11-22")))
        assertEquals(0, book.weekOf(day("2026-11-22")))
        assertEquals(day("2026-11-21"), book.roundEnd(0))
        assertEquals(day("2026-12-06"), book.weekStart(1, 2))
        assertEquals(-1, book.roundOf(day("2026-10-24")))
    }

    @Test
    fun threeWeeksIsOneEntryAndAllFourIsTwo() {
        assertEquals(listOf(0, 0, 0, 1, 2), (0..4).map { Sichos.entriesFor(it) })
    }

    @Test
    fun theHatHoldsEachNameAsManyTimesAsHisEntries() {
        val pool = listOf("a" to 2, "b" to 1)
        assertEquals(listOf("a", "a", "b"), (0..2).map { Sichos.draw(pool, Ticket(it)) })
        assertNull(Sichos.draw(emptyList()))
    }

    @Test
    fun aTapMarksTheWeekAndAnotherTakesItOff() {
        val dir = folder.newFolder()
        val vm = testVm(NightStore(dir))
        // Wednesday 21 October, in the first week of a round that started on the Sunday before
        vm.setSichosStart(day("2026-10-18"))
        assertEquals(0, vm.sichosRound())
        assertEquals(0, vm.sichosWeek())

        vm.toggleSikkum("p1", 0, 0)
        assertEquals(Sichos.IN, vm.sichos().valueAt("p1", 0, 0))
        val store = NightStore(dir)
        assertEquals("1", store.load(PEOPLE_KEY).notes["sichos.p1.2026-10-18"])
        assertTrue(PEOPLE_KEY in store.dirtyDates())

        vm.toggleSikkum("p1", 0, 0)
        assertNull(vm.sichos().valueAt("p1", 0, 0))
        assertFalse("sichos.p1.2026-10-18" in NightStore(dir).load(PEOPLE_KEY).notes)
    }

    @Test
    fun reviewedIsATickThatStillCounts() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-10-18"))
        (0..2).forEach { vm.toggleSikkum("p2", 0, it) }
        vm.setReviewed("p2", 0, 1, true)
        val book = vm.sichos()
        assertEquals(Sichos.REVIEWED, book.valueAt("p2", 0, 1))
        assertEquals(3, book.count("p2", 0))
        assertEquals(1, book.entries("p2", 0))
        vm.setReviewed("p2", 0, 1, false)
        assertEquals(Sichos.IN, vm.sichos().valueAt("p2", 0, 1))
        // reviewing a week with nothing handed in does not hand one in
        vm.setReviewed("p2", 0, 3, true)
        assertNull(vm.sichos().valueAt("p2", 0, 3))
    }

    @Test
    fun theWinnerIsKeptAndDrawingAgainLeavesHimOut() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-09-20"))
        (0..3).forEach { vm.toggleSikkum("p1", 0, it) }
        (0..2).forEach { vm.toggleSikkum("p2", 0, it) }
        (0..1).forEach { vm.toggleSikkum("p3", 0, it) }
        assertEquals(listOf("p1" to 2, "p2" to 1), vm.sichos().pool(0))

        assertEquals("p2", vm.drawRaffle(0, random = Ticket(2)))
        assertEquals("p2", vm.sichos().winner(0))
        assertEquals("p1", vm.drawRaffle(0, without = setOf("p2"), random = Ticket(0)))
        assertEquals("p1", vm.sichos().winner(0))
        // nobody left in the hat draws nobody, and the winner stays who he was
        assertNull(vm.drawRaffle(0, without = setOf("p1", "p2")))
        assertEquals("p1", vm.sichos().winner(0))
        assertNull(vm.sichos().winner(1))
    }

    @Test
    fun theWeekOpensOnTodayAndALateOneOnARoundThatIsOver() {
        val vm = testVm(NightStore(folder.newFolder()))
        // a round that started on 4 October is in its third week on 21 October
        vm.setSichosStart(day("2026-10-04"))
        assertEquals(0, vm.sichosRound())
        assertEquals(2, vm.sichosWeek())
        val earlier = testVm(NightStore(folder.newFolder()))
        earlier.setSichosStart(day("2026-09-06"))
        assertEquals(1, earlier.sichosRound())
        earlier.showSichosRound(0)
        assertEquals(Sichos.WEEKS - 1, earlier.sichosWeek())
        earlier.pickSichosWeek(1)
        assertEquals(1, earlier.sichosWeek())
    }

    @Test
    fun movingTheStartKeepsASikkumInTheWeekItWasFor() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-10-18"))
        vm.toggleSikkum("p4", 0, 0)
        // two days earlier, the old week's cell still falls in the new first week
        vm.setSichosStart(day("2026-10-16"))
        assertEquals(Sichos.IN, vm.sichos().valueAt("p4", 0, 0))
        vm.toggleSikkum("p4", 0, 0)
        assertNull(vm.sichos().valueAt("p4", 0, 0))
    }

    @Test
    fun itTravelsInTheSharedRecordAndTheOtherSettingsIgnoreIt() {
        val here = NightStore(folder.newFolder())
        val vm = testVm(here)
        vm.setSichosStart(day("2026-10-18"))
        vm.toggleSikkum("p5", 0, 0)
        assertTrue(here.extra.isEmpty())

        // another phone marked someone else in the meantime: the record merges cell by cell
        val there = NightStore(folder.newFolder())
        val other = testVm(there)
        other.setSichosStart(day("2026-10-18"))
        other.toggleSikkum("p6", 0, 0)
        val merged = Merge.merge(here.load(PEOPLE_KEY), there.load(PEOPLE_KEY))
        here.save(PEOPLE_KEY, merged)
        here.rebuildExtra()
        val book = Sichos(here.sichos)
        assertEquals(Sichos.IN, book.valueAt("p5", 0, 0))
        assertEquals(Sichos.IN, book.valueAt("p6", 0, 0))
        assertTrue(NightStore.extraFrom(merged).isEmpty())
    }
}
