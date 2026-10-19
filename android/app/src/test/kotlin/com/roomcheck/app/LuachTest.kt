package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Luach
import com.roomcheck.app.data.LuachNow
import com.roomcheck.app.data.NightStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek
import java.time.DayOfWeek.*
import java.time.LocalDate
import java.time.LocalDateTime

/** The yeshiva's luach: its dates, what the year looks like at a given moment, and the summer zman switch. */
class LuachTest {

    @get:Rule val folder = TemporaryFolder()

    private fun at(s: String) = LocalDateTime.parse(s)

    @Test
    fun everyLineFallsOnTheWeekdayTheLuachGivesIt() {
        val expected: List<List<DayOfWeek>> = listOf(
            listOf(SUNDAY, THURSDAY, THURSDAY),
            // the Chanukah break is Tuesday to Tuesday now, where the printed luach had Monday to Monday
            listOf(THURSDAY, TUESDAY, TUESDAY, THURSDAY, MONDAY, MONDAY),
            listOf(WEDNESDAY, WEDNESDAY, MONDAY, WEDNESDAY)
        )
        assertEquals(expected, Luach.ZMANIM.map { z -> z.days.map { it.date.dayOfWeek } })
    }

    @Test
    fun theYearRunsFromElulToAv() {
        assertEquals(LocalDate.of(2026, 8, 23), Luach.ZMANIM.first().start.date)
        assertEquals("י׳ אלול", Luach.ZMANIM.first().start.hebrew)
        assertEquals(LocalDate.of(2027, 8, 18), Luach.ZMANIM.last().end.date)
        assertEquals("ט״ו אב", Luach.ZMANIM.last().end.hebrew)
    }

    @Test
    fun theChanukahBreakIsTuesdayToTuesday() {
        val g = Luach.ZMANIM.first { it.id == "winter" }.gaps.first()
        assertEquals("כ״ח כסלו", g.from.hebrew)
        assertEquals(LocalDate.of(2026, 12, 8), g.from.date)
        assertEquals("ה׳ טבת", g.to.hebrew)
        assertEquals(LocalDate.of(2026, 12, 15), g.to.date)
        assertEquals("Tue, Dec 8 · 2:00 pm", Luach.whenText(g.from))
        assertEquals("Tue, Dec 15 · 5:30 pm", Luach.whenText(g.to))
    }

    @Test
    fun inTheWinterZmanTheNextThingIsTheChanukahBreak() {
        val now = Luach.now(at("2026-10-19T19:00"), summer = true) as LuachNow.InZman
        assertEquals("winter", now.zman.id)
        assertEquals("הפסקה", now.next?.title)
        assertEquals("in 50 days", Luach.countdown(now.next!!.date, LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun aBreakLastsUntilTheMinuteSederStartsAgain() {
        assertTrue(Luach.now(at("2026-12-10T12:00"), true) is LuachNow.InGap)
        assertTrue(Luach.now(at("2026-12-15T17:00"), true) is LuachNow.InGap)
        val back = Luach.now(at("2026-12-15T18:00"), true) as LuachNow.InZman
        assertEquals("התחלת שבת חופשה", back.next?.title)
    }

    @Test
    fun beinHazmanimSitsBetweenTwoZmanim() {
        val sukkos = Luach.now(at("2026-09-25T12:00"), true) as LuachNow.Between
        assertEquals("elul", sukkos.after?.id)
        assertEquals("winter", sukkos.before?.id)
        val before = Luach.now(at("2026-08-01T12:00"), true) as LuachNow.Between
        assertNull(before.after)
        assertEquals("elul", before.before?.id)
        val over = Luach.now(at("2027-09-01T12:00"), true) as LuachNow.Between
        assertEquals("summer", over.after?.id)
        assertNull(over.before)
    }

    @Test
    fun theTripToBeisChayeinuIsTimeAway() {
        val trip = Luach.now(at("2027-07-09T12:00"), true) as LuachNow.InGap
        assertTrue(trip.gap.isTrip)
        assertEquals("חזרה לישיבה", trip.gap.to.title)
    }

    @Test
    fun withTheSummerZmanOffPesachRunsToTheEndOfTheYear() {
        val on = Luach.now(at("2027-04-20T12:00"), summer = true) as LuachNow.Between
        assertEquals("summer", on.before?.id)
        val off = Luach.now(at("2027-04-20T12:00"), summer = false) as LuachNow.Between
        assertEquals("winter", off.after?.id)
        assertNull(off.before)
        assertEquals(listOf("elul", "winter"), Luach.zmanim(summer = false).map { it.id })
    }

    @Test
    fun lengthsReadAsWeeksOrDays() {
        assertEquals("1 week", Luach.lengthText(7))
        assertEquals("3 weeks", Luach.lengthText(21))
        assertEquals("4 days", Luach.lengthText(4))
        assertEquals("23 days", Luach.lengthText(23))
        assertEquals("27 weeks", Luach.lengthText(186))
    }

    @Test
    fun theSummerSwitchIsSharedLikeEverythingElse() {
        val dir = folder.newFolder()
        val vm = AppViewModel(NightStore(dir))
        assertTrue(vm.state.value.summerZman)
        vm.setSummerZman(false)
        assertFalse(vm.state.value.summerZman)

        val store = NightStore(dir)
        assertFalse(store.summerZman)
        assertTrue(NightStore.PEOPLE_KEY in store.dirtyDates())
        assertEquals("1", store.load(NightStore.PEOPLE_KEY).notes[NightStore.NO_SUMMER])
        // it is the yeshiva's, not a person's - nobody called "yeshiva" turns up in the list
        assertFalse(store.extra.containsKey("yeshiva"))

        vm.setSummerZman(true)
        assertTrue(NightStore(dir).summerZman)
    }
}
