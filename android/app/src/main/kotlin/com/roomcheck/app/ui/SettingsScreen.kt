package com.roomcheck.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.BuildConfig
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.SyncState
import com.roomcheck.app.data.Tab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything that is a preference rather than a night: which language the names are in, what the
 * plan draws, backups, and the way through to the roster. The two Hebrew switches are deliberately
 * separate - marking in Hebrew and sending in Hebrew are different decisions.
 */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val logic = vm.logic(state)
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val hasHebrew = logic.anyHebrewNames()

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { vm.setTab(Tab.CHECK) }) { Text("Done") }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item { SectionHeader("Names") }
            item {
                Card {
                    SettingRow(
                        "Hebrew on the plan", state.settings.hebrewOnPlan,
                        enabled = hasHebrew, sub = if (hasHebrew) null else "No Hebrew names yet"
                    ) { on -> vm.setSettings { it.copy(hebrewOnPlan = on) } }
                    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                    SettingRow(
                        "Hebrew in the sent picture", state.settings.hebrewInExport,
                        enabled = hasHebrew, sub = if (hasHebrew) null else "No Hebrew names yet"
                    ) { on -> vm.setSettings { it.copy(hebrewInExport = on) } }
                }
            }
            item { SectionHeader("Plan") }
            item {
                Card {
                    SettingRow("Bunk labels", state.settings.bunkLabels) { on ->
                        vm.setSettings { it.copy(bunkLabels = on) }
                    }
                }
            }
            item { SectionHeader("Sync") }
            item {
                Card {
                    var url by remember(state.settings.syncUrl) { mutableStateOf(state.settings.syncUrl) }
                    var token by remember(state.settings.syncToken) { mutableStateOf(state.settings.syncToken) }
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("Server address") }, singleLine = true,
                        placeholder = { Text("https://…") },
                        modifier = Modifier.fillMaxWidth().padding(14.dp, 12.dp, 14.dp, 4.dp)
                    )
                    OutlinedTextField(
                        value = token, onValueChange = { token = it },
                        label = { Text("Sync password") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(14.dp, 4.dp, 14.dp, 8.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.setSettings { it.copy(syncUrl = url.trim(), syncToken = token.trim()) }
                            vm.syncNow(loud = true)
                        }) { Text("Save & sync") }
                    }
                    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.syncNow(loud = true) }.padding(14.dp, 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sync now", color = RC.blue, fontWeight = FontWeight.SemiBold)
                        Text(syncLine(state.sync, state.settings.syncUrl), fontSize = 12.5.sp, color = RC.sub)
                    }
                }
            }
            item { SectionHeader("Roster") }
            item {
                Card {
                    Row(Modifier.fillMaxWidth().clickable { vm.setTab(Tab.NAMES) }.padding(14.dp, 13.dp)) {
                        Text("Names & rooms", color = RC.blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { SectionHeader("Backup") }
            item {
                Card {
                    Row(Modifier.fillMaxWidth().clickable { showExport = true }.padding(14.dp, 13.dp)) {
                        Text("Save a backup file", color = RC.blue, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().clickable { showImport = true }.padding(14.dp, 13.dp)) {
                        Text("Restore from a backup", color = RC.blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { SectionHeader("App") }
            item {
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp, 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Version", fontSize = 15.sp)
                        Text(BuildConfig.VERSION_NAME, fontSize = 15.sp, color = RC.sub)
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
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

/** One line of plain state under Sync now - what happened, not how it works. */
private fun syncLine(state: SyncState, url: String): String = when {
    url.isBlank() -> "Not set up"
    state is SyncState.Working -> "Syncing…"
    state is SyncState.Ok -> "Last synced " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(state.at)).lowercase()
    state is SyncState.Failed -> state.reason
    else -> "Ready"
}
