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
import com.roomcheck.app.ui.SettingsScreen
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

    /** The widest room - drawn to the same page width, so its cards have the least room. */
    @Test
    fun widestRoom() {
        val vm = viewModel()
        vm.jumpRoom(Roster.PLAN.indexOfFirst { it.w == Roster.PLAN.maxOf { r -> r.w } })
        paparazzi.snapshot(name = "check-widest-room") {
            RoomCheckTheme { CheckScreen(vm) }
        }
    }

    /** The plan with Hebrew names switched on - the longest names in the narrowest cards. */
    @Test
    fun hebrewPlan() {
        val vm = viewModel()
        vm.setSettings { it.copy(hebrewOnPlan = true) }
        vm.jumpRoom(4)
        paparazzi.snapshot(name = "check-hebrew") {
            RoomCheckTheme { CheckScreen(vm) }
        }
    }

    /**
     * The closed, read-only night. It had no snapshot until its list turned out to be clipping
     * its own last rooms away, which is exactly the kind of thing only a render shows.
     */
    @Test
    fun closedNight() {
        val vm = viewModel()
        val slot = vm.state.value.curSlot
        Roster.PEOPLE.forEach { vm.setMark(it.id, slot, Mark.IN) }
        listOf("p3", "p11", "p24").forEach { vm.setMark(it, slot, Mark.OUT) }
        vm.closeNight()
        paparazzi.snapshot(name = "closed-night") {
            RoomCheckTheme { CheckScreen(vm) }
        }
    }

    @Test
    fun settingsScreen() {
        val vm = viewModel()
        paparazzi.snapshot(name = "settings") {
            RoomCheckTheme { SettingsScreen(vm) }
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
