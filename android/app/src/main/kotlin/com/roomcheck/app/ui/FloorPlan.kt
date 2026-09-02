package com.roomcheck.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.Bed
import com.roomcheck.app.data.Door
import com.roomcheck.app.data.DoorWall
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Room

private const val PLAN_H = 112f // room aspect: 100 wide x 112 tall, matches the web app's viewBox
private const val GAP = 6f          // half the doorway width, in the same 0-100/0-112 units as beds
private const val DOOR_R = GAP * 2  // door-swing radius: full doorway width

/**
 * Walls with a doorway gap and a swing arc, in the same 100x112 percentage coordinate system as
 * beds. The arc's start/end angles were solved and verified independently (not guessed) for each
 * wall - see the derivation this mirrors: hinge = the gap edge nearer the room corner the door
 * swings away from; the arc spans exactly 90 degrees between the along-the-wall point and the
 * point straight out from the hinge at radius DOOR_R.
 */
private fun DrawScope.drawRoomWalls(door: Door, sx: Float, sy: Float, color: Color) {
    val inset = 0.8f
    val left = inset; val top = inset
    val right = 100f - inset; val bottom = PLAN_H - inset
    val pos = door.pos
    val a = pos - GAP
    val b = pos + GAP
    val strokeWidth = 1.6f * ((sx + sy) / 2f)
    fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Square)

    // the three walls without a door, or the full run when the door isn't on that wall
    if (door.wall != DoorWall.TOP) line(left, top, right, top)
    if (door.wall != DoorWall.BOTTOM) line(left, bottom, right, bottom)
    if (door.wall != DoorWall.LEFT) line(left, top, left, bottom)
    if (door.wall != DoorWall.RIGHT) line(right, top, right, bottom)

    val geom = when (door.wall) {
        DoorWall.BOTTOM -> {
            line(left, bottom, a, bottom); line(b, bottom, right, bottom)
            DoorGeom(a, bottom, 0f, -90f, a, bottom - DOOR_R)
        }
        DoorWall.TOP -> {
            line(left, top, a, top); line(b, top, right, top)
            DoorGeom(a, top, 0f, 90f, a, top + DOOR_R)
        }
        DoorWall.LEFT -> {
            line(left, top, left, a); line(left, b, left, bottom)
            DoorGeom(left, a, 90f, -90f, left + DOOR_R, a)
        }
        DoorWall.RIGHT -> {
            line(right, top, right, a); line(right, b, right, bottom)
            DoorGeom(right, a, 90f, 90f, right - DOOR_R, a)
        }
    }

    val diameter = DOOR_R * 2
    drawArc(
        color = RC.swing,
        startAngle = geom.startAngle,
        sweepAngle = geom.sweepAngle,
        useCenter = false,
        topLeft = Offset((geom.hingeX - DOOR_R) * sx, (geom.hingeY - DOOR_R) * sy),
        size = Size(diameter * sx, diameter * sy),
        style = Stroke(width = 0.8f * ((sx + sy) / 2f))
    )
    drawLine(RC.swing, p(geom.hingeX, geom.hingeY), p(geom.leafX, geom.leafY), strokeWidth = 0.8f * ((sx + sy) / 2f))
}

private data class DoorGeom(
    val hingeX: Float, val hingeY: Float,
    val startAngle: Float, val sweepAngle: Float,
    val leafX: Float, val leafY: Float
)

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
    ) {
        val wPx = maxWidth
        val hPx = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawRoomWalls(room.door, size.width / 100f, size.height / PLAN_H, RC.wall)
        }
        room.beds.forEach { bed ->
            Box(
                Modifier
                    .offset(x = wPx * (bed.x / 100f), y = hPx * (bed.y / PLAN_H))
                    .size(width = wPx * (bed.w / 100f), height = hPx * (bed.h / PLAN_H))
                    .background(Color.White, RoundedCornerShape(5.dp))
                    .border(1.5.dp, Color(0xFFC9CBD2), RoundedCornerShape(5.dp))
            ) {
                // A bunk (2+ people in one bed) always stacks top/bottom - never splits the width -
                // so each occupant's name+buttons keeps the bed's full width. Splitting side-by-side
                // is what silently squeezed names down to nothing before. Only a single occupant's
                // card orientation follows the bed's own row/column shape.
                if (bed.slots.size > 1) {
                    Column(Modifier.fillMaxSize()) {
                        bed.slots.forEachIndexed { i, pid ->
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                slotContent(bed, pid, if (i == 0) "top" else "bottom")
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        slotContent(bed, bed.slots[0], null)
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
    // No weight() in this vertical stack: a Column asked to fill a height that isn't reliably
    // bounded can silently collapse a weighted child to zero - that's what was swallowing the
    // mark buttons. Arrangement.Center groups [label?, content] as a block without needing weight
    // at all. Name text is capped to one line with ellipsis so a long name can never grow enough
    // to threaten the buttons' space - the bunk label sits as a normal line above it, in-flow, so
    // it can't overlap the name either.
    Column(
        modifier.fillMaxSize().background(bgColor).padding(6.dp, 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        bunkLabel?.let {
            Text(
                it, fontSize = 9.sp, color = Color(0xFFB7B9C2),
                modifier = Modifier.align(Alignment.Start).padding(bottom = 2.dp)
            )
        }
        if (row) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onNameClick)) {
                    Text(last, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(first, fontSize = 9.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MarkButtons(status, compact = true, onSet = onMark)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.clickable(onClick = onNameClick), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(last, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = nameColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(first, fontSize = 9.5.sp, color = RC.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                MarkButtons(status, compact = false, onSet = onMark)
            }
        }
    }
}
