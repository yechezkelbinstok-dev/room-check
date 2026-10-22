package com.roomcheck.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// a break is amber and a trip green, so the year reads at a glance: blue seder, amber off, green away
private val amber = Color(0xFF9A5B08)
private val amberL = Color(0xFFFBF1DF)

/**
 * English lines read left to right even inside the right-to-left Hebrew calendar. Left to work it
 * out from the text, a line starting with a number - "3 weeks" - takes the screen's direction and
 * comes out as "weeks 3".
 */
private val ltr = TextStyle(textDirection = TextDirection.Ltr)

private fun tintOf(kind: LuachKind): Pair<Color, Color> = when (kind) {
    LuachKind.OFF -> amber to amberL
    LuachKind.TRIP, LuachKind.HOME, LuachKind.EVENT -> RC.green to RC.greenL
    else -> RC.blue to RC.blueL
}

private fun tintOf(kind: DayKind): Pair<Color, Color> = when (kind) {
    DayKind.ZMAN -> RC.blue to RC.blueL
    DayKind.OFF -> amber to amberL
    DayKind.TRIP -> RC.green to RC.greenL
    // bein hazmanim is time off like a break, so it reads the same on the month
    DayKind.BEIN -> amber to amberL
    DayKind.NONE -> Color.Transparent to Color.Transparent
}

/**
 * The yeshiva's year as its luach has it: where things stand tonight, then the year either as a
 * calendar, a month at a time, or as the luach's own list - every zman with its breaks and trips
 * and the bein hazmanim between them. In Hebrew it reads right to left, in English left to right.
 * The month goes by Hebrew months or regular ones, the other calendar's date small in each day -
 * Hebrew months in Hebrew and regular ones in English, unless the month's own switch says otherwise.
 */
