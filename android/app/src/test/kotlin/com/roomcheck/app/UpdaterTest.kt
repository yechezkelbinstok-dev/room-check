package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Release
import com.roomcheck.app.data.UpdateState
import com.roomcheck.app.data.Updater
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * The app finding and fetching its own update - against a little web server standing in for the
 * site, so the download and its checking are the real code, not a pretend version of it.
 */
class UpdaterTest {

    @get:Rule val folder = TemporaryFolder()

    private lateinit var server: ServerSocket
    private val files = ConcurrentHashMap<String, ByteArray>()
    private val base get() = "http://127.0.0.1:${server.localPort}/dist/"

    /** Just enough of a web server to answer GET /dist/<file>, the query string ignored like the site's. */
    @Before
    fun serve() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (true) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    c.use {
                        val req = c.getInputStream().bufferedReader()
                        val line = req.readLine() ?: return@use
                        while (req.readLine()?.isNotEmpty() == true) Unit   // the headers, not needed
                        val body = files[line.split(" ")[1].substringBefore("?").removePrefix("/dist/")]
                        val out = c.getOutputStream()
                        val head = if (body == null) "404 Not Found" else "200 OK"
                        out.write("HTTP/1.1 $head\r\nContent-Length: ${body?.size ?: 0}\r\nConnection: close\r\n\r\n".toByteArray())
                        body?.let(out::write)
                        out.flush()
                    }
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private fun sha(b: ByteArray) = Updater.hex(MessageDigest.getInstance("SHA-256").digest(b))
    private val apk = ByteArray(300_000) { (it * 7 % 251).toByte() }
    private fun json(code: Long, bytes: ByteArray = apk, name: String = "RoomCheck-1.16.0.apk") =
        """{"version":"1.16.0","code":$code,"apk":"$name","size":${bytes.size},"sha256":"${sha(bytes)}"}"""

    @Test
    fun theVersionFileIsReadAndNothingOddInItIsTrusted() {
        val r = Updater.parse(json(44))!!
        assertEquals(Release("1.16.0", 44, "RoomCheck-1.16.0.apk", 300_000, sha(apk)), r)
        // a path, another site, or a file that is not an APK is never fetched
        assertNull(Updater.parse(json(44, name = "../RoomCheck-1.16.0.apk")))
        assertNull(Updater.parse(json(44, name = "https://example.com/RoomCheck-1.16.0.apk")))
        assertNull(Updater.parse(json(44, name = "RoomCheck-1.16.0.zip")))
        assertNull(Updater.parse(json(44).replace(sha(apk), "1234")))
        assertNull(Updater.parse("""{"version":"1.16.0"}"""))
        assertNull(Updater.parse("<html>not found</html>"))
    }

    @Test
    fun onlyANewerBuildIsOffered() = runBlocking {
        files["latest.json"] = json(44).toByteArray()
        assertEquals(44L, Updater.check(installed = 43, base = base)?.code)
        assertNull(Updater.check(installed = 44, base = base))
        assertNull(Updater.check(installed = 50, base = base))
    }

    @Test
    fun aBrokenVersionFileIsAFailureNotAnUpdate() = runBlocking {
        files["latest.json"] = "nonsense".toByteArray()
        try { Updater.check(installed = 43, base = base); fail() } catch (e: IllegalStateException) { }
        files.remove("latest.json")
        try { Updater.check(installed = 43, base = base); fail() } catch (e: IllegalStateException) { }
    }

    @Test
    fun theDownloadIsExactlyThePublishedFile() = runBlocking {
        val r = Updater.parse(json(44))!!
        files[r.apk] = apk
        val seen = mutableListOf<Float>()
        val got = Updater.download(r, folder.newFolder(), base) { seen += it }
        assertArrayEquals(apk, got.readBytes())
        assertEquals(1f, seen.last())
        assertTrue(seen.zipWithNext().all { (a, b) -> b > a })   // whole percents, only ever forward
    }

    @Test
    fun aDownloadThatIsNotThePublishedFileIsThrownAway() = runBlocking {
        val r = Updater.parse(json(44))!!
        val dir = folder.newFolder()
        files[r.apk] = apk.copyOf().also { it[1000] = (it[1000] + 1).toByte() }   // one byte off
        try { Updater.download(r, dir, base); fail() } catch (e: IllegalStateException) { }
        files[r.apk] = apk.copyOf(200_000)                                          // cut short
        try { Updater.download(r, dir, base); fail() } catch (e: IllegalStateException) { }
        assertFalse(dir.listFiles()!!.any { it.name == r.apk })
    }

    @Test
    fun anUpdateOnOfferShowsUntilItIsTaken() {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        assertEquals(UpdateState.Unknown, vm.state.value.update)
        val r = Updater.parse(json(44))!!
        vm.offerUpdate(r)
        assertEquals(UpdateState.Ready(r), vm.state.value.update)
        // Android turning it down leaves it on offer to try again
        vm.updateFailed("Update cancelled")
        assertEquals(UpdateState.Failed(r, "Update cancelled"), vm.state.value.update)
    }
}
