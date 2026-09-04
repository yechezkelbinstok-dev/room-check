package com.roomcheck.app

import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Merge
import com.roomcheck.app.data.Night
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides what happens when the same night was edited in two places. Every one of
 * these is a thing that actually happens on a given night, and whole-night last-write-wins gets
 * most of them wrong.
 */
class MergeTest {

    private fun night(build: Night.() -> Unit) = Night().apply(build)

    private fun Night.mark(sid: String, pid: String, m: Mark, at: Long) {
        marks.getOrPut(sid) { mutableMapOf() }[pid] = m
        touch(Merge.markKey(sid, pid), at)
    }

    private fun Night.unmark(sid: String, pid: String, at: Long) {
        marks[sid]?.remove(pid)
        touch(Merge.markKey(sid, pid), at)
    }

    private fun status(n: Night, sid: String, pid: String) = n.marks[sid]?.get(pid)

    /** Two people walking different rooms at the same time. Both sets of marks must survive. */
    @Test
    fun concurrentEditsToDifferentPeopleBothSurvive() {
        val phone = night { mark("1115", "p1", Mark.IN, 1000); mark("1115", "p2", Mark.OUT, 1000) }
        val web = night { mark("1115", "p20", Mark.IN, 1001); mark("1115", "p21", Mark.OUT, 1001) }
        val m = Merge.merge(phone, web)
        assertEquals(Mark.IN, status(m, "1115", "p1"))
        assertEquals(Mark.OUT, status(m, "1115", "p2"))
        assertEquals(Mark.IN, status(m, "1115", "p20"))
        assertEquals(Mark.OUT, status(m, "1115", "p21"))
    }

    /** Someone corrects a mark on another device. The correction is later, so it wins. */
    @Test
    fun theLaterEditOfTheSameCellWins() {
        val phone = night { mark("1115", "p1", Mark.OUT, 1000) }
        val web = night { mark("1115", "p1", Mark.IN, 2000) }
        assertEquals(Mark.IN, status(Merge.merge(phone, web), "1115", "p1"))
        assertEquals(Mark.IN, status(Merge.merge(web, phone), "1115", "p1"))
    }

    /** Unmarking is an edit too - a later clear has to beat an earlier mark, not be ignored. */
    @Test
    fun aLaterUnmarkBeatsAnEarlierMark() {
        val phone = night { mark("1115", "p1", Mark.OUT, 1000) }
        val web = night { mark("1115", "p1", Mark.OUT, 1000); unmark("1115", "p1", 2000) }
        assertEquals(null, status(Merge.merge(phone, web), "1115", "p1"))
        assertEquals(null, status(Merge.merge(web, phone), "1115", "p1"))
    }

    /** A device that never saw a cell must not delete it just because it has no opinion. */
    @Test
    fun anUnawareDeviceDoesNotDeleteWhatItNeverSaw() {
        val phone = night { mark("1115", "p1", Mark.OUT, 1000) }
        val fresh = Night()
        assertEquals(Mark.OUT, status(Merge.merge(phone, fresh), "1115", "p1"))
        assertEquals(Mark.OUT, status(Merge.merge(fresh, phone), "1115", "p1"))
    }

    /** Merging in either order lands in the same place, or the two devices never converge. */
    @Test
    fun mergeOrderDoesNotMatter() {
        val a = night {
            mark("1115", "p1", Mark.IN, 5); mark("1130", "p3", Mark.OUT, 9)
            notes["p1"] = "late"; touch(Merge.noteKey("p1"), 7)
        }
        val b = night {
            mark("1115", "p1", Mark.OUT, 5)   // same stamp, different value: the tie-break case
            mark("1130", "p3", Mark.IN, 4)
            excusedTonight.add("p9"); touch(Merge.tonightKey("p9"), 6)
            closed = true; touch(Merge.CLOSED_KEY, 8)
        }
        val ab = Merge.merge(a, b)
        val ba = Merge.merge(b, a)
        assertEquals(ab.toJson().toString(), ba.toJson().toString())
        // and the later of each pair is what stuck
        assertEquals(Mark.OUT, status(ab, "1130", "p3"))
        assertTrue(ab.excusedTonight.contains("p9"))
        assertTrue(ab.closed)
        assertEquals("late", ab.notes["p1"])
    }

    /** Merging is stable: a second pass over the same pair changes nothing. */
    @Test
    fun mergingTwiceChangesNothing() {
        val a = night { mark("1115", "p1", Mark.IN, 5); closed = true; touch(Merge.CLOSED_KEY, 6) }
        val b = night { mark("1130", "p2", Mark.OUT, 7) }
        val once = Merge.merge(a, b)
        val twice = Merge.merge(once, b)
        assertEquals(once.toJson().toString(), twice.toJson().toString())
    }

    /** A night saved before timestamps existed is dated by its file, and keeps its marks. */
    @Test
    fun legacyNightsKeepTheirMarks() {
        val legacy = Night.fromJson(
            org.json.JSONObject("""{"marks":{"1115":{"p1":"out"}},"tonight":[],"notes":{},"closed":false}"""),
            fallbackStamp = 500
        )
        assertEquals(500L, legacy.stamps[Merge.markKey("1115", "p1")])
        val fresh = Night()
        assertEquals(Mark.OUT, status(Merge.merge(legacy, fresh), "1115", "p1"))
    }

    /** A custom time is just another slot id, and has to merge like any other. */
    @Test
    fun customTimeSlotsMerge() {
        val phone = night { mark("0130", "p1", Mark.OUT, 1000) }
        val web = night { mark("0130", "p2", Mark.IN, 1000) }
        val m = Merge.merge(phone, web)
        assertEquals(Mark.OUT, status(m, "0130", "p1"))
        assertEquals(Mark.IN, status(m, "0130", "p2"))
    }
}
