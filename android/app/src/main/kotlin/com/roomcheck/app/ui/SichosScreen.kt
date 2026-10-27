package com.roomcheck.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private fun dayText(d: LocalDate): String = d.format(DAY)
private fun hebDay(d: LocalDate): String = Dates.hebrewOf(d).let { Dates.hebrewDayMonthOf(it.year, it.month, it.day) }
private fun roundDays(book: Sichos, round: Int) = "${dayText(book.roundStart(round))} – ${dayText(book.roundEnd(round))}"
private fun entriesText(n: Int) = if (n == 1) "1 entry" else "$n entries"

/** Everyone in the roster, the way a list of names is read: by surname. */
private fun byName(logic: NightLogic): List<String> =
    Roster.PEOPLE.map { it.id }.sortedWith(compareBy({ logic.last(it) }, { logic.first(it) }))

/**
 * Seder Sichos: who handed in a sikkum on each week's sicha, a round of four weeks at a time, and
 * the round's raffle. A tap on a name marks the week picked above for him - the bochur standing
 * there with one in his hand is what this is for - and tapping again takes it off. Holding a name,
 * or its ⋯, opens his round: every week of it, and which ones you have reviewed.
 */
@Composable
fun SichosScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val book = vm.sichos(state)
    val logic = vm.logic(state)
    var openPid by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var movingStart by remember { mutableStateOf(false) }
    BackTo(vm, Tab.CHECK)

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Header(
            "Seder Sichos", "", "Done",
            onChangeStart = if (book.start != null) ({ movingStart = true }) else null
        ) { vm.setTab(Tab.CHECK) }
        if (book.start == null) {
            StartCard(vm.today()) { vm.setSichosStart(it) }
            return@Column
        }
        val round = vm.sichosRound(state)
        val week = vm.sichosWeek(state)
        val current = book.roundOf(vm.today())
        RoundBar(book, round, current, onRound = vm::showSichosRound)
        WeekPicker(book, round, week, thisWeek = if (round == current) book.weekOf(vm.today()) else null, onPick = vm::pickSichosWeek)
        FindField(query) { query = it }

        val q = query.trim()
        val pids = byName(logic).filter {
            q.isEmpty() || logic.nameOf(it).contains(q, ignoreCase = true) || logic.nameOf(it, hebrew = true).contains(q)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item {
                Card {
                    pids.forEachIndexed { i, pid ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        BochurRow(
                            first = logic.first(pid), last = logic.last(pid), weeks = (0 until Sichos.WEEKS).map { book.valueAt(pid, round, it) },
                            week = week, entries = book.entries(pid, round),
                            onTap = { vm.toggleSikkum(pid, round, week) }, onOpen = { openPid = pid }
                        )
                    }
                    if (pids.isEmpty()) Text("No match", fontSize = 14.sp, color = RC.sub, modifier = Modifier.padding(14.dp))
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        RaffleBar(book, round, logic) { vm.setTab(Tab.RAFFLE) }
    }

    openPid?.let { pid ->
        BochurSheet(vm, book, logic, pid, vm.sichosRound(state), onClose = { openPid = null })
    }
    book.start?.let { start ->
        // every round moves with it, each sikkum staying in the week it was marked for
        if (movingStart) DayPicker(start, onPick = {
            vm.setSichosStart(it)
            vm.toast("Round 1 now starts ${dayText(it)}")
        }, onClose = { movingStart = false })
    }
    Toast(vm, state.toast)
}

@Composable
private fun Header(title: String, subtitle: String, button: String, onChangeStart: (() -> Unit)? = null, onButton: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 13.dp, if (onChangeStart != null) 4.dp else 16.dp, 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.5.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = onButton) { Text(button) }
        if (onChangeStart != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = RC.sub2) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Change the start day") }, onClick = { menu = false; onChangeStart() })
                }
            }
        }
    }
}

/** The phone's back gesture goes where the screen's own button does, rather than out of the app. */
@Composable
private fun BackTo(vm: AppViewModel, tab: Tab) {
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler { vm.setTab(tab) }
}

/**
 * The one question before anything else: the day the first round started. Any day will do - an
 * earlier one too, so sikumim already handed in on paper can go in as well.
 */
@Composable
private fun StartCard(today: LocalDate, onStart: (LocalDate) -> Unit) {
    var day by remember { mutableStateOf(today) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(16.dp)) {
        Text("First round starts", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                Text("${day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))} · ${hebDay(day)}", maxLines = 1)
            }
            Button(onClick = { onStart(day) }) { Text("Start") }
        }
    }
    if (picking) DayPicker(day, onPick = { day = it }, onClose = { picking = false })
}

