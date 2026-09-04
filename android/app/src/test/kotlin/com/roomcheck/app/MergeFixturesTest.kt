package com.roomcheck.app

import com.roomcheck.app.data.Merge
import com.roomcheck.app.data.Night
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The same cases the website's merge is run against, run here. The rule exists twice - once in
 * Kotlin, once in JavaScript for the site and the sync server - and two copies that quietly
 * disagree would lose marks rather than fail loudly. This is what keeps them honest.
 */
class MergeFixturesTest {

    private fun canonical(o: JSONObject): String {
        fun norm(v: Any?): Any? = when (v) {
            is JSONObject -> v.keys().asSequence().sorted().associateWith { norm(v.get(it)) }
            is org.json.JSONArray -> (0 until v.length()).map { v.get(it).toString() }.sorted()
            else -> v
        }
        return norm(o).toString()
    }

    @Test
    fun kotlinAgreesWithTheSharedFixtures() {
        val text = javaClass.classLoader!!.getResourceAsStream("fixtures.json")!!
            .bufferedReader().readText()
        val cases = JSONObject(text).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val local = Night.fromJson(c.getJSONObject("local"))
            val remote = Night.fromJson(c.getJSONObject("remote"))
            val want = canonical(c.getJSONObject("expect"))
            assertEquals(c.getString("name") + " (local,remote)", want, canonical(Merge.merge(local, remote).toJson()))
            assertEquals(c.getString("name") + " (remote,local)", want, canonical(Merge.merge(remote, local).toJson()))
        }
    }
}