@Composable
fun LuachScreen(vm: AppViewModel, now: LocalDateTime = LocalDateTime.now(), firstItem: Int = 0) {
    val state by vm.state.collectAsState()
    val summer = state.summerZman
    val en = state.settings.luachEnglish
    val asList = state.settings.luachList
    val status = Luach.now(now, summer)
    val today = now.toLocalDate()
    val current: Zman? = when (status) {
        is LuachNow.InZman -> status.zman
        is LuachNow.InGap -> status.zman
        is LuachNow.Between -> null
    }
    val next: LuachDay? = when (status) {
        is LuachNow.InZman -> status.next
        is LuachNow.InGap -> status.gap.to
        is LuachNow.Between -> status.before?.start
    }
    // Hebrew months or regular ones: the language picks unless the month switch has been set
    val hebMonths = Luach.hebrewMonthsFor(state.settings.luachMonths, en)
    val months = Luach.months(hebMonths, summer)
    var month by remember(hebMonths, summer) { mutableIntStateOf(Luach.monthIndexOf(today, hebMonths, summer)) }
    var picked by remember(hebMonths, summer) { mutableStateOf<LocalDate?>(today) }

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 13.dp, 16.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("Yeshiva calendar", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (en) "${Luach.YEAR_EN} school year" else "שנת הלימודים ${Luach.YEAR}",
                    fontSize = 12.5.sp, color = RC.sub
                )
            }
            OutlinedButton(onClick = { vm.setTab(Tab.CHECK) }) { Text("Done") }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Segmented(if (en) listOf("Month", "List") else listOf("חודש", "רשימה"), if (asList) 1 else 0) { i ->
                vm.setSettings { it.copy(luachList = i == 1) }
            }
            Spacer(Modifier.weight(1f))
            Segmented(listOf("עברית", "English"), if (en) 1 else 0) { i ->
                vm.setSettings { it.copy(luachEnglish = i == 1) }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides if (en) LayoutDirection.Ltr else LayoutDirection.Rtl) {
            LazyColumn(
                Modifier.weight(1f), state = rememberLazyListState(firstItem),
                contentPadding = PaddingValues(12.dp, 2.dp, 12.dp, 28.dp)
            ) {
                item { NowCard(status, now, summer, en) }
                item { Spacer(Modifier.height(14.dp)) }
                if (!asList) {
                    item {
                        MonthCard(
                            months, month, { month = it }, today, summer, en, hebMonths,
                            { heb -> vm.setSettings { it.copy(luachMonths = if (heb) "heb" else "greg") } },
                            picked
                        ) { picked = it }
                    }
                    picked?.takeIf { it in months[month].days && Luach.dayKind(it, summer) != DayKind.NONE }?.let { d ->
                        item { Spacer(Modifier.height(10.dp)) }
                        item { DayCard(d, today, summer, en, next) }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { MonthLines(months[month], today, summer, en, next) }
                } else {
                    val year = Luach.zmanim(summer)
                    year.forEachIndexed { i, z ->
                        item { ZmanCard(z, today, current == z, next, summer, en) { vm.setSummerZman(it) } }
                        // the bein hazmanim between this zman and the next; after the last there is nothing
                        year.getOrNull(i + 1)?.let { following ->
                            val isNow = status is LuachNow.Between && status.after == z
                            item { BeinBand(z.end, following.start, isNow, en) }
                        }
                    }
                }
                if (!asList) {
                    // the switch lives with the summer zman in the list; the month view keeps one too
                    item { Spacer(Modifier.height(10.dp)) }
                    item { SummerSwitchCard(summer, en) { vm.setSummerZman(it) } }
                }
                item {
                    Text(
                        if (en) Luach.YESHIVA_EN else Luach.YESHIVA, fontSize = 12.sp, color = RC.sub,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Two choices side by side, the chosen one raised - the look of the app's other switches between views. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFE3E3E8)).padding(2.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Text(
                o, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                color = if (on) RC.text else RC.sub2,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 13.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun NowCard(status: LuachNow, now: LocalDateTime, summer: Boolean, en: Boolean) {
    val today = now.toLocalDate()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(16.dp, 14.dp, 16.dp, 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // tonight, named the way the marking screen names it - the Hebrew date has already turned,
            // while the month below rings today's daytime date
            val night = Dates.tonightKey(now)
            val h = Dates.hebrewOf(Dates.parseKey(night).plusDays(1))
            Text(if (en) "Tonight" else "הלילה", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = RC.blue)
            Text(
                " · " + if (en) "${h.day} ${Dates.hebMonthNameEn(h.month, h.year)}" else Dates.hebrewDayMonth(night),
                fontSize = 12.5.sp, color = RC.sub2
            )
            Spacer(Modifier.weight(1f))
            Text(Luach.dateText(Dates.parseKey(night)), style = ltr, fontSize = 12.5.sp, color = RC.sub)
        }
        Spacer(Modifier.height(6.dp))
        when (status) {
            is LuachNow.InZman -> {
                val z = status.zman
                Text(z.name(en), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                status.next?.let { NextUp(it, today, if (en) "Next" else "הבא", en) }
            }
            is LuachNow.InGap -> {
                val g = status.gap
                val (tint, light) = tintOf(g.from.kind)
                Text(g.name(en), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = tint)
                Text(
                    (if (g.isTrip) "Away · back " else "Off · seder again ") + Luach.whenText(g.to),
                    style = ltr, fontSize = 13.sp, color = RC.sub2
                )
                Spacer(Modifier.height(12.dp))
                ProgressLine(Luach.progress(g.from.at, g.to.at, now), tint, light)
                NextUp(g.to, today, if (en) "Next" else "הבא", en)
            }
            is LuachNow.Between -> {
                val before = status.before
                val after = status.after
                when {
                    // before the year starts: nothing yet, just when it does
                    after == null && before != null -> {
                        Text(before.name(en), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Starts " + Luach.whenText(before.start), style = ltr, fontSize = 13.sp, color = RC.sub2,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    before == null -> {
                        Text(if (en) "The year is over" else "השנה נגמרה", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (after?.cut == true) "It ended with the trip" else "It ended " + Luach.dateText(after!!.end.date),
                            style = ltr, fontSize = 13.sp, color = RC.sub2, modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    else -> {
                        Text(if (en) "Bein hazmanim" else "בין הזמנים", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = amber)
                        val n = Luach.days(today, before.start.date)
                        Text(
                            if (n <= 0) "The zman starts today" else "The zman starts in $n day${if (n == 1L) "" else "s"}",
                            style = ltr, fontSize = 13.sp, color = RC.sub2
                        )
                        Spacer(Modifier.height(12.dp))
                        ProgressLine(Luach.progress(after!!.end.at, before.start.at, now), amber, amberL)
                        NextUp(before.start, today, before.name(en), en)
                    }
                }
            }
        }
    }
}

/** What comes next, under a small heading - the same row the calendar shows it as. */
@Composable
private fun NextUp(d: LuachDay, today: LocalDate, label: String, en: Boolean) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = RC.sub, modifier = Modifier.padding(top = 10.dp))
    LuachRow(d, today, isNext = true, en = en, padH = 0.dp, countdown = false)
}

@Composable
private fun ProgressLine(fraction: Float, color: Color, track: Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(track)
            .drawBehind {
                val w = size.width * fraction
                drawRect(color, Offset(if (rtl) size.width - w else 0f, 0f), Size(w, size.height))
            }
    )
}

/**
 * One month, the way a wall calendar has it: a week to a row, Sunday first, every day tinted by
 * what it is - seder, a break, a trip, or bein hazmanim left plain. A day with a line on the luach
 * carries a dot in that line's colour; today is ringed in black, the day tapped in blue.
 */
@Composable
private fun MonthCard(
    months: List<LuachMonth>, index: Int, onMonth: (Int) -> Unit, today: LocalDate, summer: Boolean,
    en: Boolean, hebMonths: Boolean, onHebMonths: (Boolean) -> Unit, picked: LocalDate?, onPick: (LocalDate) -> Unit
) {
    val m = months[index]
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(10.dp, 12.dp, 10.dp, 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // back is on the right in Hebrew, pointing right; forward on the left, pointing left
            MonthArrow(pointsRight = rtl, enabled = index > 0) { onMonth(index - 1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.title(en), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                // the other calendar's months; English unless they are Hebrew months named in Hebrew
                Text(m.sub(en), style = if (hebMonths || en) ltr else TextStyle.Default, fontSize = 12.sp, color = RC.sub)
            }
            MonthArrow(pointsRight = !rtl, enabled = index < months.lastIndex) { onMonth(index + 1) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            Segmented(
                if (en) listOf("Hebrew months", "Regular months") else listOf("חודשים עבריים", "חודשים לועזיים"),
                if (hebMonths) 0 else 1
            ) { onHebMonths(it == 0) }
        }
        Spacer(Modifier.height(10.dp))
        val heads = if (en) listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    else listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
        Row(Modifier.fillMaxWidth()) {
            heads.forEach { Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = RC.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(4.dp))
        val lead = m.days.first().dayOfWeek.value % 7   // Sunday first
        val cells: List<LocalDate?> = List(lead) { null } + m.days
        // a week wholly before the year or after it is left out rather than drawn empty
        cells.chunked(7).filter { w -> w.any { it != null && Luach.dayKind(it, summer) != DayKind.NONE } }.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(54.dp).padding(1.5.dp)) {
                        // days before the year starts and after it ends are left blank
                        if (d != null && Luach.dayKind(d, summer) != DayKind.NONE) DayCell(d, today, summer, en, hebMonths, picked == d) { onPick(d) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Legend(RC.blueL, RC.blue, if (en) "Seder" else "סדר")
            Legend(amberL, amber, if (en) "Off" else "חופש")
            Legend(RC.greenL, RC.green, if (en) "Trip" else "נסיעה")
        }
    }
}

@Composable
private fun MonthArrow(pointsRight: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) RC.blue else RC.sep
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (enabled) RC.blueL else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // drawn rather than typed: a ‹ in right-to-left text is turned round into a ›
        Canvas(Modifier.size(width = 9.dp, height = 15.dp)) {
            val inset = 1.5.dp.toPx()
            val tip = if (pointsRight) size.width - inset else inset
            val tail = if (pointsRight) inset else size.width - inset
            val chevron = Path().apply {
                moveTo(tail, inset); lineTo(tip, size.height / 2); lineTo(tail, size.height - inset)
            }
            drawPath(chevron, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, today: LocalDate, summer: Boolean, en: Boolean, hebMonths: Boolean, picked: Boolean, onClick: () -> Unit) {
    val kind = Luach.dayKind(d, summer)
    val (_, light) = tintOf(kind)
    val lines = Luach.linesOn(d, summer)
    val h = Dates.hebrewOf(d)
    val ring = when {
        picked -> RC.blue
        d == today -> RC.text
        else -> Color.Transparent
    }
    Box(
        Modifier.fillMaxSize().alpha(if (d < today) 0.55f else 1f).clip(RoundedCornerShape(10.dp)).background(light)
            .border(if (ring == Color.Transparent) 0.dp else 2.dp, ring, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // big, the day in the months on screen - a Hebrew one in letters only when the calendar is in Hebrew
            Text(
                if (!hebMonths) d.dayOfMonth.toString() else if (en) h.day.toString() else Dates.hebrewDayOf(h.day),
                fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold,
                color = RC.text
            )
            // the other calendar, small; on the first of its month, the month's name
            val small = if (!hebMonths) {
                when {
                    h.day == 1 -> if (en) Dates.hebMonthNameEn(h.month, h.year) else Dates.hebMonthName(h.month, h.year)
                    en -> h.day.toString()
                    else -> Dates.hebrewDayOf(h.day)
                }
            } else {
                if (d.dayOfMonth == 1) d.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH) + " 1" else d.dayOfMonth.toString()
            }
            Text(small, style = if (hebMonths || en) ltr else TextStyle.Default, fontSize = 9.sp, lineHeight = 11.sp, color = RC.sub, maxLines = 1)
            Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(
                lines.firstOrNull()?.let { tintOf(it.kind).first } ?: Color.Transparent
            ))
        }
    }
}

@Composable
private fun Legend(color: Color, edge: Color, label: String) {
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color)
            .border(1.dp, edge.copy(alpha = 0.45f), RoundedCornerShape(3.dp)))
        Text(label, fontSize = 11.sp, color = RC.sub2, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The day tapped on the month: its date both ways, what kind of day it is, and its luach lines. */
@Composable
private fun DayCard(d: LocalDate, today: LocalDate, summer: Boolean, en: Boolean, next: LuachDay?) {
    val h = Dates.hebrewOf(d)
    val kind = Luach.dayKind(d, summer)
    val (tint, _) = tintOf(kind)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(16.dp, 12.dp, 16.dp, 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (en) Luach.dateText(d) else Dates.hebrewDayMonthOf(h.year, h.month, h.day),
                fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (en) "${h.day} ${Dates.hebMonthNameEn(h.month, h.year)}" else Luach.dateText(d),
                style = ltr, fontSize = 12.5.sp, color = RC.sub
            )
        }
        val z = Luach.zmanOn(d, summer)
        val what = when (kind) {
            DayKind.ZMAN -> z!!.name(en)
            DayKind.OFF, DayKind.TRIP -> Luach.gapOn(d, summer)!!.name(en)
            DayKind.BEIN -> if (en) "Bein hazmanim" else "בין הזמנים"
            DayKind.NONE -> ""
        }
        Row(Modifier.padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
            Text(what, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = RC.sub2, modifier = Modifier.padding(start = 7.dp))
        }
        Luach.linesOn(d, summer).forEach { LuachRow(it, today, isNext = it == next, en = en, padH = 0.dp) }
    }
}

/** The luach's lines that fall in the month on screen, in order. */
@Composable
private fun MonthLines(m: LuachMonth, today: LocalDate, summer: Boolean, en: Boolean, next: LuachDay?) {
    val lines = Luach.zmanim(summer).flatMap { it.days }.filter { it.date in m.days }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(vertical = 6.dp)) {
        Text(
            if (en) "THIS MONTH" else "בחודש הזה", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RC.sub,
            modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 2.dp)
        )
        if (lines.isEmpty()) {
            Text(
                if (en) "Nothing on the luach this month" else "אין כלום בלוח בחודש הזה",
                fontSize = 13.5.sp, color = RC.sub, modifier = Modifier.padding(16.dp, 6.dp, 16.dp, 12.dp)
            )
        }
        lines.forEach { LuachRow(it, today, isNext = it == next, en = en) }
    }
}

@Composable
private fun SummerSwitchCard(summer: Boolean, en: Boolean, onSummer: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(16.dp, 8.dp, 12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (en) "Summer zman after the trip" else "זמן קיץ אחרי הנסיעה", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (summer) "On the calendar, to the end of the zman" else "Off - the year ends with the trip",
                style = ltr, fontSize = 12.sp, color = RC.sub
            )
        }
        Switch(checked = summer, onCheckedChange = onSummer)
    }
}

