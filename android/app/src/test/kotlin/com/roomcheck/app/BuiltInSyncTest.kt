package com.roomcheck.app

import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The yeshiva's own server comes filled in, and the room buttons stand floor by floor. */
class BuiltInSyncTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun aNewInstallComesWithTheServerFilledIn() {
        val store = NightStore(folder.newFolder(), builtInSync = true)
        assertEquals(NightStore.SYNC_URL, store.settings.syncUrl)
        assertEquals(NightStore.SYNC_TOKEN, store.settings.syncToken)
    }

    @Test
    fun aServerSomeoneTypedInIsKept() {
        val dir = folder.newFolder()
        NightStore(dir).apply { settings = settings.copy(syncUrl = "https://elsewhere.example", syncToken = "x"); saveState() }
        val store = NightStore(dir, builtInSync = true)
        assertEquals("https://elsewhere.example", store.settings.syncUrl)
        assertEquals("x", store.settings.syncToken)
    }

    @Test
    fun testsNeverTalkToTheRealServer() {
        assertTrue(NightStore(folder.newFolder()).settings.syncUrl.isBlank())
    }

    @Test
    fun topFloorRowAboveRoomsOneToThree() {
        assertEquals(listOf(listOf(3, 4, 5, 6, 7), listOf(0, 1, 2)), Roster.FLOORS)
    }
}
