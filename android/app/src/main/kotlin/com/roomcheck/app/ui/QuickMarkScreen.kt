package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*

/**
 * The whole round in one go: name whoever is missing, everyone else is in.
 *
 * The room-by-room pass stays the default, because most nights you have to actually look at each
 * bed. This is for the night you already know - you were told three names, or you walked it and
 * only need to record it - where thirty taps to say "everyone except these three" is the slow way
 * round.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickMarkScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val logic = vm.logic(state)
    val hebrew = state.settings.hebrewOnPlan
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }

    val label = Slots.all(state.night).firstOrNull { it.id == state.curSlot }?.label ?: state.curSlot

    // Excused people are not on the list at all: their status already overrides any mark, so
    // picking one could only ever be a tap that did nothing.
    val roster = remember(state.rev, hebrew) {
        Roster.PEOPLE.filter { logic.statusOf(it.id, state.curSlot) != Mark.EXC }
    }
    val matches = roster.filter { p ->
        val q = query.trim()
        q.isEmpty() || listOf(
            logic.first(p.id, hebrew), logic.last(p.id, hebrew),
            logic.first(p.id, false), logic.last(p.id, false)
        ).any { it.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Who's missing", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 12.5.sp, color = RC.sub)
            }
            OutlinedButton(onClick = { vm.setTab(Tab.CHECK) }) { Text("Cancel") }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )

        if (picked.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(12.dp, 10.dp, 12.dp, 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                picked.sortedBy { pid -> Roster.PEOPLE.indexOfFirst { it.id == pid } }.forEach { pid ->
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(RC.redL)
                            .clickable { picked = picked - pid }.padding(10.dp, 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(logic.nameOf(pid, hebrew), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = RC.red)
                        Icon(Icons.Filled.Close, "Remove", tint = RC.red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        LazyColumn(Modifier.weight(1f).padding(12.dp, 10.dp, 12.dp, 0.dp)) {
            items(matches, key = { it.id }) { p ->
                val on = p.id in picked
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (on) RC.redL else RC.card)
                        .clickable { picked = if (on) picked - p.id else picked + p.id }
                        .padding(14.dp, 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        logic.nameOf(p.id, hebrew), fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) RC.red else RC.text
                    )
                    Text(
                        Roster.roomOf[p.id]?.let { if (hebrew) it.hebLabel else it.label } ?: "",
                        fontSize = 12.5.sp, color = RC.sub
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        Box(
            Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 14.dp).height(50.dp)
                .clip(RoundedCornerShape(12.dp)).background(RC.blue)
                .clickable { vm.markMissing(picked, state.curSlot); vm.setTab(Tab.CHECK) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (picked.isEmpty()) "Everyone in" else "${picked.size} out, rest in",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}
