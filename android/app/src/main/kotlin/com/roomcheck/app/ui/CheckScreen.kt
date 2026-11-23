package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*
import com.roomcheck.app.startUpdate
import com.roomcheck.app.util.NightImage

@Composable
fun CheckScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val logic = vm.logic(state)
    val locked = vm.isLocked(state)
    val review = vm.showingReview(state)
    val noSeder = vm.noSeder(state)
    var showCalendar by remember { mutableStateOf(false) }
    var openPerson by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        TopBar(vm, state, locked, onCalendar = { showCalendar = true })
        UpdateBar(state.update) { startUpdate(context, vm) }

        if (!review) {
            if (searching) FindBar(vm, state, logic) { searching = false }
            else StickyControls(vm, state, logic) { searching = true }
        }

        Box(Modifier.weight(1f)) {
            when {
                noSeder != null -> NoSeder(noSeder)
                review -> ReviewScreen(vm, state, logic)
                else -> RoomsArea(vm, state, logic, onOpenPerson = { openPerson = it })
            }
        }

        if (noSeder == null) BottomBar(vm, state, logic, review)
    }

    if (showCalendar) {
        CalendarSheet(vm, state, onClose = { showCalendar = false })
    }
    openPerson?.let { pid ->
        PersonSheet(vm, state, pid, onClose = { openPerson = null })
    }
    state.highlight?.let { pid ->
        LaunchedEffect(pid) { kotlinx.coroutines.delay(3500); vm.clearHighlight() }
    }
    Toast(vm, state.toast)
}

