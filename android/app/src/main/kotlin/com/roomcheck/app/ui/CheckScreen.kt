package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*

@Composable
fun CheckScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val logic = vm.logic(state)
    val locked = vm.isLocked(state)
    val review = vm.showingReview(state)
    var showCalendar by remember { mutableStateOf(false) }
    var openPerson by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        TopBar(vm, state, locked, onCalendar = { showCalendar = true })

        if (!review) {
            StickyControls(vm, state, logic)
        }

        Box(Modifier.weight(1f)) {
            when {
                review -> ReviewScreen(vm, state, logic)
                else -> RoomsArea(vm, state, logic, onOpenPerson = { openPerson = it })
            }
        }

        BottomBar(vm, state, logic, review)
    }

    if (showCalendar) {
        CalendarSheet(vm, state, onClose = { showCalendar = false })
    }
    openPerson?.let { pid ->
        PersonSheet(vm, state, pid, onClose = { openPerson = null })
    }
    state.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(1700); vm.clearToast() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.padding(bottom = 90.dp).background(Color(0xF0121218).copy(alpha = 0.94f), RoundedCornerShape(10.dp)).padding(17.dp, 9.dp)
            ) { Text(msg, color = Color.White, fontSize = 13.5.sp) }
        }
    }
}

@Composable
private fun TopBar(vm: AppViewModel, state: UiState, locked: Boolean, onCalendar: () -> Unit) {
    Column(Modifier.padding(16.dp, 13.dp, 16.dp, 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBtn(Icons.Filled.ChevronLeft, "Previous night") { vm.shiftDay(-1) }
            Column(
                Modifier.weight(1f).clickable(onClick = onCalendar),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(Dates.hebrewDate(state.dateKey), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Dates.longDate(state.dateKey), fontSize = 11.5.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(Icons.Filled.ChevronRight, "Next night") { vm.shiftDay(1) }
            if (state.dateKey != Dates.tonightKey()) {
                IconBtn(Icons.Filled.MyLocation, "Jump to tonight", small = true) { vm.goToday() }
            }
            if (!locked) IconBtn(Icons.Filled.Undo, "Undo") { vm.undo() }
            IconBtn(Icons.Filled.MoreVert, "More") { vm.setTab(Tab.NAMES) }
        }
        Text(savedText(), fontSize = 10.5.sp, color = RC.sub, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

private fun savedText(): String = "saved" // native app: writes are synchronous to disk, always current

@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, small: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
            .border(1.dp, RC.sep, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = RC.blue, modifier = Modifier.size(if (small) 16.dp else 18.dp)) }
}

@Composable
private fun StickyControls(vm: AppViewModel, state: UiState, logic: NightLogic) {
    Column(Modifier.background(RC.bg).padding(12.dp, 10.dp, 12.dp, 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFE7E7EC)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Roster.SLOTS.forEach { (sid, label) ->
                val on = sid == state.curSlot
                val st = logic.stats(sid)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable { vm.selectSlot(sid) }
                        .padding(2.dp, 6.dp, 2.dp, 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) RC.text else RC.sub2)
                    val (txt, color) = when {
                        st.out > 0 -> st.out.toString() + " out" to RC.red
                        !st.started -> "—" to RC.sub
                        st.left == 0 -> "all in" to RC.green
                        else -> st.left.toString() + " left" to RC.sub
                    }
                    Text(txt, fontSize = 11.sp, color = color, fontWeight = if (st.out > 0 || st.left == 0) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val modeIcon = if (state.mode == RoomMode.ONE) Icons.Filled.GridView else Icons.Filled.CropSquare
            IconBtn(modeIcon, "Toggle room view") {
                vm.setMode(if (state.mode == RoomMode.ONE) RoomMode.SCROLL else RoomMode.ONE)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Roster.PLAN.forEachIndexed { i, room ->
                    val out = logic.outIn(room, state.curSlot).size
                    val checked = logic.roomChecked(room, state.curSlot)
                    val (bg, fg) = when {
                        i == state.room && state.mode == RoomMode.ONE -> RC.text to Color.White
                        out > 0 -> RC.redL to RC.red
                        checked -> RC.greenL to RC.green
                        else -> Color.White to RC.sub2
                    }
                    Box(
                        Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp))
                            .background(bg).border(1.dp, if (bg == Color.White) RC.sep else Color.Transparent, RoundedCornerShape(9.dp))
                            .clickable { vm.jumpRoom(i) },
                        contentAlignment = Alignment.Center
                    ) { Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg) }
                }
            }
            IconBtn(Icons.Filled.Visibility, "Review") { vm.setReview(true) }
        }
    }
}

