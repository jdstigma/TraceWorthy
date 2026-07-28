package com.traceworthy.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Renders the Profile charts to a PNG using Android's native Canvas (no Compose
 * capture, no external library), saves it to the phone's Pictures folder, and
 * opens the system share sheet. Returns the saved image Uri, or null on failure.
 */
object ChartImageExporter {

    private val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    private val human = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val dateFmt = SimpleDateFormat("MMM d", Locale.US)

    fun export(context: Context, stats: CallStats, entries: List<CallEntry>, rangeLabel: String): Uri? {
        val bitmap = render(stats, entries, rangeLabel)
        val fileName = "TraceWorthy_charts_${stamp.format(Date())}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return uri
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share TraceWorthy charts"))
    }

    private fun render(stats: CallStats, entries: List<CallEntry>, rangeLabel: String): Bitmap {
        val w = 1000
        val topNumbers = stats.perNumber.take(5)
        val h = 720 + topNumbers.size * 80 + 760
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AColor.WHITE)

        val title = paint(AColor.BLACK, 46f, bold = true)
        val h2 = paint(AColor.BLACK, 34f, bold = true)
        val body = paint(AColor.DKGRAY, 28f)
        val red = paint(AColor.rgb(0xB0, 0x00, 0x20))
        val green = paint(AColor.rgb(0x2E, 0x7D, 0x32))
        val blue = paint(AColor.rgb(0x15, 0x65, 0xC0))

        var y = 64f
        c.drawText("TraceWorthy - Call Evidence", 40f, y, title)
        y += 46f
        c.drawText("Range: $rangeLabel", 40f, y, body)
        y += 38f
        c.drawText("Generated: ${human.format(Date())}", 40f, y, body)
        y += 50f

        // --- Pie: flagged vs normal ---
        val flagged = stats.flaggedCalls
        val normal = (stats.totalCalls - stats.flaggedCalls).coerceAtLeast(0)
        val total = (flagged + normal).coerceAtLeast(1)
        val cx = 200f
        val cy = y + 150f
        val r = 140f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val flaggedSweep = 360f * flagged / total
        c.drawArc(rect, -90f, flaggedSweep, true, red)
        c.drawArc(rect, -90f + flaggedSweep, 360f - flaggedSweep, true, green)

        // legend beside the pie
        val lx = 420f
        c.drawText("Flagged vs. normal calls", lx, y + 24f, h2)
        var ly = y + 70f
        c.drawRect(lx, ly, lx + 30f, ly + 30f, red)
        c.drawText("Flagged: $flagged", lx + 45f, ly + 26f, body)
        ly += 52f
        c.drawRect(lx, ly, lx + 30f, ly + 30f, green)
        c.drawText("Normal: $normal", lx + 45f, ly + 26f, body)
        ly += 52f
        c.drawText("Total: ${stats.totalCalls}", lx, ly + 26f, body)

        y = cy + r + 80f

        // --- Top numbers bars ---
        c.drawText("Top numbers by call count", 40f, y, h2)
        y += 20f
        val maxCount = (topNumbers.maxOfOrNull { it.totalCount } ?: 1).coerceAtLeast(1)
        val barLeft = 40f
        val barMaxW = w - 80f
        topNumbers.forEach { n ->
            y += 44f
            val name = n.name?.takeIf { it.isNotBlank() } ?: n.number
            val flagTxt = if (n.flaggedCount > 0) "  [${n.flaggedCount} flagged]" else ""
            c.drawText("$name  -  ${n.totalCount} calls$flagTxt", barLeft, y, body)
            y += 14f
            val bw = barMaxW * n.totalCount / maxCount
            c.drawRect(barLeft, y, barLeft + bw, y + 26f, if (n.flaggedCount > 0) red else blue)
            y += 26f
        }

        // --- Scatter: calls over time (last 90 days), colored by top-5 numbers ---
        y += 70f
        c.drawText("Calls over time - last 90 days (date x time of day)", 40f, y, h2)
        y += 30f
        val plotLeft = 110f
        val plotRight = w - 40f
        val plotTop = y
        val plotH = 320f
        val plotBottom = plotTop + plotH
        val grid = Paint().apply { color = AColor.LTGRAY; strokeWidth = 2f; isAntiAlias = true }
        val yLabels = listOf("12a", "6p", "12p", "6a", "12a")
        for (i in 0..4) {
            val yy = plotTop + plotH * (i / 4f)
            c.drawLine(plotLeft, yy, plotRight, yy, grid)
            c.drawText(yLabels[i], 40f, yy + 9f, body)
        }
        val scEntries = ScatterColors.last90Days(entries)
        val scTop5 = ScatterColors.top5Numbers(scEntries)
        val scNums = scTop5.map { it.first }
        val dotPaints = (ScatterColors.top5 + ScatterColors.other).associateWith { paint(it.toInt()) }
        if (scEntries.isNotEmpty()) {
            val minT = scEntries.minOf { it.timestampMillis }
            val maxT = scEntries.maxOf { it.timestampMillis }
            val spanT = (maxT - minT).coerceAtLeast(1L).toFloat()
            scEntries.forEach { e ->
                val xf = (e.timestampMillis - minT) / spanT
                val cal = Calendar.getInstance().apply { timeInMillis = e.timestampMillis }
                val hourFrac = (cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f) / 24f
                val px = plotLeft + (plotRight - plotLeft) * xf
                val py = plotBottom - plotH * hourFrac
                c.drawCircle(px, py, 7f, dotPaints.getValue(ScatterColors.colorFor(e.number, scNums)))
            }
            y = plotBottom + 38f
            c.drawText(dateFmt.format(Date(minT)), plotLeft, y, body)
            val endLabel = dateFmt.format(Date(maxT))
            c.drawText(endLabel, plotRight - body.measureText(endLabel), y, body)
        } else {
            y = plotBottom + 38f
            c.drawText("No calls in the last 90 days.", plotLeft, y, body)
        }

        // Legend: top-5 numbers
        y += 54f
        c.drawText("Top numbers", 40f, y, h2)
        scTop5.forEachIndexed { i, (_, name) ->
            y += 44f
            c.drawRect(40f, y - 26f, 74f, y + 4f, dotPaints.getValue(ScatterColors.top5[i]))
            c.drawText(name, 90f, y, body)
        }

        return bmp
    }

    private fun paint(color: Int, size: Float = 28f, bold: Boolean = false) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        isFakeBoldText = bold
    }
}
