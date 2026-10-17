package com.roomcheck.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AWAY is never written into a night: it comes from the person's standing "not in town" switch,
 * the way "excused indefinitely" gives EXC, so it reads the same whichever night is open.
 */
enum class Mark { IN, OUT, EXC, AWAY }

/** Off the walk this round - excused or not in town - so the room is judged without him. */
val Mark?.offCheck: Boolean get() = this == Mark.EXC || this == Mark.AWAY

data class Night(
    val marks: MutableMap<String, MutableMap<String, Mark>> = mutableMapOf(),
    val excusedTonight: MutableSet<String> = mutableSetOf(),
    val notes: MutableMap<String, String> = mutableMapOf(),
    var closed: Boolean = false,
    /**
     * The rounds walked on THIS night, when they are not the usual ones. Empty - the normal case -
     * means the standing three.
     *
     * A one-off: the zman moved and there is only one check at 12:30, or there are two instead of
     * three. It belongs to the night the way its marks do, not to the app, so tomorrow is back to
     * the usual three with nothing to put back.
     */
    val rounds: MutableSet<String> = mutableSetOf(),
    /** Which rounds go on this night's picture. Empty means all of them, which is the usual case. */
    val sheetSlots: MutableSet<String> = mutableSetOf(),
    /** When each cell was last set, so two devices' copies can be merged cell by cell. */
    val stamps: MutableMap<String, Long> = mutableMapOf()
) {
    /** Records that a cell just changed. Every edit goes through here or it will not sync. */
    fun touch(key: String, at: Long = System.currentTimeMillis()) { stamps[key] = at }

    fun toJson(): JSONObject {
        val o = JSONObject()
        val marksObj = JSONObject()
        // the night's own slots, not a fixed three: a custom time has to survive being saved
        marks.forEach { (sid, slot) ->
            val obj = JSONObject()
            slot.forEach { (pid, mark) -> obj.put(pid, mark.name.lowercase()) }
            marksObj.put(sid, obj)
        }
        o.put("marks", marksObj)
        o.put("tonight", JSONArray(excusedTonight.toList()))
        val notesObj = JSONObject()
        notes.forEach { (pid, note) -> notesObj.put(pid, note) }
        o.put("notes", notesObj)
        o.put("closed", closed)
        o.put("slots", JSONArray(rounds.toList().sorted()))
        o.put("sheet", JSONArray(sheetSlots.toList().sorted()))
        val stampObj = JSONObject()
        stamps.forEach { (k, v) -> stampObj.put(k, v) }
        o.put("ts", stampObj)
        return o
    }

    companion object {
        /**
         * [fallbackStamp] dates cells in a night saved before timestamps existed - the file's own
         * mtime, which is roughly when those marks were made. Without it they would all read as
         * "never set" and the first sync from any other device would wipe them.
         */
        fun fromJson(json: JSONObject?, fallbackStamp: Long = 0L): Night {
            val n = Night()
            if (json == null) return n
            val marksObj = json.optJSONObject("marks")
            marksObj?.keys()?.forEach { sid ->
                val slot = marksObj.optJSONObject(sid) ?: return@forEach
                val map = mutableMapOf<String, Mark>()
                slot.keys().forEach { pid ->
                    when (slot.optString(pid)) {
                        "in" -> map[pid] = Mark.IN
                        "out" -> map[pid] = Mark.OUT
                        "exc" -> map[pid] = Mark.EXC
                    }
                }
                n.marks[sid] = map
            }
            val tonight = json.optJSONArray("tonight")
            if (tonight != null) for (i in 0 until tonight.length()) n.excusedTonight.add(tonight.getString(i))
            val notesObj = json.optJSONObject("notes")
            notesObj?.keys()?.forEach { pid -> n.notes[pid] = notesObj.optString(pid) }
            n.closed = json.optBoolean("closed", false)
            json.optJSONArray("slots")?.let { for (i in 0 until it.length()) n.rounds.add(it.getString(i)) }
            json.optJSONArray("sheet")?.let { for (i in 0 until it.length()) n.sheetSlots.add(it.getString(i)) }

            val stampObj = json.optJSONObject("ts")
            if (stampObj != null) {
                stampObj.keys().forEach { k -> n.stamps[k] = stampObj.optLong(k) }
            } else if (fallbackStamp > 0L) {
                n.marks.forEach { (sid, slot) -> slot.keys.forEach { n.stamps[Merge.markKey(sid, it)] = fallbackStamp } }
                n.excusedTonight.forEach { n.stamps[Merge.tonightKey(it)] = fallbackStamp }
                n.notes.keys.forEach { n.stamps[Merge.noteKey(it)] = fallbackStamp }
                n.rounds.forEach { n.stamps[Merge.slotKey(it)] = fallbackStamp }
                n.sheetSlots.forEach { n.stamps[Merge.sheetKey(it)] = fallbackStamp }
                if (n.closed) n.stamps[Merge.CLOSED_KEY] = fallbackStamp
            }
            return n
        }
    }
}

