package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Dates
import com.roomcheck.app.data.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(vm: AppViewModel, state: UiState, onClose: () -> Unit) {
    var month by remember { mutableStateOf(Dates.hebMonthOf(state.dateKey)) }
    val hasData = remember { vm.calendarKeysWithData() }
    val today = Dates.tonightKey()

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp, 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                CalNavBtn(Icons.Filled.ChevronLeft) { month = Dates.prevHebMonth(month) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${Dates.hebMonthName(month.month, month.year)} ${Dates.hebYearStr(month.year)}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${Dates.hebMonthNameEn(month.month, month.year)} ${month.year}", fontSize = 11.5.sp, color = RC.sub)
                }
                CalNavBtn(Icons.Filled.ChevronRight) { month = Dates.nextHebMonth(month) }
            }
            val days = Dates.daysInHebMonth(month)
            val dow = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                dow.forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = RC.sub, fontWeight = FontWeight.Bold) }
            }
            val leadOffset = days.first().dayOfWeek
            val cells = List(leadOffset) { null } + days
            val rows = cells.chunked(7)
            Column(Modifier.padding(8.dp, 4.dp)) {
                rows.forEach { rowCells ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        rowCells.forEach { day ->
                            Box(Modifier.weight(1f).padding(1.dp)) {
                                if (day == null) {
                                    Spacer(Modifier.height(50.dp))
                                } else {
                                    val isToday = day.dateKey == today
                                    val isSel = day.dateKey == state.dateKey
                                    val hasMark = hasData.contains(day.dateKey)
                                    val isSat = day.dayOfWeek == 6
                                    Column(
                                        Modifier.fillMaxWidth().height(50.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) RC.blue else if (isSat) Color(0xFFF5F6FA) else Color.Transparent)
                                            .border(if (isToday) 1.5.dp else 0.dp, if (isToday) RC.blue else Color.Transparent, RoundedCornerShape(10.dp))
                                            .clickable { vm.goToDate(day.dateKey); onClose() },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(Dates.hebNum(day.hebDay), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isSel) Color.White else RC.text)
                                        val g = Dates.parseKey(day.dateKey).plusDays(1)
                                        Text("${g.monthValue}/${g.dayOfMonth}", fontSize = 9.5.sp, color = if (isSel) Color.White.copy(alpha = 0.75f) else RC.sub)
                                        if (hasMark) {
                                            Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (isSel) Color.White else RC.blue))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CalNavBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(RC.blueL).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = RC.blue) }
}
