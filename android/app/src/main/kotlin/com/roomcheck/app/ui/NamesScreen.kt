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
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

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
            OutlinedButton(onClick = { vm.setTab(Tab.CHECK) }) { Text("Done") }
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
            item { SectionHeader("Backup") }
            item {
                Card {
                    Row(Modifier.fillMaxWidth().clickable { showExport = true }.padding(14.dp, 12.dp)) {
                        Text("Save a backup file", color = RC.blue, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().clickable { showImport = true }.padding(14.dp, 12.dp)) {
                        Text("Restore from a backup", color = RC.blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { SectionHeader("App") }
            item {
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp, 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Version", fontSize = 15.sp); Text(BuildConfig.VERSION_NAME, fontSize = 12.sp, color = RC.sub) }
                    }
                }
            }
            item {
                Text(
                    "Everything saves on this device — its own private storage, not your browser. Works with no signal.",
                    fontSize = 13.5.sp, color = RC.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 30.dp)
                )
            }
        }
    }

    renamePid?.let { pid ->
        RenameDialog(vm, pid, logic.first(pid), logic.last(pid), onClose = { renamePid = null })
    }
    if (showExport) {
        val json = remember { vm.exportBackup() }
        ExportDialog(json, onShare = {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, json) }
            context.startActivity(Intent.createChooser(intent, "Save backup"))
        }, onClose = { showExport = false })
    }
    if (showImport) {
        ImportDialog(
            onRestore = { text ->
                val ok = vm.importBackup(text)
                vm.toast(if (ok) "Restored" else "Not a backup")
                if (ok) showImport = false
            },
            onClose = { showImport = false }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RC.sub2, modifier = Modifier.padding(4.dp, 20.dp, 4.dp, 7.dp))
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
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
private fun ExportDialog(json: String, onShare: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Backup") },
        text = { Text("Share this to save it — email it to yourself, save to Drive, whatever's easy.") },
        confirmButton = { TextButton(onClick = { onShare(); onClose() }) { Text("Share") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun ImportDialog(onRestore: (String) -> Unit, onClose: () -> Unit) {
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
