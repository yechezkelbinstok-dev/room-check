package com.roomcheck.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.BuildConfig
import com.roomcheck.app.data.*

@Composable
fun NamesScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val logic = vm.logic(state)
    val context = LocalContext.current
    var renamePid by remember { mutableStateOf<String?>(null) }

    val alwaysExcused = Roster.PEOPLE.filter { logic.isAlways(it.id) }

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("Names", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("30 bochurim · 8 rooms", fontSize = 12.5.sp, color = RC.sub)
            }
            OutlinedButton(onClick = { vm.setTab(Tab.SETTINGS) }) { Text("Back") }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (alwaysExcused.isNotEmpty()) {
                item { SectionHeader("Excused indefinitely") }
                item {
                    Card {
                        alwaysExcused.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                            val room = Roster.roomOf.getValue(p.id)
                            val reason = state.extra[p.id]?.reason
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp, 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(logic.nameOf(p.id), fontSize = 15.sp)
                                    Text(room.label + (reason?.let { " · $it" } ?: ""), fontSize = 12.sp, color = RC.sub)
                                }
                                Text("Turn back on", fontSize = 13.sp, color = RC.blue, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { vm.unexcuse(p.id) })
                            }
                        }
                    }
                }
            }
            Roster.PLAN.forEach { room ->
                item { SectionHeader(room.label) }
                item {
                    Card {
                        val slots = room.beds.flatMap { bed -> bed.slots.mapIndexed { i, pid -> Triple(pid, bed.slots.size, i) } }
                        slots.forEachIndexed { idx, (pid, bedSize, i) ->
                            if (idx > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                            Row(
                                Modifier.fillMaxWidth().clickable { renamePid = pid }.padding(14.dp, 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(logic.nameOf(pid), fontSize = 15.sp)
                                    Text(if (bedSize > 1) (if (i == 0) "top bunk" else "bottom bunk") else "single bed", fontSize = 12.sp, color = RC.sub)
                                }
                                if (logic.isAlways(pid)) Text("excused", fontSize = 13.sp, color = RC.sub)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    renamePid?.let { pid ->
        RenameDialog(vm, pid, logic.first(pid), logic.last(pid), onClose = { renamePid = null })
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RC.sub2, modifier = Modifier.padding(4.dp, 20.dp, 4.dp, 7.dp))
}

@Composable
internal fun SettingRow(
    label: String,
    on: Boolean,
    enabled: Boolean = true,
    sub: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp, 8.dp, 10.dp, 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = if (enabled) RC.text else RC.sub)
            sub?.let { Text(it, fontSize = 12.sp, color = RC.sub) }
        }
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(RC.card).padding(bottom = 0.dp), content = content)
}

@Composable
private fun RenameDialog(vm: AppViewModel, pid: String, first: String, last: String, onClose: () -> Unit) {
    var f by remember { mutableStateOf(first) }
    var l by remember { mutableStateOf(last) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Rename") },
        text = {
            Column {
                OutlinedTextField(value = f, onValueChange = { f = it }, label = { Text("First") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = l, onValueChange = { l = it }, label = { Text("Last") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { vm.renamePerson(pid, f, l); onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
internal fun ExportDialog(json: String, onShare: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Backup") },
        text = { Text("Share this to save it — email it to yourself, save to Drive, whatever's easy.") },
        confirmButton = { TextButton(onClick = { onShare(); onClose() }) { Text("Share") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
internal fun ImportDialog(onRestore: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Restore") },
        text = {
            Column {
                Text("Replaces roster customizations; adds/overwrites the nights it contains.", fontSize = 12.sp, color = RC.sub)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Paste a backup") },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onRestore(text) }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