data class PersonOverride(var first: String? = null, var last: String? = null, var always: Boolean = false, var reason: String? = null, var away: Boolean = false)

/**
 * The things you can turn on and off, kept off by default: the plan and the sheet read cleaner
 * without them, and anyone who wants them back can say so in Names.
 */
data class Settings(
    /** "top"/"bottom" written on each half of a bunk. */
    val bunkLabels: Boolean = false,
    /** Hebrew names on the floor plan you tap through. */
    val hebrewOnPlan: Boolean = false,
    /** Hebrew names in the sent picture. Separate on purpose: you may want one and not the other. */
    val hebrewInExport: Boolean = false,
    /** Where the shared copy lives. Blank means this device keeps to itself, as it always did. */
    val syncUrl: String = "",
    val syncToken: String = ""
)

private fun JSONObject.optStringOrNull(key: String): String? = if (has(key)) getString(key) else null

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

/**
 * Every night lives in a file under [home] - on the phone that is the app's private storage, which
 * is why the data is the app's own and not the browser's. [home] is a plain directory rather than a
 * Context so the screenshot tests can point a real store at a scratch folder and render the actual
 * screen instead of a stand-in.
 */
/**
 * [builtInSync] fills in this yeshiva's own server and password wherever none has been entered,
 * so a new install or a reinstall syncs from the first open. The app turns it on; tests leave it
 * off, so they never talk to the real server.
 */
class NightStore(home: File, private val builtInSync: Boolean = false) {
    constructor(context: Context) : this(context.filesDir, builtInSync = true)

    private val dir = File(home, "nights").apply { mkdirs() }
    private val stateFile = File(home, "state.json")

    var extra: MutableMap<String, PersonOverride> = mutableMapOf()
        private set

    var settings: Settings = Settings()

    /** How far through the server's changes this device has read. */
    var lastPull: Long = 0L

    // Nights edited here since the last successful push. Kept on disk because the edit may have
    // happened on a bus with no signal and the app may be killed before it ever gets one.
    private val dirty = mutableSetOf<String>()

    fun dirtyDates(): Set<String> = dirty.toSet()
    fun clearDirty(done: Set<String>) { dirty.removeAll(done); saveState() }

    init {
        loadState()
        if (builtInSync && settings.syncUrl.isBlank()) {
            settings = settings.copy(syncUrl = SYNC_URL, syncToken = settings.syncToken.ifBlank { SYNC_TOKEN })
        }
        // A device from before people synced has them only in state.json: start the shared
        // record from that, dated as old as can be so anything set anywhere since wins over it.
        if (!hasData(PEOPLE_KEY) && extra.isNotEmpty()) {
            saveLocal(PEOPLE_KEY, Merge.merge(Night(), peopleNight(extra, 1L)))
        }
        if (hasData(PEOPLE_KEY)) rebuildExtra()
    }

