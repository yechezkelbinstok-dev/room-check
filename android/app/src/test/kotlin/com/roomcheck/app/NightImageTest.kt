package com.roomcheck.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Roster
import com.roomcheck.app.util.NightImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The sendable sheet, rendered for real and drawn into a snapshot so it can be looked at. It is a
 * Bitmap, not Compose, so nothing else in the app would ever show it to me.
 */
class NightImageTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @get:Rule
    val folder = TemporaryFolder()

    /** Two rounds walked in full, 12:00 not started - the state you copy from most nights. */
    private fun halfCheckedNight(): AppViewModel {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        listOf("1115", "1130").forEach { sid ->
            Roster.PEOPLE.forEach { vm.setMark(it.id, sid, Mark.IN) }
        }
        listOf("p3", "p6", "p11", "p19", "p24", "p29").forEach { vm.setMark(it, "1115", Mark.OUT) }
        vm.setMark("p8", "1115", Mark.EXC)
        listOf("p13", "p24", "p29").forEach { vm.setMark(it, "1130", Mark.OUT) }
        return vm
    }

    /**
     * The picture has to be readable in the chat thread without opening it, and a thread crops
     * anything much taller than about 1.4x its width. Checked on the worst night there is - every
     * name missing at every time, which is what makes the summary at the top run longest.
     */
    @Test
    fun neverTallerThanAChatPreviewShows() {
        listOf(halfCheckedNight(), everybodyMissing()).forEach { vm ->
          listOf(false, true).forEach { heb ->
            val state = vm.state.value
            val bmp = NightImage.render(vm.logic(state), state.dateKey, heb)
            val ratio = bmp.height.toFloat() / bmp.width
            assertEquals("ratio was $ratio (${bmp.width}x${bmp.height}) hebrew=$heb", true, ratio <= 1.35f)
          }
        }
    }

    private fun everybodyMissing(): AppViewModel {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        Roster.SIDS.forEach { sid -> Roster.PEOPLE.forEach { vm.setMark(it.id, sid, Mark.OUT) } }
        return vm
    }

    /** The same night in Hebrew, which is what most of these are going to be sent as. */
    @Test
    fun sendableSheetHebrew() {
        val vm = halfCheckedNight()
        val state = vm.state.value
        paparazzi.snapshot(name = "sheet-hebrew") {
            val bmp = NightImage.render(vm.logic(state), state.dateKey, hebrew = true)
            dump(bmp, "night-image-hebrew.png")
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
    }

    @Test
    fun worstCaseSheet() {
        val vm = everybodyMissing()
        val state = vm.state.value
        paparazzi.snapshot(name = "sheet-everyone-out") {
            val bmp = NightImage.render(vm.logic(state), state.dateKey)
            dump(bmp, "night-image-worst.png")
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
    }

    /** A round left half-walked still says so, rather than reading as an all-clear. */
    @Test
    fun unwalkedRoomsAreCalledOut() {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        vm.setMark("p1", "1115", Mark.IN)
        val state = vm.state.value
        val text = vm.logic(state).report(state.dateKey)
        assertEquals(true, text.contains("(still to check: Room 2, Room 3,"))
    }

    @Test
    fun sendableSheet() {
        val vm = halfCheckedNight()
        val state = vm.state.value
        paparazzi.snapshot(name = "sheet") {
            val bmp = NightImage.render(vm.logic(state), state.dateKey)
            // also written out whole: a snapshot crops to the phone screen, and the point of this
            // picture is everything below the fold
            dump(bmp, "night-image.png")
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
    }

    private fun dump(bmp: android.graphics.Bitmap, name: String) {
        val out = java.io.File("build/preview").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(out, name)).use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** An untouched night, so the empty state of the picture is seen too. */
    @Test
    fun sendableSheetBlank() {
        val vm = AppViewModel(NightStore(folder.newFolder()))
        val state = vm.state.value
        paparazzi.snapshot(name = "sheet-blank") {
            val bmp = NightImage.render(vm.logic(state), state.dateKey)
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
    }

    /** The pasted text, in the exact shape asked for: date, then each time and who was missing. */
    @Test
    fun copyTextFormat() {
        val vm = halfCheckedNight()
        val state = vm.state.value
        val body = vm.logic(state).report(state.dateKey).lines().drop(1).joinToString("\n")
        assertEquals(
            """
            |
            |11:15
            |Menachem Mendel Piekarski, Menachem Mendel Stolik, Asher Wolfe, Menachem Mendel HaLevi Flint, Leib Meir November, Yehuda Fehler
            |
            |11:30
            |Dovid Altein, Leib Meir November, Yehuda Fehler
            |
            |12:00
            |Not checked yet
            """.trimMargin(),
            body
        )
    }
}
