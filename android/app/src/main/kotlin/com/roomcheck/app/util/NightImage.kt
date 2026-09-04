package com.roomcheck.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.roomcheck.app.data.Dates
import com.roomcheck.app.data.Mark
import com.roomcheck.app.data.NightLogic
import com.roomcheck.app.data.Room
import com.roomcheck.app.data.Roster
import com.roomcheck.app.data.Slot
import java.io.File
import java.io.FileOutputStream

/**
 * The whole night as one picture to send: who was missing at each time up top, where it is read
 * first, and the filled-in sheet underneath as the backing detail.
 *
 * Shaped to be read WITHOUT opening it. A chat app shows a picture in the thread only down to
 * about 1.4 times as tall as it is wide and crops whatever is past that, so this is laid out
 * in two columns and kept inside [MAX_RATIO]. A tall single column put most of the sheet below
 * the fold, where it may as well not have been drawn.
 */
object NightImage {

    private const val MAX_RATIO = 1.28f    // height : width, with room to spare under the crop
    private const val PAD = 64f
    private const val GAP = 56f            // between the two sheet columns
    private const val ROW_H = 76f          // one person
    private const val ROOM_HEAD_H = 66f    // the "Room 3" band above each group
    private const val BAND_H = 74f         // the header strip over the sheet
    private const val CHIP = 52f
    private const val COL_W = 88f          // one time column

    private val ink = Color.parseColor("#111114")
    private val sub = Color.parseColor("#6C6E76")
    private val faint = Color.parseColor("#B7B9C2")
    private val hair = Color.parseColor("#E4E4E9")
    private val panel = Color.parseColor("#F2F2F7")
    private val green = Color.parseColor("#12795A")
    private val greenL = Color.parseColor("#E6F4EE")
    private val red = Color.parseColor("#C42B21")
    private val redL = Color.parseColor("#FBEBEA")
    private val grey = Color.parseColor("#8A8A8E")
    private val greyL = Color.parseColor("#EEEEF1")

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun namePaint() = paint(34f, ink)

    /** Greedy wrap - the missing-names lines are the only text here long enough to need it. */
    private fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var line = StringBuilder()
        text.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                out.add(line.toString()); line = StringBuilder(word)
            }
        }
        out.add(line.toString())
        return out
    }

    // tone carries the meaning: red for names, green only for a round actually walked and clear,