    /**
     * Everyone's standing settings - not in town, excused indefinitely and why, a changed name -
     * ride to the server as one more night, under a date no real night can have. Each is a note
     * cell named "pid.field", so the server and the merge carry them exactly like a night's notes,
     * the later edit of each one winning, with nothing new on the server to deploy.
     */
    companion object {
        const val PEOPLE_KEY = "0000-00-00"

        /** This yeshiva's sync server - the same one written down in server/README.md. */
        const val SYNC_URL = "https://room-check-sync.yechezkelbinstok.workers.dev"
        const val SYNC_TOKEN = "6iciRn6C1NAU8xOGfHS99ZrCO2wtqjyfNQ-_Lr-jJDg"

        fun peopleNight(extra: Map<String, PersonOverride>, stamp: Long): Night {
            val n = Night()
            extra.forEach { (pid, po) ->
                listOf(
                    "first" to po.first, "last" to po.last, "reason" to po.reason,
                    "always" to (if (po.always) "1" else null), "away" to (if (po.away) "1" else null)
                ).forEach { (field, v) ->
                    if (!v.isNullOrEmpty()) { n.notes["$pid.$field"] = v; n.touch(Merge.noteKey("$pid.$field"), stamp) }
                }
            }
            return n
        }

        fun extraFrom(people: Night): MutableMap<String, PersonOverride> {
            val out = mutableMapOf<String, PersonOverride>()
            people.notes.forEach { (key, v) ->
                val pid = key.substringBefore('.', "")
                if (pid.isEmpty()) return@forEach
                val po = out.getOrPut(pid) { PersonOverride() }
                when (key.substringAfter('.')) {
                    "first" -> po.first = v
                    "last" -> po.last = v
                    "reason" -> po.reason = v
                    "always" -> po.always = v == "1"
                    "away" -> po.away = v == "1"
                }
            }
            return out
        }
    }

    /** One person's one setting, changed here: saved, owed to the server, and read back. */
    fun setPerson(pid: String, field: String, value: String?) {
        val n = load(PEOPLE_KEY)
        val key = "$pid.$field"
        if (value.isNullOrEmpty()) n.notes.remove(key) else n.notes[key] = value
        n.touch(Merge.noteKey(key))
        saveLocal(PEOPLE_KEY, n)
        rebuildExtra()
    }

    /** [extra] is only ever a reading of the shared people record, so it follows what arrives. */
    fun rebuildExtra() {
        extra = extraFrom(load(PEOPLE_KEY))
        saveState()
    }

    private fun loadState() {
        extra = mutableMapOf()
        if (stateFile.exists()) {
            runCatching {
                val root = JSONObject(stateFile.readText())
                root.optJSONObject("settings")?.let {
                    settings = Settings(
                        bunkLabels = it.optBoolean("bunkLabels", false),
                        hebrewOnPlan = it.optBoolean("hebrewOnPlan", false),
                        hebrewInExport = it.optBoolean("hebrewInExport", false),
                        syncUrl = it.optString("syncUrl", ""),
                        syncToken = it.optString("syncToken", "")
                    )
                }
                lastPull = root.optLong("lastPull", 0L)
                root.optJSONArray("dirty")?.let { arr ->
                    for (i in 0 until arr.length()) dirty.add(arr.getString(i))
                }
                val extraObj = root.optJSONObject("extra")
                extraObj?.keys()?.forEach { pid ->
                    val po = extraObj.getJSONObject(pid)
                    extra[pid] = PersonOverride(
                        first = po.optStringOrNull("first"),
                        last = po.optStringOrNull("last"),
                        always = po.optBoolean("always", false),
                        away = po.optBoolean("away", false),
                        reason = po.optStringOrNull("reason")
                    )
                }
            }
        }
    }

