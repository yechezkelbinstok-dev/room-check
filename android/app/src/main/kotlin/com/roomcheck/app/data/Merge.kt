package com.roomcheck.app.data

/**
 * How two copies of the same night are reconciled.
 *
 * Whole-night last-write-wins would be wrong here: two people walking different rooms at the same
 * time would each save a full night, and whoever saved second would erase the other's rooms. So a
 * night carries a timestamp per CELL - one mark, one note, one excusal - and merging is decided
 * cell by cell. Marking Room 1 on the phone and Room 5 on the website at the same moment leaves
 * both, because they are different cells and neither is newer than the other.
 *
 * Cells are addressed by a short key so the same rule can be written identically in the website's
 * JavaScript, and the two implementations cannot drift into disagreeing about who wins.
 */
object Merge {

    fun markKey(sid: String, pid: String) = "m:$sid:$pid"
    fun tonightKey(pid: String) = "t:$pid"
    fun noteKey(pid: String) = "n:$pid"
    const val CLOSED_KEY = "c"

    /** Every cell either side knows anything about. */
    private fun keysOf(n: Night): Set<String> = buildSet {
        n.marks.forEach { (sid, slot) -> slot.keys.forEach { add(markKey(sid, it)) } }
        n.excusedTonight.forEach { add(tonightKey(it)) }
        n.notes.keys.forEach { add(noteKey(it)) }
        add(CLOSED_KEY)
        addAll(n.stamps.keys)
    }

    /** The cell's value as a string, or null when the cell is empty - unmarking is a value too. */
    fun valueOf(n: Night, key: String): String? = when {
        key == CLOSED_KEY -> if (n.closed) "1" else null
        key.startsWith("m:") -> key.split(":", limit = 3).let { (_, sid, pid) ->
            n.marks[sid]?.get(pid)?.name?.lowercase()
        }
        key.startsWith("t:") -> if (n.excusedTonight.contains(key.substring(2))) "1" else null
        key.startsWith("n:") -> n.notes[key.substring(2)]
        else -> null
    }

    fun setValue(n: Night, key: String, value: String?) {
        when {
            key == CLOSED_KEY -> n.closed = value != null
            key.startsWith("m:") -> key.split(":", limit = 3).let { (_, sid, pid) ->
                val slot = n.marks.getOrPut(sid) { mutableMapOf() }
                val mark = when (value) {
                    "in" -> Mark.IN; "out" -> Mark.OUT; "exc" -> Mark.EXC; else -> null
                }
                if (mark == null) slot.remove(pid) else slot[pid] = mark
            }
            key.startsWith("t:") -> {
                val pid = key.substring(2)
                if (value != null) n.excusedTonight.add(pid) else n.excusedTonight.remove(pid)
            }
            key.startsWith("n:") -> {
                val pid = key.substring(2)
                if (value.isNullOrBlank()) n.notes.remove(pid) else n.notes[pid] = value
            }
        }
    }

    /**
     * The later edit of each cell wins. Ties fall back to comparing the values themselves, so two
     * devices merging the same pair in either order always land on the same answer rather than on
     * whichever one happened to run second.
     */
    fun merge(local: Night, remote: Night): Night {
        val out = Night()
        // sorted, so two devices at the same logical state also write byte-identical files -
        // otherwise "did this change?" can only ever be answered by a field-by-field walk
        (keysOf(local) + keysOf(remote)).sorted().forEach { key ->
            val lt = local.stamps[key] ?: 0L
            val rt = remote.stamps[key] ?: 0L
            val lv = valueOf(local, key)
            val rv = valueOf(remote, key)
            val takeRemote = when {
                rt != lt -> rt > lt
                else -> (rv ?: "") > (lv ?: "")
            }
            val value = if (takeRemote) rv else lv
            setValue(out, key, value)
            val stamp = maxOf(lt, rt)
            if (stamp > 0L || value != null) out.stamps[key] = stamp
        }
        // a slot whose last mark was cleared leaves an empty map behind; drop it so an empty
        // night is empty in both implementations rather than only in one of them
        out.marks.keys.filter { out.marks[it]?.isEmpty() == true }.forEach { out.marks.remove(it) }
        return out
    }
}
