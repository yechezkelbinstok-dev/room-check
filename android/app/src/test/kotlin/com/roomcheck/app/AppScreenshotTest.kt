package com.roomcheck.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import com.roomcheck.app.data.RoomMode
import com.roomcheck.app.ui.CheckScreen
import com.roomcheck.app.ui.NamesScreen
import com.roomcheck.app.ui.RoomCheckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The whole app screen, rendered exactly as the phone will draw it - top bar, time tabs, room
 * chips, floor plan and bottom bar together. The floor-plan test alone could not catch a control
 * squeezed by the bar above it, because it never drew the bar.
 */
class AppScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @get:Rule
    val folder = TemporaryFolder()

    private fun viewModel(): AppViewModel = AppViewModel(NightStore(folder.newFolder()))

    @Test
    fun checkScreen() {
        val vm = viewModel()
        paparazzi.snapshot(name = "check-empty") {
            RoomCheckTheme { CheckScreen(vm) }
        }
    }

    /** A partly-checked room: the states an actual bed check produces, not a blank slate. */
    @Test
    fun checkScreenInProgress() {
        val vm = viewModel()
        val slot = vm.state.value.curSlot
        vm.setMark("p2", slot, Mark.IN)
        vm.setMark("p1", slot, Mark.OUT)
        vm.setMark("p3", slot, Mark.EXC)
        paparazzi.snapshot(name = "check-in-progress") {
            RoomCheckTheme { CheckScreen(vm) }
        }
    }

    /** The all-rooms scroll mode, where several plans share the screen. */
    @Test
    fun scrollMode() {
        val vm = viewModel()
        vm.setMode(RoomMode.SCROLL)
        paparazzi.snapshot(name = "check-scroll") {
            RoomCheckTheme { CheckScreen(vm) }
        }
    }

    /** The deepest room in the plan - the one most likely to crowd the bars around it. */
    @Test
    fun deepestRoom() {
        val vm = viewModel()
        vm.jumpRoom(Roster.PLAN.indexOfFirst { it.h == Roster.PLAN.maxOf { r -> r.h } })
        paparazzi.snapshot(name = "check-deepest-room") {
            RoomCheckTheme { CheckScreen(vm) }
        }
    }

    @Test
    fun namesScreen() {
        val vm = viewModel()
        paparazzi.snapshot(name = "names") {
            RoomCheckTheme { NamesScreen(vm) }
        }
    }
}
