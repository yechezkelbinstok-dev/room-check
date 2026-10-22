package com.roomcheck.app.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.roomcheck.app.UpdateReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A build on the site: which one it is, and enough to know a download is that file and all of it. */
data class Release(val version: String, val code: Long, val apk: String, val size: Long, val sha256: String)

/** Where updating stands, for the bar on the marking screen and the line in Settings. */
sealed interface UpdateState {
    /** Not looked yet this time the app is open. */
    data object Unknown : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Ready(val release: Release) : UpdateState
    data class Downloading(val release: Release, val fraction: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    /** [release] is null when it was the looking that failed, not an update. */
    data class Failed(val release: Release?, val reason: String) : UpdateState
}

/**
 * The app updating itself from the site, so a new build is one tap inside the app rather than a
 * download and Android's string of install-from-an-unknown-source screens.
 *
 * The site serves dist/latest.json beside the APK - the publishApk Gradle task writes it - and
 * the app reads it. When the build there is newer it fetches the APK, checks it is byte for byte
 * the file the JSON describes, and hands it to Android's PackageInstaller. From Android 12 an app
 * updating itself, holding UPDATE_PACKAGES_WITHOUT_USER_ACTION, goes in with no screen at all; on
 * an older phone, or whenever Android wants one anyway, its one confirm screen comes up instead.
 * Either way Android only accepts an APK signed with the same key as the one installed.
 */
object Updater {
    const val SITE = "https://yechezkelbinstok-dev.github.io/room-check/"
    const val DIST = SITE + "dist/"

    /**
     * How an install that Android turned down ended, handed from UpdateReceiver to the screen. A
     * successful one never gets here - the app is closed and replaced by the new version.
     */
    val failures = MutableStateFlow<String?>(null)

    private val APK_NAME = Regex("""RoomCheck-[0-9A-Za-z.\-]+\.apk""")
    private val SHA256 = Regex("[0-9a-f]{64}")

    /** The JSON as published, or null when it is not - a bad file means no update, never a crash. */
    fun parse(json: String): Release? = runCatching {
        val o = JSONObject(json)
        // a bare file name in dist/ and nothing more: never a path, never another site
        val apk = o.getString("apk").also { require(APK_NAME.matches(it)) }
        val sha = o.getString("sha256").lowercase().also { require(SHA256.matches(it)) }
        Release(o.getString("version"), o.getLong("code"), apk, o.getLong("size").also { require(it > 0) }, sha)
    }.getOrNull()

    /** The build on the site, if it is newer than the one [installed]; null when it is not. */
    suspend fun check(installed: Long, base: String = DIST): Release? = withContext(Dispatchers.IO) {
        // the query string steps past the site's cache, so a build pushed a minute ago is seen
        val conn = URL(base + "latest.json?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            check(conn.responseCode == 200) { "The site said ${conn.responseCode}" }
            val r = parse(conn.inputStream.bufferedReader().use { it.readText() })
            checkNotNull(r) { "The site's version file is not right" }
            r.takeIf { it.code > installed }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Fetches the APK into [dir], saying how far along it is in whole percents. Throws when what
     * arrives is not exactly the file described, so a cut-off or altered download is never offered
     * to Android at all.
     */
    suspend fun download(r: Release, dir: File, base: String = DIST, progress: (Float) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }   // whatever an earlier try left behind
            val file = File(dir, r.apk)
            val conn = URL(base + r.apk).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            try {
                check(conn.responseCode == 200) { "The site said ${conn.responseCode}" }
                val digest = MessageDigest.getInstance("SHA-256")
                var got = 0L
                var shown = -1
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            got += n
                            val pct = (got * 100 / r.size).toInt().coerceIn(0, 100)
                            if (pct != shown) { shown = pct; progress(pct / 100f) }
                        }
                    }
                }
                if (got != r.size || hex(digest.digest()) != r.sha256) {
                    file.delete()
                    error("The download came out wrong - try again")
                }
                file
            } finally {
                conn.disconnect()
            }
        }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Hands the APK to Android to install over this app. Blocking - it copies the file into the
     * install session - so it is called off the main thread.
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // mutable, because Android writes the outcome into it on the way back
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val done = PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java), flags)
            session.commit(done.intentSender)
        }
    }
}
