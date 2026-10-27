package com.roomcheck.app.data

import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Seder Sichos: a sikkum on each week's sicha, counted in rounds of four weeks from the day the
 * first round started. Three of a round's four weeks is one entry in that round's raffle for a set
 * of sefarim, all four is two. Marking one reviewed is only a tick for going over a few - it counts
 * either way.
 *
 * It all lives in the shared people record, beside the yeshiva's other standing settings, as note
 * cells under "sichos." - so it syncs and is in every backup with nothing new on the server, and the
 * site and the iPhone app, which do not show it, carry it along untouched:
 *  - sichos.start          the first day of the first round
 *  - sichos.<pid>.<day>    a sikkum for the week starting that day: "1", or "r" once reviewed
 *  - sichos.win.<day>      whom the raffle of the round starting that day was drawn for
 */
class Sichos(notes: Map<String, String>) {
    companion object {
        const val PREFIX = "sichos."
        const val WEEKS = 4
        const val IN = "1"
        const val REVIEWED = "r"
        /** The fields under "sichos." the cells are written as - NightStore.setPerson("sichos", field, ...). */
        const val START = "start"
        fun weekField(pid: String, weekStart: LocalDate) = "$pid.${Dates.keyOf(weekStart)}"
        fun winField(roundStart: LocalDate) = "win.${Dates.keyOf(roundStart)}"

        /** A round's raffle entries for sikumim in [weeks] of its four weeks. */
        fun entriesFor(weeks: Int): Int = when {
            weeks >= WEEKS -> 2
            weeks >= WEEKS - 1 -> 1
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

    /** pid -> week counted from [start] -> the fields holding a sikkum for it. */
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

    // A week belongs to whichever round and week the day it was written for falls in now - which is
    // where it was written, unless the start has been moved since.
    private fun weekIndex(day: LocalDate): Int =
        Math.floorDiv(ChronoUnit.DAYS.between(requireNotNull(start), day), 7L).toInt()

    fun roundOf(day: LocalDate): Int = Math.floorDiv(weekIndex(day), WEEKS)
    fun weekOf(day: LocalDate): Int = Math.floorMod(weekIndex(day), WEEKS)
    fun roundStart(round: Int): LocalDate = requireNotNull(start).plusDays(7L * WEEKS * round)
    fun roundEnd(round: Int): LocalDate = roundStart(round + 1).minusDays(1)
    fun weekStart(round: Int, week: Int): LocalDate = roundStart(round).plusDays(7L * week)

    /** The fields holding his sikkum for that week - more than one only if the start was moved. */
    fun fieldsAt(pid: String, round: Int, week: Int): List<String> = cells[pid]?.get(round * WEEKS + week).orEmpty()

    /** "1", "r" once reviewed, or null when he has not handed one in for that week. */
    fun valueAt(pid: String, round: Int, week: Int): String? {
        val here = fieldsAt(pid, round, week).mapNotNull { values[it] }
        return when {
            here.isEmpty() -> null
            REVIEWED in here -> REVIEWED
            else -> IN
        }
    }

    fun count(pid: String, round: Int): Int = (0 until WEEKS).count { valueAt(pid, round, it) != null }
    fun entries(pid: String, round: Int): Int = entriesFor(count(pid, round))

    /** How many handed one in for that week. */
    fun handedIn(round: Int, week: Int): Int = Roster.PEOPLE.count { valueAt(it.id, round, week) != null }

    /** Everyone in [round]'s raffle, with his entries - but [without] whoever has been set aside. */
    fun pool(round: Int, without: Set<String> = emptySet()): List<Pair<String, Int>> =
        Roster.PEOPLE.map { it.id to entries(it.id, round) }.filter { it.second > 0 && it.first !in without }

    fun winner(round: Int): String? = start?.let { winners[roundStart(round)] }?.takeIf { Roster.byId[it] != null }
}
