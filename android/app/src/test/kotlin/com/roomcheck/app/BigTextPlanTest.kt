package com.roomcheck.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import com.roomcheck.app.ui.CheckScreen
import com.roomcheck.app.ui.RoomCheckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every room's plan in Hebrew, and every room at a phone's large text size in both languages.
 * Hebrew letters stand taller than Latin ones and large text makes everything taller again, so
 * this is where a name pushed out of half a bunk, or broken onto a second line, shows up first.
 */
class BigTextPlanTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @get:Rule
    val folder = TemporaryFolder()

    private fun everyRoom(tag: String, hebrew: Boolean) {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        vm.setSettings { it.copy(hebrewOnPlan = hebrew) }
        Roster.PLAN.indices.forEach { i ->
            vm.jumpRoom(i)
            paparazzi.snapshot(name = "$tag-room-${i + 1}") { RoomCheckTheme { CheckScreen(vm) } }
        }
    }

    private fun largeText() =
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f))

    @Test
    fun hebrewEveryRoom() = everyRoom("hebrew", hebrew = true)

    @Test
    fun hebrewLargeText() {
        largeText()
        everyRoom("hebrew-large", hebrew = true)
    }

    @Test
    fun englishLargeText() {
        largeText()
        everyRoom("english-large", hebrew = false)
    }
}
