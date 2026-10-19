package com.roomcheck.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

/**
 * One line of the luach, as printed: a Hebrew date (ICU month numbering, Tishrei = 0) and its time
 * if it has one. The Gregorian date and the weekday are worked out from the Hebrew date rather
 * than typed in beside it, so the two can never disagree.
 */
data class LuachDay(
    val year: Int, val month: Int, val day: Int,
    val title: String,
    val kind: LuachKind,
    val time: LocalTime? = null,
    val note: String? = null,
    /** What the stretch this line opens is called, where its title says how it starts: שבת חופשה. */
    val stretch: String? = null
) {
    val date: LocalDate = Dates.gregorianOf(year, month, day)
    /** When it happens. A line with no time counts from the start of its day. */
    val at: LocalDateTime = date.atTime(time ?: LocalTime.MIDNIGHT)
    val hebrew: String get() = Dates.hebrewDayMonthOf(year, month, day)
}

/** A stretch inside a zman away from seder: a break, OFF to BACK, or a trip, TRIP to HOME. */
data class LuachGap(val from: LuachDay, val to: LuachDay) {
    val isTrip: Boolean get() = from.kind == LuachKind.TRIP
    val name: String get() = from.stretch ?: from.title
}

data class Zman(val id: String, val name: String, val days: List<LuachDay>, val optional: Boolean = false) {
    val start: LuachDay get() = days.first()
    val end: LuachDay get() = days.last()
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

/**
 * The yeshiva's luach for the year - לוח שנת הלימודים תשפ״ו-תשפ״ז, ישיבה גדולה ליובאוויטש
 * סינסינאטי - kept to the lines as printed, with the changes made since written into it.
 */
object Luach {
    const val YEAR = "תשפ״ו–תשפ״ז"

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    // ICU months: Tishrei 0, Cheshvan 1, Kislev 2, Teves 3, Shevat 4, Adar I 5, Adar (II) 6,
    // Nissan 7, Iyar 8, Sivan 9, Tammuz 10, Av 11, Elul 12. תשפ״ז is a leap year, so Adar I is 5.
    val ZMANIM: List<Zman> = listOf(
        Zman("elul", "זמן אלול", listOf(
            LuachDay(5786, 12, 10, "התחלת הזמן", LuachKind.START, t(17, 30)),
            LuachDay(5786, 12, 28, "נסיעה להרבי", LuachKind.EVENT, note = "המשך הסדר אחר ר״ה אצל הרבי"),
            LuachDay(5787, 0, 6, "סיום הזמן", LuachKind.END, t(14, 0))
        )),
        Zman("winter", "זמן חורף", listOf(
            LuachDay(5787, 0, 27, "התחלת הזמן", LuachKind.START, t(17, 30)),
            // the luach printed Monday כ״ז כסלו to Monday ד׳ טבת; it has since moved to Tuesday to Tuesday
            LuachDay(5787, 2, 28, "הפסקה", LuachKind.OFF, t(14, 0), note = "חנוכה"),
            LuachDay(5787, 3, 5, "המשך הזמן", LuachKind.BACK, t(17, 30)),
            LuachDay(5787, 5, 4, "התחלת שבת חופשה", LuachKind.OFF, t(11, 0), stretch = "שבת חופשה"),
            LuachDay(5787, 5, 8, "המשך הזמן", LuachKind.BACK, t(20, 30)),
            LuachDay(5787, 7, 5, "סיום הזמן", LuachKind.END, t(14, 0))
        )),
        // may not happen, so it can be switched off - see summerZman
        Zman("summer", "זמן קיץ", optional = true, days = listOf(
            LuachDay(5787, 7, 28, "התחלת הזמן", LuachKind.START, t(17, 30)),
            LuachDay(5787, 10, 2, "נסיעה לבית חיינו", LuachKind.TRIP, note = "הישיבה יארגן אוטובוס"),
            LuachDay(5787, 10, 7, "חזרה לישיבה", LuachKind.HOME),
            LuachDay(5787, 11, 15, "סיום הזמן", LuachKind.END, t(14, 0))
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
        val mon = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return "$dow, $mon ${d.dayOfMonth}" + if (withYear) ", ${d.year}" else ""
    }

    /** "Tue, Dec 8 · 2:00 pm", the way the luach gives a line's day and time. */
    fun whenText(d: LuachDay): String = dateText(d.date) + (d.time?.let { " · " + timeText(it) } ?: "")

    /** "Oct 8, 2026 – Apr 12, 2027" */
    fun rangeText(from: LocalDate, to: LocalDate): String {
        fun md(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.dayOfMonth
        return if (from.year == to.year) "${md(from)} – ${md(to)}, ${to.year}" else "${md(from)}, ${from.year} – ${md(to)}, ${to.year}"
    }
}
