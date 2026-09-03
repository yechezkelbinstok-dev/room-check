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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.Bed
import com.roomcheck.app.data.Door
import com.roomcheck.app.data.DoorWall
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.Room

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
private fun DrawScope.drawRoomWalls(
    door: Door,
    roomH: Float,
    ix: (Float) -> Float,
    iy: (Float) -> Float,
    color: Color
) {
    val left = ix(0f); val right = ix(100f)
    val top = iy(0f); val bottom = iy(roomH)
    val strokeWidth = 1.6f * (size.width / (100f + 2 * MARGIN))
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
    // The canvas is the room plus a MARGIN of outside floor all round, so a door box has somewhere
    // to sit. Both axes are divided by that same total, which is what makes one unit across equal
    // one unit down: a bed turned 90 degrees then measures exactly as long as it did upright.
    val totalW = 100f + 2 * MARGIN
    val totalH = room.h + 2 * MARGIN
    BoxWithConstraints(
        modifier.aspectRatio(totalW / totalH).clip(RoundedCornerShape(4.dp)).background(bg)
    ) {
        val wPx = maxWidth
        val hPx = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawRoomWalls(
                room.door,
                roomH = room.h,
                ix = { v -> size.width * ((MARGIN + v) / totalW) },
                iy = { v -> size.height * ((MARGIN + v) / totalH) },
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
                        x = wPx * ((MARGIN + bed.x) / totalW),
                        y = hPx * ((MARGIN + bed.y) / totalH)
                    )
                    .size(
                        width = wPx * (bed.w / totalW),
                        height = hPx * (bed.h / totalH)
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

/**
 * The three mark buttons, sized to the space they actually get - on BOTH axes. Measuring width
 * alone was the bug behind the squashed, pill-shaped buttons with a half-cut "E": Modifier.size
 * asks for a square but is still clamped by the incoming height constraint, so a button starved
 * of height silently flattened instead of shrinking. Taking the smaller of the two keeps every
 * button square, whatever room it lands in.
 */
@Composable
fun MarkButtons(
    status: Mark?,
    compact: Boolean,
    onSet: (Mark) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val gap = 3.dp
        val preferred = if (compact) 26.dp else 30.dp
        val byWidth = (maxWidth - gap * 2) / 3
        val byHeight = if (maxHeight == Dp.Infinity) preferred else maxHeight
        val size = minOf(byWidth, byHeight, preferred).coerceAtLeast(15.dp)
        MarkButtonRow(status, size, gap, onSet)
    }
}

@Composable
private fun MarkButtonRow(status: Mark?, size: Dp, gap: Dp, onSet: (Mark) -> Unit) {
    val glyph = size * 0.5f                      // icon scales with the button, never overflows it
    val letter = (size.value * 0.38f).coerceAtLeast(8f).sp
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        MarkButton(size, status == Mark.IN, RC.green, { onSet(Mark.IN) }) {
            Icon(Icons.Filled.Check, contentDescription = "Here", modifier = Modifier.size(glyph))
        }
        MarkButton(size, status == Mark.OUT, RC.red, { onSet(Mark.OUT) }) {
            Icon(Icons.Filled.Close, contentDescription = "Not here", modifier = Modifier.size(glyph))
        }
        MarkButton(size, status == Mark.EXC, RC.grey, { onSet(Mark.EXC) }) {
            Text("E", fontWeight = FontWeight.Bold, fontSize = letter)
        }
    }
}

@Composable
private fun MarkButton(size: Dp, on: Boolean, onColor: Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
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
    Column(
        modifier.fillMaxSize().background(bgColor).padding(6.dp, 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (row) {
            // Wide slot: the bunk label rides inline at the left, because half a bed's height has
            // no room to spend on a whole extra line.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                bunkLabel?.let {
                    Text(
                        it, fontSize = 9.sp, lineHeight = 10.sp, color = RC.bunk, maxLines = 1,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Column(Modifier.weight(1f).clickable(onClick = onNameClick)) {
                    Text(last, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(first, fontSize = 9.sp, lineHeight = 11.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MarkButtons(status, compact = true, onSet = onMark)
            }
        } else {
            // Tall slot. Order here is load-bearing: a Column measures its UNWEIGHTED children
            // first and hands each one the height still unspent, so the buttons - unweighted -
            // are served before the names and always get their full square. The names take the
            // weighted remainder, which means a long name shortens itself rather than crushing
            // the buttons. Written the other way round (names first, buttons last) the buttons
            // got whatever scraps were left, which is exactly how they ended up as clipped pills.
            // fill = false so the name block claims only the height it needs: in a long single
            // bed that keeps the name and its buttons together in the middle of the mattress
            // instead of stranding them at opposite ends of the card.
            bunkLabel?.let {
                Text(
                    it, fontSize = 9.sp, lineHeight = 10.sp, color = RC.bunk,
                    modifier = Modifier.align(Alignment.Start)
                )
            }
            Box(
                Modifier.weight(1f, fill = false).fillMaxWidth().clickable(onClick = onNameClick),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // a long surname in a narrow bed (Heidingsfeld, Levitansky) may take two lines
                    Text(last, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = nameColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(first, fontSize = 9.5.sp, lineHeight = 12.sp, color = RC.sub, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(4.dp))
            MarkButtons(status, compact = false, onSet = onMark)
        }
    }
}
