package com.roomcheck.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import java.text.Collator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

private val gold = Color(0xFFF5B400)
private val goldL = Color(0xFFFFE07A)
private val stage = Brush.linearGradient(listOf(Color(0xFF1C2A7A), Color(0xFF4E2385)))

/** The Hebrew date of the Wednesday night a week's sicha is learned. */
private fun nightOf(d: LocalDate): String = Dates.hebrewDayMonth(Dates.keyOf(d))
/** A round as the nights of its first sicha and its last. */
private fun roundNights(book: Sichos, round: Int) =
    "${nightOf(book.weekStart(round, 0))} – ${nightOf(book.weekStart(round, Sichos.WEEKS - 1))}"
private fun entriesText(n: Int) = if (n == 1) "1 entry" else "$n entries"
/** Seder Sichos is on Wednesday nights: the first round's start is offered as the next one. */
private fun nextWednesday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))

private val hebrewOrder: Collator = Collator.getInstance(Locale.forLanguageTag("he"))

/** Everyone in the roster as a list of names is read: by surname, in Hebrew. */
internal fun byName(logic: NightLogic): List<String> = Roster.PEOPLE.map { it.id }.sortedWith(
    compareBy<String, String>(hebrewOrder) { logic.last(it, hebrew = true) }.thenBy(hebrewOrder) { logic.first(it, hebrew = true) }
)

