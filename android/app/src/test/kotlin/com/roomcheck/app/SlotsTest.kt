package com.roomcheck.app

import com.roomcheck.app.data.Settings
import com.roomcheck.app.data.Slots
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Times are kept in the order they are walked, which is not the order the ids sort as numbers.
 * Ids read like a clock face - "1115" is quarter past eleven at night, "1200" is midnight - so
 * 12 comes after 11 but before 1, and a 1:30 round sorted naively lands at the head of the sheet.
 */
class SlotsTest {

    @Test
    fun aLateRoundSortsAfterMidnightNotBeforeEverything() {
        val s = Settings(customSlots = listOf("0130"))
        assertEquals(listOf("11:15", "11:30", "12:00", "1:30"), Slots.all(s).map { it.label })
    }

    @Test
    fun anEarlierExtraRoundSortsWhereItBelongs() {
        val s = Settings(customSlots = listOf("1045"))
        assertEquals(listOf("10:45", "11:15", "11:30", "12:00"), Slots.all(s).map { it.label })
    }

    /** Midnight sits between eleven and one, which is the whole reason ids cannot sort as numbers. */
    @Test
    fun midnightSitsBetweenElevenAndOne() {
        val s = Settings(customSlots = listOf("0130", "1045"))
        assertEquals(listOf("10:45", "11:15", "11:30", "12:00", "1:30"), Slots.all(s).map { it.label })
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
        val s = Settings(customSlots = listOf("0130"), sheetSlots = listOf("0130"))
        assertEquals(listOf("1:30"), Slots.forSheet(s).map { it.label })
    }

    @Test
    fun theSheetCanBeNarrowedToTwo() {
        val s = Settings(customSlots = listOf("0130"), sheetSlots = listOf("1200", "0130"))
        assertEquals(listOf("12:00", "1:30"), Slots.forSheet(s).map { it.label })
    }

    /** Deleting the custom time the sheet was narrowed to must not leave a blank sheet. */
    @Test
    fun narrowingToATimeThatNoLongerExistsFallsBackToAll() {
        val s = Settings(customSlots = emptyList(), sheetSlots = listOf("0130"))
        assertEquals(listOf("11:15", "11:30", "12:00"), Slots.forSheet(s).map { it.label })
    }
}