    fun saveState() {
        val root = JSONObject()
        val extraObj = JSONObject()
        extra.forEach { (pid, po) ->
            val o = JSONObject()
            po.first?.let { o.put("first", it) }
            po.last?.let { o.put("last", it) }
            o.put("always", po.always)
            o.put("away", po.away)
            po.reason?.let { o.put("reason", it) }
            extraObj.put(pid, o)
        }
        root.put("extra", extraObj)
        root.put("settings", JSONObject()
            .put("bunkLabels", settings.bunkLabels)
            .put("hebrewOnPlan", settings.hebrewOnPlan)
            .put("hebrewInExport", settings.hebrewInExport)
            .put("syncUrl", settings.syncUrl)
            .put("syncToken", settings.syncToken))
        root.put("lastPull", lastPull)
        root.put("dirty", JSONArray(dirty.toList()))
        stateFile.writeText(root.toString())
    }

    private fun fileFor(dateKey: String) = File(dir, "$dateKey.json")

    fun load(dateKey: String): Night {
        val f = fileFor(dateKey)
        if (!f.exists()) return Night()
        // a night written before timestamps existed is dated by its file, not by zero
        return runCatching { Night.fromJson(JSONObject(f.readText()), f.lastModified()) }.getOrElse { Night() }
    }

    fun save(dateKey: String, night: Night) {
        fileFor(dateKey).writeText(night.toJson().toString())
    }

    /** Saves a local edit and remembers the night still owes the server a push. */
    fun saveLocal(dateKey: String, night: Night) {
        save(dateKey, night)
        if (dirty.add(dateKey)) saveState()
    }

    fun hasData(dateKey: String): Boolean = fileFor(dateKey).exists()

    fun allDateKeys(): Set<String> = dir.listFiles()?.mapNotNull {
        it.name.takeIf { n -> n.endsWith(".json") }?.removeSuffix(".json")
    }?.filter { it != PEOPLE_KEY }?.toSet() ?: emptySet()

    // ---- backup / restore, same schema as the web app's dl/doimp handlers ----
    fun exportBackup(): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("saved", java.time.Instant.now().toString())
        val extraObj = JSONObject()
        extra.forEach { (pid, po) ->
            val o = JSONObject()
            po.first?.let { o.put("first", it) }
            po.last?.let { o.put("last", it) }
            o.put("always", po.always)
            o.put("away", po.away)
            po.reason?.let { o.put("reason", it) }
            extraObj.put(pid, o)
        }
        root.put("extra", extraObj)
        val nights = JSONObject()
        allDateKeys().forEach { key -> nights.put("dn:$key", load(key).toJson().toString()) }
        root.put("nights", nights)
        return root.toString(1)
    }

    fun importBackup(text: String): Boolean {
        return runCatching {
            val root = JSONObject(text)
            val hasNights = root.has("nights")
            val hasExtra = root.has("extra")
            if (!hasNights && !hasExtra) return false

            extra = mutableMapOf()
            root.optJSONObject("extra")?.let { extraObj ->
                extraObj.keys().forEach { pid ->
                    val po = extraObj.getJSONObject(pid)
                    extra[pid] = PersonOverride(
                        first = po.optStringOrNull("first"),
                        last = po.optStringOrNull("last"),
                        always = po.optBoolean("always", false),
                        away = po.optBoolean("away", false),
                        reason = po.optStringOrNull("reason")
                    )
                }
            }
            saveState()

            root.optJSONObject("nights")?.let { nightsObj ->
                nightsObj.keys().forEach { key ->
                    val dateKey = key.removePrefix("dn:")
                    val nightJsonStr = nightsObj.getString(key)
                    val night = Night.fromJson(JSONObject(nightJsonStr))
                    // a backup from the site carries the people record too: fold it in, never
                    // overwrite, or settings changed since the backup would be thrown away
                    if (dateKey == PEOPLE_KEY) saveLocal(dateKey, Merge.merge(load(dateKey), night))
                    else save(dateKey, night)
                }
            }
            // the backup's own list, as old as can be, so anything set since it was taken wins
            saveLocal(PEOPLE_KEY, Merge.merge(load(PEOPLE_KEY), peopleNight(extra, 1L)))
            rebuildExtra()
            true
        }.getOrElse { false }
    }
}
