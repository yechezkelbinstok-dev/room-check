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

/** Seder Sichos: a sikkum on each Sunday's and Wednesday's sicha, in rounds of eight, and each round's raffle. */
class SichosTest {

    @get:Rule val folder = TemporaryFolder()

    /** A hat that always gives the same ticket, so a draw can be checked. */
    private class Ticket(private val n: Int) : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int) = n
    }

    private fun day(s: String) = LocalDate.parse(s)

    @Test
    fun roundsAreEightSichosSundaysAndWednesdaysFromTheStart() {
        val book = Sichos(mapOf("sichos.start" to "2026-10-25"))
        assertEquals(0 to 0, book.roundOf(day("2026-10-25")) to book.weekOf(day("2026-10-25")))
        assertEquals(0, book.weekOf(day("2026-10-27")))
        assertEquals(1, book.weekOf(day("2026-10-28")))
        assertEquals(2, book.weekOf(day("2026-11-01")))
        assertEquals(0 to 7, book.roundOf(day("2026-11-21")) to book.weekOf(day("2026-11-21")))
        assertEquals(1 to 0, book.roundOf(day("2026-11-22")) to book.weekOf(day("2026-11-22")))
        assertEquals(day("2026-11-21"), book.roundEnd(0))
        assertEquals(day("2026-11-29"), book.weekStart(1, 2))
        assertEquals(day("2026-12-02"), book.weekStart(1, 3))
        assertEquals(-1, book.roundOf(day("2026-10-24")))
        assertEquals("Sun", Sichos.dayOf(book.weekStart(1, 2)))
        assertEquals("Wednesday", Sichos.dayOf(book.weekStart(1, 3), long = true))
    }

    @Test
    fun sixIsOneEntryAndAllEightIsTwo() {
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 1, 2), (0..8).map { Sichos.entriesFor(it) })
    }

    @Test
    fun theHatHoldsEachNameAsManyTimesAsHisEntries() {
        val pool = listOf("a" to 2, "b" to 1)
        assertEquals(listOf("a", "a", "b"), (0..2).map { Sichos.draw(pool, Ticket(it)) })
        assertNull(Sichos.draw(emptyList()))
    }

    @Test
    fun aTapMarksTheSichaAndAnotherTakesItOff() {
        val dir = folder.newFolder()
        val vm = testVm(NightStore(dir))
        // Wednesday 21 October, collecting the first sicha of a round that started on the Sunday before
        vm.setSichosStart(day("2026-10-18"))
        assertEquals(0, vm.sichosRound())
        assertEquals(0, vm.sichosWeek())

        vm.toggleSikkum("p1", 0, 0)
        assertEquals(Sichos.IN, vm.sichos().valueAt("p1", 0, 0))
        val store = NightStore(dir)
        assertEquals("1", store.load(PEOPLE_KEY).notes["sichos.p1.2026-10-18"])
        assertTrue(PEOPLE_KEY in store.dirtyDates())

        vm.toggleSikkum("p1", 0, 1)
        assertEquals("1", NightStore(dir).load(PEOPLE_KEY).notes["sichos.p1.2026-10-21"])

        vm.toggleSikkum("p1", 0, 0)
        assertNull(vm.sichos().valueAt("p1", 0, 0))
        assertFalse("sichos.p1.2026-10-18" in NightStore(dir).load(PEOPLE_KEY).notes)
    }

    @Test
    fun reviewedIsATickThatStillCounts() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-10-18"))
        (0..5).forEach { vm.toggleSikkum("p2", 0, it) }
        vm.setReviewed("p2", 0, 1, true)
        val book = vm.sichos()
        assertEquals(Sichos.REVIEWED, book.valueAt("p2", 0, 1))
        assertEquals(6, book.count("p2", 0))
        assertEquals(1, book.entries("p2", 0))
        vm.setReviewed("p2", 0, 1, false)
        assertEquals(Sichos.IN, vm.sichos().valueAt("p2", 0, 1))
        // reviewing a sicha with nothing handed in does not hand one in
        vm.setReviewed("p2", 0, 7, true)
        assertNull(vm.sichos().valueAt("p2", 0, 7))
    }

    @Test
    fun theWinnerIsKeptAndDrawingAgainLeavesHimOut() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-09-20"))
        (0..7).forEach { vm.toggleSikkum("p1", 0, it) }
        (0..5).forEach { vm.toggleSikkum("p2", 0, it) }
        (0..4).forEach { vm.toggleSikkum("p3", 0, it) }
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
    fun aSikkumHandedInAtSederIsOnTheSichaBefore() {
        val start = day("2026-11-01")
        fun at(now: String) = testVm(NightStore(folder.newFolder()), java.time.LocalDateTime.parse(now)).also { it.setSichosStart(start) }
        // the first night, before there is a sicha to write on
        assertEquals(0 to 0, at("2026-11-01T20:00").sichosNow())
        assertEquals(0 to 0, at("2026-11-02T10:00").sichosNow())
        // Wednesday at seder, the ones handed in are on Sunday's sicha
        assertEquals(0 to 0, at("2026-11-04T21:00").sichosNow())
        assertEquals(0 to 1, at("2026-11-05T08:00").sichosNow())
        // and Sunday's are on Wednesday's
        assertEquals(0 to 1, at("2026-11-08T21:00").sichosNow())
        assertEquals(0 to 2, at("2026-11-09T08:00").sichosNow())
        // the eighth sicha's are coming in until the Sunday after it; then the next round's first
        assertEquals(0 to 7, at("2026-11-29T21:00").sichosNow())
        assertEquals(1 to 0, at("2026-11-30T08:00").sichosNow())
    }

    @Test
    fun aSichaOrAWholeRoundCanBeCleared() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-09-20"))
        (0..7).forEach { vm.toggleSikkum("p1", 0, it) }
        (0..7).forEach { vm.toggleSikkum("p2", 0, it) }
        vm.toggleSikkum("p3", 1, 0)
        vm.drawRaffle(0, random = Ticket(0))
        vm.clearSichos(0, 1)
        assertEquals(7, vm.sichos().count("p1", 0))
        assertNull(vm.sichos().valueAt("p2", 0, 1))
        assertEquals("p1", vm.sichos().winner(0))
        vm.clearSichos(0)
        assertEquals(0, vm.sichos().count("p1", 0))
        assertEquals(0, vm.sichos().count("p2", 0))
        assertNull(vm.sichos().winner(0))
        // another round is left as it was
        assertEquals(Sichos.IN, vm.sichos().valueAt("p3", 1, 0))
    }

    @Test
    fun aRandomFillGivesAMixAndACoupleWithNothing() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-09-20"))
        vm.toggleSikkum("p1", 0, 0)
        vm.fillSichosAtRandom(0, Random(7))
        val book = vm.sichos()
        val counts = com.roomcheck.app.data.Roster.PEOPLE.map { book.count(it.id, 0) }
        assertEquals(2, counts.count { it == 4 })
        assertTrue(counts.all { it in setOf(4, 6, 7, 8) })
        assertTrue((6 in counts || 7 in counts) && 8 in counts)
        assertEquals(counts.size - 2, book.pool(0).size)
    }

    @Test
    fun theRaffleOpensOnTheRoundJustEndedUntilItIsDrawn() {
        val vm = testVm(NightStore(folder.newFolder()))
        // 21 October collects the first sicha of the second round
        vm.setSichosStart(day("2026-09-20"))
        assertEquals(1 to 0, vm.sichosNow())
        assertEquals(1, vm.raffleRound())
        (0..7).forEach { vm.toggleSikkum("p1", 0, it) }
        assertEquals(0, vm.raffleRound())
        vm.drawRaffle(0, random = Ticket(0))
        assertEquals(1, vm.raffleRound())
    }

    @Test
    fun aWinnerCanBeClearedForADrawThatWasOnlyATry() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-09-20"))
        (0..7).forEach { vm.toggleSikkum("p1", 0, it) }
        assertEquals("p1", vm.drawRaffle(0, random = Ticket(0)))
        vm.clearRaffle(0)
        assertNull(vm.sichos().winner(0))
        assertFalse(vm.state.value.sichos.keys.any { it.startsWith("sichos.win.") })
        assertEquals("p1", vm.drawRaffle(0, random = Ticket(1)))
    }

    @Test
    fun theSichaOpensOnTodayAndALateOneOnARoundThatIsOver() {
        val vm = testVm(NightStore(folder.newFolder()))
        // a round that started on 4 October is on its fifth sicha on 21 October
        vm.setSichosStart(day("2026-10-04"))
        assertEquals(0, vm.sichosRound())
        assertEquals(4, vm.sichosWeek())
        val earlier = testVm(NightStore(folder.newFolder()))
        earlier.setSichosStart(day("2026-09-06"))
        assertEquals(1, earlier.sichosRound())
        earlier.showSichosRound(0)
        assertEquals(Sichos.PER_ROUND - 1, earlier.sichosWeek())
        earlier.pickSichosWeek(1)
        assertEquals(1, earlier.sichosWeek())
    }

    @Test
    fun movingTheStartTakesEverySikkumWithItsSicha() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-09-20"))
        (0..7).forEach { vm.toggleSikkum("p4", 0, it) }
        vm.setReviewed("p4", 0, 2, true)
        vm.toggleSikkum("p5", 1, 0)
        vm.drawRaffle(0, random = Ticket(0))
        // the first round really started on the Wednesday
        vm.setSichosStart(day("2026-09-23"))
        val book = vm.sichos()
        assertEquals(day("2026-09-23"), book.start)
        assertEquals(8, book.count("p4", 0))
        assertEquals(Sichos.REVIEWED, book.valueAt("p4", 0, 2))
        assertEquals(Sichos.IN, book.valueAt("p5", 1, 0))
        assertEquals(day("2026-10-21"), book.weekStart(1, 0))
        assertEquals("p4", book.winner(0))
        // the old cells are gone rather than left to count again: the start, eight, one, the winner
        assertEquals(11, vm.state.value.sichos.size)
        vm.toggleSikkum("p5", 1, 0)
        assertNull(vm.sichos().valueAt("p5", 1, 0))
    }

    @Test
    fun aStartMovedByWholeWeeksDoesNotLoseTheCellsItLandsOn() {
        val vm = testVm(NightStore(folder.newFolder()))
        vm.setSichosStart(day("2026-09-20"))
        (0..7).forEach { vm.toggleSikkum("p7", 0, it) }
        // a week earlier: each sicha's new night is the night two before it had
        vm.setSichosStart(day("2026-09-13"))
        assertEquals(8, vm.sichos().count("p7", 0))
        assertEquals(9, vm.state.value.sichos.size)
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