/** The phone's own calendar, for picking a day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onClose: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onClose()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    ) { DatePicker(state = st) }
}

/** Which round is on screen, with its dates - back through the ones before, up to the one running now. */
@Composable
private fun RoundBar(book: Sichos, round: Int, current: Int, onRound: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp, 0.dp, 12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        NavBtn(Icons.Filled.ChevronLeft, "Round before", enabled = round > 0) { onRound(round - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Round ${round + 1}" + when {
                    round == current -> " · now"
                    round < current -> " · over"
                    else -> ""
                },
                fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "${roundDays(book, round)} · ${hebDay(book.roundStart(round))} – ${hebDay(book.roundEnd(round))}",
                fontSize = 11.5.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        NavBtn(Icons.Filled.ChevronRight, "Round after", enabled = round < maxOf(current, 0)) { onRound(round + 1) }
    }
}

@Composable
private fun NavBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).alpha(if (enabled) 1f else 0.35f).clip(RoundedCornerShape(10.dp)).background(RC.blueL)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = RC.blue) }
}

/** The round's four weeks: the one picked is what a tap on a name marks, this week ringed. */
@Composable
private fun WeekPicker(book: Sichos, round: Int, week: Int, thisWeek: Int?, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until Sichos.WEEKS).forEach { w ->
            val on = w == week
            val shape = RoundedCornerShape(11.dp)
            Column(
                Modifier.weight(1f).clip(shape).background(if (on) RC.blue else RC.card)
                    .border(if (w == thisWeek && !on) 1.5.dp else 0.dp, if (w == thisWeek && !on) RC.blue else Color.Transparent, shape)
                    .clickable { onPick(w) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Week ${w + 1}", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else RC.text)
                Text(dayText(book.weekStart(round, w)), fontSize = 11.sp, color = if (on) Color.White.copy(alpha = 0.8f) else RC.sub)
                Text("${book.handedIn(round, w)} in", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = if (on) Color.White.copy(alpha = 0.9f) else RC.green)
            }
        }
    }
}

@Composable
private fun FindField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onChange, singleLine = true,
        placeholder = { Text("Find a bochur") },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = RC.sub) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = RC.card, focusedContainerColor = RC.card, unfocusedBorderColor = RC.sep),
        modifier = Modifier.fillMaxWidth().padding(12.dp, 10.dp, 12.dp, 10.dp)
    )
}

