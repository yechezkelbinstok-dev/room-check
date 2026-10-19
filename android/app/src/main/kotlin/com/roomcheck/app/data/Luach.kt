package com.roomcheck.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a line of the luach marks. */
enum class LuachKind {
    /** The zman starts, or ends. */
    START, END,
    /** Seder stops for a break, and starts again. */
    OFF, BACK,
    /** The yeshiva travels together, and comes home. */
    TRIP, HOME,
    /** Something that happens with the zman carrying on - נסיעה להרבי, where seder continues at the Rebbe. */
    EVENT
}

/** What a whole day is, for colouring it on the month: seder, a break, a trip, or bein hazmanim. */
enum class DayKind { ZMAN, OFF, TRIP, BEIN }

/**
 * One line of the luach, as printed: a Hebrew date (ICU month numbering, Tishrei = 0) and its time
 * if it has one, in Hebrew as the luach words it and in English. The Gregorian date and the weekday
 * are worked out from the Hebrew date rather than typed in beside it, so the two can never disagree.
 */
data class LuachDay(
    val year: Int, val month: Int, val day: Int,
    val kind: LuachKind,
    val title: String,
    val titleEn: String,
    val time: LocalTime? = null,
    val note: String? = null,
    val noteEn: String? = null,
    /** What the stretch this line opens is called, where the title only says how it starts. */
    val stretch: String? = null,
    val stretchEn: String? = null
) {
    val date: LocalDate = Dates.gregorianOf(year, month, day)
    /** When it happens. A line with no time counts from the start of its day. */
    val at: LocalDateTime = date.atTime(time ?: LocalTime.MIDNIGHT)
    /** "כ״ח כסלו" */
    val hebrew: String get() = Dates.hebrewDayMonthOf(year, month, day)
    /** "28 Kislev" */
    val hebrewEn: String get() = "$day ${Dates.hebMonthNameEn(month, year)}"

    fun title(en: Boolean) = if (en) titleEn else title
    fun note(en: Boolean) = if (en) noteEn else note
}

/** A stretch inside a zman away from seder: a break, OFF to BACK, or a trip, TRIP to HOME. */
data class LuachGap(val from: LuachDay, val to: LuachDay) {
    val isTrip: Boolean get() = from.kind == LuachKind.TRIP
    fun name(en: Boolean): String =
        if (en) from.stretchEn ?: from.titleEn else from.stretch ?: from.title
}

data class Zman(
    val id: String, val name: String, val nameEn: String, val days: List<LuachDay>, val optional: Boolean = false
) {
    val start: LuachDay get() = days.first()
    val end: LuachDay get() = days.last()
    fun name(en: Boolean) = if (en) nameEn else name
    val gaps: List<LuachGap>
        get() = days.zipWithNext()
            .filter { (a, b) ->
                (a.kind == LuachKind.OFF && b.kind == LuachKind.BACK) || (a.kind == LuachKind.TRIP && b.kind == LuachKind.HOME)
            }
            .map { (a, b) -> LuachGap(a, b) }
}

/** Where the year stands at a given moment. */
sealed interface LuachNow {
    /** In a zman, seder on. [next] is the next line still to come in it. */
    data class InZman(val zman: Zman, val next: LuachDay?) : LuachNow
    /** In a break or a trip inside a zman. */
    data class InGap(val zman: Zman, val gap: LuachGap) : LuachNow
    /** Bein hazmanim: [after] the zman just ended, [before] the next to start - either may be missing. */
    data class Between(val after: Zman?, val before: Zman?) : LuachNow
}

/** One month of the calendar: its name, the other calendar's months it spans, and its days in order. */
data class LuachMonth(val title: String, val sub: String, val days: List<LocalDate>)

/**
 * The yeshiva's luach for the year - לוח שנת הלימודים תשפ״ו-תשפ״ז, ישיבה גדולה ליובאוויטש
 * סינסינאטי - kept to the lines as printed, with the changes made since written into it.
 */
