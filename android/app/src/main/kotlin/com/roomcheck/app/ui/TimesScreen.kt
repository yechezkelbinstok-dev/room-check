package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Dates
import com.roomcheck.app.data.Slots
import com.roomcheck.app.data.Tab

/**
 * The rounds walked on ONE night, and which of them the sent picture shows.
 *
 * A round added here belongs to that night alone - the zman moved, or you went round again at
 * 1:30 - and the next night opens on the standing three with nothing to put back. It is reached
 * from the night it changes rather than from Settings, because a setting is a standing decision
 * and this is not one.
 */
@Composable
fun TimesScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val all = Slots.all(state.night)
    val onSheet = Slots.forSheet(state.night).map { it.id }.toSet()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Rounds", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(Dates.hebrewDate(state.dateKey), fontSize = 12.5.sp, color = RC.sub)
            }
            OutlinedButton(onClick = { vm.setTab(Tab.CHECK) }) { Text("Done") }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item {
                Card {
                    all.forEachIndexed { i, slot ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp, 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(slot.label, fontSize = 16.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (all.size > 1) {
                                    Text("Only this", color = RC.blue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { vm.onlyRoundTonight(slot.id) })
                                    Text("Remove", color = RC.red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { vm.removeRoundTonight(slot.id) })
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().clickable { adding = true }.padding(14.dp, 13.dp)) {
                        Text("Add a time", color = RC.blue, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.night.rounds.isNotEmpty()) {
                        HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth().clickable { vm.resetRoundsTonight() }.padding(14.dp, 13.dp)) {
                            Text("Restore defaults", color = RC.blue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item { SectionHeader("On the sent picture") }
            item {
                Card {
                    all.forEachIndexed { i, slot ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        SettingRow(slot.label, slot.id in onSheet) { vm.toggleSheetSlot(slot.id) }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    if (adding) AddTimeDialog(
        onAdd = { id -> vm.addRoundTonight(id); adding = false },
        onClose = { adding = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimeDialog(onAdd: (String) -> Unit, onClose: () -> Unit) {
    val st = rememberTimePickerState(initialHour = 23, initialMinute = 30, is24Hour = false)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add a time") },
        text = { AddTimeContent(st) },
        confirmButton = {
            TextButton(onClick = { onAdd(Slots.idForClock(st.hour, st.minute)) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

/**
 * Pulled out of the dialog so it can be rendered in a snapshot - Paparazzi draws composables, not
 * the separate window a dialog lives in, so inside AlertDialog this would never be looked at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTimeContent(state: TimePickerState) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) }
}
