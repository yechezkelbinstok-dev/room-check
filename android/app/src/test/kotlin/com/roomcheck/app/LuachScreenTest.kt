package com.roomcheck.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.ui.LuachScreen
import com.roomcheck.app.ui.RoomCheckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

/**
 * The yeshiva calendar at a few moments of the year, as a month and as the year's list, in Hebrew
 * and in English. Each is longer than a screen, so it is drawn more than once, scrolled down.
 */
class LuachScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @get:Rule
    val folder = TemporaryFolder()

    // the month view's items: now card, gap, month, gap, the day tapped, gap, the month's lines, ...
    private val monthParts = listOf("top" to 0, "month" to 2, "lines" to 4)
    // the list's items: now card, gap, Elul, bein hazmanim, winter, bein hazmanim, summer, ...
    private val listParts = listOf("top" to 0, "winter" to 4, "summer" to 6)

    private fun shot(name: String, now: String, parts: List<Pair<String, Int>>, english: Boolean = false, list: Boolean = false, summer: Boolean = true) {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        vm.setSettings { it.copy(luachEnglish = english, luachList = list) }
        if (!summer) vm.setSummerZman(false)
        parts.forEach { (part, item) ->
            paparazzi.snapshot(name = "$name-$part") { RoomCheckTheme { LuachScreen(vm, LocalDateTime.parse(now), firstItem = item) } }
        }
    }

    /** An ordinary night in the winter zman, the Chanukah break the next thing up. */
    @Test
    fun monthHebrew() = shot("luach-month", "2026-10-19T19:00", monthParts)

    @Test
    fun monthEnglish() = shot("luach-month-en", "2026-10-19T19:00", monthParts, english = true)

    /** Kislev in the middle of the Chanukah break: its days tinted as the break. */
    @Test
    fun monthDuringChanukah() = shot("luach-month-chanukah", "2026-12-10T12:00", monthParts.take(2))

    @Test
    fun listHebrew() = shot("luach-list", "2026-10-19T19:00", listParts, list = true)

    @Test
    fun listEnglish() = shot("luach-list-en", "2026-10-19T19:00", listParts, english = true, list = true)

    @Test
    fun beinHazmanim() = shot("luach-bein-hazmanim", "2026-09-25T12:00", listParts.take(1), list = true)

    @Test
    fun summerZmanSwitchedOff() = shot("luach-no-summer", "2027-04-20T12:00", listParts, list = true, summer = false)
}
