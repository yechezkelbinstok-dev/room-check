package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.Bed
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Room

private const val PLAN_H = 112f // room aspect: 100 wide x 112 tall, matches the web app's viewBox

/**
 * Draws one room's floor plan: a plain wall outline and every bed positioned by the same
 * x/y/w/h percentages the web app uses. Each bed's slots (1 or 2 people) are rendered via
 * [slotContent] so callers can wire in real status/handlers.
 */
@Composable
fun FloorPlanCanvas(
    room: Room,
    allIn: Boolean,
    modifier: Modifier = Modifier,
    slotContent: @Composable (bed: Bed, pid: String, bunkLabel: String?) -> Unit
) {
    val bg = if (allIn) Color(0xFFF7FCFA) else RC.floor
    BoxWithConstraints(
        modifier
            .aspectRatio(100f / PLAN_H)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.5.dp, RC.wall, RoundedCornerShape(4.dp))
    ) {
        val wPx = maxWidth
        val hPx = maxHeight
        room.beds.forEach { bed ->
            Box(
                Modifier
                    .offset(x = wPx * (bed.x / 100f), y = hPx * (bed.y / PLAN_H))
                    .size(width = wPx * (bed.w / 100f), height = hPx * (bed.h / PLAN_H))
                    .background(Color.White, RoundedCornerShape(5.dp))
                    .border(1.5.dp, Color(0xFFC9CBD2), RoundedCornerShape(5.dp))
            ) {
                val multi = bed.slots.size > 1
                if (bed.row) {
                    Row(Modifier.fillMaxSize()) {
                        bed.slots.forEachIndexed { i, pid ->
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                slotContent(bed, pid, if (multi) (if (i == 0) "top" else "bottom") else null)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        bed.slots.forEachIndexed { i, pid ->
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                slotContent(bed, pid, if (multi) (if (i == 0) "top" else "bottom") else null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkButtons(
    status: Mark?,
    compact: Boolean,
    onSet: (Mark) -> Unit,
    modifier: Modifier = Modifier
) {
    val size = if (compact) 26.dp else 31.dp
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        MarkButton(size, status == Mark.IN, RC.green, { onSet(Mark.IN) }) {
            Icon(Icons.Filled.Check, contentDescription = "Here", modifier = Modifier.size(15.dp))
        }
        MarkButton(size, status == Mark.OUT, RC.red, { onSet(Mark.OUT) }) {
            Icon(Icons.Filled.Close, contentDescription = "Not here", modifier = Modifier.size(15.dp))
        }
        MarkButton(size, status == Mark.EXC, RC.grey, { onSet(Mark.EXC) }) {
            Text("E", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MarkButton(size: androidx.compose.ui.unit.Dp, on: Boolean, onColor: Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(if (on) onColor else Color(0xFFEDEEF2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (on) Color.White else Color(0xFF9A9CA6),
            content = icon
        )
    }
}

@Composable
fun PersonSlot(
    first: String,
    last: String,
    bunkLabel: String?,
    status: Mark?,
    row: Boolean,
    onNameClick: () -> Unit,
    onMark: (Mark) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (status) {
        Mark.IN -> RC.greenL
        Mark.OUT -> RC.redL
        Mark.EXC -> RC.greyL
        null -> Color.Transparent
    }
    val nameColor = when (status) {
        Mark.OUT -> RC.red
        Mark.EXC -> RC.grey
        else -> RC.text
    }
    Box(modifier.fillMaxSize().background(bgColor).padding(6.dp, 5.dp)) {
        bunkLabel?.let {
            Text(it, fontSize = 9.sp, color = Color(0xFFB7B9C2), modifier = Modifier.align(Alignment.TopStart))
        }
        if (row) {
            Row(Modifier.fillMaxSize().padding(top = if (bunkLabel != null) 10.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onNameClick)) {
                    Text(last, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(first, fontSize = 9.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MarkButtons(status, compact = true, onSet = onMark)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = if (bunkLabel != null) 6.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).clickable(onClick = onNameClick), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(last, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = nameColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                    Text(first, fontSize = 9.5.sp, color = RC.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                }
                Spacer(Modifier.height(4.dp))
                MarkButtons(status, compact = false, onSet = onMark)
            }
        }
    }
}
