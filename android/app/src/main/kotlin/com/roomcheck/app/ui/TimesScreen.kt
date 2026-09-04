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
import com.roomcheck.app.data.Slots
import com.roomcheck.app.data.Tab

/**
 * Adding a time, and choosing which times the sent picture shows.
 *
 * Its own page rather than a row in Settings because it is barely ever touched - most nights are
 * the three standing rounds - and something used twice a year should not be sitting in the way of
 * the things used every night.
 */
@Composable
fun TimesScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val all = Slots.all(state.settings)
    val onSheet = Slots.forSheet(state.settings).map { it.id }.toSet()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Times", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { vm.setTab(Tab.SETTINGS) }) { Text("Back") }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item { SectionHeader("Rounds") }
            item {
                Card {
                    all.forEachIndexed { i, slot ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        val custom = slot.id in state.settings.customSlots
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp, 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(slot.label, fontSize = 16.sp)
                            if (custom) {
                                Text("Remove", color = RC.red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        vm.setSettings {
                                            it.copy(
                                                customSlots = it.customSlots - slot.id,
                                                sheetSlots = it.sheetSlots - slot.id
                                            )
                                        }
                                    })
                            } else {
                                Text("every night", fontSize = 13.sp, color = RC.sub)
                            }
                        }
                    }
                    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().clickable { adding = true }.padding(14.dp, 13.dp)) {
                        Text("Add a time", color = RC.blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item { SectionHeader("On the sent picture") }
            item {
                Card {
                    all.forEachIndexed { i, slot ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        SettingRow(slot.label, slot.id in onSheet) { on ->
                            vm.setSettings { st ->
                                // stored as the explicit list of what to show; an empty list means
                                // "all of them", so unticking down to nothing is the same as all
                                val current = if (st.sheetSlots.isEmpty()) all.map { s -> s.id } else st.sheetSlots
                                val next = if (on) current + slot.id else current - slot.id
                                st.copy(sheetSlots = if (next.toSet() == all.map { s -> s.id }.toSet()) emptyList() else next)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    if (adding) AddTimeDialog(onAdd = { id ->
        vm.setSettings { it.copy(customSlots = (it.customSlots + id).distinct()) }
        adding = false
    }, onClose = { adding = false })
}

@Composable
private fun AddTimeDialog(onAdd: (String) -> Unit, onClose: () -> Unit) {
    var hh by remember { mutableStateOf("") }
    var mm by remember { mutableStateOf("") }
    val id = hh.padStart(2, '0') + mm.padStart(2, '0')
    val ok = hh.isNotBlank() && mm.isNotBlank() && Slots.isValidId(id)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add a time") },
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
                    if (ok) "Adds " + Slots.labelFor(id) else "As on a clock — 1 : 30 for half one",
                    fontSize = 12.5.sp, color = RC.sub
                )
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onAdd(id) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
