package com.roomcheck.app

import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Night
import com.roomcheck.app.data.Slots
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Times are kept in the order they are walked, which is not the order the ids sort as numbers.
 * Ids read like a clock face - "1115" is quarter past eleven at night, "1200" is midnight - so
 * 12 comes after 11 but before 1, and a 1:30 round sorted naively lands at the head of the sheet.
 *
 * Rounds belong to a night, not to the app: one night marked at 1:30 leaves every other night
 * alone.
 */
class SlotsTest {

    private fun night(vararg only: String) = Night().apply { rounds.addAll(only) }

    /** A night with nothing said about it is the usual three. */
    @Test
    fun theUsualNightIsTheStandingThree() {
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.all(Night()).map { it.label })
    }

    /**
     * The case this exists for: the zman moved, so tonight is ONE check at 12:30 - not the usual
     * three with a fourth bolted on.
     */
    @Test
    fun aNightCanBeOneRoundAtItsOwnTime() {
        assertEquals(listOf("12:30"), Slots.all(night("1230")).map { it.label })
    }

    @Test
    fun aNightCanBeTwoRounds() {
        assertEquals(listOf("12:30", "1:30"), Slots.all(night("1230", "0130")).map { it.label })
    }

    /** Midnight sits between eleven and one, which is the whole reason ids cannot sort as numbers. */
    @Test
    fun midnightSitsBetweenElevenAndOne() {
        assertEquals(
            listOf("10:45", "11:15", "12:00", "1:30"),
            Slots.all(night("0130", "1045", "1115", "1200")).map { it.label }
        )
    }

    /** One night's rounds say nothing about any other night's. */
    @Test
    fun roundsBelongToTheirOwnNightOnly() {
        assertEquals(listOf("12:30"), Slots.all(night("1230")).map { it.label })
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.all(Night()).map { it.label })
    }

    /** It has to survive being written out and read back, or it lasts until the app is closed. */
    @Test
    fun aNightsRoundsSurviveASaveAndLoad() {
        val n = night("1230")
        n.marks["1230"] = mutableMapOf("p1" to Mark.OUT)
        val back = Night.fromJson(n.toJson())
        assertEquals(listOf("12:30"), Slots.all(back).map { it.label })
        assertEquals(Mark.OUT, back.marks["1230"]?.get("p1"))
    }

    /**
     * A round that has marks in it counts even if nothing declared it, so a round removed by
     * mistake - or a night from a copy that recorded them some other way - keeps its column.
     */
    @Test
    fun aRoundWithMarksInItCountsWithoutBeingDeclared() {
        val n = Night()
        n.marks["0130"] = mutableMapOf("p1" to Mark.OUT)
        assertEquals(listOf("11:15", "11:30", "12:00", "1:30"), Slots.all(n).map { it.label })
    }

    /** An empty slot map is not a round - clearing the last mark must not pin the column open. */
    @Test
    fun anEmptySlotMapIsNotARound() {
        val n = Night()
        n.marks["0130"] = mutableMapOf()
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.all(n).map { it.label })
    }

    /** The round the app opens on is whichever is nearest behind the clock, across midnight too. */
    @Test
    fun theClockMapsOntoTheRightRound() {
        assertEquals("1120", Slots.idForClock(23, 20))
        assertEquals("1230", Slots.idForClock(0, 30))
        assertEquals("0140", Slots.idForClock(1, 40))
        assertEquals(true, Slots.order(Slots.idForClock(23, 20)) > Slots.order("1115"))
        assertEquals(true, Slots.order(Slots.idForClock(0, 30)) > Slots.order("1200"))
        assertEquals(true, Slots.order(Slots.idForClock(1, 40)) > Slots.order("0130"))
    }

    @Test
    fun labelsReadAsAClockDoes() {
        assertEquals("1:30", Slots.labelFor("0130"))
        assertEquals("12:00", Slots.labelFor("1200"))
        assertEquals("10:45", Slots.labelFor("1045"))
    }

    @Test
    fun rubbishTimesAreRefused() {
        listOf("2460", "99", "abcd", "1300", "1160", "0060").forEach {
            assertEquals(it, false, Slots.isValidId(it))
        }
        assertEquals(true, Slots.isValidId("0130"))
    }

    /** The sheet shows everything unless you narrowed it, and narrowing can pick just one. */
    @Test
    fun theSheetCanBeNarrowedToOneTime() {
        val n = Night().apply { sheetSlots.add("1200") }
        assertEquals(listOf("12:00"), Slots.forSheet(n).map { it.label })
    }

    @Test
    fun theSheetCanBeNarrowedToTwo() {
        val n = night("1115", "1200", "0130").apply { sheetSlots.addAll(listOf("1200", "0130")) }
        assertEquals(listOf("12:00", "1:30"), Slots.forSheet(n).map { it.label })
    }

    /** Narrowing is one night's too - the next night's picture is the full three again. */
    @Test
    fun narrowingTheSheetIsAlsoJustThatNight() {
        night("0130").apply { sheetSlots.add("0130") }
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.forSheet(Night()).map { it.label })
    }

    /** Removing the round the sheet was narrowed to must not leave a blank sheet. */
    @Test
    fun narrowingToATimeThatNoLongerExistsFallsBackToAll() {
        val n = Night().apply { sheetSlots.add("0130") }
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.forSheet(n).map { it.label })
    }

    /** A round with marks in it still shows, so an older night keeps the columns it was marked in. */
    @Test
    fun aRoundWithMarksInItKeepsItsColumn() {
        val n = night("1230")
        n.marks["0130"] = mutableMapOf("p1" to Mark.OUT)
        assertEquals(listOf("12:30", "1:30"), Slots.all(n).map { it.label })
    }
}