@Composable
private fun RoomsArea(vm: AppViewModel, state: UiState, logic: NightLogic, onOpenPerson: (String) -> Unit) {
    when (state.mode) {
        RoomMode.SCROLL -> LazyColumn(Modifier.fillMaxSize().padding(bottom = 90.dp)) {
            items(Roster.PLAN) { room -> RoomBlock(vm, state, logic, room, onOpenPerson) }
            item { FinNote() }
        }
        RoomMode.ONE -> {
            val room = Roster.PLAN[state.room]
            Column(
                Modifier.fillMaxSize().padding(bottom = 90.dp)
                    .pointerInput(state.room) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragX > 55) vm.goRoom(-1) else if (dragX < -55) vm.goRoom(1)
                                dragX = 0f
                            }
                        ) { _, dragAmount -> dragX += dragAmount }
                    }
            ) {
                RoomBlock(vm, state, logic, room, onOpenPerson)
                Text(
                    "Doorway is on the wall you walk in from. Swipe the plan for the next room.",
                    fontSize = 11.5.sp, color = RC.sub, modifier = Modifier.padding(16.dp, 9.dp, 16.dp, 0.dp)
                )
                Row(Modifier.padding(12.dp, 12.dp, 12.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavButton(Modifier.weight(1f), enabled = state.room > 0, label = if (state.room > 0) "‹ ${Roster.PLAN[state.room - 1].label}" else "") { vm.goRoom(-1) }
                    NavButton(Modifier.weight(1f), enabled = state.room < Roster.PLAN.size - 1, label = if (state.room < Roster.PLAN.size - 1) "${Roster.PLAN[state.room + 1].label} ›" else "") { vm.goRoom(1) }
                }
                FinNote()
            }
        }
    }
}

@Composable
private fun NavButton(modifier: Modifier, enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(11.dp)).background(Color.White)
            .border(1.dp, RC.sep, RoundedCornerShape(11.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) RC.blue else RC.sub.copy(alpha = 0.5f)) }
}

@Composable
private fun FinNote() {
    Text(
        "Locks tonight to a read-only record — tap Finish below. It closes on its own at 6am anyway.",
        fontSize = 11.5.sp, color = RC.sub, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(10.dp, 7.dp, 10.dp, 16.dp)
    )
}

@Composable
private fun RoomBlock(vm: AppViewModel, state: UiState, logic: NightLogic, room: Room, onOpenPerson: (String) -> Unit) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(room.label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                val out = logic.outIn(room, state.curSlot).size
                val beds = room.beds.sumOf { it.slots.size }
                Text(
                    if (out > 0) "$out out" else "$beds beds",
                    fontSize = 13.sp, fontWeight = if (out > 0) FontWeight.Bold else FontWeight.Medium,
                    color = if (out > 0) RC.red else RC.sub, modifier = Modifier.padding(start = 8.dp)
                )
            }
            val allIn = logic.roomAllIn(room, state.curSlot)
            Text(
                if (allIn) "Clear room" else "Everyone in",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RC.blue,
                modifier = Modifier.clickable { vm.markRoom(room, state.curSlot) }
            )
        }
        FloorPlanCanvas(
            room = room,
            allIn = logic.roomAllIn(room, state.curSlot),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) { _, pid, bunkLabel, wideCard ->
            PersonSlot(
                first = logic.first(pid),
                last = logic.last(pid),
                bunkLabel = bunkLabel,
                status = logic.statusOf(pid, state.curSlot),
                row = wideCard,
                onNameClick = { onOpenPerson(pid) },
                onMark = { mark -> vm.setMark(pid, state.curSlot, mark) }
            )
        }
    }
}

@Composable
private fun BottomBar(vm: AppViewModel, state: UiState, logic: NightLogic, review: Boolean) {
    val st = logic.stats(state.curSlot)
    Row(
        Modifier.fillMaxWidth().background(Color(0xF0F2F2F7)).padding(12.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val label = Roster.SLOTS.first { it.first == state.curSlot }.second
        val bits = mutableListOf(if (st.out > 0) "${st.out} out" else "Nobody out")
        if (st.left > 0) bits.add("${st.left} left")
        if (st.exc > 0) bits.add("${st.exc} excused")
        Text(
            "$label · ${bits.joinToString(" · ")}", fontSize = 13.sp, color = RC.sub2,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (!review) {
            Button(
                onClick = { vm.closeNight() },
                colors = ButtonDefaults.buttonColors(containerColor = RC.blue),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Finish", fontWeight = FontWeight.Bold) }
        }
        val clipboard = LocalClipboardManager.current
        Button(
            onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(logic.report(state.dateKey)))
                vm.toast("Copied")
            },
            colors = ButtonDefaults.buttonColors(containerColor = RC.text),
            shape = RoundedCornerShape(10.dp)
        ) { Text("Copy", fontWeight = FontWeight.Bold) }
    }
}