object Luach {
    const val YEAR = "תשפ״ו–תשפ״ז"
    const val YEAR_EN = "5786–5787"
    const val YESHIVA = "ישיבה גדולה ליובאוויטש סינסינאטי"
    const val YESHIVA_EN = "Yeshiva Gedolah Lubavitch Cincinnati"

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    // ICU months: Tishrei 0, Cheshvan 1, Kislev 2, Teves 3, Shevat 4, Adar I 5, Adar (II) 6,
    // Nissan 7, Iyar 8, Sivan 9, Tammuz 10, Av 11, Elul 12. תשפ״ז is a leap year, so Adar I is 5.
    val ZMANIM: List<Zman> = listOf(
        Zman("elul", "זמן אלול", "Elul zman", listOf(
            LuachDay(5786, 12, 10, LuachKind.START, "התחלת הזמן", "Zman begins", t(17, 30)),
            LuachDay(
                5786, 12, 28, LuachKind.EVENT, "נסיעה להרבי", "Trip to the Rebbe",
                note = "המשך הסדר אחר ר״ה אצל הרבי", noteEn = "Seder continues at the Rebbe after Rosh Hashanah"
            ),
            LuachDay(5787, 0, 6, LuachKind.END, "סיום הזמן", "Zman ends", t(14, 0))
        )),
        Zman("winter", "זמן חורף", "Winter zman", listOf(
            LuachDay(5787, 0, 27, LuachKind.START, "התחלת הזמן", "Zman begins", t(17, 30)),
            // the luach printed Monday כ״ז כסלו to Monday ד׳ טבת; it has since moved to Tuesday to Tuesday
            LuachDay(
                5787, 2, 28, LuachKind.OFF, "הפסקה", "Break begins", t(14, 0),
                note = "חנוכה", noteEn = "Chanukah", stretchEn = "Chanukah break"
            ),
            LuachDay(5787, 3, 5, LuachKind.BACK, "המשך הזמן", "Zman resumes", t(17, 30)),
            LuachDay(
                5787, 5, 4, LuachKind.OFF, "התחלת שבת חופשה", "Shabbos off begins", t(11, 0),
                stretch = "שבת חופשה", stretchEn = "Shabbos off"
            ),
            LuachDay(5787, 5, 8, LuachKind.BACK, "המשך הזמן", "Zman resumes", t(20, 30)),
            LuachDay(5787, 7, 5, LuachKind.END, "סיום הזמן", "Zman ends", t(14, 0))
        )),
        // may not happen, so it can be switched off - see NightStore.summerZman
        Zman("summer", "זמן קיץ", "Summer zman", optional = true, days = listOf(
            LuachDay(5787, 7, 28, LuachKind.START, "התחלת הזמן", "Zman begins", t(17, 30)),
            LuachDay(
                5787, 10, 2, LuachKind.TRIP, "נסיעה לבית חיינו", "Trip to Beis Chayeinu",
                note = "הישיבה יארגן אוטובוס", noteEn = "The yeshiva is arranging a bus"
            ),
            LuachDay(5787, 10, 7, LuachKind.HOME, "חזרה לישיבה", "Back to yeshiva"),
            LuachDay(5787, 11, 15, LuachKind.END, "סיום הזמן", "Zman ends", t(14, 0))
        ))
    )

    /** The zmanim the year actually has: the summer zman only while it is switched on. */
    fun zmanim(summer: Boolean): List<Zman> = ZMANIM.filter { summer || !it.optional }

    fun now(at: LocalDateTime, summer: Boolean): LuachNow {
        val year = zmanim(summer)
        year.forEachIndexed { i, z ->
            if (at < z.start.at) return LuachNow.Between(year.getOrNull(i - 1), z)
            if (at < z.end.at) {
                z.gaps.firstOrNull { at >= it.from.at && at < it.to.at }?.let { return LuachNow.InGap(z, it) }
                return LuachNow.InZman(z, z.days.firstOrNull { it.at > at })
            }
        }
        return LuachNow.Between(year.lastOrNull(), null)
    }

    /**
     * What a whole day is. A break's first and last days count as the break - each has half a day
     * of it, and it is the break that the day is remembered for.
     */
    fun dayKind(date: LocalDate, summer: Boolean): DayKind {
        val z = zmanOn(date, summer) ?: return DayKind.BEIN
        val g = z.gaps.firstOrNull { date >= it.from.date && date <= it.to.date } ?: return DayKind.ZMAN
        return if (g.isTrip) DayKind.TRIP else DayKind.OFF
    }

    fun zmanOn(date: LocalDate, summer: Boolean): Zman? =
        zmanim(summer).firstOrNull { date >= it.start.date && date <= it.end.date }

    /** The luach's lines on a day - usually none, now and then one. */
    fun linesOn(date: LocalDate, summer: Boolean): List<LuachDay> =
        zmanim(summer).flatMap { it.days }.filter { it.date == date }

    /** The gap a day falls in, if it falls in one. */
    fun gapOn(date: LocalDate, summer: Boolean): LuachGap? =
        zmanOn(date, summer)?.gaps?.firstOrNull { date >= it.from.date && date <= it.to.date }

    private val firstDay = ZMANIM.first().start.date
    private val lastDay = ZMANIM.last().end.date