/** A night with no seder: nothing to mark, only what the night is. Hebrew on a line of its own, so it reads the right way. */
@Composable
private fun NoSeder(why: Luach.NoSeder) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(why.en, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(why.he, fontSize = 22.sp, color = RC.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** What a change came to, over the bottom of the screen for a moment. */
@Composable
internal fun Toast(vm: AppViewModel, msg: String?) {
    msg ?: return
    LaunchedEffect(msg) { kotlinx.coroutines.delay(1700); vm.clearToast() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.padding(bottom = 90.dp).background(Color(0xF0121218).copy(alpha = 0.94f), RoundedCornerShape(10.dp)).padding(17.dp, 9.dp)
        ) { Text(msg, color = Color.White, fontSize = 13.5.sp) }
    }
}

/**
 * A newer build is out: one line under the date, with the button that takes it. It stays until
 * the update is in, so it can wait for after the bed check.
 */
@Composable
private fun UpdateBar(update: UpdateState, onUpdate: () -> Unit) {
    val (text, action) = when (update) {
        is UpdateState.Ready -> "Version ${update.release.version} is out" to "Update"
        is UpdateState.Downloading -> "Downloading ${update.release.version} · ${(update.fraction * 100).toInt()}%" to null
        is UpdateState.Installing -> "Installing - the app closes, then open it again" to null
        is UpdateState.Failed -> if (update.release == null) return else update.reason to "Try again"
        else -> return
    }
    Row(
        Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RC.blueL)
            .heightIn(min = 42.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = RC.blue, maxLines = 2, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onUpdate) { Text(action, fontWeight = FontWeight.Bold, color = RC.blue) }
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
            if (state.dateKey != vm.tonight()) {
                IconBtn(Icons.Filled.MyLocation, "Jump to tonight", small = true) { vm.goToday() }
            }
            if (!locked) IconBtn(Icons.Filled.Undo, "Undo") { vm.undo() }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconBtn(Icons.Filled.MoreVert, "More") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!locked) {
                        DropdownMenuItem(
                            text = { Text("Who's missing") },
                            onClick = { menu = false; vm.setTab(Tab.QUICK) }
                        )
                    }
                    // tucked in here rather than on the marking screen: most nights are the three
                    // standing rounds and never need it, but the night it does is this night
                    DropdownMenuItem(
                        text = { Text("Night settings") },
                        onClick = { menu = false; vm.setTab(Tab.TIMES) }
                    )
                    DropdownMenuItem(
                        text = { Text("Yeshiva calendar") },
                        onClick = { menu = false; vm.setTab(Tab.LUACH) }
                    )
                    DropdownMenuItem(
                        text = { Text("Seder Sichos") },
                        onClick = { menu = false; vm.openSichos(Tab.SICHOS) }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menu = false; vm.setTab(Tab.SETTINGS) }
                    )
                }
            }
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
private fun StickyControls(vm: AppViewModel, state: UiState, logic: NightLogic, onSearch: () -> Unit) {
    Column(Modifier.background(RC.bg).padding(12.dp, 10.dp, 12.dp, 8.dp)) {
        // Every tab as tall as the tallest, so one round with both counts stacked does not leave
        // the white of the tab you are on shorter or taller than the rest. Top-aligned, so the
        // times stay level whichever tab has the extra line.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(11.dp))
                .background(Color(0xFFE7E7EC)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            logic.slots.forEach { (sid, label) ->
                val on = sid == state.curSlot
                val st = logic.stats(sid)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable { vm.selectSlot(sid) }
                        .padding(2.dp, 6.dp, 2.dp, 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) RC.text else RC.sub2)
                    // out and in the dorm are two different things, so each gets its own line -
                    // neither stands in for the other
                    if (st.out > 0 || st.dorm > 0) {
                        if (st.out > 0) Text("${st.out} out", fontSize = 11.sp, lineHeight = 14.sp, color = RC.red, fontWeight = FontWeight.Bold)
                        if (st.dorm > 0) Text("${st.dorm} in dorm", fontSize = 11.sp, lineHeight = 14.sp, color = RC.amber, fontWeight = FontWeight.Bold)
                    } else {
                        val (txt, color) = when {
                            !st.started -> "—" to RC.sub
                            st.left == 0 -> "all in" to RC.green
                            else -> st.left.toString() + " left" to RC.sub
                        }
                        Text(txt, fontSize = 11.sp, lineHeight = 14.sp, color = color, fontWeight = if (st.left == 0) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val modeIcon = if (state.mode == RoomMode.ONE) Icons.Filled.GridView else Icons.Filled.CropSquare
            IconBtn(modeIcon, "Toggle room view") {
                vm.setMode(if (state.mode == RoomMode.ONE) RoomMode.SCROLL else RoomMode.ONE)
            }
            // One row per floor, rooms 1 to 3 on top. Every row is cut into as many
            // columns as the fullest floor has rooms, so a number sits in the same place on both.
            val cols = Roster.FLOORS.maxOf { it.size }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Roster.FLOORS.forEach { floor ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        floor.forEach { i ->
                            val room = Roster.PLAN[i]
                            val out = logic.outIn(room, state.curSlot).size
                            val dorm = logic.dormIn(room, state.curSlot).size
                            val checked = logic.roomChecked(room, state.curSlot)
                            val (bg, fg) = when {
                                i == state.room && state.mode == RoomMode.ONE -> RC.text to Color.White
                                out > 0 -> Color.White to Color(0xFF7A1A14)
                                dorm > 0 -> Color.White to RC.amber
                                checked -> RC.greenL to RC.green
                                else -> Color.White to RC.sub2
                            }
                            // Split by its marks: red across the share missing, amber across the share
                            // in the dorm, green across the share in, white across whoever is not
                            // marked yet. Nothing marked or everyone in stays one colour, as before.
                            val red = logic.missingShare(room, state.curSlot)
                            val amber = logic.dormShare(room, state.curSlot)
                            val green = logic.inShare(room, state.curSlot)
                            val split = !(i == state.room && state.mode == RoomMode.ONE) &&
                                (red > 0f || amber > 0f || green > 0f) && green < 1f
                            Box(
                                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp))
                                    .background(if (split) Color.White else bg)
                                    .then(if (split) Modifier.drawBehind {
                                        drawRect(Color(0xFFF4C7C3), size = size.copy(width = size.width * red))
                                        drawRect(Color(0xFFF6DDB2), topLeft = androidx.compose.ui.geometry.Offset(size.width * red, 0f),
                                            size = size.copy(width = size.width * amber))
                                        drawRect(Color(0xFFD3ECE1), topLeft = androidx.compose.ui.geometry.Offset(size.width * (red + amber), 0f),
                                            size = size.copy(width = size.width * green))
                                    } else Modifier)
                                    .border(1.dp, when {
                                        split && red > 0f -> Color(0xFFE8B9B4)
                                        split && amber > 0f -> Color(0xFFE8C88E)
                                        split -> Color(0xFFBCE0D2)
                                        bg == Color.White -> RC.sep
                                        else -> Color.Transparent
                                    }, RoundedCornerShape(9.dp))
                                    .clickable { vm.jumpRoom(i) },
                                contentAlignment = Alignment.Center
                            ) { Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg) }
                        }
                        repeat(cols - floor.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            IconBtn(Icons.Filled.Search, "Find someone", onClick = onSearch)
            IconBtn(Icons.Filled.Visibility, "Review") { vm.setReview(true) }
        }
    }
}

/**
 * A room's presets, one button each, under its name: tapped, its bochurim get their marks in the
 * round on screen. Green with a tick once that round already has them, so a glance says whether it
 * still needs the tap. Only on the room they are for - anywhere else they are just in the way.
 *
 * They wrap rather than scroll: with large text or Hebrew names they outgrow one line, and a button
 * scrolled off the edge of the screen is a button nobody knows is there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(vm: AppViewModel, state: UiState, logic: NightLogic, presets: List<Preset>) {
    val hebrew = state.settings.hebrewOnPlan
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            val done = logic.presetDone(preset, state.curSlot)
            Row(
                Modifier.height(34.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (done) RC.greenL else Color.White)
                    .border(1.dp, if (done) Color(0xFFBCE0D2) else RC.sep, RoundedCornerShape(9.dp))
                    .clickable { vm.applyPreset(preset, state.curSlot) }
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // each name its own Text and each mark its own icon - the same tick and cross as the
                // bed's buttons - so a Hebrew name cannot drag its mark to the wrong side of it
                logic.presetParts(preset, hebrew).forEachIndexed { i, (names, mark) ->
                    if (i > 0) Text("·", fontSize = 13.5.sp, color = RC.sub)
                    Text(names, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = RC.text, maxLines = 1)
                    when (mark) {
                        Mark.IN -> Icon(Icons.Filled.Check, "in", tint = RC.green, modifier = Modifier.size(17.dp))
                        Mark.DORM -> Icon(Icons.Filled.Home, "in the dorm", tint = RC.amber, modifier = Modifier.size(17.dp))
                        else -> Icon(Icons.Filled.Close, "out", tint = RC.red, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

/**
 * Type a name, tap it, and the plan jumps to that bed with the person ringed.
 *
 * It takes the room chips' place rather than sitting above them: this is for when you are holding
 * a name and do not know the room, which is exactly when a row of room numbers is no use anyway.
 */
@Composable
internal fun FindBar(
    vm: AppViewModel,
    state: UiState,
    logic: NightLogic,
    initialQuery: String = "",   // only ever set by the snapshot test, which cannot type
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val heb = state.settings.hebrewOnPlan

    val hits = if (query.isBlank()) emptyList() else Roster.PEOPLE.filter { p ->
        val q = query.trim()
        listOf(
            logic.first(p.id, heb), logic.last(p.id, heb),
            logic.first(p.id, false), logic.last(p.id, false)
        ).any { it.contains(q, ignoreCase = true) }
    }.take(6)

    Column(Modifier.background(RC.bg).padding(12.dp, 10.dp, 12.dp, 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Name") }, singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
            IconBtn(Icons.Filled.Close, "Close", onClick = onClose)
        }
        hits.forEach { p ->
            val room = Roster.roomOf[p.id]
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.White).border(1.dp, RC.sep, RoundedCornerShape(10.dp))
                    .clickable { vm.findPerson(p.id); onClose() }
                    .padding(13.dp, 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(logic.nameOf(p.id, heb), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    room?.let { if (heb) it.hebLabel else it.label } ?: "",
                    fontSize = 12.5.sp, color = RC.sub
                )
            }
        }
    }
}

