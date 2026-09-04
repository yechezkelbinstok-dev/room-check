package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSheet(vm: AppViewModel, state: UiState, pid: String, onClose: () -> Unit) {
    val logic = vm.logic(state)
    val room = Roster.roomOf.getValue(pid)
    val excusedTonight = state.night.excusedTonight.contains(pid)
    val always = logic.isAlways(pid)
    var noteText by remember(pid, state.night) { mutableStateOf(state.night.notes[pid] ?: "") }
    var reasonText by remember(pid, state.extra[pid]?.reason) { mutableStateOf(state.extra[pid]?.reason ?: "") }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(RC.card)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(logic.nameOf(pid), fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                    Text(room.label, fontSize = 12.5.sp, color = RC.sub)
                }
                HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                logic.slots.forEach { (sid, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp, 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 15.sp)
                        MarkButtons(logic.statusOf(pid, sid), compact = false, onSet = { m -> vm.setMark(pid, sid, m) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(RC.card)) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleExcusedTonight(pid) }.padding(14.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(excusedTonight)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("Excused tonight", fontSize = 15.sp)
                        Text("All three times, tonight only", fontSize = 12.sp, color = RC.sub)
                    }
                }
                HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleAlwaysExcused(pid) }.padding(14.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(always)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("Excused indefinitely", fontSize = 15.sp)
                        Text("Until you turn it back on", fontSize = 12.sp, color = RC.sub)
                    }
                }
                if (always) {
                    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                    OutlinedTextField(
                        value = reasonText, onValueChange = { reasonText = it; vm.setReason(pid, it) },
                        placeholder = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth().padding(14.dp, 4.dp),
                        singleLine = true
                    )
                }
                HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                OutlinedTextField(
                    value = noteText, onValueChange = { noteText = it; vm.setNote(pid, it) },
                    placeholder = { Text("Note for tonight") },
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Done", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RC.blue, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(RC.card).clickable(onClick = onClose).padding(15.dp)
            )
        }
    }
}

@Composable
private fun Checkbox(checked: Boolean) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
            .background(if (checked) RC.green else androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
    }
}