/**
 * A name, his round so far, and the round's four weeks as dots - the one a tap marks ringed. The
 * surname leads, as the list is sorted by it, so a long first name is what gets cut, not it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BochurRow(first: String, last: String, weeks: List<String?>, week: Int, entries: Int, onTap: () -> Unit, onOpen: () -> Unit) {
    val count = weeks.count { it != null }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onTap, onLongClick = onOpen).padding(14.dp, 9.dp, 4.dp, 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(last) }
                    append(", $first")
                },
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "$count of ${Sichos.WEEKS}" + if (entries > 0) " · ${entriesText(entries)}" else "",
                fontSize = 12.sp, fontWeight = if (entries > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (entries > 0) RC.green else RC.sub
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(4.dp)) {
            weeks.forEachIndexed { w, v -> WeekDot(w, v, ringed = w == week) }
        }
        Icon(Icons.Filled.MoreHoriz, "His round", tint = RC.sub, modifier = Modifier.clip(CircleShape).clickable(onClick = onOpen).padding(8.dp).size(20.dp))
    }
}

/** One week of a bochur's round: its number when nothing is in yet, a tick once it is, two once reviewed. */
@Composable
private fun WeekDot(w: Int, value: String?, ringed: Boolean) {
    val ring = if (ringed) Modifier.border(2.dp, RC.blue, CircleShape).padding(3.dp) else Modifier.padding(3.dp)
    Box(ring) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (value != null) RC.green else RC.greyL),
            contentAlignment = Alignment.Center
        ) {
            when (value) {
                null -> Text("${w + 1}", fontSize = 11.sp, color = RC.sub, fontWeight = FontWeight.Medium)
                Sichos.REVIEWED -> Icon(Icons.Filled.DoneAll, "Reviewed", tint = Color.White, modifier = Modifier.size(15.dp))
                else -> Icon(Icons.Filled.Check, "Handed in", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** The round's raffle at a glance, and the way into it. */
@Composable
private fun RaffleBar(book: Sichos, round: Int, logic: NightLogic, onOpen: () -> Unit) {
    val pool = book.pool(round)
    val winner = book.winner(round)
    Row(
        Modifier.fillMaxWidth().background(RC.card).padding(16.dp, 10.dp, 12.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${entriesText(pool.sumOf { it.second })} · ${pool.size} bochurim",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            if (winner != null) {
                Text("Winner: ${logic.nameOf(winner)}", fontSize = 12.sp, color = RC.green, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = onOpen) { Text("Raffle") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BochurSheet(vm: AppViewModel, book: Sichos, logic: NightLogic, pid: String, round: Int, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) { BochurRound(vm, book, logic, pid, round) }
}

/** His round: every week of it, in or not, and which you have reviewed. */
@Composable
internal fun BochurRound(vm: AppViewModel, book: Sichos, logic: NightLogic, pid: String, round: Int) {
    Column(Modifier.padding(16.dp, 0.dp, 16.dp, 28.dp)) {
        Text(logic.nameOf(pid), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val count = book.count(pid, round)
        Text(
            "Round ${round + 1} · ${roundDays(book, round)} · $count of ${Sichos.WEEKS}" +
                book.entries(pid, round).let { if (it > 0) " · ${entriesText(it)}" else "" },
            fontSize = 12.5.sp, color = RC.sub, modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(RC.bg)) {
            (0 until Sichos.WEEKS).forEach { w ->
                if (w > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                val v = book.valueAt(pid, round, w)
                Row(Modifier.fillMaxWidth().padding(14.dp, 6.dp, 10.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Week ${w + 1}", fontSize = 15.sp)
                        Text("${dayText(book.weekStart(round, w))} · ${hebDay(book.weekStart(round, w))}", fontSize = 12.sp, color = RC.sub)
                    }
                    if (v != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { vm.setReviewed(pid, round, w, v != Sichos.REVIEWED) }.padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = v == Sichos.REVIEWED, onCheckedChange = { vm.setReviewed(pid, round, w, it) })
                            Text("Reviewed", fontSize = 13.5.sp, color = RC.sub2)
                        }
                    }
                    Switch(checked = v != null, onCheckedChange = { vm.toggleSikkum(pid, round, w) })
                }
            }
        }
    }
}

/**
 * The round's raffle: everyone in the hat, as many times as he has entries, and the draw. The
 * winner is kept, on every device, so the draw is made once - "Draw again" puts the one who won to
 * one side and draws from the rest, for a winner who cannot take it.
 */
@Composable
fun RaffleScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val book = vm.sichos(state)
    val logic = vm.logic(state)
    val round = vm.sichosRound(state)
    var aside by remember(round) { mutableStateOf(setOf<String>()) }
    var rolling by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackTo(vm, Tab.SICHOS)

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        if (book.start == null) {
            Header("Raffle", "", "Back") { vm.setTab(Tab.SICHOS) }
            return@Column
        }
        Header("Raffle", "Round ${round + 1} · ${roundDays(book, round)}", "Back") { vm.setTab(Tab.SICHOS) }
        val pool = book.pool(round)
        val winner = book.winner(round)

        fun draw(without: Set<String>) {
            if (rolling != null) return
            val names = book.pool(round, without).map { it.first }
            if (vm.drawRaffle(round, without) == null) return
            // the winner is already drawn and kept; this is only the names going round before it shows
            scope.launch {
                var wait = 40L
                var i = 0
                while (wait < 280) {
                    rolling = names[i++ % names.size]
                    delay(wait)
                    wait = wait * 113 / 100
                }
                rolling = null
            }
        }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val shown = rolling ?: winner
                    if (shown != null) {
                        if (rolling != null) Text("Drawing…", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RC.sub)
                        else Text("WINNER", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RC.green)
                        Text(logic.nameOf(shown), fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp))
                        Text(logic.nameOf(shown, hebrew = true), fontSize = 15.sp, color = RC.sub2, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                    }
                    if (winner == null || rolling != null) {
                        Button(
                            onClick = { draw(aside) }, enabled = rolling == null && book.pool(round, aside).isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("Draw", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        val rest = aside + winner
                        OutlinedButton(
                            onClick = { aside = rest; draw(rest) }, enabled = book.pool(round, rest).isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Draw again without ${logic.first(winner)}") }
                    }
                }
            }
            item { SectionHeader("In the hat · ${entriesText(pool.sumOf { it.second })}") }
            item {
                Card {
                    if (pool.isEmpty()) {
                        Text("No entries", fontSize = 14.sp, color = RC.sub, modifier = Modifier.padding(14.dp))
                    }
                    pool.sortedWith(compareBy({ -it.second }, { logic.last(it.first) })).forEachIndexed { i, (pid, n) ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        val out = pid in aside
                        Row(Modifier.fillMaxWidth().padding(14.dp, 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                logic.nameOf(pid), fontSize = 15.sp, modifier = Modifier.weight(1f),
                                color = if (out) RC.sub else RC.text, textDecoration = if (out) TextDecoration.LineThrough else null
                            )
                            Text(
                                if (out) "set aside" else "${entriesText(n)} · ${book.count(pid, round)} of ${Sichos.WEEKS}",
                                fontSize = 13.sp, color = if (out) RC.sub else RC.green, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
