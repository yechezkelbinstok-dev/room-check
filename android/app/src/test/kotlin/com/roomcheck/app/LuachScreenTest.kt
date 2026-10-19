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
 * The yeshiva calendar at a few moments of the year. The list is longer than a screen, so each
 * moment is drawn from the top and again scrolled down to the winter and summer zmanim.
 */
class LuachScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @get:Rule
    val folder = TemporaryFolder()

    private fun shot(name: String, now: String, summer: Boolean = true) {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        if (!summer) vm.setSummerZman(false)
        // the list's items: now card, gap, Elul, bein hazmanim, winter, bein hazmanim, summer, ...
        listOf("top" to 0, "winter" to 4, "summer" to 6).forEach { (part, item) ->
            paparazzi.snapshot(name = "$name-$part") { RoomCheckTheme { LuachScreen(vm, LocalDateTime.parse(now), firstItem = item) } }
        }
    }

    /** An ordinary night in the winter zman, the Chanukah break the next thing up. */
    @Test
    fun winterZman() = shot("luach-winter", "2026-10-19T19:00")

    @Test
    fun chanukahBreak() = shot("luach-chanukah", "2026-12-10T12:00")

    @Test
    fun beinHazmanim() = shot("luach-bein-hazmanim", "2026-09-25T12:00")

    @Test
    fun summerZmanSwitchedOff() = shot("luach-no-summer", "2027-04-20T12:00", summer = false)
}