@Composable
private fun ZmanCard(
    z: Zman, today: LocalDate, isCurrent: Boolean, next: LuachDay?, summer: Boolean, en: Boolean,
    onSummer: (Boolean) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card)
            .then(if (isCurrent) Modifier.border(1.5.dp, RC.blue, RoundedCornerShape(16.dp)) else Modifier)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(z.name(en), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (isCurrent) Pill(if (en) "Now" else "עכשיו", Color.White, RC.blue)
                    else Pill(Luach.lengthText(Luach.days(z.start.date, z.end.date)), RC.sub2, RC.greyL, english = true)
                }
                if (en) {
                    Text(Luach.rangeText(z.start.date, z.end.date), fontSize = 13.sp, color = RC.sub2, modifier = Modifier.padding(top = 2.dp))
                    Text("${z.start.hebrewEn} – ${z.end.hebrewEn}", fontSize = 12.sp, color = RC.sub)
                } else {
                    Text("${z.start.hebrew} – ${z.end.hebrew}", fontSize = 13.sp, color = RC.sub2, modifier = Modifier.padding(top = 2.dp))
                    Text(Luach.rangeText(z.start.date, z.end.date), style = ltr, fontSize = 12.sp, color = RC.sub)
                }
            }
        }
        HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
        Spacer(Modifier.height(4.dp))
        var i = 0
        val gaps = z.gaps
        while (i < z.days.size) {
            val d = z.days[i]
            val gap = gaps.firstOrNull { it.from == d }
            if (gap != null) {
                GapBlock(gap, today, next, en)
                i += 2
            } else {
                LuachRow(d, today, isNext = d == next, en = en)
                i++
            }
        }
        // the stretch after the trip, which may not happen
        if (z.optional) {
            HorizontalDivider(color = RC.sep, thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
            SummerSwitchCard(summer, en, onSummer)
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** A break or a trip, its two lines held together in its own colour with how long it lasts. */
@Composable
private fun GapBlock(g: LuachGap, today: LocalDate, next: LuachDay?, en: Boolean) {
    val (tint, light) = tintOf(g.from.kind)
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(light)) {
        LuachRow(g.from, today, isNext = g.from == next, en = en, padH = 8.dp, onTint = true)
        Text(
            Luach.lengthText(Luach.days(g.from.date, g.to.date)),
            style = ltr, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tint,
            modifier = Modifier.padding(start = 66.dp, bottom = 2.dp)
        )
        LuachRow(g.to, today, isNext = g.to == next, en = en, padH = 8.dp, onTint = true)
    }
}

@Composable
private fun LuachRow(
    d: LuachDay, today: LocalDate, isNext: Boolean, en: Boolean, padH: Dp = 16.dp, onTint: Boolean = false,
    countdown: Boolean = true
) {
    val past = d.date < today
    val (tint, light) = tintOf(d.kind)
    Row(
        Modifier.fillMaxWidth().alpha(if (past) 0.42f else 1f).padding(horizontal = padH, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 46.dp, height = 50.dp).clip(RoundedCornerShape(11.dp))
                .background(if (onTint) Color.White.copy(alpha = 0.75f) else light),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // the tile leads with the calendar the screen goes by
                Text(
                    if (en) d.date.dayOfMonth.toString() else Dates.hebrewDayOf(d.day),
                    fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = tint
                )
                Text(
                    if (en) d.date.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase() else Dates.hebMonthName(d.month, d.year),
                    fontSize = 10.5.sp, lineHeight = 12.sp, color = tint
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title(en), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            d.note(en)?.let { Text(it, fontSize = 12.5.sp, color = RC.sub2) }
            Text(if (en) Luach.whenTextEn(d) else Luach.whenText(d), style = ltr, fontSize = 12.5.sp, color = RC.sub)
        }
        // not at the top, where it would only count down the time still to go
        if (countdown) Luach.countdown(d.date, today)?.let {
            if (isNext) Pill(it, RC.blue, RC.blueL, english = true) else Text(it, style = ltr, fontSize = 12.sp, color = RC.sub)
        }
    }
}

/** Bein hazmanim, from one zman's end to the next one's start - or open, when nothing follows. */
@Composable
private fun BeinBand(end: LuachDay, nextStart: LuachDay?, isNow: Boolean, en: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(if (isNow) RC.blue else RC.sep))
        Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (en) "Bein hazmanim" else "בין הזמנים", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isNow) RC.blue else RC.sub2)
                if (isNow) Pill(if (en) "Now" else "עכשיו", Color.White, RC.blue)
            }
            Text(
                if (nextStart != null)
                    Luach.lengthText(Luach.days(end.date, nextStart.date)) + " · " +
                        Luach.rangeText(end.date, nextStart.date).substringBeforeLast(",")
                else "from " + Luach.dateText(end.date, withYear = true),
                style = ltr, fontSize = 11.5.sp, color = RC.sub
            )
        }
        Box(Modifier.weight(1f).height(1.dp).background(if (isNow) RC.blue else RC.sep))
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color, english: Boolean = false) {
    Text(
        text, style = if (english) ltr else TextStyle.Default, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