/**
 * Seder Sichos: who handed in a sikkum on each Wednesday night's sicha, a round of four weeks at a
 * time, and the round's raffle. A tap on a name marks the week picked above for him - the bochur
 * standing there with one in his hand is what this is for - and tapping again takes it off.
 * Holding a name, or its ⋯, opens his round: every week of it, and which you have reviewed.
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
            menu = if (book.start != null) listOf("Change the start day" to { movingStart = true }) else emptyList()
        ) { vm.setTab(Tab.CHECK) }
        if (book.start == null) {
            StartCard(nextWednesday(vm.today())) { vm.setSichosStart(it) }
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
            q.isEmpty() || logic.nameOf(it, hebrew = true).contains(q) || logic.nameOf(it).contains(q, ignoreCase = true)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item {
                Card {
                    pids.forEachIndexed { i, pid ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        BochurRow(
                            first = logic.first(pid, hebrew = true), last = logic.last(pid, hebrew = true),
                            weeks = (0 until Sichos.WEEKS).map { book.valueAt(pid, round, it) },
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
            vm.toast("Round 1 · ${nightOf(it)}")
        }, onClose = { movingStart = false })
    }
    Toast(vm, state.toast)
}

@Composable
private fun Header(title: String, subtitle: String, button: String, menu: List<Pair<String, () -> Unit>> = emptyList(), onButton: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 13.dp, if (menu.isNotEmpty()) 4.dp else 16.dp, 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.5.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = onButton) { Text(button) }
        if (menu.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More", tint = RC.sub2) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
                    }
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

/** The one thing to set before anything else: the Wednesday the first round starts. */
@Composable
private fun StartCard(first: LocalDate, onStart: (LocalDate) -> Unit) {
    var day by remember { mutableStateOf(first) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(16.dp)) {
        Text("First round starts", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                Text("${day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))} · ${nightOf(day)}", maxLines = 1)
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

/** Which round is on screen, as the nights of its sichos - back through the ones before, up to now. */
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
            Text(roundNights(book, round), fontSize = 12.5.sp, color = RC.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** The round's four Wednesday nights: the one picked is what a tap on a name marks, this week ringed. */
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
                Text(nightOf(book.weekStart(round, w)), fontSize = 12.sp, color = if (on) Color.White.copy(alpha = 0.85f) else RC.sub2)
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
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
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
            Text("${entriesText(pool.sumOf { it.second })} · ${pool.size} bochurim", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (winner != null) {
                Text("Winner: ${logic.nameOf(winner, hebrew = true)}", fontSize = 12.5.sp, color = RC.green, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = onOpen) {
            Icon(Icons.Filled.ConfirmationNumber, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Raffle")
        }
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
        Text(logic.nameOf(pid, hebrew = true), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        val count = book.count(pid, round)
        Text(
            "Round ${round + 1} · ${roundNights(book, round)} · $count of ${Sichos.WEEKS}" +
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
                        Text(nightOf(book.weekStart(round, w)), fontSize = 12.5.sp, color = RC.sub)
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
 * The round's raffle: a stage with the hat on it - everyone in it as many times as he has
 * entries - and the draw, the names going round faster than you can read and slowing down to the
 * one it landed on, confetti and all. The winner is kept on every device, so the draw is made once;
 * "Draw again" puts the one who won to one side for a winner who cannot take it, and the menu
 * clears a draw that was only a try.
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
    val haptic = LocalHapticFeedback.current
    BackTo(vm, Tab.SICHOS)

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        if (book.start == null) {
            Header("Raffle", "", "Back") { vm.setTab(Tab.SICHOS) }
            return@Column
        }
        val pool = book.pool(round)
        val winner = book.winner(round)
        Header(
            "Raffle", "Round ${round + 1} · ${roundNights(book, round)}", "Back",
            menu = if (winner != null && rolling == null) listOf("Clear the winner" to { aside = emptySet(); vm.clearRaffle(round) }) else emptyList()
        ) { vm.setTab(Tab.SICHOS) }

        fun draw(without: Set<String>) {
            if (rolling != null) return
            val names = book.pool(round, without).map { it.first }
            if (vm.drawRaffle(round, without) == null) return
            // the winner is already drawn and kept; this is the names going round before it shows
            scope.launch {
                var wait = 35L
                var i = 0
                while (wait < 330) {
                    rolling = names[i++ % names.size]
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    delay(wait)
                    wait = wait * 112 / 100
                }
                rolling = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item {
                Stage(
                    entries = pool.sumOf { it.second },
                    rolling = rolling?.let { logic.nameOf(it, hebrew = true) },
                    winner = winner?.let { logic.nameOf(it, hebrew = true) },
                    canDraw = rolling == null && book.pool(round, aside).isNotEmpty(),
                    again = winner?.let { w -> "Draw again without ${logic.first(w, hebrew = true)}" to book.pool(round, aside + w).isNotEmpty() },
                    onDraw = { draw(aside) },
                    onDrawAgain = { winner?.let { w -> val rest = aside + w; aside = rest; draw(rest) } }
                )
            }
            item { SectionHeader("In the hat") }
            item {
                Card {
                    if (pool.isEmpty()) Text("No entries", fontSize = 14.sp, color = RC.sub, modifier = Modifier.padding(14.dp))
                    pool.sortedWith(
                        compareByDescending<Pair<String, Int>> { it.second }.thenBy(hebrewOrder) { logic.last(it.first, hebrew = true) }
                    ).forEachIndexed { i, (pid, n) ->
                        if (i > 0) HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
                        val out = pid in aside
                        Row(Modifier.fillMaxWidth().padding(14.dp, 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                logic.nameOf(pid, hebrew = true), fontSize = 16.sp, modifier = Modifier.weight(1f),
                                color = if (out) RC.sub else RC.text, textDecoration = if (out) TextDecoration.LineThrough else null
                            )
                            repeat(n) {
                                Icon(Icons.Filled.ConfirmationNumber, null, tint = if (out) RC.greyL else gold,
                                    modifier = Modifier.padding(start = 4.dp).size(24.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The draw's stage: the hat and its count, then the names going round, then the winner. */
@Composable
private fun Stage(
    entries: Int, rolling: String?, winner: String?, canDraw: Boolean, again: Pair<String, Boolean>?,
    onDraw: () -> Unit, onDrawAgain: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse"
    )
    Box(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(shape).background(stage)
            .border(2.dp, gold.copy(alpha = if (rolling != null) pulse else 0.5f), shape)
    ) {
        if (winner != null && rolling == null) Confetti(Modifier.matchParentSize(), key = winner)
        Column(Modifier.fillMaxWidth().padding(22.dp, 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                rolling != null -> {
                    Icon(Icons.Filled.ConfirmationNumber, null, tint = gold, modifier = Modifier.size(46.dp).scale(0.85f + 0.25f * pulse))
                    Spacer(Modifier.height(14.dp))
                    AnimatedContent(
                        targetState = rolling,
                        transitionSpec = {
                            (slideInVertically(tween(80)) { it } + fadeIn(tween(80))) togetherWith
                                (slideOutVertically(tween(80)) { -it } + fadeOut(tween(80)))
                        },
                        label = "name"
                    ) { name ->
                        Text(name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
                    }
                    Spacer(Modifier.height(78.dp))
                }
                winner != null -> {
                    Icon(Icons.Filled.EmojiEvents, null, tint = gold, modifier = Modifier.size(64.dp))
                    Text("WINNER", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = goldL, letterSpacing = 4.sp,
                        modifier = Modifier.padding(top = 6.dp))
                    Text(winner, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp))
                    if (again != null) {
                        Spacer(Modifier.height(22.dp))
                        OutlinedButton(
                            onClick = onDrawAgain, enabled = again.second,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.4f))
                        ) { Text(again.first) }
                    }
                }
                else -> {
                    Icon(Icons.Filled.ConfirmationNumber, null, tint = gold, modifier = Modifier.size(46.dp))
                    Text("$entries", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(if (entries == 1) "entry" else "entries", fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(22.dp))
                    GoldButton("Draw", enabled = canDraw, onClick = onDraw)
                }
            }
        }
    }
}

@Composable
private fun GoldButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(58.dp).alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(29.dp))
            .background(Brush.horizontalGradient(listOf(goldL, gold))).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3A2600)) }
}

private class Bit(val x: Float, val y: Float, val fall: Float, val sway: Float, val spin: Float, val size: Float, val color: Color)

private val CONFETTI = listOf(gold, goldL, Color(0xFFFF5C8A), Color(0xFF4FD1C5), Color(0xFF7AA7FF), Color.White)

/** Confetti over the stage when the winner shows: scattered to start with, falling off the bottom. */
@Composable
private fun Confetti(modifier: Modifier, key: Any) {
    val bits = remember(key) {
        val r = Random(key.hashCode())
        List(90) {
            Bit(
                x = r.nextFloat(), y = r.nextFloat() * 1.5f - 0.6f, fall = 1.6f + r.nextFloat() * 0.8f,
                sway = 0.02f + r.nextFloat() * 0.04f, spin = 1f + r.nextFloat() * 3f, size = 9f + r.nextFloat() * 10f,
                color = CONFETTI[r.nextInt(CONFETTI.size)]
            )
        }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(3400, easing = LinearEasing)) }
    Canvas(modifier) {
        val p = t.value
        bits.forEach { b ->
            val y = (b.y + b.fall * p) * size.height
            if (y < -30f || y > size.height + 30f) return@forEach
            val x = (b.x + sin(p * 9f + b.x * 12f) * b.sway) * size.width
            rotate(b.x * 360f + b.spin * 360f * p, Offset(x, y)) {
                drawRect(b.color, topLeft = Offset(x - b.size / 2, y - b.size / 4), size = Size(b.size, b.size / 2))
            }
        }
    }
}
