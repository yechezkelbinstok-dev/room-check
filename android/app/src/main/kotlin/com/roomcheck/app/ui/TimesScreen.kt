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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
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
                // The date is Hebrew, the rest is not. The date is isolated so it stays in one
                // piece, and the English leads so the line as a whole is laid out left to right -
                // put the date first and its direction is taken for the whole line, which throws
                // the English to the far side and reads as nonsense.
                Text(
                    "This night only · ⁨" + Dates.hebrewDate(state.dateKey) + "⁩",
                    fontSize = 12.5.sp, color = RC.sub
                )
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
                                // the whole point of the page: one check tonight, at this time
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
                            Text("Back to the usual three", color = RC.sub, fontWeight = FontWeight.SemiBold)
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
        onOnly = { id -> vm.onlyRoundTonight(id); adding = false },
        onClose = { adding = false }
    )
}

/**
 * Two ways out on purpose. "Only this time" is the case that actually comes up - the zman moved
 * and there is one check tonight - and making it one step is the difference between using this
 * and not bothering.
 */
@Composable
private fun AddTimeDialog(onAdd: (String) -> Unit, onOnly: (String) -> Unit, onClose: () -> Unit) {
    var hh by remember { mutableStateOf("") }
    var mm by remember { mutableStateOf("") }
    val id = hh.padStart(2, '0') + mm.padStart(2, '0')
    val ok = hh.isNotBlank() && mm.isNotBlank() && Slots.isValidId(id)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add a time to this night") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hh, onValueChange = { hh = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = mm, onValueChange = { mm = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(Modifier.height(8.dp))
                // A check runs from evening into the small hours, so the hour alone says which
                // side of midnight it is on: 6-12 is evening, 1-5 is after it. No am/pm needed.
                Text(
                    if (ok) Slots.labelFor(id) else "As on a clock — 1 : 30 for half one",
                    fontSize = 12.5.sp, color = RC.sub
                )
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onOnly(id) }) { Text("Only this time") }
        },
        dismissButton = {
            TextButton(enabled = ok, onClick = { onAdd(id) }) { Text("Add to tonight") }
        }
    )
}
