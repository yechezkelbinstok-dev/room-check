package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Merge
import com.roomcheck.app.data.Night
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.NightStore.Companion.PEOPLE_KEY
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A person's standing settings travel as the shared people record, the same one the site keeps. */
class PeopleSyncTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun settingsAreWrittenIntoThePeopleRecordAndOweAPush() {
        val dir = folder.newFolder()
        val vm = AppViewModel(NightStore(dir))
        vm.toggleAway("p31")
        vm.toggleAlwaysExcused("p11")
        vm.setReason("p11", "Home sick")
        vm.renamePerson("p2", "Yossi", "")

        val store = NightStore(dir)
        val notes = store.load(PEOPLE_KEY).notes
        assertEquals(mapOf("p31.away" to "1", "p11.always" to "1", "p11.reason" to "Home sick", "p2.first" to "Yossi"), notes)
        assertTrue(PEOPLE_KEY in store.dirtyDates())
        assertTrue(store.extra.getValue("p31").away)
        assertEquals("Yossi", store.extra.getValue("p2").first)
        assertEquals(null, store.extra.getValue("p2").last)
    }

    @Test
    fun aRecordFromTheSiteIsReadTheSameWay() {
        // what index.html sends: note cells "pid.field", stamped like any note
        val site = Night.fromJson(JSONObject("""
            {"marks":{},"tonight":[],"notes":{"p31.away":"1","p9.first":"Avi"},"closed":false,"slots":[],"sheet":[],
             "ts":{"n:p31.away":5000,"n:p9.first":5000}}
        """))
        val store = NightStore(folder.newFolder())
        store.save(PEOPLE_KEY, Merge.merge(store.load(PEOPLE_KEY), site))
        store.rebuildExtra()
        assertTrue(store.extra.getValue("p31").away)
        assertEquals("Avi", store.extra.getValue("p9").first)
    }

    @Test
    fun turningOffElsewhereWinsOverAnOlderOnHere() {
        val store = NightStore(folder.newFolder())
        store.setPerson("p31", "away", "1")
        val later = Night().apply { touch(Merge.noteKey("p31.away"), System.currentTimeMillis() + 60_000) }
        store.save(PEOPLE_KEY, Merge.merge(store.load(PEOPLE_KEY), later))
        store.rebuildExtra()
        assertFalse(store.extra["p31"]?.away == true)
    }

    @Test
    fun aDeviceFromBeforeStartsTheRecordFromItsOwnList() {
        val dir = folder.newFolder()
        File(dir, "state.json").writeText("""{"extra":{"p11":{"always":true,"reason":"Home sick"}}}""")
        val store = NightStore(dir)
        val people = store.load(PEOPLE_KEY)
        assertEquals("1", people.notes["p11.always"])
        // as old as a stamp can be, so anything set anywhere since wins over it
        assertEquals(1L, people.stamps[Merge.noteKey("p11.always")])
        assertTrue(PEOPLE_KEY in store.dirtyDates())
        assertTrue(store.extra.getValue("p11").always)
    }

    @Test
    fun thePeopleRecordIsNeverTakenForANight() {
        val store = NightStore(folder.newFolder())
        store.setPerson("p31", "away", "1")
        assertFalse(PEOPLE_KEY in store.allDateKeys())
    }
}
