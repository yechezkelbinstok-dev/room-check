package com.roomcheck.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Mark { IN, OUT, EXC }

data class Night(
    val marks: MutableMap<String, MutableMap<String, Mark>> = mutableMapOf(),
    val excusedTonight: MutableSet<String> = mutableSetOf(),
    val notes: MutableMap<String, String> = mutableMapOf(),
    var closed: Boolean = false,
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

            val stampObj = json.optJSONObject("ts")
            if (stampObj != null) {
                stampObj.keys().forEach { k -> n.stamps[k] = stampObj.optLong(k) }
            } else if (fallbackStamp > 0L) {
                n.marks.forEach { (sid, slot) -> slot.keys.forEach { n.stamps[Merge.markKey(sid, it)] = fallbackStamp } }
                n.excusedTonight.forEach { n.stamps[Merge.tonightKey(it)] = fallbackStamp }
                n.notes.keys.forEach { n.stamps[Merge.noteKey(it)] = fallbackStamp }
                if (n.closed) n.stamps[Merge.CLOSED_KEY] = fallbackStamp
            }
            return n
        }
    }
}

data class PersonOverride(var first: String? = null, var last: String? = null, var always: Boolean = false, var reason: String? = null)

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

/**
 * Every night lives in a file under [home] - on the phone that is the app's private storage, which
 * is why the data is the app's own and not the browser's. [home] is a plain directory rather than a
 * Context so the screenshot tests can point a real store at a scratch folder and render the actual
 * screen instead of a stand-in.
 */
class NightStore(home: File) {
    constructor(context: Context) : this(context.filesDir)

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

    init { loadState() }

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
    }?.toSet() ?: emptySet()

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
                    save(dateKey, night)
                }
            }
            true
        }.getOrElse { false }
    }
}
