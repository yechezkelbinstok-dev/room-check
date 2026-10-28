package com.roomcheck.app.data

import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Seder Sichos: a sicha every Sunday and Wednesday night and a sikkum on each, counted in rounds
 * of four weeks - eight sichos - from the first night. Six of a round's eight is one entry in its
 * raffle for a set of sefarim, all eight is two. Marking one reviewed is only a tick for going over
 * a few - it counts either way.
 *
 * It all lives in the shared people record, beside the yeshiva's other standing settings, as note
 * cells under "sichos." - so it syncs and is in every backup with nothing new on the server, and the
 * site and the iPhone app, which do not show it, carry it along untouched:
 *  - sichos.start          the first day of the first round
 *  - sichos.<pid>.<day>    a sikkum on that night's sicha: "1", or "r" once reviewed
 *  - sichos.win.<day>      whom the raffle of the round starting that day was drawn for
 */
class Sichos(notes: Map<String, String>) {
    companion object {
        const val PREFIX = "sichos."
        /** Sichos in a round: four weeks of two. */
        const val PER_ROUND = 8
        val NIGHTS = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY)
        const val IN = "1"
        const val REVIEWED = "r"
        /** The fields under "sichos." the cells are written as - NightStore.setPerson("sichos", field, ...). */
        const val START = "start"
        fun weekField(pid: String, night: LocalDate) = "$pid.${Dates.keyOf(night)}"
        fun winField(roundStart: LocalDate) = "win.${Dates.keyOf(roundStart)}"

        /** "Sun" or "Wed" - or "Sunday", "Wednesday". */
        fun dayOf(night: LocalDate, long: Boolean = false): String =
            night.dayOfWeek.getDisplayName(if (long) TextStyle.FULL else TextStyle.SHORT, Locale.ENGLISH)

        /** A round's raffle entries for sikumim on [n] of its eight sichos. */
        fun entriesFor(n: Int): Int = when {
            n >= PER_ROUND -> 2
            n >= PER_ROUND - 2 -> 1
            else -> 0
        }

        /** One name out of the hat, in it as many times as he has entries. */
        fun draw(pool: List<Pair<String, Int>>, random: Random = SecureRandom().asKotlinRandom()): String? {
            val total = pool.sumOf { it.second }
            if (total <= 0) return null
            var ticket = random.nextInt(total)
            for ((pid, n) in pool) {
                if (ticket < n) return pid
                ticket -= n
            }
            return null
        }
    }

    val start: LocalDate? = notes[PREFIX + START]?.let { runCatching { Dates.parseKey(it) }.getOrNull() }

    /** How many days after the start, within a week, the two nights fall. */
    private val offsets: List<Int> = start?.let { s -> (0..6).filter { s.plusDays(it.toLong()).dayOfWeek in NIGHTS } }.orEmpty()

    /** pid -> sicha counted from [start] -> the fields holding a sikkum on it. */
    private val cells = mutableMapOf<String, MutableMap<Int, MutableList<String>>>()
    private val values = mutableMapOf<String, String>()
    private val winners = mutableMapOf<LocalDate, String>()

    init {
        for ((key, v) in notes) {
            if (!key.startsWith(PREFIX)) continue
            val field = key.removePrefix(PREFIX)
            val parts = field.split('.')
            if (parts.size != 2) continue
            val day = runCatching { Dates.parseKey(parts[1]) }.getOrNull() ?: continue
            if (parts[0] == "win") { winners[day] = v; continue }
            if (start == null || Roster.byId[parts[0]] == null) continue
            values[field] = v
            cells.getOrPut(parts[0]) { mutableMapOf() }.getOrPut(weekIndex(day)) { mutableListOf() }.add(field)
        }
    }

    // A sikkum belongs to whichever sicha the night it was written for is now - which is where it
    // was written, unless the start has been moved since.
    private fun weekIndex(day: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(requireNotNull(start), day)
        val w = Math.floorDiv(days, 7L).toInt()
        val r = Math.floorMod(days, 7L).toInt()
        return 2 * w + offsets.count { it <= r } - 1
    }

    /** The night of sicha [index], counted from the start. */
    private fun night(index: Int): LocalDate =
        requireNotNull(start).plusDays(7L * Math.floorDiv(index, 2) + offsets[Math.floorMod(index, 2)])

    /**
     * The sicha, counted from the start, whose sikumim are coming in on [today]: the last one
     * learned before today. Its sikumim follow from the next day up to and including the next
     * sicha night - one handed in at that night's seder is on the one before.
     */
    fun collecting(today: LocalDate): Int = maxOf(0, weekIndex(today.minusDays(1)))

    fun roundOf(day: LocalDate): Int = Math.floorDiv(weekIndex(day), PER_ROUND)
    fun weekOf(day: LocalDate): Int = Math.floorMod(weekIndex(day), PER_ROUND)
    fun roundStart(round: Int): LocalDate = night(round * PER_ROUND)
    fun roundEnd(round: Int): LocalDate = roundStart(round + 1).minusDays(1)
    /** The night of the round's sicha [week], 0 to 7. */
    fun weekStart(round: Int, week: Int): LocalDate = night(round * PER_ROUND + week)

    /** The fields holding his sikkum on that sicha - more than one only if the start was moved. */
    fun fieldsAt(pid: String, round: Int, week: Int): List<String> = cells[pid]?.get(round * PER_ROUND + week).orEmpty()

    /** "1", "r" once reviewed, or null when he has not handed one in on that sicha. */
    fun valueAt(pid: String, round: Int, week: Int): String? {
        val here = fieldsAt(pid, round, week).mapNotNull { values[it] }
        return when {
            here.isEmpty() -> null
            REVIEWED in here -> REVIEWED
            else -> IN
        }
    }

    fun count(pid: String, round: Int): Int = (0 until PER_ROUND).count { valueAt(pid, round, it) != null }
    fun entries(pid: String, round: Int): Int = entriesFor(count(pid, round))

    /** How many handed one in on that sicha. */
    fun handedIn(round: Int, week: Int): Int = Roster.PEOPLE.count { valueAt(it.id, round, week) != null }

    /** Everyone in [round]'s raffle, with his entries - but [without] whoever has been set aside. */
    fun pool(round: Int, without: Set<String> = emptySet()): List<Pair<String, Int>> =
        Roster.PEOPLE.map { it.id to entries(it.id, round) }.filter { it.second > 0 && it.first !in without }

    fun winner(round: Int): String? = start?.let { winners[roundStart(round)] }?.takeIf { Roster.byId[it] != null }

    /**
     * The cells to write for the first round to start on [day] instead: every sikkum and every
     * winner moves with its round and week, so a start fixed after the fact leaves each week's
     * sikumim the week they were marked for. Fields to clear are null; one both cleared and
     * written again keeps what is written.
     */
    fun restartedOn(day: LocalDate): Map<String, String?> {
        val out = linkedMapOf<String, String?>(START to Dates.keyOf(day))
        if (start == null || start == day) return out
        val moved = mutableMapOf<String, String>()
        val after = Sichos(mapOf(PREFIX + START to Dates.keyOf(day)))
        cells.forEach { (pid, weeks) ->
            weeks.forEach { (index, fields) ->
                fields.forEach { out[it] = null }
                val value = if (fields.any { values[it] == REVIEWED }) REVIEWED else IN
                moved[weekField(pid, after.night(index))] = value
            }
        }
        winners.forEach { (roundStart, pid) ->
            out[winField(roundStart)] = null
            moved[winField(after.roundStart(roundOf(roundStart)))] = pid
        }
        out.putAll(moved)
        return out
    }
}
