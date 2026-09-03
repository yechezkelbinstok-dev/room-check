package com.roomcheck.app.data

data class SlotStats(val out: Int, val inCount: Int, val exc: Int, val left: Int, val started: Boolean)

class NightLogic(private val night: Night, private val extra: Map<String, PersonOverride>) {

    fun isAlways(pid: String): Boolean = extra[pid]?.always == true
    fun first(pid: String): String = extra[pid]?.first ?: Roster.byId.getValue(pid).first
    fun last(pid: String): String = extra[pid]?.last ?: Roster.byId.getValue(pid).last
    fun nameOf(pid: String): String = "${first(pid)} ${last(pid)}"

    fun statusOf(pid: String, sid: String): Mark? {
        if (isAlways(pid)) return Mark.EXC
        if (night.excusedTonight.contains(pid)) return Mark.EXC
        return night.marks[sid]?.get(pid)
    }

    private fun livePids(room: Room, sid: String): List<String> =
        room.beds.flatMap { it.slots }.filter { statusOf(it, sid) != Mark.EXC }

    fun roomChecked(room: Room, sid: String): Boolean {
        val live = livePids(room, sid)
        if (live.isEmpty()) return true
        return live.any { night.marks[sid]?.containsKey(it) == true }
    }

    fun roomAllIn(room: Room, sid: String): Boolean {
        var any = false
        for (bed in room.beds) for (pid in bed.slots) {
            val st = statusOf(pid, sid)
            if (st == Mark.EXC) continue
            if (st != Mark.IN) return false
            any = true
        }
        return any || roomChecked(room, sid)
    }

    fun outIn(room: Room, sid: String): List<String> =
        room.beds.flatMap { it.slots }.filter { statusOf(it, sid) == Mark.OUT }

    fun stats(sid: String): SlotStats {
        var out = 0; var inC = 0; var exc = 0
        Roster.PEOPLE.forEach { p ->
            when (statusOf(p.id, sid)) {
                Mark.OUT -> out++
                Mark.IN -> inC++
                Mark.EXC -> exc++
                null -> {}
            }
        }
        val left = Roster.PLAN.count { !roomChecked(it, sid) }
        return SlotStats(out, inC, exc, left, left < Roster.PLAN.size)
    }

    /** Everyone marked out at [sid], in room order then bed order - the order you walked them. */
    fun missingAt(sid: String): List<String> =
        Roster.PLAN.flatMap { room -> room.beds.flatMap { it.slots } }
            .filter { statusOf(it, sid) == Mark.OUT }

    fun uncheckedAt(sid: String): List<Room> = Roster.PLAN.filter { !roomChecked(it, sid) }

    /**
     * The night as plain text to paste into a message: the Hebrew date, then each time with just
     * the names of whoever wasn't there. Nothing else - no room labels, no counts, no totals.
     */
    fun report(dateKey: String): String {
        val lines = mutableListOf(Dates.hebrewDayMonth(dateKey))
        Roster.SIDS.forEachIndexed { i, sid ->
            lines.add("")
            lines.add(Roster.SLOTS[i].second)
            val missing = missingAt(sid)
            lines.add(
                when {
                    missing.isNotEmpty() -> missing.joinToString(", ") { nameOf(it) }
                    !stats(sid).started -> "Not checked yet"
                    else -> "Everybody there"
                }
            )
            val unchecked = uncheckedAt(sid)
            if (stats(sid).started && unchecked.isNotEmpty()) {
                lines.add("(still to check: ${unchecked.joinToString(", ") { it.label }})")
            }
        }
        return lines.joinToString("\n")
    }
}
