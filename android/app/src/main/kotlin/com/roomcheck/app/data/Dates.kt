package com.roomcheck.app.data

import android.icu.util.GregorianCalendar
import android.icu.util.HebrewCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Dates {
    private val KEY_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun keyOf(date: LocalDate): String = date.format(KEY_FMT)
    fun parseKey(key: String): LocalDate = LocalDate.parse(key, KEY_FMT)
    fun shiftKey(key: String, days: Long): String = keyOf(parseKey(key).plusDays(days))

    /** Same 6am rollover rule as the web app: a check after midnight still belongs to last night. */
    fun tonightKey(now: LocalDateTime = LocalDateTime.now()): String {
        val effective = if (now.hour < 6) now.minusDays(1) else now
        return keyOf(effective.toLocalDate())
    }

    fun longDate(key: String): String {
        val d = parseKey(key)
        val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val month = d.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$dow, $month ${d.dayOfMonth}"
    }

    private val HEB_NUM = listOf(
        400 to "ת", 300 to "ש", 200 to "ר", 100 to "ק", 90 to "צ", 80 to "פ", 70 to "ע",
        60 to "ס", 50 to "נ", 40 to "מ", 30 to "ל", 20 to "כ", 10 to "י", 9 to "ט", 8 to "ח",
        7 to "ז", 6 to "ו", 5 to "ה", 4 to "ד", 3 to "ג", 2 to "ב", 1 to "א"
    )
    fun hebNum(n: Int): String {
        if (n == 15) return "טו"
        if (n == 16) return "טז"
        var v = n
        val sb = StringBuilder()
        while (v > 0) {
            val (amt, ch) = HEB_NUM.first { v >= it.first }
            sb.append(ch); v -= amt
        }
        return sb.toString()
    }
    private fun gershayim(s: String): String =
        if (s.length == 1) s + "׳" else s.dropLast(1) + "״" + s.last()

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /** Converts a Gregorian y/m/d into the equivalent Hebrew calendar, via shared epoch millis (fields must never be set directly across calendar systems). */
    private fun hebrewCalendarFor(g: LocalDate): HebrewCalendar {
        val greg = GregorianCalendar(UTC)
        greg.clear()
        greg.set(g.year, g.monthValue - 1, g.dayOfMonth, 12, 0, 0) // noon, clear of any DST edge cases
        val heb = HebrewCalendar(UTC)
        heb.timeInMillis = greg.timeInMillis
        return heb
    }

    // ICU's HebrewCalendar has no public isLeapYear(); the 19-year Metonic-cycle test below is
    // simple, exact, well-known arithmetic (same formula the original web app used, proven there).
    private fun isHebLeapYear(year: Int): Boolean = ((year * 7 + 1) % 19) < 7

    // ICU month constants: TISHRI=0 HESHVAN=1 KISLEV=2 TEVET=3 SHEVAT=4 ADAR_1=5 ADAR=6
    // NISAN=7 IYAR=8 SIVAN=9 TAMUZ=10 AV=11 ELUL=12. ADAR_1(5) only ever occurs in a leap
    // year (the inserted 13th month); ADAR(6) is the only Adar in a regular year, or "Adar II"
    // when a leap year's ADAR_1 preceded it.
    private val HEB_MONTH_NAMES = arrayOf(
        "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר א׳", "אדר",
        "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול"
    )
    private val HEB_MONTH_NAMES_EN = arrayOf(
        "Tishrei", "Cheshvan", "Kislev", "Teves", "Shevat", "Adar I", "Adar",
        "Nissan", "Iyar", "Sivan", "Tammuz", "Av", "Elul"
    )
    fun hebMonthName(monthIdx: Int, year: Int): String =
        if (monthIdx == 6 && isHebLeapYear(year)) "אדר ב׳" else HEB_MONTH_NAMES[monthIdx]
    fun hebMonthNameEn(monthIdx: Int, year: Int): String =
        if (monthIdx == 6 && isHebLeapYear(year)) "Adar II" else HEB_MONTH_NAMES_EN[monthIdx]
    fun hebYearStr(year: Int): String = gershayim(hebNum(year % 1000))

    /** Hebrew date for the NIGHT that starts on this Gregorian date (matches the web app's hebParts: absOfKey+1). */
    fun hebrewDate(key: String): String {
        val g = parseKey(key).plusDays(1) // night belongs to the Hebrew date of the *morning after*, same as web app
        val cal = hebrewCalendarFor(g)
        val day = cal.get(HebrewCalendar.DATE)
        val year = cal.get(HebrewCalendar.YEAR)
        val monthIdx = cal.get(HebrewCalendar.MONTH)
        return "${gershayim(hebNum(day))} ${hebMonthName(monthIdx, year)} ${hebYearStr(year)}"
    }

    data class HebMonth(val year: Int, val month: Int)

    /** The Hebrew year/month containing the night that starts on this Gregorian date. */
    fun hebMonthOf(key: String): HebMonth {
        val cal = hebrewCalendarFor(parseKey(key).plusDays(1))
        return HebMonth(cal.get(HebrewCalendar.YEAR), cal.get(HebrewCalendar.MONTH))
    }

    fun nextHebMonth(m: HebMonth): HebMonth {
        val cal = HebrewCalendar(UTC)
        cal.clear()
        cal.set(m.year, m.month, 1, 12, 0, 0)
        cal.add(HebrewCalendar.MONTH, 1)
        return HebMonth(cal.get(HebrewCalendar.YEAR), cal.get(HebrewCalendar.MONTH))
    }

    fun prevHebMonth(m: HebMonth): HebMonth {
        val cal = HebrewCalendar(UTC)
        cal.clear()
        cal.set(m.year, m.month, 1, 12, 0, 0)
        cal.add(HebrewCalendar.MONTH, -1)
        return HebMonth(cal.get(HebrewCalendar.YEAR), cal.get(HebrewCalendar.MONTH))
    }

    data class CalDay(val hebDay: Int, val dateKey: String, val dayOfWeek: Int) // dayOfWeek: 0=Sun..6=Sat

    /** All nights (dateKeys) that fall in this Hebrew month, in order. */
    fun daysInHebMonth(m: HebMonth): List<CalDay> {
        val cal = HebrewCalendar(UTC)
        cal.clear()
        cal.set(m.year, m.month, 1, 12, 0, 0)
        val length = cal.getActualMaximum(HebrewCalendar.DATE)
        return (1..length).map { day ->
            cal.clear()
            cal.set(m.year, m.month, day, 12, 0, 0)
            val millis = cal.timeInMillis
            val greg = GregorianCalendar(UTC)
            greg.timeInMillis = millis
            val gDate = LocalDate.of(
                greg.get(GregorianCalendar.YEAR),
                greg.get(GregorianCalendar.MONTH) + 1,
                greg.get(GregorianCalendar.DAY_OF_MONTH)
            )
            val nightKey = keyOf(gDate.minusDays(1)) // inverse of hebrewDate()'s +1
            val dow = greg.get(GregorianCalendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY=1 -> 0
            CalDay(day, nightKey, dow)
        }
    }
}
