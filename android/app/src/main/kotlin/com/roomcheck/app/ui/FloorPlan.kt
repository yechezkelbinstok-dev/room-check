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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
private const val MARGIN = 5f   // space outside the walls so a door box can sit outside, per the sketch
private const val GAP = 7f      // half the doorway width, in room-interior units
private const val DOOR_D = 4f   // how far the three-sided door box juts out past the wall

/**
 * Walls plus a doorway drawn the way the sketch draws it: a gap in the wall with a small
 * three-sided box sitting just outside it. No swing arc.
 *
 * Coordinates coming in are room-interior units (0-100 x 0-112); [ix]/[iy] map those onto the
 * inset wall rectangle so the door boxes have somewhere to go outside the walls.
 */
private fun DrawScope.drawRoomWalls(door: Door, ix: (Float) -> Float, iy: (Float) -> Float, color: Color) {
    val left = ix(0f); val right = ix(100f)
    val top = iy(0f); val bottom = iy(PLAN_H)
    val strokeWidth = with(size) { 1.6f * ((width / 100f + height / PLAN_H) / 2f) }
    val a: Float; val b: Float
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Square)

    // walls without the door run their full length; the door's wall is split around the gap
    if (door.wall == DoorWall.TOP || door.wall == DoorWall.BOTTOM) {
        a = ix(door.pos - GAP); b = ix(door.pos + GAP)
    } else {
        a = iy(door.pos - GAP); b = iy(door.pos + GAP)
    }
    val depthX = ix(DOOR_D) - ix(0f)
    val depthY = iy(DOOR_D) - iy(0f)

    if (door.wall != DoorWall.TOP) line(left, top, right, top)
    if (door.wall != DoorWall.BOTTOM) line(left, bottom, right, bottom)
    if (door.wall != DoorWall.LEFT) line(left, top, left, bottom)
    if (door.wall != DoorWall.RIGHT) line(right, top, right, bottom)

    when (door.wall) {
        DoorWall.BOTTOM -> {
            line(left, bottom, a, bottom); line(b, bottom, right, bottom)
            val out = bottom + depthY
            line(a, bottom, a, out); line(a, out, b, out); line(b, out, b, bottom)
        }
        DoorWall.TOP -> {
            line(left, top, a, top); line(b, top, right, top)
            val out = top - depthY
            line(a, top, a, out); line(a, out, b, out); line(b, out, b, top)
        }
        DoorWall.LEFT -> {
            line(left, top, left, a); line(left, b, left, bottom)
            val out = left - depthX
            line(left, a, out, a); line(out, a, out, b); line(out, b, left, b)
        }
        DoorWall.RIGHT -> {
            line(right, top, right, a); line(right, b, right, bottom)
            val out = right + depthX
            line(right, a, out, a); line(out, a, out, b); line(out, b, right, b)
        }
    }
}

/**
 * Draws one room's floor plan from the sketch: walls with a three-sided doorway, and every bed
 * where the sketch puts it. Bed coordinates are room-interior units, mapped inside the walls so
 * the door box has room to sit outside them. Each slot is rendered via [slotContent], which is
 * told whether that slot is wide enough for the side-by-side card layout.
 */
@Composable
fun FloorPlanCanvas(
    room: Room,
    allIn: Boolean,
    modifier: Modifier = Modifier,
    slotContent: @Composable (bed: Bed, pid: String, bunkLabel: String?, wideCard: Boolean) -> Unit
) {
    val bg = if (allIn) Color(0xFFF7FCFA) else RC.floor
    // room interior sits inside a margin, leaving space outside the walls for the door box
    val spanX = (100f - 2 * MARGIN) / 100f
    val spanY = (PLAN_H - 2 * MARGIN) / PLAN_H
    val originX = MARGIN / 100f
    val originY = MARGIN / PLAN_H
    BoxWithConstraints(
        modifier.aspectRatio(100f / PLAN_H).clip(RoundedCornerShape(4.dp)).background(bg)
    ) {
        val wPx = maxWidth
        val hPx = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawRoomWalls(
                room.door,
                ix = { v -> size.width * (originX + (v / 100f) * spanX) },
                iy = { v -> size.height * (originY + (v / PLAN_H) * spanY) },
                color = RC.wall
            )
        }
        room.beds.forEach { bed ->
            // A bunk always stacks its people top/bottom - never splits the width - so each person
            // keeps the bed's full width. Whether a slot uses the wide (name beside buttons) or tall
            // (name above buttons) card follows the slot's real shape, so neither one gets squeezed.
            val slotCount = bed.slots.size
            val wideCard = bed.w > (bed.h / slotCount) * 1.4f
            Box(
                Modifier
                    .offset(
                        x = wPx * (originX + (bed.x / 100f) * spanX),
                        y = hPx * (originY + (bed.y / PLAN_H) * spanY)
                    )
                    .size(
                        width = wPx * (bed.w / 100f) * spanX,
                        height = hPx * (bed.h / PLAN_H) * spanY
                    )
                    .background(Color.White, RoundedCornerShape(5.dp))
                    .border(1.5.dp, Color(0xFFC9CBD2), RoundedCornerShape(5.dp))
            ) {
                if (slotCount > 1) {
                    Column(Modifier.fillMaxSize()) {
                        bed.slots.forEachIndexed { i, pid ->
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                slotContent(bed, pid, if (i == 0) "top" else "bottom", wideCard)
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        slotContent(bed, bed.slots[0], null, wideCard)
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
                    // a long surname in a narrow bed (Heidingsfeld, Levitansky) may take two lines;
                    // those beds are tall enough for it, and the first name stays capped at one
                    Text(last, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = nameColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(first, fontSize = 9.5.sp, color = RC.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                MarkButtons(status, compact = false, onSet = onMark)
            }
        }
    }
}
