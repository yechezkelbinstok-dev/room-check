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
 * Every room with the plan in Hebrew, at the phone's normal text size and at a large one. Hebrew
 * letters stand taller than Latin ones and a phone set to big text makes everything taller still,
 * so this is where a name squeezed out of its half of a bunk shows up first.
 */
class HebrewPlanTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @get:Rule
    val folder = TemporaryFolder()

    private fun everyRoom(tag: String) {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        vm.setSettings { it.copy(hebrewOnPlan = true) }
        Roster.PLAN.indices.forEach { i ->
            vm.jumpRoom(i)
            paparazzi.snapshot(name = "hebrew-$tag-room-${i + 1}") { RoomCheckTheme { CheckScreen(vm) } }
        }
    }

    @Test
    fun normalText() = everyRoom("normal")

    @Test
    fun largeText() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f))
        everyRoom("large")
    }
}
