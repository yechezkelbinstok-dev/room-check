package com.roomcheck.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.ui.BochurRound
import com.roomcheck.app.ui.RaffleScreen
import com.roomcheck.app.ui.RoomCheckTheme
import com.roomcheck.app.ui.SichosScreen
import com.roomcheck.app.ui.SichosWeeksScreen
import com.roomcheck.app.ui.byName
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import kotlin.random.Random

/** Seder Sichos as the phone draws it: the start, this week, all weeks, his round, the raffle. */
class SichosScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @get:Rule
    val folder = TemporaryFolder()

    private fun vm(start: String, weeks: List<IntRange>): AppViewModel {
        val vm = testVm(NightStore(folder.newFolder()))
        // the list's own order, so the names marked are the ones at the top of the screen
        val byName = byName(vm.logic())
        vm.setSichosStart(LocalDate.parse(start))
        val round = vm.sichosRound()
        weeks.forEachIndexed { i, range -> range.forEach { vm.toggleSikkum(byName[i], round, it) } }
        vm.clearToast()
        return vm
    }

    /**
     * Wednesday 21 October: a round that started on Wednesday night 30 September, collecting its
     * third week's - the sicha of the 14th.
     */
    private fun underWay() = vm("2026-09-30", listOf(0..2, 0..1, 0..2, IntRange.EMPTY, 2..2, 1..2, 0..2, 0..0)).also {
        it.setReviewed(byName(it.logic())[0], 0, 0, true)
    }

    @Test
    fun start() {
        val vm = testVm(NightStore(folder.newFolder()))
        paparazzi.snapshot(name = "sichos-start") { RoomCheckTheme { SichosScreen(vm) } }
    }

    @Test
    fun thisWeek() {
        val vm = underWay()
        paparazzi.snapshot(name = "sichos-week") { RoomCheckTheme { SichosScreen(vm) } }
    }

    @Test
    fun allWeeks() {
        val vm = underWay()
        paparazzi.snapshot(name = "sichos-weeks") { RoomCheckTheme { SichosWeeksScreen(vm) } }
    }

    @Test
    fun hisRound() {
        val vm = underWay()
        paparazzi.snapshot(name = "sichos-bochur") {
            RoomCheckTheme {
                Box(Modifier.fillMaxWidth().background(Color.White).padding(top = 20.dp)) {
                    BochurRound(vm, vm.sichos(), vm.logic(), byName(vm.logic())[0], 0)
                }
            }
        }
    }

    /** A round that is over - the four Wednesday nights from 16 September - with five bochurim in the hat. */
    private fun roundOver() = vm("2026-09-16", emptyList()).also { vm ->
        val byName = byName(vm.logic())
        listOf(0..3, 0..2, 0..3, 1..3, 0..1, 0..2).forEachIndexed { i, range -> range.forEach { vm.toggleSikkum(byName[i], 0, it) } }
        vm.showSichosRound(0)
        vm.clearToast()
    }

    @Test
    fun raffleReady() {
        val vm = roundOver()
        paparazzi.snapshot(name = "raffle-ready") { RoomCheckTheme { RaffleScreen(vm) } }
    }

    @Test
    fun raffleWon() {
        val vm = roundOver()
        vm.drawRaffle(0, random = Random(5))
        paparazzi.snapshot(name = "raffle-won") { RoomCheckTheme { RaffleScreen(vm) } }
    }
}
