package com.roomcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomcheck.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime

// a break is amber and a trip green, so the year reads at a glance: blue seder, amber off, green away
private val amber = Color(0xFF9A5B08)
private val amberL = Color(0xFFFBF1DF)

/**
 * English lines read left to right even inside this right-to-left screen. Left to work it out
 * from the text, a line starting with a number - "3 weeks" - takes the screen's direction and
 * comes out as "weeks 3".
 */
private val ltr = TextStyle(textDirection = TextDirection.Ltr)

private fun tintOf(kind: LuachKind): Pair<Color, Color> = when (kind) {
    LuachKind.OFF -> amber to amberL
    LuachKind.TRIP, LuachKind.HOME, LuachKind.EVENT -> RC.green to RC.greenL
    else -> RC.blue to RC.blueL
}

/**
 * The yeshiva's year as its luach has it: where things stand today, then every zman with its
 * breaks and trips, and the bein hazmanim between them. Laid out right to left, the way the luach
 * itself is, with the English day and time under each line the way the luach gives them too.
 */
@Composable
fun LuachScreen(vm: AppViewModel, now: LocalDateTime = LocalDateTime.now(), firstItem: Int = 0) {
    val state by vm.state.collectAsState()
    val summer = state.summerZman
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

    Column(Modifier.fillMaxSize().background(RC.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("Yeshiva calendar", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("שנת הלימודים ${Luach.YEAR}", fontSize = 12.5.sp, color = RC.sub)
            }
            OutlinedButton(onClick = { vm.setTab(Tab.CHECK) }) { Text("Done") }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LazyColumn(
                Modifier.weight(1f), state = rememberLazyListState(firstItem),
                contentPadding = PaddingValues(12.dp, 2.dp, 12.dp, 28.dp)
            ) {
                item { NowCard(status, now, summer) }
                item { Spacer(Modifier.height(14.dp)) }
                val year = Luach.ZMANIM
                year.forEachIndexed { i, z ->
                    item {
                        ZmanCard(z, today, now, current == z, next, summer) { vm.setSummerZman(it) }
                    }
                    // the bein hazmanim after this zman, up to the next one the year really has
                    val shown = z.optional.not() || summer
                    if (shown) {
                        val following = year.drop(i + 1).firstOrNull { !it.optional || summer }
                        val isNow = status is LuachNow.Between && status.after == z
                        item { BeinBand(z.end, following?.start, isNow) }
                    }
                }
                item {
                    Text(
                        "ישיבה גדולה ליובאוויטש סינסינאטי", fontSize = 12.sp, color = RC.sub,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun NowCard(status: LuachNow, now: LocalDateTime, summer: Boolean) {
    val today = now.toLocalDate()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card).padding(16.dp, 14.dp, 16.dp, 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // named the way the marking screen names it, so the two never show different dates
            val night = Dates.tonightKey(now)
            Text("היום", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = RC.blue)
            Text(" · " + Dates.hebrewDayMonth(night), fontSize = 12.5.sp, color = RC.sub2)
            Spacer(Modifier.weight(1f))
            Text(Luach.dateText(Dates.parseKey(night)), style = ltr, fontSize = 12.5.sp, color = RC.sub)
        }
        Spacer(Modifier.height(6.dp))
        when (status) {
            is LuachNow.InZman -> {
                val z = status.zman
                Text(z.name, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                val total = Luach.days(z.start.date, z.end.date) + 1
                val day = Luach.days(z.start.date, today) + 1
                Text("Day $day of $total · ends ${Luach.dateText(z.end.date)}", style = ltr, fontSize = 13.sp, color = RC.sub2)
                Spacer(Modifier.height(12.dp))
                ZmanBar(z, now)
                status.next?.let { NextUp(it, today, label = "הבא") }
            }
            is LuachNow.InGap -> {
                val g = status.gap
                val (tint, _) = tintOf(g.from.kind)
                Text(g.name, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = tint)
                Text(
                    (if (g.isTrip) "Away · back " else "Off · seder again ") + Luach.whenText(g.to),
                    style = ltr, fontSize = 13.sp, color = RC.sub2
                )
                Spacer(Modifier.height(12.dp))
                ProgressLine(Luach.progress(g.from.at, g.to.at, now), tint, tintOf(g.from.kind).second)
                NextUp(g.to, today, label = "הבא")
            }
            is LuachNow.Between -> {
                Text("בין הזמנים", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                val before = status.before
                val after = status.after
                if (before != null) {
                    val n = Luach.days(today, before.start.date)
                    Text(
                        if (n <= 0) "The zman starts today" else "The zman starts in $n day${if (n == 1L) "" else "s"}",
                        style = ltr, fontSize = 13.sp, color = RC.sub2
                    )
                    if (after != null) {
                        Spacer(Modifier.height(12.dp))
                        ProgressLine(Luach.progress(after.end.at, before.start.at, now), RC.sub, RC.greyL)
                    }
                    NextUp(before.start, today, label = before.name)
                } else {
                    Text(
                        if (!summer && after?.id == "winter") "No summer zman this year" else "The year's zmanim are over",
                        style = ltr, fontSize = 13.sp, color = RC.sub2, modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/** What comes next, under a small heading - the same row the zman's card shows it as. */
@Composable
private fun NextUp(d: LuachDay, today: LocalDate, label: String) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = RC.sub, modifier = Modifier.padding(top = 10.dp))
    LuachRow(d, today, isNext = true, padH = 0.dp)
}

/** The zman from end to end, its breaks and trips shaded in, filled up to now. */
@Composable
private fun ZmanBar(z: Zman, now: LocalDateTime) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val total = z.start.at to z.end.at
    fun f(t: LocalDateTime) = Luach.progress(total.first, total.second, t)
    val done = f(now)
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(RC.greyL)
            .drawBehind {
                fun span(a: Float, b: Float, c: Color) {
                    val x0 = if (rtl) size.width * (1 - b) else size.width * a
                    drawRect(c, Offset(x0, 0f), Size(size.width * (b - a), size.height))
                }
                span(0f, done, RC.blue)
                z.gaps.forEach { g ->
                    val (strong, light) = tintOf(g.from.kind)
                    val a = f(g.from.at)
                    val b = f(g.to.at)
                    span(a, b, if (b <= done) strong.copy(alpha = 0.55f) else light)
                }
            }
    )
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

@Composable
private fun ZmanCard(
    z: Zman, today: LocalDate, now: LocalDateTime, isCurrent: Boolean, next: LuachDay?,
    summer: Boolean, onSummer: (Boolean) -> Unit
) {
    val off = z.optional && !summer
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RC.card)
            .then(if (isCurrent) Modifier.border(1.5.dp, RC.blue, RoundedCornerShape(16.dp)) else Modifier)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (off) 0.5f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(z.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (isCurrent) Pill("עכשיו", Color.White, RC.blue)
                    else if (!off) Pill(Luach.lengthText(Luach.days(z.start.date, z.end.date)), RC.sub2, RC.greyL, english = true)
                }
                Text("${z.start.hebrew} – ${z.end.hebrew}", fontSize = 13.sp, color = RC.sub2, modifier = Modifier.padding(top = 2.dp))
                Text(Luach.rangeText(z.start.date, z.end.date), style = ltr, fontSize = 12.sp, color = RC.sub)
            }
            if (z.optional) Switch(checked = summer, onCheckedChange = onSummer)
        }
        if (off) {
            Text(
                "Switched off - no summer zman this year.",
                style = ltr, fontSize = 12.5.sp, color = RC.sub, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 14.dp)
            )
            return@Column
        }
        HorizontalDivider(color = RC.sep, thickness = 0.5.dp)
        Spacer(Modifier.height(4.dp))
        var i = 0
        val gaps = z.gaps
        while (i < z.days.size) {
            val d = z.days[i]
            val gap = gaps.firstOrNull { it.from == d }
            if (gap != null) {
                GapBlock(gap, today, next)
                i += 2
            } else {
                LuachRow(d, today, isNext = d == next)
                i++
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** A break or a trip, its two lines held together in its own colour with how long it lasts. */
@Composable
private fun GapBlock(g: LuachGap, today: LocalDate, next: LuachDay?) {
    val (tint, light) = tintOf(g.from.kind)
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(light)) {
        LuachRow(g.from, today, isNext = g.from == next, padH = 8.dp, onTint = true)
        Text(
            Luach.lengthText(Luach.days(g.from.date, g.to.date)),
            style = ltr, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tint,
            modifier = Modifier.padding(start = 66.dp, bottom = 2.dp)
        )
        LuachRow(g.to, today, isNext = g.to == next, padH = 8.dp, onTint = true)
    }
}

@Composable
private fun LuachRow(d: LuachDay, today: LocalDate, isNext: Boolean, padH: androidx.compose.ui.unit.Dp = 16.dp, onTint: Boolean = false) {
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
                Text(Dates.hebrewDayOf(d.day), fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = tint)
                Text(Dates.hebMonthName(d.month, d.year), fontSize = 10.5.sp, lineHeight = 12.sp, color = tint)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            d.note?.let { Text(it, fontSize = 12.5.sp, color = RC.sub2) }
            Text(Luach.whenText(d), style = ltr, fontSize = 12.5.sp, color = RC.sub)
        }
        Luach.countdown(d.date, today)?.let {
            if (isNext) Pill(it, RC.blue, RC.blueL, english = true) else Text(it, style = ltr, fontSize = 12.sp, color = RC.sub)
        }
    }
}

/** Bein hazmanim, from one zman's end to the next one's start - or open, when nothing follows. */
@Composable
private fun BeinBand(end: LuachDay, nextStart: LuachDay?, isNow: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(if (isNow) RC.blue else RC.sep))
        Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("בין הזמנים", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isNow) RC.blue else RC.sub2)
                if (isNow) Pill("עכשיו", Color.White, RC.blue)
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
