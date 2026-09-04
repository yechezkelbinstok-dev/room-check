package com.roomcheck.app.data

/** One time a night gets walked. [id] is HHMM as the clock reads it, and is what marks are filed under. */
data class Slot(val id: String, val label: String)

/**
 * The times a night can be marked at.
 *
 * Three of them stand every night; anything else you add is yours.
 *
 * Ids are the time as a clock face reads it, not a 24-hour one: "1115" is quarter past eleven at
 * night and "1200" is midnight. That is how the three original ids were written and every night
 * ever marked is filed under them, so the scheme stays. It also means they cannot be sorted as
 * numbers - 12 comes after 11 but before 1, because 1 o'clock is the far side of midnight - and a
 * 1:30 round sorted naively would land at the head of the sheet and be marked into as if it were
 * the first round of the evening.
 *
 * A bed check runs from evening to the small hours, so the hour alone says which side of midnight
 * a time is on and no am/pm is needed: 6 through 12 is evening, 1 through 5 is after it.
 */
object Slots {

    val DEFAULT = listOf(
        Slot("1115", "11:15"),
        Slot("1130", "11:30"),
        Slot("1200", "12:00")
    )

    private fun hourOf(id: String) = id.take(2).toIntOrNull() ?: 0
    private fun minuteOf(id: String) = id.drop(2).toIntOrNull() ?: 0

    /** Hours laid end to end across one night: 6pm…11pm, midnight, then 1am…5am. */
    private fun nightHour(h: Int) = when {
        h == 12 -> 12      // midnight, between 11 and 1
        h < 6 -> h + 12    // the small hours come after it
        else -> h          // the evening comes before it
    }

    /** Minutes into the night, so the list is in the order the rounds are actually walked. */
    fun order(id: String): Int = nightHour(hourOf(id)) * 60 + minuteOf(id)

    /** "0130" -> "1:30". */
    fun labelFor(id: String): String = "${hourOf(id)}:" + minuteOf(id).toString().padStart(2, '0')

    fun isValidId(id: String): Boolean =
        Regex("^\\d{4}$").matches(id) && hourOf(id) in 1..12 && minuteOf(id) in 0..59

    /** The id for a time on the wall clock right now, in the same shape as the stored ones. */
    fun idForClock(hour24: Int, minute: Int): String {
        val h = if (hour24 % 12 == 0) 12 else hour24 % 12
        return "%02d%02d".format(h, minute)
    }

    /** Every time this night can be marked at. */
    fun all(s: Settings): List<Slot> =
        (DEFAULT + s.customSlots.filter(::isValidId).map { Slot(it, labelFor(it)) })
            .distinctBy { it.id }
            .sortedBy { order(it.id) }

    /**
     * The times that go on the sent picture. All of them unless you narrowed it - and if the
     * narrowing ends up naming nothing that still exists, all of them again, so deleting a custom
     * time cannot leave you with a blank sheet.
     */
    fun forSheet(s: Settings): List<Slot> {
        val every = all(s)
        if (s.sheetSlots.isEmpty()) return every
        return every.filter { it.id in s.sheetSlots }.ifEmpty { every }
    }
}
