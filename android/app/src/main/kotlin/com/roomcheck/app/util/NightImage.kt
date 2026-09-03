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
    private const val GUTTER = 170f        // the time labels down the left of the summary

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
    private fun bunkPaint() = paint(22f, faint)

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

    private class Summary(val label: String, val lines: List<String>, val missing: Boolean)

    private fun summarize(logic: NightLogic, textWidth: Float): List<Summary> {
        val p = paint(38f, red)
        return Roster.SIDS.mapIndexed { i, sid ->
            val missing = logic.missingAt(sid)
            val text = when {
                missing.isNotEmpty() -> missing.joinToString(", ") { logic.nameOf(it) }
                !logic.stats(sid).started -> "Not checked yet"
                else -> "Everybody there"
            }
            Summary(Roster.SLOTS[i].second, wrap(text, p, textWidth), missing.isNotEmpty())
        }
    }

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
    private fun widestNameWidth(logic: NightLogic): Float {
        val np = namePaint()
        val bp = bunkPaint()
        val label = bp.measureText("bottom") + 10f
        return Roster.PEOPLE.maxOf { np.measureText(logic.nameOf(it.id)) } + label
    }

    fun render(logic: NightLogic, dateKey: String): Bitmap {
        val colW = widestNameWidth(logic) + 24f + 3 * COL_W
        var w = 2 * colW + GAP + 2 * PAD
        var summaries = summarize(logic, w - 2 * PAD - GUTTER)
        var h = heightOf(summaries)
        // A heavy night makes the summary long. Widen rather than let the thread crop it: the
        // extra width re-wraps the names shorter, so one pass always lands inside the ratio.
        if (h / w > MAX_RATIO) {
            w = h / MAX_RATIO
            summaries = summarize(logic, w - 2 * PAD - GUTTER)
            h = heightOf(summaries)
        }

        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        drawHeader(c, dateKey, w)
        drawSummary(c, summaries, HEADER_H)
        drawSheet(c, logic, HEADER_H + summaryH(summaries), w)
        return bmp
    }

    private const val HEADER_H = 190f

    private fun summaryH(s: List<Summary>): Float =
        28f + s.sumOf { (60f + it.lines.size * 50f + 22f).toDouble() }.toFloat()

    private fun heightOf(summaries: List<Summary>): Float {
        val split = splitPoint()
        val left = Roster.PLAN.take(split).sumOf { blockH(it).toDouble() }.toFloat()
        val right = Roster.PLAN.drop(split).sumOf { blockH(it).toDouble() }.toFloat()
        return HEADER_H + summaryH(summaries) + BAND_H + maxOf(left, right) + PAD
    }

    private fun drawHeader(c: Canvas, dateKey: String, w: Float) {
        c.drawText(Dates.hebrewDayMonth(dateKey), PAD, 104f, paint(64f, ink, bold = true))
        c.drawText(Dates.longDate(dateKey), PAD, 156f, paint(34f, sub))
        c.drawLine(PAD, 186f, w - PAD, 186f, Paint().apply { color = hair; strokeWidth = 2f })
    }

    /** Who was missing, at the top, so it answers the question before anything is opened. */
    private fun drawSummary(c: Canvas, summaries: List<Summary>, startY: Float) {
        var y = startY + 28f
        summaries.forEach { s ->
            c.drawText(s.label, PAD, y + 42f, paint(42f, ink, bold = true))
            val p = paint(38f, if (s.missing) red else green)
            s.lines.forEachIndexed { i, line -> c.drawText(line, PAD + GUTTER, y + 42f + i * 50f, p) }
            y += 60f + s.lines.size * 50f + 22f
        }
    }

    /** The filled-in sheet: every bochur, every time, in two columns so it all fits above the fold. */
    private fun drawSheet(c: Canvas, logic: NightLogic, startY: Float, w: Float) {
        val colW = (w - 2 * PAD - GAP) / 2f
        val colX = floatArrayOf(PAD, PAD + colW + GAP)

        c.drawRect(0f, startY, w, startY + BAND_H, Paint().apply { color = panel })
        c.drawText("Sheet", PAD, startY + 48f, paint(30f, sub, bold = true))
        colX.forEach { x ->
            Roster.SLOTS.forEachIndexed { i, (_, label) ->
                val p = paint(28f, sub, bold = true)
                val cx = x + colW - (2.5f - i) * COL_W
                c.drawText(label, cx - p.measureText(label) / 2f, startY + 48f, p)
            }
        }

        val split = splitPoint()
        drawColumn(c, logic, Roster.PLAN.take(split), colX[0], startY + BAND_H, colW)
        drawColumn(c, logic, Roster.PLAN.drop(split), colX[1], startY + BAND_H, colW)
    }

    private fun drawColumn(c: Canvas, logic: NightLogic, rooms: List<Room>, x: Float, top: Float, colW: Float) {
        val hairline = Paint().apply { color = hair; strokeWidth = 1.5f }
        var y = top
        rooms.forEach { room ->
            c.drawText(room.label.uppercase(), x, y + 44f, paint(28f, faint, bold = true))
            y += ROOM_HEAD_H
            room.beds.forEach { bed ->
                bed.slots.forEachIndexed { i, pid ->
                    val bunk = if (bed.slots.size > 1) (if (i == 0) "top" else "bottom") else null
                    drawPersonRow(c, logic, pid, bunk, x, y, colW)
                    c.drawLine(x, y + ROW_H, x + colW, y + ROW_H, hairline)
                    y += ROW_H
                }
            }
        }
    }

    private fun drawPersonRow(c: Canvas, logic: NightLogic, pid: String, bunk: String?, x: Float, y: Float, colW: Float) {
        val mid = y + ROW_H / 2f
        val np = namePaint()
        val name = logic.nameOf(pid)
        c.drawText(name, x, mid + 12f, np)
        bunk?.let { c.drawText(it, x + np.measureText(name) + 10f, mid + 11f, bunkPaint()) }
        Roster.SIDS.forEachIndexed { i, sid ->
            val cx = x + colW - (3 - i) * COL_W + (COL_W - CHIP) / 2f
            drawChip(c, logic.statusOf(pid, sid), cx, mid - CHIP / 2f)
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
    fun share(context: Context, logic: NightLogic, dateKey: String) {
        val bmp = render(logic, dateKey)
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
