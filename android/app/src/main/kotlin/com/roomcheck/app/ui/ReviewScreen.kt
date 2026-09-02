package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*

@Composable
fun ReviewScreen(vm: AppViewModel, state: UiState, logic: NightLogic) {
    val locked = vm.isLocked(state)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(11.dp)).background(RC.blueL).padding(14.dp, 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (locked) "Closed — read only" else "Review — read only", color = Color(0xFF0B4F9E), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text("Edit", color = RC.blue, fontWeight = FontWeight.Bold, fontSize = 14.5.sp,
                modifier = Modifier.clickable { vm.setEditing(true) })
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE7E7EC)).padding(2.dp)
        ) {
            listOf(false to "Everyone", true to "Only not here").forEach { (v, label) ->
                val on = state.onlyOut == v
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (on) Color.White else Color.Transparent)
                        .clickable { vm.setOnlyOut(v) }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) RC.text else RC.sub2) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 18.dp, top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
            Roster.SLOTS.forEach { (_, label) -> Text(label, fontSize = 10.5.sp, color = RC.sub, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val roomsWithPeople = Roster.PLAN.mapNotNull { room ->
            val ppl = room.beds.flatMap { it.slots }
            val list = if (state.onlyOut) ppl.filter { pid -> Roster.SIDS.any { logic.statusOf(pid, it) == Mark.OUT } } else ppl
            if (list.isNotEmpty()) room to list else null
        }
        LazyColumn(Modifier.weight(1f).padding(bottom = 100.dp)) {
            if (roomsWithPeople.isEmpty()) {
                item {
                    Text(
                        if (state.onlyOut) "Nobody was marked out tonight." else "Nothing marked yet.",
                        fontSize = 13.5.sp, color = RC.sub,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(13.dp)).background(RC.card).padding(16.dp)
                    )
                }
            }
            items(roomsWithPeople) { (room, list) ->
                Text(room.label.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RC.sub2,
                    modifier = Modifier.padding(20.dp, 20.dp, 4.dp, 7.dp))
                Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(13.dp)).background(RC.card)) {
                    list.forEachIndexed { i, pid ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp, 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(logic.nameOf(pid), fontSize = 15.sp)
                            Row {
                                Roster.SIDS.forEach { sid ->
                                    val (glyph, color) = when (logic.statusOf(pid, sid)) {
                                        Mark.IN -> "✓" to RC.green
                                        Mark.OUT -> "✕" to RC.red
                                        Mark.EXC -> "E" to RC.grey
                                        null -> "–" to Color(0xFFC9CBD2)
                                    }
                                    Text(glyph, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color,
                                        modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "✓ here · ✕ not here · E excused · – not marked",
                    fontSize = 13.5.sp, color = RC.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 24.dp)
                )
            }
        }
    }
}