@Composable
private fun RoomsArea(vm: AppViewModel, state: UiState, logic: NightLogic, onOpenPerson: (String) -> Unit) {
    // No bottom padding to clear the bottom bar: that bar is a sibling below this area, not an
    // overlay on top of it. The 90dp it used to reserve was carried over from the web build and
    // did nothing here but push the bottom bar off the bottom of the screen, unread.
    when (state.mode) {
        RoomMode.SCROLL -> LazyColumn(Modifier.fillMaxSize()) {
            item { OftenOutRow(vm, state, logic, onOpenPerson) }
            items(Roster.PLAN) { room -> RoomBlock(vm, state, logic, room, onOpenPerson) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        RoomMode.ONE -> {
            val room = Roster.PLAN[state.room]
            Column(
                Modifier.fillMaxSize()
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
                RoomHead(vm, state, logic, room)
                // The plan moves over for the box of bochurim often out of their rooms, which runs
                // down the side to the bottom bar and is the same on every room. Weighted, so the
                // plan gets the space that is LEFT after the next/previous row rather than however
                // tall its own shape wants to be: a Column measures an unweighted child with an
                // unbounded height, so the two deepest rooms used to draw themselves past the
                // bottom of the screen and take the nav buttons with them.
                Row(
                    Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        // a deep room fits the height and comes out narrower, centred in what it has
                        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.TopCenter) {
                            RoomPlan(state, logic, room, onOpenPerson, vm)
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NavButton(Modifier.weight(1f), enabled = state.room > 0, label = if (state.room > 0) roomName(state, state.room - 1) else "", back = true) { vm.goRoom(-1) }
                            NavButton(Modifier.weight(1f), enabled = state.room < Roster.PLAN.size - 1, label = if (state.room < Roster.PLAN.size - 1) roomName(state, state.room + 1) else "", back = false) { vm.goRoom(1) }
                        }
                    }
                    OftenOutColumn(vm, state, logic, onOpenPerson, Modifier.width(OFTEN_W).fillMaxHeight())
                }
            }
        }
    }
}

