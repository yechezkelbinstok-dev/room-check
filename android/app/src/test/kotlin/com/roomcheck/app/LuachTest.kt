package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Dates
import com.roomcheck.app.data.DayKind
import com.roomcheck.app.data.Luach
import com.roomcheck.app.data.LuachNow
import com.roomcheck.app.data.NightStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            // the Shabbos off from Wednesday night to Sunday night, then the Chanukah break
            listOf(THURSDAY, WEDNESDAY, MONDAY, TUESDAY, TUESDAY, THURSDAY, MONDAY, MONDAY),
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
        val g = Luach.ZMANIM.first { it.id == "winter" }.gaps.first { it.name(en = true) == "Chanukah break" }
        assertEquals("כ״ח כסלו", g.from.hebrew)
        assertEquals(LocalDate.of(2026, 12, 8), g.from.date)
        assertEquals("ה׳ טבת", g.to.hebrew)
        assertEquals(LocalDate.of(2026, 12, 15), g.to.date)
        assertEquals("Tue, Dec 8 · 2:00 pm", Luach.whenText(g.from))
        assertEquals("Tue, Dec 15 · 5:30 pm", Luach.whenText(g.to))
    }

    @Test
    fun inTheWinterZmanTheNextThingIsTheShabbosOff() {
        val now = Luach.now(at("2026-10-19T19:00"), summer = true) as LuachNow.InZman
        assertEquals("winter", now.zman.id)
        assertEquals("התחלת שבת חופשה", now.next?.title)
        assertEquals("in 16 days", Luach.countdown(now.next!!.date, LocalDate.of(2026, 10, 19)))
        // and after it the Chanukah break
        val after = Luach.now(at("2026-11-09T19:00"), summer = true) as LuachNow.InZman
        assertEquals("הפסקה", after.next?.title)
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
    fun crownHeightsIsTimeAway() {
        val trip = Luach.now(at("2027-07-09T12:00"), true) as LuachNow.InGap
        assertTrue(trip.gap.isTrip)
        assertEquals("חזרה לישיבה", trip.gap.to.title)
        assertEquals("Going to Crown Heights", trip.gap.name(en = true))
        assertEquals("נוסעים לקראון הייטס", trip.gap.name(en = false))
    }

    @Test
    fun theShabbosOffIsWednesdayNightToSundayNight() {
        val g = Luach.ZMANIM.first { it.id == "winter" }.gaps.first()
        assertEquals("Shabbos off", g.name(en = true))
        assertEquals("שבת חופשה", g.name(en = false))
        assertEquals(LocalDate.of(2026, 11, 4), g.from.date)
        assertEquals("כ״ד חשון", g.from.hebrew)
        assertEquals(LocalDate.of(2026, 11, 9), g.to.date)
        assertEquals("כ״ט חשון", g.to.hebrew)
        // seder is back from Monday morning, so Sunday is the last day of it
        assertEquals(LocalDate.of(2026, 11, 8), g.lastDate)
        assertEquals(DayKind.ZMAN, Luach.dayKind(LocalDate.of(2026, 11, 3), true))
        (4..8).forEach { assertEquals(DayKind.OFF, Luach.dayKind(LocalDate.of(2026, 11, it), true)) }
        assertEquals(DayKind.ZMAN, Luach.dayKind(LocalDate.of(2026, 11, 9), true))
        assertTrue(Luach.now(at("2026-11-08T22:00"), true) is LuachNow.InGap)
        assertTrue(Luach.now(at("2026-11-09T08:00"), true) is LuachNow.InZman)
    }

    @Test
    fun noBedCheckOnShabbosShavuosOrWhenThereIsNoSeder() {
        fun why(d: String, summer: Boolean = true) = Luach.noSeder(LocalDate.parse(d), summer)?.en
        // every Friday night, in the zman or out of it
        assertEquals("Shabbos", why("2026-10-23"))
        assertEquals("Shabbos", why("2026-12-11"))
        assertEquals("Shabbos", why("2026-09-04"))
        assertEquals("Shabbos", why("2028-03-03"))
        assertEquals("שבת", Luach.noSeder(LocalDate.parse("2026-10-23"), true)?.he)
        // an ordinary night, and the night after Shabbos
        assertNull(why("2026-10-21"))
        assertNull(why("2026-10-24"))
        // the two nights of Shavuos: the evenings of the 5th and 6th of Sivan
        val sivan5 = Dates.gregorianOf(5787, 9, 5)
        assertEquals("Shavuos", why(sivan5.toString()))
        assertEquals("Shavuos", why(sivan5.plusDays(1).toString()))
        assertEquals("שבועות", Luach.noSeder(sivan5, true)?.he)
        assertNull(why(sivan5.minusDays(1).toString()))
        assertNull(why(sivan5.plusDays(2).toString()))
        // the Shabbos off: Wednesday night to Sunday night, seder again on Monday night
        assertNull(why("2026-11-03"))
        assertEquals("Shabbos off", why("2026-11-04"))
        assertEquals("Shabbos off", why("2026-11-05"))
        assertEquals("Shabbos", why("2026-11-06"))
        assertEquals("Shabbos off", why("2026-11-07"))
        assertEquals("Shabbos off", why("2026-11-08"))
        assertNull(why("2026-11-09"))
        // the Chanukah break, to the evening seder starts again
        assertEquals("Chanukah break", why("2026-12-08"))
        assertEquals("Chanukah break", why("2026-12-14"))
        assertNull(why("2026-12-15"))
        // bein hazmanim, before the year and after it
        assertEquals("Bein hazmanim", why("2026-09-24"))
        assertEquals("בין הזמנים", Luach.noSeder(LocalDate.parse("2026-09-24"), true)?.he)
        assertEquals("Bein hazmanim", why("2027-04-20"))
        assertNull(why("2026-08-01"))
        assertNull(why("2027-09-01"))
        // away in Crown Heights, and with the summer zman off the zman ends the day it would have gone
        assertNull(why("2027-07-06"))
        assertEquals("Going to Crown Heights", why("2027-07-07"))
        assertEquals("Going to Crown Heights", why("2027-07-11"))
        assertNull(why("2027-07-12"))
        assertNull(why("2027-07-06", summer = false))
        assertEquals("Bein hazmanim", why("2027-07-07", summer = false))
        assertEquals("Bein hazmanim", why("2027-07-12", summer = false))
    }

    @Test
    fun withTheSummerZmanOffTheZmanEndsTheDayItWouldHaveGone() {
        val trip = Luach.ZMANIM.last().days.first { it.kind == com.roomcheck.app.data.LuachKind.TRIP }
        val cut = Luach.zmanim(summer = false).last()
        assertTrue(cut.cut)
        assertEquals(com.roomcheck.app.data.LuachKind.END, cut.end.kind)
        assertEquals("Zman ends", cut.end.title(en = true))
        assertEquals(trip.date, cut.end.date)
        assertEquals(trip.date, Luach.lastDay(summer = false))
        // Pesach is still bein hazmanim before the summer zman
        val pesach = Luach.now(at("2027-04-20T12:00"), summer = false) as LuachNow.Between
        assertEquals("summer", pesach.before?.id)
        // the last day is the end of the zman - not Crown Heights - and after it there is nothing
        assertEquals(DayKind.ZMAN, Luach.dayKind(trip.date, false))
        assertEquals(DayKind.NONE, Luach.dayKind(trip.date.plusDays(1), false))
        val over = Luach.now(trip.date.plusDays(1).atTime(12, 0), summer = false) as LuachNow.Between
        assertTrue(over.after!!.cut)
        assertNull(over.before)
        // no months after the trip's
        assertEquals("תמוז תשפ״ז", Luach.months(hebrewMonths = true, summer = false).last().title(en = false))
        assertEquals("July 2027", Luach.months(hebrewMonths = false, summer = false).last().title(en = true))
    }

    @Test
    fun everyDayOfTheYearCanBeOpenedWithTheSummerZmanOnOrOff() {
        for (summer in listOf(true, false)) {
            var d = LocalDate.of(2026, 8, 1)
            while (d <= LocalDate.of(2027, 8, 31)) {
                for (en in listOf(true, false)) {
                    when (Luach.dayKind(d, summer)) {
                        DayKind.OFF, DayKind.TRIP -> assertTrue("$d summer=$summer", Luach.gapOn(d, summer)?.name(en).orEmpty().isNotEmpty())
                        DayKind.ZMAN -> assertNotNull("$d summer=$summer", Luach.zmanOn(d, summer))
                        else -> {}
                    }
                }
                d = d.plusDays(1)
            }
        }
        // the day it would have gone: Crown Heights with the summer zman on, the end of the zman with it off
        assertEquals("Going to Crown Heights", Luach.gapOn(LocalDate.of(2027, 7, 7), true)?.name(en = true))
        assertNull(Luach.gapOn(LocalDate.of(2027, 7, 7), false))
        assertEquals(DayKind.TRIP, Luach.dayKind(LocalDate.of(2027, 7, 7), true))
        assertEquals(DayKind.ZMAN, Luach.dayKind(LocalDate.of(2027, 7, 7), false))
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
    fun everyDayIsSederABreakATripOrBeinHazmanim() {
        assertEquals(DayKind.ZMAN, Luach.dayKind(LocalDate.of(2026, 10, 19), true))
        // the break's first and last days count as the break
        assertEquals(DayKind.OFF, Luach.dayKind(LocalDate.of(2026, 12, 8), true))
        assertEquals(DayKind.OFF, Luach.dayKind(LocalDate.of(2026, 12, 15), true))
        assertEquals(DayKind.ZMAN, Luach.dayKind(LocalDate.of(2026, 12, 16), true))
        assertEquals(DayKind.BEIN, Luach.dayKind(LocalDate.of(2026, 9, 25), true))
        assertEquals(DayKind.TRIP, Luach.dayKind(LocalDate.of(2027, 7, 9), true))
        // before the year and after it, nothing
        assertEquals(DayKind.NONE, Luach.dayKind(LocalDate.of(2026, 8, 1), true))
        assertEquals(DayKind.NONE, Luach.dayKind(LocalDate.of(2027, 8, 19), true))
        assertEquals(listOf("הפסקה"), Luach.linesOn(LocalDate.of(2026, 12, 8), true).map { it.title })
    }

    @Test
    fun theCalendarGoesByHebrewMonthsOrRegularOnes() {
        val dec8 = LocalDate.of(2026, 12, 8)
        val heb = Luach.months(hebrewMonths = true)
        assertEquals("אלול תשפ״ו", heb.first().title(en = false))
        assertEquals("אב תשפ״ז", heb.last().title(en = false))
        assertEquals(13, heb.size)   // a leap year: Elul, then Tishrei to Av with two Adars
        val kislev = heb[Luach.monthIndexOf(dec8, hebrewMonths = true)]
        assertEquals("כסלו תשפ״ז", kislev.title(en = false))
        assertEquals("Kislev 5787", kislev.title(en = true))
        assertEquals("Nov – Dec 2026", kislev.sub(en = true))
        val greg = Luach.months(hebrewMonths = false)
        assertEquals("August 2026", greg.first().title(en = true))
        assertEquals("August 2027", greg.last().title(en = true))
        val december = greg[Luach.monthIndexOf(dec8, hebrewMonths = false)]
        assertEquals("December 2026", december.title(en = true))
        assertEquals("דצמבר 2026", december.title(en = false))
        assertEquals("Kislev – Teves 5787", december.sub(en = true))
        assertEquals("כסלו – טבת תשפ״ז", december.sub(en = false))
        // every day of the year is in exactly one month, both ways
        listOf(heb, greg).forEach { ms -> assertEquals(ms.flatMap { it.days }.size, ms.flatMap { it.days }.toSet().size) }
    }

    @Test
    fun theLanguagePicksTheMonthsUntilTheSwitchIsSet() {
        assertTrue(Luach.hebrewMonthsFor("", en = false))
        assertFalse(Luach.hebrewMonthsFor("", en = true))
        assertTrue(Luach.hebrewMonthsFor("heb", en = true))
        assertFalse(Luach.hebrewMonthsFor("greg", en = false))
        val dir = folder.newFolder()
        testVm(NightStore(dir)).setSettings { it.copy(luachMonths = "heb") }
        assertEquals("heb", NightStore(dir).settings.luachMonths)
    }

    @Test
    fun inEnglishTheLuachReadsInEnglish() {
        val winter = Luach.ZMANIM.first { it.id == "winter" }
        assertEquals("Winter zman", winter.name(en = true))
        assertEquals("Shabbos off", winter.gaps[0].name(en = true))
        val brk = winter.gaps[1]
        assertEquals("Chanukah break", brk.name(en = true))
        assertEquals("Break begins", brk.from.title(en = true))
        assertEquals("Tue · 28 Kislev · 2:00 pm", Luach.whenTextEn(brk.from))
        assertEquals("Shabbos off", winter.gaps[2].name(en = true))
    }

    @Test
    fun aNightWithNoSederIsLockedUnlessSomethingWasAlreadyMarkedOnIt() {
        // Friday night, 23 October
        val vm = testVm(NightStore(folder.newFolder()), at("2026-10-23T23:00"))
        assertEquals("Shabbos", vm.noSeder()?.en)
        assertTrue(vm.isLocked())
        // Saturday night has its seder, and Thursday's is its own two rounds
        vm.shiftDay(1)
        assertNull(vm.noSeder())
        vm.shiftDay(-2)
        assertNull(vm.noSeder())
        // a Friday that already has marks keeps them, to look at and to change
        vm.shiftDay(1)
        assertTrue(vm.isLocked())
        vm.setMark("p1", "1100", com.roomcheck.app.data.Mark.OUT)
        assertNull(vm.noSeder())
        assertFalse(vm.isLocked())
        // the summer switch moves what the luach says of the night
        val summer = testVm(NightStore(folder.newFolder()), at("2027-07-07T23:00"))
        assertEquals("Going to Crown Heights", summer.noSeder()?.en)
        summer.setSummerZman(false)
        assertEquals("Bein hazmanim", summer.noSeder()?.en)
    }

    @Test
    fun theSummerSwitchIsSharedLikeEverythingElse() {
        val dir = folder.newFolder()
        val vm = testVm(NightStore(dir))
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
