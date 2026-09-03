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
import com.roomcheck.app.data.Roster
import java.io.File
import java.io.FileOutputStream

/**
 * The whole night as one picture to send: who was missing at each time up top, where it is read
 * first, and the filled-in sheet underneath as the backing detail.
 *
 * Drawn straight onto a Bitmap rather than by screenshotting the app, so it is laid out for
 * reading in a chat thread - one column, every one of the thirty names, nothing cropped.
 */
object NightImage {

    private const val W = 1080
    private const val PAD = 56f
    private const val ROW_H = 62f          // one person
    private const val ROOM_HEAD_H = 58f    // the "Room 3" band above each group
    private const val CHIP = 46f           // a mark chip in the sheet
    private const val COL_W = 96f          // width of one time column

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

    fun render(logic: NightLogic, dateKey: String): Bitmap {
        val namesPaint = paint(34f, red)
        val bodyW = W - 2 * PAD

        // Measure first: the sheet is a fixed height, the summary grows with the names in it.
        val summaries = Roster.SIDS.mapIndexed { i, sid ->
            val missing = logic.missingAt(sid)
            val text = when {
                missing.isNotEmpty() -> missing.joinToString(", ") { logic.nameOf(it) }
                !logic.stats(sid).started -> "Not checked yet"
                else -> "Everybody there"
            }
            Summary(Roster.SLOTS[i].second, wrap(text, namesPaint, bodyW - 150f), missing.isNotEmpty())
        }

        val headerH = 190f
        val summaryH = 26f + summaries.sumOf { (54f + it.lines.size * 44f + 18f).toDouble() }.toFloat() + 20f
        val sheetH = 82f + Roster.PLAN.sumOf {
            (ROOM_HEAD_H + it.beds.sumOf { b -> b.slots.size } * ROW_H).toDouble()
        }.toFloat() + 34f
        val height = (headerH + summaryH + sheetH + PAD).toInt()

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        var y = drawHeader(c, dateKey)
        y = drawSummary(c, summaries, y)
        drawSheet(c, logic, y)
        return bmp
    }

    private fun drawHeader(c: Canvas, dateKey: String): Float {
        c.drawText(Dates.hebrewDayMonth(dateKey), PAD, 96f, paint(58f, ink, bold = true))
        c.drawText(Dates.longDate(dateKey), PAD, 142f, paint(30f, sub))
        c.drawLine(PAD, 176f, W - PAD, 176f, Paint().apply { color = hair; strokeWidth = 2f })
        return 176f
    }

    /** Who was missing, at the top, so it answers the question before anything is scrolled. */
    private fun drawSummary(c: Canvas, summaries: List<Summary>, startY: Float): Float {
        var y = startY + 26f
        summaries.forEach { s ->
            c.drawText(s.label, PAD, y + 38f, paint(38f, ink, bold = true))
            val p = paint(34f, if (s.missing) red else green)
            s.lines.forEachIndexed { i, line ->
                c.drawText(line, PAD + 150f, y + 38f + i * 44f, p)
            }
            y += 54f + s.lines.size * 44f + 18f
        }
        return y + 20f
    }

    /** The filled-in sheet: every bochur, every time, exactly as it was marked. */
    private fun drawSheet(c: Canvas, logic: NightLogic, startY: Float) {
        val colX = FloatArray(3) { W - PAD - (3 - it) * COL_W + (COL_W - CHIP) / 2f }
        var y = startY

        c.drawRect(0f, y, W.toFloat(), y + 62f, Paint().apply { color = panel })
        c.drawText("Sheet", PAD, y + 42f, paint(30f, sub, bold = true))
        Roster.SLOTS.forEachIndexed { i, (_, label) ->
            val p = paint(26f, sub, bold = true)
            c.drawText(label, colX[i] + CHIP / 2f - p.measureText(label) / 2f, y + 42f, p)
        }
        y += 82f

        val hairline = Paint().apply { color = hair; strokeWidth = 1.5f }
        Roster.PLAN.forEach { room ->
            c.drawText(room.label.uppercase(), PAD, y + 38f, paint(26f, faint, bold = true))
            y += ROOM_HEAD_H
            room.beds.forEach { bed ->
                bed.slots.forEachIndexed { i, pid ->
                    val bunk = if (bed.slots.size > 1) (if (i == 0) "top" else "bottom") else null
                    drawPersonRow(c, logic, pid, bunk, y, colX)
                    c.drawLine(PAD, y + ROW_H, W - PAD, y + ROW_H, hairline)
                    y += ROW_H
                }
            }
        }
    }

    private fun drawPersonRow(c: Canvas, logic: NightLogic, pid: String, bunk: String?, y: Float, colX: FloatArray) {
        val mid = y + ROW_H / 2f
        c.drawText(logic.nameOf(pid), PAD, mid + 11f, paint(32f, ink))
        bunk?.let {
            val p = paint(22f, faint)
            c.drawText(it, PAD + logic.nameOf(pid).let { n -> paint(32f, ink).measureText(n) } + 14f, mid + 10f, p)
        }
        Roster.SIDS.forEachIndexed { i, sid ->
            drawChip(c, logic.statusOf(pid, sid), colX[i], mid - CHIP / 2f)
        }
    }

    private fun drawChip(c: Canvas, mark: Mark?, x: Float, y: Float) {
        val (bg, fg) = when (mark) {
            Mark.IN -> greenL to green
            Mark.OUT -> redL to red
            Mark.EXC -> greyL to grey
            null -> Color.parseColor("#F7F7F9") to faint
        }
        c.drawRoundRect(RectF(x, y, x + CHIP, y + CHIP), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg; strokeWidth = 4.5f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        val cx = x + CHIP / 2f
        val cy = y + CHIP / 2f
        when (mark) {
            // drawn as strokes rather than glyphs so the sheet cannot depend on a font's tick mark
            Mark.IN -> {
                c.drawLine(cx - 11f, cy + 1f, cx - 3f, cy + 9f, stroke)
                c.drawLine(cx - 3f, cy + 9f, cx + 11f, cy - 9f, stroke)
            }
            Mark.OUT -> {
                c.drawLine(cx - 9f, cy - 9f, cx + 9f, cy + 9f, stroke)
                c.drawLine(cx + 9f, cy - 9f, cx - 9f, cy + 9f, stroke)
            }
            Mark.EXC -> {
                val p = paint(28f, fg, bold = true)
                c.drawText("E", cx - p.measureText("E") / 2f, cy + 10f, p)
            }
            null -> {
                val p = paint(28f, fg)
                c.drawText("–", cx - p.measureText("–") / 2f, cy + 10f, p)
            }
        }
    }

    /** Writes the sheet to a cache file and hands it to the share sheet as a picture. */
    fun share(context: Context, logic: NightLogic, dateKey: String) {
        val bmp = render(logic, dateKey)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "room-check-$dateKey.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, logic.report(dateKey))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send tonight's sheet").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
