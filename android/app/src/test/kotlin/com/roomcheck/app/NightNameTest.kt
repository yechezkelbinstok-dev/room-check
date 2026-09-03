package com.roomcheck.app

import com.roomcheck.app.data.Dates
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A night is named for the day it leads into, matching the Hebrew date shown beside it - both roll
 * at nightfall, so naming the night after the day that just ended would read the two by different
 * clocks. Friday and Saturday nights have their own names and are the ones worth pinning down.
 */
class NightNameTest {

    @Test
    fun everyNightOfTheWeek() {
        val expected = mapOf(
            "2026-09-01" to "ליל רביעי",   // Tuesday evening
            "2026-09-02" to "ליל חמישי",   // Wednesday evening
            "2026-09-03" to "ליל שישי",    // Thursday evening
            "2026-09-04" to "ליל שבת",     // Friday evening
            "2026-09-05" to "מוצאי שבת",   // Saturday evening
            "2026-09-06" to "ליל שני",     // Sunday evening
            "2026-09-07" to "ליל שלישי"    // Monday evening
        )
        expected.forEach { (key, name) -> assertEquals(key, name, Dates.hebrewNightName(key)) }
    }
}
