package com.roomcheck.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Mark { IN, OUT, EXC }

data class Night(
    val marks: MutableMap<String, MutableMap<String, Mark>> = Roster.SIDS.associateWith { mutableMapOf<String, Mark>() }.toMutableMap(),
    val excusedTonight: MutableSet<String> = mutableSetOf(),
    val notes: MutableMap<String, String> = mutableMapOf(),
    var closed: Boolean = false
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        val marksObj = JSONObject()
        Roster.SIDS.forEach { sid ->
            val slot = JSONObject()
            marks[sid]?.forEach { (pid, mark) -> slot.put(pid, mark.name.lowercase()) }
            marksObj.put(sid, slot)
        }
        o.put("marks", marksObj)
        o.put("tonight", JSONArray(excusedTonight.toList()))
        val notesObj = JSONObject()
        notes.forEach { (pid, note) -> notesObj.put(pid, note) }
        o.put("notes", notesObj)
        o.put("closed", closed)
        return o
    }

    companion object {
        fun fromJson(json: JSONObject?): Night {
            val n = Night()
            if (json == null) return n
            val marksObj = json.optJSONObject("marks")
            Roster.SIDS.forEach { sid ->
                val slot = marksObj?.optJSONObject(sid)
                val map = mutableMapOf<String, Mark>()
                slot?.keys()?.forEach { pid ->
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
    val hebrewInExport: Boolean = false
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
                        hebrewInExport = it.optBoolean("hebrewInExport", false)
                    )
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
            .put("hebrewInExport", settings.hebrewInExport))
        stateFile.writeText(root.toString())
    }

    private fun fileFor(dateKey: String) = File(dir, "$dateKey.json")

    fun load(dateKey: String): Night {
        val f = fileFor(dateKey)
        if (!f.exists()) return Night()
        return runCatching { Night.fromJson(JSONObject(f.readText())) }.getOrElse { Night() }
    }

    fun save(dateKey: String, night: Night) {
        fileFor(dateKey).writeText(night.toJson().toString())
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
