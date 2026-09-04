package com.roomcheck.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** What the last sync did, for the one line about it in Settings. */
sealed interface SyncState {
    data object Off : SyncState
    data object Working : SyncState
    data class Ok(val at: Long, val changed: Int) : SyncState
    data class Failed(val reason: String) : SyncState
}

/**
 * Keeps this device's nights and the server's in step.
 *
 * Offline-first on purpose: the phone's own files stay the truth and every mark is saved and
 * usable with no signal at all. Sync is a background reconciliation on top of that, never a
 * gate in front of it - a bed check happens at 11pm in a building with bad reception, and it
 * cannot wait on a network round trip.
 *
 * Nothing is ever overwritten wholesale. Local edits are pushed, the server merges them into
 * whatever else arrived, and what comes back is merged in again cell by cell, so two people
 * marking different rooms at once keep both sets.
 */
class SyncClient(private val store: NightStore) {

    private fun post(base: String, path: String, token: String, body: JSONObject): JSONObject {
        val url = URL(base.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        conn.disconnect()
        if (code == 401) throw IllegalStateException("Wrong sync password")
        if (code !in 200..299) throw IllegalStateException("Server said $code")
        return JSONObject(text.ifBlank { "{}" })
    }

    /**
     * One full exchange: push what changed here, then take everything that changed elsewhere.
     * Returns how many nights ended up different locally.
     */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val cfg = store.settings
        val base = cfg.syncUrl.trim()
        check(base.isNotBlank()) { "No sync address set" }

        var changed = 0

        // push first, so a night edited here is on the server before we ask what we are missing
        val dirty = store.dirtyDates()
        if (dirty.isNotEmpty()) {
            val nights = JSONObject()
            dirty.forEach { nights.put(it, store.load(it).toJson()) }
            val res = post(base, "/push", cfg.syncToken, JSONObject().put("nights", nights))
            val back = res.optJSONObject("nights")
            back?.keys()?.forEach { date ->
                val merged = Night.fromJson(back.getJSONObject(date))
                if (adopt(date, merged)) changed++
            }
            store.clearDirty(dirty)
        }

        val since = store.lastPull
        val res = post(base, "/pull", cfg.syncToken, JSONObject().put("since", since))
        val nights = res.optJSONObject("nights")
        nights?.keys()?.forEach { date ->
            val remote = Night.fromJson(nights.getJSONObject(date))
            if (adopt(date, remote)) changed++
        }
        store.lastPull = res.optLong("now", since)
        store.saveState()
        changed
    }

    /** Merge a night from the server into the local copy. True when the local copy actually moved. */
    private fun adopt(date: String, remote: Night): Boolean {
        val local = store.load(date)
        val merged = Merge.merge(local, remote)
        val before = local.toJson().toString()
        val after = merged.toJson().toString()
        if (before == after) return false
        store.save(date, merged)
        return true
    }
}