// grey for one nobody has marked. Green on "not marked" would read as an all-clear it has not earned.
private class Summary(val label: String, val lines: List<String>, val tone: Int)

    /**
     * One card per time, each holding its own names one to a line. Run together with commas the
     * three lists were a wall of text you had to parse to find a single name in; as three lists
     * side by side you read down the one you want and stop.
     */
    private fun summarize(logic: NightLogic, slots: List<Slot>, hebrew: Boolean, cardTextW: Float): List<Summary> {
        val p = paint(NAME_SZ, red)
        return slots.map { slot ->
            val sid = slot.id
            val missing = logic.missingAt(sid)
            val started = logic.stats(sid).started
            val lines = if (missing.isNotEmpty()) {
                // a name too long for the card wraps rather than being cut
                missing.flatMap { wrap(logic.nameOf(it, hebrew), p, cardTextW) }
            } else {
                listOf(
                    if (!started) (if (hebrew) NOT_MARKED_HE else "Not marked")
                    else (if (hebrew) ALL_IN_HE else "Everybody there")
                )
            }
            val tone = if (missing.isNotEmpty()) red else if (started) green else grey
            Summary(slot.label, lines, tone)
        }
    }

    /** One card per time on the sheet - which is not always three, so it cannot be divided by 3. */
    private fun cardWidth(w: Float, n: Int) = (w - 2 * PAD - (n - 1) * CARD_GAP) / n
    private fun cardHeight(cards: List<Summary>) =
        CARD_HEAD + cards.maxOf { it.lines.size } * LINE_H + CARD_PAD

    private fun peopleIn(room: Room) = room.beds.sumOf { it.slots.size }
    private fun blockH(room: Room) = ROOM_HEAD_H + peopleIn(room) * ROW_H

    /** Split the rooms into two columns of near-equal height, so neither runs long alone. */
    private fun splitPoint(): Int {
        val half = Roster.PLAN.sumOf { blockH(it).toDouble() } / 2.0
        var run = 0.0
        Roster.PLAN.forEachIndexed { i, room ->
            run += blockH(room)
            if (run >= half) return i + 1
        }
        return Roster.PLAN.size
    }

    /**
     * Widest name plus its bunk label, so a column is built around the longest name there is and
     * nothing is ever shortened to fit. The picture's width follows from the roster, not a guess.
     */
    private fun widestNameWidth(logic: NightLogic, hebrew: Boolean): Float {
        val np = namePaint()
        return Roster.PEOPLE.maxOf { np.measureText(logic.nameOf(it.id, hebrew)) }
    }

    fun render(logic: NightLogic, dateKey: String, hebrew: Boolean = false, slots: List<Slot> = logic.slots): Bitmap {
        val colW = widestNameWidth(logic, hebrew) + 40f + slots.size * COL_W
        var w = 2 * colW + GAP + 2 * PAD
        var summaries = summarize(logic, slots, hebrew, cardWidth(w, slots.size) - 2 * CARD_PAD)
        var h = heightOf(summaries)
        // A heavy night makes the summary long. Widen rather than let the thread crop it: the
        // extra width re-wraps the names shorter, so one pass always lands inside the ratio.
        if (h / w > MAX_RATIO) {
            w = h / MAX_RATIO
            summaries = summarize(logic, slots, hebrew, cardWidth(w, slots.size) - 2 * CARD_PAD)
            h = heightOf(summaries)
        }

        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val d = Dir(w, rtl = hebrew)
        drawHeader(c, dateKey, d)
        drawSummary(c, summaries, HEADER_H, d)
        drawSheet(c, logic, slots, hebrew, HEADER_H + summaryH(summaries), d)
        return bmp
    }

    private const val HEADER_H = 190f
    private const val TITLE_H = 76f      // the heading over the names
    private const val CARD_GAP = 26f     // between the three time cards
    private const val CARD_PAD = 28f     // inside one
    private const val CARD_HEAD = 96f    // the time at the top of a card, down to its first name
    private const val LINE_H = 50f       // one name
    private const val NAME_SZ = 32f
    // A Hebrew sheet is Hebrew throughout - a lone English phrase in the middle of it reads
    // like something the app forgot to translate.
    private const val TITLE = "חסרים"                  // absent
    private const val ALL_IN_HE = "כולם היו"           // everybody there
    // "not marked", not "not checked yet": a round can be missed outright, and "yet" promises
    // it is still coming.
    private const val NOT_MARKED_HE = "לא סומן"            // not marked

    private fun summaryH(s: List<Summary>): Float = 28f + TITLE_H + cardHeight(s) + 30f

    private fun heightOf(summaries: List<Summary>): Float {
        val split = splitPoint()
        val left = Roster.PLAN.take(split).sumOf { blockH(it).toDouble() }.toFloat()
        val right = Roster.PLAN.drop(split).sumOf { blockH(it).toDouble() }.toFloat()
        return HEADER_H + summaryH(summaries) + BAND_H + maxOf(left, right) + PAD
    }

    /**
     * Mirrors the whole sheet for a Hebrew night. Everything is still laid out left-to-right and
     * then flipped here, so there is one layout to reason about rather than two. A Hebrew sheet
     * that only translated the words still read from the left - names on the left, first column on
     * the left, 11:15 nearest the wrong edge - which is not how the page is scanned.
     */
    private class Dir(val w: Float, val rtl: Boolean) {
        /** A point measured in from the edge you start reading at. */
        fun p(x: Float) = if (rtl) w - x else x

        /** Left edge of a box whose left-to-right left edge is [x]. */
        fun box(x: Float, boxW: Float) = if (rtl) w - x - boxW else x

        /** Text grows away from the reading edge, so its anchor flips with the direction. */
        fun align(p: Paint) = p.apply { textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT }
    }

    private fun drawHeader(c: Canvas, dateKey: String, d: Dir) {
        c.drawText(Dates.hebrewDayMonth(dateKey), d.p(PAD), 104f, d.align(paint(64f, ink, bold = true)))
        c.drawText(Dates.hebrewNightName(dateKey), d.p(PAD), 156f, d.align(paint(38f, sub)))
        c.drawLine(PAD, 186f, d.w - PAD, 186f, Paint().apply { color = hair; strokeWidth = 2f })
    }

    /** Who was missing, at the top, so it answers the question before anything is opened. */
    private fun drawSummary(c: Canvas, summaries: List<Summary>, startY: Float, d: Dir) {
        var y = startY + 28f
        c.drawText(TITLE, d.p(PAD), y + 46f, d.align(paint(50f, ink, bold = true)))
        y += TITLE_H

        // All three cards take the tallest card's height. Sized to their own contents they would
        // stair-step down the page - three ragged boxes read worse than three aligned ones.
        val cardW = cardWidth(d.w, summaries.size)
        val cardH = cardHeight(summaries)
        summaries.forEachIndexed { i, s ->
            val x = PAD + i * (cardW + CARD_GAP)
            val left = d.box(x, cardW)
            c.drawRoundRect(
                RectF(left, y, left + cardW, y + cardH), 20f, 20f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panel }
            )
            c.drawText(s.label, d.p(x + CARD_PAD), y + 52f, d.align(paint(40f, ink, bold = true)))
            val p = d.align(paint(NAME_SZ, s.tone))
            s.lines.forEachIndexed { j, line ->
                c.drawText(line, d.p(x + CARD_PAD), y + CARD_HEAD + j * LINE_H, p)
            }
        }
    }

    /** The filled-in sheet: every bochur, every time, in two columns so it all fits above the fold. */
    private fun drawSheet(c: Canvas, logic: NightLogic, slots: List<Slot>, hebrew: Boolean, startY: Float, d: Dir) {
        val colW = (d.w - 2 * PAD - GAP) / 2f
        val colX = floatArrayOf(PAD, PAD + colW + GAP)
        val n = slots.size

        // No grey slab across the page: the times are a column heading, so they read as one -
        // small, faint, over a rule the width of their own column. A filled band drew the eye to
        // the furniture instead of to the sheet.
        val rule = Paint().apply { color = hair; strokeWidth = 2.5f }
        colX.forEach { x ->
            slots.forEachIndexed { i, (_, label) ->
                val p = paint(27f, faint, bold = true).apply { textAlign = Paint.Align.CENTER }
                c.drawText(label, d.p(x + colW - (n - 0.5f - i) * COL_W), startY + 40f, p)
            }
            c.drawLine(d.p(x), startY + BAND_H - 14f, d.p(x + colW), startY + BAND_H - 14f, rule)
        }

        // Mirroring puts the first group of rooms in the right-hand column on its own.
        val split = splitPoint()
        drawColumn(c, logic, slots, hebrew, Roster.PLAN.take(split), colX[0], startY + BAND_H, colW, d)
        drawColumn(c, logic, slots, hebrew, Roster.PLAN.drop(split), colX[1], startY + BAND_H, colW, d)
    }

    private fun drawColumn(
        c: Canvas, logic: NightLogic, slots: List<Slot>, hebrew: Boolean, rooms: List<Room>,
        x: Float, top: Float, colW: Float, d: Dir
    ) {
        val hairline = Paint().apply { color = hair; strokeWidth = 1.5f }
        var y = top
        rooms.forEach { room ->
            val label = if (hebrew) room.hebLabel else room.label.uppercase()
            c.drawText(label, d.p(x), y + 44f, d.align(paint(28f, faint, bold = true)))
            y += ROOM_HEAD_H
            room.beds.forEach { bed ->
                bed.slots.forEach { pid ->
                    drawPersonRow(c, logic, slots, hebrew, pid, x, y, colW, d)
                    c.drawLine(d.p(x), y + ROW_H, d.p(x + colW), y + ROW_H, hairline)
                    y += ROW_H
                }
            }
        }
    }

    private fun drawPersonRow(
        c: Canvas, logic: NightLogic, slots: List<Slot>, hebrew: Boolean, pid: String,
        x: Float, y: Float, colW: Float, d: Dir
    ) {
        val mid = y + ROW_H / 2f
        c.drawText(logic.nameOf(pid, hebrew), d.p(x), mid + 12f, d.align(namePaint()))
        slots.forEachIndexed { i, slot ->
            val cx = x + colW - (slots.size - i) * COL_W + (COL_W - CHIP) / 2f
            drawChip(c, logic.statusOf(pid, slot.id), d.box(cx, CHIP), mid - CHIP / 2f)
        }
    }

    private fun drawChip(c: Canvas, mark: Mark?, x: Float, y: Float) {
        val (bg, fg) = when (mark) {
            Mark.IN -> greenL to green
            Mark.OUT -> redL to red
            Mark.EXC -> greyL to grey
            null -> Color.parseColor("#F7F7F9") to faint
        }
        c.drawRoundRect(RectF(x, y, x + CHIP, y + CHIP), 13f, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        val cx = x + CHIP / 2f
        val cy = y + CHIP / 2f
        when (mark) {
            // drawn as strokes rather than glyphs so the sheet cannot depend on a font's tick mark
            Mark.IN -> {
                c.drawLine(cx - 12f, cy + 1f, cx - 3f, cy + 10f, stroke)
                c.drawLine(cx - 3f, cy + 10f, cx + 12f, cy - 10f, stroke)
            }
            Mark.OUT -> {
                c.drawLine(cx - 10f, cy - 10f, cx + 10f, cy + 10f, stroke)
                c.drawLine(cx + 10f, cy - 10f, cx - 10f, cy + 10f, stroke)
            }
            Mark.EXC -> {
                val p = paint(32f, fg, bold = true)
                c.drawText("E", cx - p.measureText("E") / 2f, cy + 11f, p)
            }
            null -> {
                val p = paint(32f, fg)
                c.drawText("–", cx - p.measureText("–") / 2f, cy + 11f, p)
            }
        }
    }

    /**
     * Writes the sheet to a cache file and hands it to the share sheet as a picture, and only a
     * picture - no message text riding along with it. Copy text is its own button.
     */
    fun share(context: Context, logic: NightLogic, dateKey: String, hebrew: Boolean = false, slots: List<Slot> = logic.slots) {
        val bmp = render(logic, dateKey, hebrew, slots)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "room-check-$dateKey.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send tonight's sheet").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