    /** The year a Hebrew month at a time, Elul to Av, for the calendar in Hebrew. */
    val hebrewMonths: List<LuachMonth> by lazy {
        val first = Dates.hebrewOf(firstDay)
        val last = Dates.hebrewOf(lastDay)
        val out = mutableListOf<LuachMonth>()
        var m = Dates.HebMonth(first.year, first.month)
        while (true) {
            val days = (1..Dates.hebMonthLength(m.year, m.month)).map { Dates.gregorianOf(m.year, m.month, it) }
            val a = days.first(); val b = days.last()
            val sub = if (a.month == b.month) monthYear(a) else if (a.year == b.year)
                "${monthName(a, short = true)} – ${monthName(b, short = true)} ${b.year}"
            else "${monthName(a, short = true)} ${a.year} – ${monthName(b, short = true)} ${b.year}"
            out += LuachMonth("${Dates.hebMonthName(m.month, m.year)} ${Dates.hebYearStr(m.year)}", sub, days)
            if (m.year == last.year && m.month == last.month) break
            m = Dates.nextHebMonth(m)
        }
        out
    }

    /** The year a month at a time, August to August, for the calendar in English. */
    val gregorianMonths: List<LuachMonth> by lazy {
        val out = mutableListOf<LuachMonth>()
        var ym = YearMonth.from(firstDay)
        while (!ym.isAfter(YearMonth.from(lastDay))) {
            val days = (1..ym.lengthOfMonth()).map { ym.atDay(it) }
            val a = Dates.hebrewOf(days.first()); val b = Dates.hebrewOf(days.last())
            val sub = if (a.month == b.month) "${Dates.hebMonthNameEn(a.month, a.year)} ${a.year}"
            else if (a.year == b.year) "${Dates.hebMonthNameEn(a.month, a.year)} – ${Dates.hebMonthNameEn(b.month, b.year)} ${b.year}"
            else "${Dates.hebMonthNameEn(a.month, a.year)} ${a.year} – ${Dates.hebMonthNameEn(b.month, b.year)} ${b.year}"
            out += LuachMonth(monthYear(days.first(), short = false), sub, days)
            ym = ym.plusMonths(1)
        }
        out
    }

    fun months(en: Boolean) = if (en) gregorianMonths else hebrewMonths

    /** Which month of the calendar holds this day, or the nearest end of the year when none does. */
    fun monthIndexOf(date: LocalDate, en: Boolean): Int {
        val ms = months(en)
        val i = ms.indexOfFirst { date in it.days }
        return when {
            i >= 0 -> i
            date < ms.first().days.first() -> 0
            else -> ms.lastIndex
        }
    }

    private fun monthName(d: LocalDate, short: Boolean) =
        d.month.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.ENGLISH)

    private fun monthYear(d: LocalDate, short: Boolean = true) = "${monthName(d, short)} ${d.year}"

    /** How far through a stretch a moment is, 0 to 1. */
    fun progress(from: LocalDateTime, to: LocalDateTime, at: LocalDateTime): Float {
        val total = ChronoUnit.MINUTES.between(from, to).toFloat()
        if (total <= 0f) return 1f
        return (ChronoUnit.MINUTES.between(from, at) / total).coerceIn(0f, 1f)
    }

    fun days(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** "3 weeks", "23 days", "27 weeks" - whole weeks when it is whole weeks or long, days otherwise. */
    fun lengthText(days: Long): String = when {
        days == 1L -> "1 day"
        days % 7 == 0L -> if (days == 7L) "1 week" else "${days / 7} weeks"
        days < 35 -> "$days days"
        else -> "${Math.round(days / 7.0)} weeks"
    }

    /** "today", "tomorrow", "in 50 days" - or null once the day has gone. */
    fun countdown(date: LocalDate, today: LocalDate): String? = when (val n = days(today, date)) {
        0L -> "today"
        1L -> "tomorrow"
        else -> if (n < 0) null else "in $n days"
    }

    fun timeText(t: LocalTime): String {
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "$h:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "am" else "pm"}"
    }

    /** "Tue, Dec 8" - with ", 2026" when [withYear]. */
    fun dateText(d: LocalDate, withYear: Boolean = false): String {
        val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return "$dow, ${monthName(d, short = true)} ${d.dayOfMonth}" + if (withYear) ", ${d.year}" else ""
    }

    /** "Tue, Dec 8 · 2:00 pm", the way the luach gives a line's day and time. */
    fun whenText(d: LuachDay): String = dateText(d.date) + (d.time?.let { " · " + timeText(it) } ?: "")

    /** In English the Gregorian day leads, so the line carries the Hebrew one: "Tue · 28 Kislev · 2:00 pm". */
    fun whenTextEn(d: LuachDay): String =
        d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " · " + d.hebrewEn +
            (d.time?.let { " · " + timeText(it) } ?: "")

    /** "Oct 8, 2026 – Apr 12, 2027" */
    fun rangeText(from: LocalDate, to: LocalDate): String {
        fun md(d: LocalDate) = monthName(d, short = true) + " " + d.dayOfMonth
        return if (from.year == to.year) "${md(from)} – ${md(to)}, ${to.year}" else "${md(from)}, ${from.year} – ${md(to)}, ${to.year}"
    }
}