/** The width of the box of bochurim often out: three buttons of a bed's size, and a name over them. */
private val OFTEN_W = 88.dp

/**
 * The bochurim often out of their rooms, down the side of the plan: each with his buttons for the
 * round on screen, whichever room is on screen. They share the column's height, so on a phone all
 * of them show at once; on a short screen each keeps a card it can still be tapped in, and the
 * column scrolls.
 */
@Composable
private fun OftenOutColumn(vm: AppViewModel, state: UiState, logic: NightLogic, onOpenPerson: (String) -> Unit, modifier: Modifier) {
    val people = Roster.OFTEN_OUT
    BoxWithConstraints(modifier) {
        val gap = 3.dp
        val each = ((maxHeight - gap * (people.size - 1)) / people.size).coerceIn(44.dp, 54.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
            people.forEach { pid -> OftenFor(vm, state, logic, pid, onOpenPerson, Modifier.fillMaxWidth().height(each)) }
        }
    }
}

/** The same box across the top of the list of every room, four to a row, for when the rooms are shown one under another. */
@Composable
private fun OftenOutRow(vm: AppViewModel, state: UiState, logic: NightLogic, onOpenPerson: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Roster.OFTEN_OUT.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { pid -> OftenFor(vm, state, logic, pid, onOpenPerson, Modifier.weight(1f).height(50.dp)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OftenFor(vm: AppViewModel, state: UiState, logic: NightLogic, pid: String, onOpenPerson: (String) -> Unit, modifier: Modifier) {
    OftenCard(
        name = logic.shortName(pid, state.settings.hebrewOnPlan),
        status = logic.statusOf(pid, state.curSlot),
        onNameClick = { onOpenPerson(pid) },
        onMark = { mark -> vm.setMark(pid, state.curSlot, mark) },
        modifier = modifier
    )
}

private fun roomName(state: UiState, i: Int) =
    Roster.PLAN[i].let { if (state.settings.hebrewOnPlan) it.hebLabel else it.label }

@Composable
private fun NavButton(modifier: Modifier, enabled: Boolean, label: String, back: Boolean, onClick: () -> Unit) {
    // At the first and last room the button has nowhere to go, so it holds its place as blank
    // space rather than an empty white box - an unlabelled button reads as something broken.
    if (!enabled) {
        Box(modifier.height(48.dp))
        return
    }
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(11.dp)).background(Color.White)
            .border(1.dp, RC.sep, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // The chevron is its own Text, not part of the label: inside one string a Hebrew room
        // name drags it to the wrong end of the button.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (back) Text("\u2039", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RC.blue)
            Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RC.blue)
            if (!back) Text("\u203A", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RC.blue)
        }
    }
}

