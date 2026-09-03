package com.roomcheck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Roster
import com.roomcheck.app.ui.FloorPlanCanvas
import com.roomcheck.app.ui.PersonSlot
import com.roomcheck.app.ui.RC
import com.roomcheck.app.ui.RoomCheckTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the real Compose floor plan for every room to a PNG, on the JVM, with no emulator.
 * This is the check that was missing: previews drawn by hand in HTML only approximate Compose's
 * layout, so they cannot catch a squashed button or a clipped name. This runs the actual code.
 */
class FloorPlanScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun everyRoom() {
        Roster.PLAN.forEachIndexed { index, room ->
            paparazzi.snapshot(name = "room-${index + 1}") {
                RoomCheckTheme {
                    Column(Modifier.fillMaxWidth().background(RC.bg).padding(vertical = 8.dp)) {
                        // exactly how CheckScreen sizes the plan
                        FloorPlanCanvas(
                            room = room,
                            allIn = false,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        ) { _, pid, bunkLabel, wideCard ->
                            val p = Roster.byId.getValue(pid)
                            PersonSlot(
                                first = p.first,
                                last = p.last,
                                bunkLabel = bunkLabel,
                                status = null,
                                row = wideCard,
                                onNameClick = {},
                                onMark = {}
                            )
                        }
                    }
                }
            }
        }
    }

    /** Same rooms with people marked, so coloured states and the "on" buttons are visible too. */
    @Test
    fun markedStates() {
        val room = Roster.PLAN[0]
        paparazzi.snapshot(name = "marked-room-1") {
            RoomCheckTheme {
                Column(Modifier.fillMaxWidth().background(RC.bg).padding(vertical = 8.dp)) {
                    FloorPlanCanvas(
                        room = room,
                        allIn = false,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    ) { _, pid, bunkLabel, wideCard ->
                        val p = Roster.byId.getValue(pid)
                        val status = when (pid) {
                            "p2" -> Mark.IN
                            "p1" -> Mark.OUT
                            "p3" -> Mark.EXC
                            else -> null
                        }
                        PersonSlot(
                            first = p.first,
                            last = p.last,
                            bunkLabel = bunkLabel,
                            status = status,
                            row = wideCard,
                            onNameClick = {},
                            onMark = {}
                        )
                    }
                }
            }
        }
    }
}