/** A room in the list of every room: its name and buttons, then its plan the full width. */
@Composable
private fun RoomBlock(
    vm: AppViewModel,
    state: UiState,
    logic: NightLogic,
    room: Room,
    onOpenPerson: (String) -> Unit
) {
    Column(Modifier.padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RoomHead(vm, state, logic, room)
        RoomPlan(state, logic, room, onOpenPerson, vm, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    }
}

/** A room's name, how it stands this round, its All in / All out / Clear, and its presets under them. */
@Composable
private fun RoomHead(vm: AppViewModel, state: UiState, logic: NightLogic, room: Room) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (state.settings.hebrewOnPlan) room.hebLabel else room.label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                val out = logic.outIn(room, state.curSlot).size
                val dorm = logic.dormIn(room, state.curSlot).size
                val beds = room.beds.sumOf { it.slots.size }
                // each count in its own colour: out in red, in the dorm in amber
                val parts = buildList {
                    if (out > 0) add("$out out" to RC.red)
                    if (dorm > 0) add("$dorm in dorm" to RC.amber)
                }
                if (parts.isEmpty()) {
                    Text("$beds beds", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RC.sub, modifier = Modifier.padding(start = 8.dp))
                } else parts.forEachIndexed { i, (text, color) ->
                    if (i > 0) Text("·", fontSize = 13.sp, color = RC.sub, modifier = Modifier.padding(start = 5.dp))
                    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(start = if (i > 0) 5.dp else 8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("All in" to Mark.IN, "All out" to Mark.OUT, "Clear" to null).forEach { (text, value) ->
                    Text(
                        text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RC.blue,
                        modifier = Modifier.clickable { vm.setRoom(room, state.curSlot, value) }
                    )
                }
            }
        }
        Roster.presetsIn(room).takeIf { it.isNotEmpty() }?.let { PresetRow(vm, state, logic, it) }
    }
}

/**
 * A room's plan with everyone in his bed. Left to size itself by its [modifier]: the list of rooms
 * pins it to the full width, and one room on its own hands it a box and lets it fit - aspectRatio
 * can only shrink to the height on offer when the width is a maximum rather than an exact size, so
 * a deep room comes out narrower instead of running off the bottom.
 */
@Composable
private fun RoomPlan(state: UiState, logic: NightLogic, room: Room, onOpenPerson: (String) -> Unit, vm: AppViewModel, modifier: Modifier = Modifier) {
    FloorPlanCanvas(
        room = room,
        allIn = logic.roomAllIn(room, state.curSlot),
        modifier = modifier
    ) { _, pid, bunkLabel, wideCard ->
        PersonSlot(
            first = logic.first(pid, state.settings.hebrewOnPlan),
            last = logic.last(pid, state.settings.hebrewOnPlan),
            bunkLabel = bunkLabel.takeIf { state.settings.bunkLabels },
            status = logic.statusOf(pid, state.curSlot),
            highlighted = pid == state.highlight,
            row = wideCard,
            onNameClick = { onOpenPerson(pid) },
            onMark = { mark -> vm.setMark(pid, state.curSlot, mark) }
        )
    }
}

@Composable
private fun BottomBar(vm: AppViewModel, state: UiState, logic: NightLogic, review: Boolean) {
    val st = logic.stats(state.curSlot)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Stacked, not one row: three buttons and the running count side by side left the count
    // squeezed to an ellipsis. The count reads on its own line, the buttons share the next.
    Column(
        Modifier.fillMaxWidth().background(Color(0xF0F2F2F7)).padding(12.dp, 8.dp, 12.dp, 10.dp)
    ) {
        val label = logic.slots.firstOrNull { it.id == state.curSlot }?.label ?: state.curSlot
        val bits = mutableListOf(if (st.out > 0) "${st.out} out" else "Nobody out")
        if (st.dorm > 0) bits.add("${st.dorm} in dorm")
        if (st.left > 0) bits.add("${st.left} left")
        if (st.away > 0) bits.add("${st.away} not in town")
        if (st.exc > 0) bits.add("${st.exc} excused")
        Text(
            "$label · ${bits.joinToString(" · ")}", fontSize = 13.sp, color = RC.sub2,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!review) {
                BarButton(Modifier.weight(1f), "Save", RC.blue) { vm.closeNight() }
            }
            BarButton(Modifier.weight(1f), "Copy text", RC.text) {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(logic.report(state.dateKey)))
                vm.toast("Copied")
            }
            BarButton(Modifier.weight(1f), "Send image", RC.text) {
                NightImage.share(context, logic, state.dateKey, state.settings.hebrewInExport, Slots.forSheet(state.night, state.dateKey))
            }
        }
    }
}

@Composable
private fun BarButton(modifier: Modifier, label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(10.dp)
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
}
