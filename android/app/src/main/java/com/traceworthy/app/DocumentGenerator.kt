package com.traceworthy.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One document the app can produce, filled from the user's info + call stats.
 * [seq] is the position in the evidence packet (0 = the bundle itself). Generated
 * filenames carry it — "TraceWorthy_04_FCC_complaint_….pdf" — so the individual
 * PDFs sort into filing order in a folder and drop straight into an Acrobat
 * "Combine Files" without re-ordering.
 */
enum class DocumentType(val displayName: String, val fileSlug: String, val seq: Int, val blurb: String) {
    EvidencePacket(
        "Full evidence packet",
        "evidence_packet",
        0,
        "Bundles every document below into one PDF with a cover page and index — hand this to police or the FCC.",
    ),
    EvidenceSummary(
        "Evidence summary",
        "evidence_summary",
        1,
        "One-page snapshot of your call statistics to attach to any filing.",
    ),
    IncidentTimeline(
        "Incident timeline",
        "incident_timeline",
        2,
        "Chronological log built from your dated notes — shows the pattern and any escalation.",
    ),
    CarrierScript(
        "Carrier call script",
        "carrier_script",
        3,
        "Word-for-word script for opening a documented harassment case with your carrier.",
    ),
    FccComplaint(
        "FCC complaint",
        "FCC_complaint",
        4,
        "Federal record of the spoofing campaign. Paste the description into consumercomplaints.fcc.gov.",
    ),
    PoliceReport(
        "Harassment–police report cover note",
        "police_report",
        5,
        "Cover note + talking points to hand police so they can subpoena your carrier.",
    ),
    NonDisclosureOrder(
        "Non-disclosure order request",
        "non_disclosure_order",
        6,
        "Ask police to pair the carrier subpoena with a court order that keeps it secret, so the caller isn't tipped off.",
    ),
}

/** A block of content in a generated document. */
internal sealed interface Block {
    data class Title(val text: String) : Block
    /** [addable] marks a section whose list the user may extend with "+ Add" (e.g. caller numbers). */
    data class Heading(val text: String, val addable: Boolean = false) : Block
    /** [editable] marks the free-text narrative ("notes") the user may edit in the preview. */
    data class Body(val text: String, val editable: Boolean = false) : Block
    data class Bullet(val text: String) : Block
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Block
    data class Pie(val flagged: Int, val normal: Int) : Block
    data class BarChart(val bars: List<ChartBar>) : Block
    data class Scatter(val dots: List<ScatterDot>) : Block
    data class Gap(val points: Float) : Block
    data object PageBreak : Block
}

/** One bar in a document chart. [highlight] draws it red (a flagged number). */
internal data class ChartBar(val label: String, val value: Int, val highlight: Boolean)

/** One call on the time scatter: [timeMillis] gives the date + time of day; [colorRgb] is its top-5 color (ARGB). */
internal data class ScatterDot(val timeMillis: Long, val colorRgb: Long)

/** What kind of preview row this is — drives how the editor renders it. */
enum class PreviewKind { Body, Bullet, Structural }

/**
 * One editable/read-only row inside a preview section. [id] is stable across add/remove
 * so the UI can key fields and target edits even as rows shift.
 */
data class EditRow(
    val id: Long,
    val kind: PreviewKind,
    val editable: Boolean,
    val removable: Boolean,
    val text: String,
    val label: String,
)

/**
 * A collapsible group in the preview, keyed to one of the document's own headings (or its
 * title). [canAddBullet] is true when the section already holds list items, so "+ Add" makes
 * sense there (e.g. curating caller numbers).
 */
data class EditSection(
    val id: Long,
    val title: String,
    val rows: List<EditRow>,
    val canAddBullet: Boolean,
)

/**
 * A built document the user previews and edits before it is rendered to PDF. Content is
 * grouped by the document's own titles/headings into [sections]; only body paragraphs and
 * list items are editable (titles, headings, stats and charts stay auto). Rows carry stable
 * ids so bullets can be added/removed (e.g. curating the caller-number list) without index drift.
 */
class EditableDocument internal constructor(
    private val fileSlug: String,
    initial: List<Block>,
) {
    private class Item(val id: Long, var block: Block, val userAdded: Boolean = false)

    private var seq = 0L
    private val items = initial.map { Item(seq++, it) }.toMutableList()

    private companion object { const val INTRO_ID = -1L }

    /** Group the blocks into collapsible sections. Titles and headings each start a section. */
    fun sections(): List<EditSection> {
        val out = mutableListOf<EditSection>()
        var secId = INTRO_ID
        var secTitle = "Overview"
        var secAddable = false
        var started = false
        var rows = mutableListOf<EditRow>()

        fun flush() {
            if (started || rows.isNotEmpty()) {
                out.add(EditSection(secId, secTitle, rows.toList(), secAddable && rows.any { it.kind == PreviewKind.Bullet }))
            }
        }

        items.forEach { item ->
            when (val b = item.block) {
                is Block.Title -> { flush(); secId = item.id; secTitle = b.text; secAddable = false; started = true; rows = mutableListOf() }
                is Block.Heading -> { flush(); secId = item.id; secTitle = b.text; secAddable = b.addable; started = true; rows = mutableListOf() }
                is Block.Body -> rows.add(EditRow(item.id, PreviewKind.Body, b.editable, false, b.text, "Notes"))
                is Block.Bullet -> rows.add(EditRow(item.id, PreviewKind.Bullet, item.userAdded, true, b.text, "Item"))
                is Block.Table -> rows.add(EditRow(item.id, PreviewKind.Structural, false, false, "", "Table — ${b.headers.joinToString(" / ")}"))
                is Block.Pie -> rows.add(EditRow(item.id, PreviewKind.Structural, false, false, "", "Chart — flagged vs normal (${b.flagged} / ${b.normal})"))
                is Block.BarChart -> rows.add(EditRow(item.id, PreviewKind.Structural, false, false, "", "Chart — ${b.bars.size} bar${if (b.bars.size == 1) "" else "s"}"))
                is Block.Scatter -> rows.add(EditRow(item.id, PreviewKind.Structural, false, false, "", "Chart — calls over time (${b.dots.size} calls)"))
                Block.PageBreak -> Unit
                is Block.Gap -> Unit
            }
        }
        flush()
        return out
    }

    /** Replace the text of an editable row; no-op for structural/missing rows. */
    fun updateText(id: Long, newText: String) {
        val item = items.firstOrNull { it.id == id } ?: return
        item.block = when (val b = item.block) {
            is Block.Body -> b.copy(text = newText)
            is Block.Bullet -> b.copy(text = newText)
            else -> b
        }
    }

    /** Remove a row (used by the "–" on list items). */
    fun removeRow(id: Long) {
        items.removeAll { it.id == id }
    }

    /** Append a new bullet to the end of [sectionId]'s run; returns its id for focusing/editing. */
    fun addBulletInSection(sectionId: Long, text: String = ""): Long {
        val anchor = if (sectionId == INTRO_ID) -1 else items.indexOfFirst { it.id == sectionId }
        var insertAt = anchor + 1
        while (insertAt < items.size) {
            val b = items[insertAt].block
            if (b is Block.Title || b is Block.Heading) break
            insertAt++
        }
        val item = Item(seq++, Block.Bullet(text), userAdded = true)
        items.add(insertAt, item)
        return item.id
    }

    /** Render the (possibly edited) blocks to a PDF in Downloads. */
    fun render(context: Context): DocumentGenerator.Result =
        DocumentGenerator.writePdf(context, fileSlug, items.map { it.block })
}

object DocumentGenerator {

    private val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    private val human = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val rangeFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val incidentFmt = SimpleDateFormat("EEE MMM d, yyyy · h:mm a", Locale.US)

    // US Letter at 72 dpi.
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 54f

    data class Result(val uri: Uri?, val path: String)

    /** Filename stem: seq-prefixed so the PDFs sort into filing order (see DocumentType). */
    private fun fileStem(type: DocumentType): String =
        "%02d_%s".format(type.seq, type.fileSlug)

    /** Build + render a document straight to PDF (no editing step). */
    fun generate(
        context: Context,
        type: DocumentType,
        profile: UserProfile,
        entries: List<CallEntry>,
    ): Result = writePdf(context, fileStem(type), buildDoc(context, type, profile, entries))

    /**
     * Build the document's blocks, wrapped so the UI can preview and edit the text
     * before rendering. Structural blocks (tables/charts) pass through untouched.
     */
    fun buildEditable(
        context: Context,
        type: DocumentType,
        profile: UserProfile,
        entries: List<CallEntry>,
    ): EditableDocument = EditableDocument(fileStem(type), buildDoc(context, type, profile, entries))

    private fun buildDoc(
        context: Context,
        type: DocumentType,
        profile: UserProfile,
        entries: List<CallEntry>,
    ): List<Block> {
        val stats = CallStats.from(entries)
        val branches = BranchStore.all(context)
        return buildBlocks(type, profile, stats, entries, branches)
    }

    /** Render the given blocks to a PDF saved in Downloads via MediaStore. */
    internal fun writePdf(context: Context, fileSlug: String, blocks: List<Block>): Result {
        val doc = renderPdf(blocks)

        val fileName = "TraceWorthy_${fileSlug}_${stamp.format(Date())}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            doc.close()
            return Result(null, "Failed to create file")
        }
        resolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
        return Result(uri, "Downloads/$fileName")
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share TraceWorthy document"))
    }

    // -- Rendering engine ---------------------------------------------------

    private fun renderPdf(blocks: List<Block>): PdfDocument {
        val pdf = PdfDocument()
        val title = paint(AColor.rgb(0x0F, 0x1E, 0x33), 20f, bold = true)
        val heading = paint(AColor.rgb(0x0F, 0x1E, 0x33), 13f, bold = true)
        val body = paint(AColor.rgb(0x22, 0x22, 0x22), 11f)
        val rule = Paint().apply {
            color = AColor.rgb(0xCC, 0xCC, 0xCC)
            strokeWidth = 0.7f
            isAntiAlias = true
        }
        val fillRed = fill(AColor.rgb(0xB0, 0x00, 0x20))
        val fillTeal = fill(AColor.rgb(0x1F, 0xBF, 0xA6))
        val fillBlue = fill(AColor.rgb(0x18, 0x5F, 0xA5))
        val fillTrack = fill(AColor.rgb(0xEC, 0xEF, 0xF3))
        val contentWidth = PAGE_W - MARGIN * 2

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            pdf.finishPage(page)
            pageNum++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensure(space: Float) {
            if (y + space > PAGE_H - MARGIN) newPage()
        }

        fun drawWrapped(text: String, p: Paint, lineGap: Float, indent: Float = 0f) {
            val avail = contentWidth - indent
            text.split("\n").forEach { rawLine ->
                wrap(rawLine, p, avail).forEach { line ->
                    ensure(lineGap)
                    canvas.drawText(line, MARGIN + indent, y + p.textSize, p)
                    y += lineGap
                }
            }
        }

        blocks.forEach { block ->
            when (block) {
                is Block.Title -> { drawWrapped(titleCase(block.text), title, 26f); y += 6f }
                is Block.Heading -> { ensure(24f); y += 8f; drawWrapped(titleCase(block.text), heading, 18f) }
                is Block.Body -> drawWrapped(block.text, body, 16f)
                is Block.Bullet -> {
                    ensure(16f)
                    canvas.drawText("•", MARGIN, y + body.textSize, body)
                    drawWrapped(block.text, body, 16f, indent = 16f)
                }
                is Block.Table -> {
                    val cols = block.headers.size
                    if (cols > 0) {
                        val colW = contentWidth / cols
                        ensure(20f)
                        block.headers.forEachIndexed { i, h ->
                            canvas.drawText(h, MARGIN + i * colW, y + heading.textSize, heading)
                        }
                        y += 16f
                        ensure(2f)
                        canvas.drawLine(MARGIN, y, MARGIN + contentWidth, y, rule)
                        y += 8f
                        block.rows.forEach { row ->
                            ensure(15f)
                            row.forEachIndexed { i, c ->
                                canvas.drawText(c, MARGIN + i * colW, y + body.textSize, body)
                            }
                            y += 15f
                        }
                    }
                }
                is Block.Pie -> {
                    val total = (block.flagged + block.normal).coerceAtLeast(1)
                    val d = 120f
                    ensure(d + 12f)
                    val rect = RectF(MARGIN, y, MARGIN + d, y + d)
                    val flaggedSweep = 360f * block.flagged / total
                    canvas.drawArc(rect, -90f, flaggedSweep, true, fillRed)
                    canvas.drawArc(rect, -90f + flaggedSweep, 360f - flaggedSweep, true, fillTeal)
                    val lx = MARGIN + d + 28f
                    var ly = y + 26f
                    canvas.drawRect(lx, ly, lx + 12f, ly + 12f, fillRed)
                    canvas.drawText("Flagged: ${block.flagged}", lx + 20f, ly + 11f, body)
                    ly += 26f
                    canvas.drawRect(lx, ly, lx + 12f, ly + 12f, fillTeal)
                    canvas.drawText("Normal: ${block.normal}", lx + 20f, ly + 11f, body)
                    y += d + 12f
                }
                is Block.BarChart -> {
                    val max = (block.bars.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
                    block.bars.forEach { b ->
                        ensure(30f)
                        canvas.drawText("${b.label} — ${b.value}", MARGIN, y + body.textSize, body)
                        y += 15f
                        canvas.drawRect(MARGIN, y, MARGIN + contentWidth, y + 8f, fillTrack)
                        val w = contentWidth * b.value / max
                        canvas.drawRect(MARGIN, y, MARGIN + w, y + 8f, if (b.highlight) fillRed else fillBlue)
                        y += 15f
                    }
                }
                is Block.Scatter -> {
                    val plotH = 150f
                    val yLabelW = 30f
                    ensure(plotH + 26f)
                    val left = MARGIN + yLabelW
                    val right = MARGIN + contentWidth
                    val top = y
                    val bottom = top + plotH
                    val yLabels = listOf("12a", "6p", "12p", "6a", "12a")
                    for (i in 0..4) {
                        val yy = top + plotH * (i / 4f)
                        canvas.drawLine(left, yy, right, yy, rule)
                        canvas.drawText(yLabels[i], MARGIN, yy + 3f, body)
                    }
                    val dots = block.dots
                    if (dots.isNotEmpty()) {
                        val minT = dots.minOf { it.timeMillis }
                        val maxT = dots.maxOf { it.timeMillis }
                        val span = (maxT - minT).coerceAtLeast(1L).toFloat()
                        val dotPaints = HashMap<Long, Paint>()
                        dots.forEach { d ->
                            val xf = (d.timeMillis - minT) / span
                            val cal = Calendar.getInstance().apply { timeInMillis = d.timeMillis }
                            val hourFrac = (cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f) / 24f
                            val p = dotPaints.getOrPut(d.colorRgb) { fill(d.colorRgb.toInt()) }
                            canvas.drawCircle(left + (right - left) * xf, bottom - plotH * hourFrac, 2.5f, p)
                        }
                        y = bottom + 4f
                        canvas.drawText(rangeFmt.format(Date(minT)), left, y + body.textSize, body)
                        val endLbl = rangeFmt.format(Date(maxT))
                        canvas.drawText(endLbl, right - body.measureText(endLbl), y + body.textSize, body)
                        y += body.textSize + 6f
                    } else {
                        y = bottom + 6f
                    }
                }
                is Block.Gap -> { y += block.points }
                is Block.PageBreak -> { if (y > MARGIN) newPage() }
            }
        }
        pdf.finishPage(page)
        return pdf
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (w in words) {
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                lines.add(line.toString())
                line = StringBuilder(w)
            }
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        // Android's PdfDocument canvas can collide glyphs (e.g. the "fi" in
        // "Spoofing") without these: subpixel positioning + no hinting + ligatures off.
        isSubpixelText = true
        isLinearText = true
        fontFeatureSettings = "liga off, clig off"
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun fill(color: Int) = Paint().apply {
        this.color = color
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    /** Capitalize each word for document titles/headings; preserve acronyms and *57. */
    private fun titleCase(s: String): String = s.split(" ").joinToString(" ") { w ->
        when {
            w.isEmpty() -> w
            // already an acronym / all-caps token (FCC, ID, DC) — leave it
            w.any { it.isLetter() } && w.filter { it.isLetter() }.all { it.isUpperCase() } -> w
            else -> w.replaceFirstChar { it.uppercaseChar() }
        }
    }

    // -- Shared content helpers --------------------------------------------

    private fun v(value: String, placeholder: String) = value.ifBlank { "[$placeholder]" }

    private fun dateRange(entries: List<CallEntry>): Pair<String, String> {
        if (entries.isEmpty()) return "[FIRST DATE]" to "[MOST RECENT DATE]"
        val first = entries.minOf { it.timestampMillis }
        val last = entries.maxOf { it.timestampMillis }
        return rangeFmt.format(Date(first)) to rangeFmt.format(Date(last))
    }

    private fun hourLabel(h: Int): String {
        val period = if (h < 12) "AM" else "PM"
        val hr = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return "$hr $period"
    }

    /** The 7/30/90/all table plus pattern metrics, shared by every evidence doc. */
    private fun statsSection(entries: List<CallEntry>): List<Block> {
        val extras = CallStats.extras(entries)
        val blocks = mutableListOf<Block>(
            Block.Heading("Call Statistics By Time Window"),
            Block.Table(
                listOf("Window", "Calls", "Flagged", "Numbers"),
                extras.windows.map {
                    listOf(it.label, it.total.toString(), it.flagged.toString(), it.uniqueNumbers.toString())
                },
            ),
            Block.Gap(4f),
        )
        extras.busiestHour?.let {
            blocks.add(Block.Body("Most calls arrive around ${hourLabel(it)} (${extras.busiestHourCount} calls in that hour)."))
        }
        if (extras.overnightCount > 0) {
            blocks.add(Block.Body("${extras.overnightCount} calls arrived overnight, between 10 PM and 6 AM."))
        }
        if (extras.avgPerDay > 0) {
            blocks.add(Block.Body("Average of ${String.format(Locale.US, "%.1f", extras.avgPerDay)} calls per day across the reporting period."))
        }
        return blocks
    }

    private fun trunc(s: String, n: Int = 28) = if (s.length > n) s.take(n - 1) + "…" else s

    private fun generatedFooter() = Block.Body(
        "Generated by TraceWorthy on ${human.format(Date())}. Not legal advice — " +
            "TraceWorthy is an independent tool, not a law firm.",
    )

    /** Factual, non-editable background for the reader of the packet (police, FCC, carrier). */
    private fun spoofingExplainer(): List<Block> = listOf(
        Block.Heading("How Caller ID Spoofing Works"),
        Block.Body(
            "Caller ID spoofing makes a phone display a number other than the real one. It is cheap and " +
                "trivial — apps and websites let anyone set any number as their outbound caller ID, and " +
                "the network does not verify it at the point of the call.",
        ),
        Block.Body(
            "A harasser who changes the displayed number on every call — often a new, never-reused number " +
                "each time — does so to defeat blocking and stay anonymous. A long list of numbers that " +
                "each called only once or twice is the signature of one spoofing caller, not many callers. " +
                "The same method is used at scale by robocall operations, frequently \"neighbor spoofing\" " +
                "the recipient's own area code.",
        ),
        Block.Body(
            "Neither the recipient nor consumer-grade carrier tools can identify the true originating " +
                "line. That takes a STIR/SHAKEN traceback through the Industry Traceback Group, or a " +
                "subpoena to the carriers in the call path. Spoofing to defraud or cause harm violates the " +
                "federal Truth in Caller ID Act (47 U.S.C. § 227(e)); the 2019 TRACED Act strengthened " +
                "enforcement and mandated STIR/SHAKEN.",
        ),
    )

    /**
     * The flagged numbers that actually matter — repeat callers, tagged, or noted
     * — capped so a spoofing campaign of thousands of one-off numbers doesn't print
     * pages of noise. The long tail is summarized in a single line (which is itself
     * evidence of spoofing).
     */
    private fun flaggedNumberSection(entries: List<CallEntry>, branches: Map<String, String>): List<Block> {
        val CAP = 15
        val groups = entries.groupBy { branches[it.number] ?: it.number }
            .filter { (_, calls) -> calls.any { it.isSuspicious } }
            .toList()
            .sortedWith(
                compareByDescending<Pair<String, List<CallEntry>>> { it.second.count { c -> c.severity == Severity.Threatening } }
                    .thenByDescending { it.second.count { c -> !c.note.isNullOrBlank() } }
                    .thenByDescending { it.second.size }
                    .thenByDescending { it.second.count { c -> c.isSuspicious } }
            )
        if (groups.isEmpty()) return emptyList()

        val shown = groups.take(CAP)
        val remaining = groups.size - shown.size

        val heading = if (remaining > 0) "Most Significant Flagged Numbers (Top $CAP)" else "Flagged Numbers Detail"
        val blocks = mutableListOf<Block>(Block.Heading(heading, addable = true))
        shown.forEach { (key, calls) ->
            val memberNumbers = calls.map { it.number }.distinct()
            val isBranch = memberNumbers.size > 1 || branches[memberNumbers.first()] == key
            val who = if (isBranch)
                "Caller \"$key\" (${memberNumbers.size} number${if (memberNumbers.size == 1) "" else "s"})"
            else
                calls.firstNotNullOfOrNull { it.cachedName?.takeIf { n -> n.isNotBlank() } } ?: key
            val flagged = calls.count { it.isSuspicious }
            val threat = calls.count { it.severity == Severity.Threatening }
            val spoken = calls.count { it.severity == Severity.Spoken }
            val silent = calls.count { it.severity == Severity.Silent }
            val notes = calls.count { !it.note.isNullOrBlank() }
            val sevParts = buildList {
                if (threat > 0) add("$threat threatening")
                if (spoken > 0) add("$spoken spoken")
                if (silent > 0) add("$silent silent")
            }
            val sevStr = if (sevParts.isNotEmpty()) "; tags: ${sevParts.joinToString(", ")}" else ""
            val noteStr = if (notes > 0) "; $notes noted" else ""
            blocks.add(Block.Bullet("$who — ${calls.size} calls, $flagged flagged$sevStr$noteStr"))
        }
        if (remaining > 0) {
            val tailCalls = groups.drop(CAP).sumOf { it.second.size }
            val tailOneOff = groups.drop(CAP).count { it.second.size == 1 }
            blocks.add(Block.Gap(4f))
            blocks.add(
                Block.Body(
                    "Plus $remaining additional flagged numbers accounting for $tailCalls calls" +
                        (if (tailOneOff > 0) ", $tailOneOff of which called exactly once — a hallmark of caller ID spoofing used to evade blocking" else "") +
                        ". The complete per-call list is available in the exported CSV."
                )
            )
        }
        return blocks
    }

    /** True once at least one call has been annotated with a note or a severity tag. */
    private fun hasDocumentedIncidents(entries: List<CallEntry>): Boolean =
        entries.any { !it.note.isNullOrBlank() || it.severity != Severity.Unset }

    /** Wording that adapts to the kind of harassment being reported. */
    private fun patternSentence(profile: UserProfile, stats: CallStats, entries: List<CallEntry>): String {
        val n = stats.uniqueNumbers
        val spoof = "The use of $n different numbers is consistent with deliberate caller ID spoofing to harass and evade blocking."
        val timeline = hasDocumentedIncidents(entries)
        return when (profile.harassmentType) {
            HarassmentType.Aggressive -> {
                val tl = if (timeline) " Specific incidents, with dates and times, are documented in the attached incident timeline." else ""
                "${stats.flaggedCalls} of these calls involve aggressive, abusive, or threatening conduct by the caller.$tl $spoof"
            }
            HarassmentType.Both -> {
                val tl = if (timeline) " (documented with dates and times in the attached incident timeline)" else ""
                "${stats.flaggedCalls} match a harassment pattern of silent or very short calls from numbers not in my contacts, and a number of the calls additionally involve aggressive or threatening conduct$tl. $spoof"
            }
            HarassmentType.Silent ->
                "${stats.flaggedCalls} match a consistent harassment pattern: incoming calls from numbers not in my contacts on which the caller is silent and/or disconnects within seconds. $spoof"
            HarassmentType.Unspecified ->
                "${stats.flaggedCalls} match a harassment pattern: repeated unwanted incoming calls from numbers not in my contacts, many silent or lasting only seconds. $spoof"
        }
    }

    // -- Content builders ---------------------------------------------------

    private fun buildBlocks(
        type: DocumentType,
        profile: UserProfile,
        stats: CallStats,
        entries: List<CallEntry>,
        branches: Map<String, String>,
    ): List<Block> = when (type) {
        DocumentType.EvidencePacket -> evidencePacket(profile, stats, entries, branches)
        DocumentType.FccComplaint -> fccComplaint(profile, stats, entries)
        DocumentType.PoliceReport -> policeReport(profile, stats, entries)
        DocumentType.CarrierScript -> carrierScript(profile, stats)
        DocumentType.IncidentTimeline -> incidentTimeline(profile, entries)
        DocumentType.EvidenceSummary -> evidenceSummary(profile, stats, entries, branches)
        DocumentType.NonDisclosureOrder -> nonDisclosureOrder(profile, entries)
    }

    /**
     * The documents bundled into the packet, in the order the reader should work
     * through them: understand the case, then file in sequence — carrier first (get
     * a case #), then the FCC, then police last (their cover note cross-references
     * the other two case numbers). Matches DocumentType.seq / the PC packet.
     */
    private val PACKET_CONTENTS = listOf(
        DocumentType.EvidenceSummary,
        DocumentType.IncidentTimeline,
        DocumentType.CarrierScript,
        DocumentType.FccComplaint,
        DocumentType.PoliceReport,
        DocumentType.NonDisclosureOrder,
    )

    /**
     * The packet's document list for this call log. The incident timeline is only
     * bundled once at least one call carries a note or severity — an empty timeline
     * is just a page telling the reader to add notes.
     */
    private fun packetContents(entries: List<CallEntry>): List<DocumentType> =
        if (hasDocumentedIncidents(entries)) PACKET_CONTENTS
        else PACKET_CONTENTS.filter { it != DocumentType.IncidentTimeline }

    private fun evidencePacket(profile: UserProfile, stats: CallStats, entries: List<CallEntry>, branches: Map<String, String>): List<Block> {
        val (first, last) = dateRange(entries)
        val blocks = mutableListOf<Block>(
            Block.Title("TraceWorthy Evidence Packet"),
            Block.Body("Prepared by ${v(profile.fullName, "YOUR FULL NAME")} · ${v(profile.phone, "YOUR PHONE")}"),
            Block.Body("Affected number (receiving the calls): ${v(profile.affectedLine, "AFFECTED NUMBER")}"),
            Block.Body("Reporting period: $first to $last"),
            Block.Body("Generated: ${human.format(Date())}"),
            Block.Gap(6f),
            Block.Body("This packet documents a campaign of harassing phone calls to ${v(profile.affectedLine, "AFFECTED NUMBER")} and is intended to support a carrier traceback. It contains ${stats.totalCalls} logged calls from ${stats.uniqueNumbers} distinct numbers, ${stats.flaggedCalls} matching the harassment pattern. The full statistics and charts are on the evidence summary that follows."),
        )
        val contents = packetContents(entries)
        blocks.add(Block.Heading("Contents"))
        contents.forEachIndexed { i, t ->
            blocks.add(Block.Bullet("${i + 1}.  ${t.displayName}"))
        }
        blocks.add(Block.Heading("How To Use This Packet"))
        blocks.add(Block.Bullet("1. Call your carrier's fraud / harassment line using the carrier call script. Open a case, get a reference number, and enter it in My info."))
        blocks.add(Block.Bullet("2. File an FCC complaint at consumercomplaints.fcc.gov using the FCC complaint document. Save the confirmation number in My info."))
        blocks.add(Block.Bullet("3. Take the police report cover note and the non-disclosure order request to your local police — in person is best. Bring this packet and the CSV of every call."))
        blocks.add(Block.Bullet("4. As each case / complaint number comes in, add it in My info and regenerate — the documents cross-reference one another."))
        blocks.add(Block.PageBreak)
        blocks.add(Block.Heading("Background: How Caller ID Spoofing Works"))
        blocks.addAll(spoofingExplainer().drop(1))  // drop the duplicate heading
        contents.forEach { t ->
            blocks.add(Block.PageBreak)
            blocks.addAll(buildBlocks(t, profile, stats, entries, branches))
        }
        return blocks
    }

    private fun fccComplaint(profile: UserProfile, stats: CallStats, entries: List<CallEntry>): List<Block> {
        val (first, last) = dateRange(entries)
        val name = v(profile.fullName, "YOUR FULL NAME")
        val affected = v(profile.affectedLine, "AFFECTED NUMBER")
        val contact = v(profile.phone, "YOUR CONTACT NUMBER")
        val distinctAffected = profile.affectedNumber.isNotBlank() && profile.affectedNumber != profile.phone
        val blocks = mutableListOf<Block>(
            Block.Title("FCC Complaint — Caller ID Spoofing"),
            Block.Body("File online at consumercomplaints.fcc.gov → Phone → Unwanted Calls → issue type \"Caller ID Spoofing.\" Use the field notes below, then paste the description into the complaint's free-text box."),
            Block.Gap(6f),
            Block.Heading("Form Field Cheat-Sheet"),
            Block.Bullet("Your phone number (the line that was called): $affected"),
            Block.Bullet("Phone issue: Unwanted calls"),
            Block.Bullet("Sub-issue: Caller ID Spoofing"),
            Block.Bullet("Did you give consent? No"),
            Block.Bullet("Caller's number: Multiple / spoofed — ${stats.uniqueNumbers} different numbers (see description)"),
            Block.Bullet("Date(s) of calls: $first through $last"),
            Block.Bullet("Method: Phone call"),
        )
        if (distinctAffected) blocks.add(Block.Bullet("Best number to reach you: $contact"))
        blocks.add(Block.Heading("Totals By Period"))
        blocks.add(
            Block.Table(
                listOf("Window", "Calls", "Flagged", "Numbers"),
                CallStats.extras(entries).windows.map {
                    listOf(it.label, it.total.toString(), it.flagged.toString(), it.uniqueNumbers.toString())
                },
            )
        )
        blocks.add(Block.Heading("Description (Paste This)"))
        blocks.add(Block.Body("I am receiving a sustained campaign of harassing phone calls to my number, $affected. Over the period $first to $last I have logged ${stats.totalCalls} calls from ${stats.uniqueNumbers} distinct phone numbers. ${patternSentence(profile, stats, entries)}", editable = true))
        blocks.add(Block.Body("I did not consent to these calls. I am requesting FCC action against this illegal spoofing under the Truth in Caller ID Act and the TRACED Act."))
        blocks.add(Block.Body("Name: $name" + if (distinctAffected) "    Best contact number: $contact" else ""))
        blocks.add(Block.Gap(10f))
        blocks.add(generatedFooter())
        return blocks
    }

    private fun policeReport(profile: UserProfile, stats: CallStats, entries: List<CallEntry>): List<Block> {
        val (first, last) = dateRange(entries)
        val blocks = mutableListOf<Block>(
            Block.Title("Harassment — Police Report Cover Note"),
            Block.Body("Bring this to the police (in person is best) along with your TraceWorthy evidence summary and CSV. It states the facts plainly and cross-references your other filings so the file is self-contained."),
            Block.Gap(6f),
            Block.Heading("Complainant"),
            Block.Bullet("Date: ${human.format(Date())}"),
            Block.Bullet("Name: ${v(profile.fullName, "YOUR FULL NAME")}"),
            Block.Bullet("Contact: ${v(profile.phone, "YOUR PHONE")} · ${v(profile.email, "YOUR EMAIL")}"),
            Block.Bullet("Affected line (receiving the calls): ${v(profile.affectedLine, "AFFECTED NUMBER")}"),
            Block.Bullet("Carrier: ${v(profile.carrier, "YOUR CARRIER")}"),
            Block.Bullet("Location: ${v(profile.addressCity, "CITY")}, ${v(profile.state, "ST")}"),
            Block.Heading("Nature Of Complaint"),
            Block.Body(
                if (profile.harassmentType.includesAggressive)
                    "Ongoing telephone harassment involving aggressive, abusive, or threatening calls, with caller ID spoofing used to evade blocking."
                else
                    "Ongoing telephone harassment via spoofed caller ID."
            ),
            Block.Heading("Summary Of Evidence"),
            Block.Body("Over the period $first to $last I have logged ${stats.totalCalls} calls from ${stats.uniqueNumbers} distinct phone numbers. ${patternSentence(profile, stats, entries)}", editable = true),
        )
        blocks.add(Block.Body("The full call statistics — the flagged-vs-normal breakdown, the time-window totals, the charts, and the per-number list — are in the attached TraceWorthy evidence summary, with every call itemized in the accompanying CSV."))
        if (profile.harassmentType.includesAggressive && hasDocumentedIncidents(entries)) {
            blocks.add(Block.Body("Specific threatening/abusive incidents are itemized in the attached TraceWorthy incident timeline, compiled from notes taken at the time of each call."))
        }
        blocks.add(Block.Heading("Cross-References"))
        blocks.add(Block.Bullet("FCC complaint number: ${v(profile.fccComplaintNumber, "FCC COMPLAINT #")}"))
        blocks.add(Block.Bullet("Carrier harassment case number: ${v(profile.carrierCaseNumber, "CARRIER CASE #")}"))
        blocks.add(Block.Heading("Request"))
        blocks.add(Block.Body("I am requesting a police report be filed so that a subpoena can be issued to my carrier for the true originating records of these calls (a traceback). The attached CSV and evidence summary document every call."))
        blocks.add(Block.Body("Please also see the attached non-disclosure order request — I am asking that any subpoena to the carrier be kept confidential from the subscriber so the caller is not tipped off."))
        blocks.add(Block.Gap(10f))
        blocks.add(generatedFooter())
        return blocks
    }

    /**
     * A plain-language request the complainant hands their detective/prosecutor asking
     * that the carrier subpoena be paired with an 18 U.S.C. § 2705(b) non-disclosure
     * order. Only a prosecutor/court can obtain one — this document just flags the need
     * and lays out the statutory grounds so the officer doesn't have to.
     */
    private fun nonDisclosureOrder(profile: UserProfile, entries: List<CallEntry>): List<Block> {
        val (first, last) = dateRange(entries)
        val aggressive = profile.harassmentType.includesAggressive
        val timeline = aggressive && hasDocumentedIncidents(entries)
        val blocks = mutableListOf<Block>(
            Block.Title("Request for a Non-Disclosure Order"),
            Block.Body("For the investigating officer / prosecutor. This accompanies my police report and TraceWorthy evidence packet."),
            Block.Gap(6f),
            Block.Heading("Why This Matters"),
            Block.Body("When a subpoena or court order is served on a phone carrier for subscriber and call records, the carrier's normal practice is to notify the account holder. In this case that would tip off the person placing these calls before the records can be secured — they use caller ID spoofing specifically to stay anonymous and evade blocking, and services that enable spoofing routinely purge their logs."),
            Block.Body("Under 18 U.S.C. § 2705(b), the government may apply for a court order directing the provider not to notify the subscriber for a set period (commonly 90 days, renewable). Financial institutions are subject to parallel non-disclosure provisions if bank records are also sought."),
            Block.Heading("What I Am Asking"),
            Block.Body("That any subpoena, § 2703(d) order, or search warrant issued to my carrier for records relating to the calls to my number, ${v(profile.affectedLine, "AFFECTED NUMBER")}, over the period $first to $last be accompanied by a § 2705(b) non-disclosure order barring the carrier from notifying the subscriber(s) whose records are produced.", editable = true),
            Block.Heading("Statutory Grounds (§ 2705(b))"),
            Block.Body("A court may issue the order on a finding that notification would result in one or more of the following. The grounds most applicable here are noted."),
            Block.Bullet("Endangering the life or physical safety of an individual" + if (aggressive) "  — APPLIES: the caller has made threatening/abusive statements" + (if (timeline) ", documented with dates and times in the attached incident timeline." else ".") else "."),
            Block.Bullet("Flight from prosecution."),
            Block.Bullet("Destruction of or tampering with evidence  — APPLIES: spoofing-service and intermediate-carrier call logs are short-lived and are routinely deleted; advance notice invites their destruction."),
            Block.Bullet("Intimidation of potential witnesses" + if (aggressive) "  — APPLIES: I am the complainant and a witness, and the caller has already engaged in intimidating conduct." else "."),
            Block.Bullet("Otherwise seriously jeopardizing an investigation or unduly delaying a trial  — APPLIES: identifying the true originating line depends on a traceback through multiple carriers that cannot succeed if the subject is alerted."),
            Block.Heading("Cross-References"),
            Block.Bullet("Police case number: ${v(profile.policeCaseNumber, "POLICE CASE #")}"),
            Block.Bullet("FCC complaint number: ${v(profile.fccComplaintNumber, "FCC COMPLAINT #")}"),
            Block.Bullet("Carrier harassment case number: ${v(profile.carrierCaseNumber, "CARRIER CASE #")}"),
            Block.Gap(6f),
            Block.Body("I understand that only a prosecutor or court can obtain this order and that the decision rests with them. This document is provided so the request and its basis are on the record from the outset."),
            Block.Body("Name: ${v(profile.fullName, "YOUR FULL NAME")}    Contact: ${v(profile.phone, "YOUR PHONE")}"),
            Block.Gap(8f),
            generatedFooter(),
        )
        return blocks
    }

    private fun carrierScript(profile: UserProfile, stats: CallStats): List<Block> {
        val blocks = mutableListOf<Block>(
            Block.Title("Carrier Harassment Case — Call Script"),
            Block.Body("Call your carrier's fraud / harassment department (dial 611 from your phone, or use the customer-service number on your bill) and ask to open a documented harassment case."),
            Block.Gap(6f),
            Block.Heading("Word-For-Word Script"),
            Block.Body("\"I'm a ${v(profile.carrier, "CARRIER")} customer and I'm being harassed by repeated calls from different numbers that I believe are spoofed. I want to:", editable = true),
            Block.Bullet("Open a documented harassment case on my account."),
            Block.Bullet("Get a case / reference number for my records."),
            Block.Bullet("Turn on any free spam-blocking tools you offer."),
            Block.Bullet("Understand how the police can request a traceback of these calls.\""),
            Block.Heading("Write Down"),
            Block.Bullet("Case / reference number: ____________________  (save this in My info)"),
            Block.Bullet("Representative name and date: ____________________"),
            Block.Heading("Context To Give Them"),
            Block.Body("The affected line on my account is ${v(profile.affectedLine, "AFFECTED NUMBER")}. I have logged ${stats.totalCalls} calls from ${stats.uniqueNumbers} different numbers, ${stats.flaggedCalls} matching the harassment pattern. I am also filing an FCC complaint and a police report."),
        )
        blocks.add(Block.Gap(6f))
        blocks.add(Block.Body("Note: carrier tools block and document — they cannot reveal a spoofed caller to you directly. Only a police subpoena unmasks the origin."))
        blocks.add(Block.Gap(6f))
        blocks.add(generatedFooter())
        return blocks
    }

    private fun incidentTimeline(profile: UserProfile, entries: List<CallEntry>): List<Block> {
        val documented = entries
            .filter { !it.note.isNullOrBlank() || it.severity != Severity.Unset }
            .sortedBy { it.timestampMillis }
        val blocks = mutableListOf<Block>(
            Block.Title("Harassment Incident Timeline"),
            Block.Body("Complainant: ${v(profile.fullName, "YOUR FULL NAME")} · ${v(profile.phone, "YOUR PHONE")}"),
            Block.Body("Affected number (receiving the calls): ${v(profile.affectedLine, "AFFECTED NUMBER")}"),
            Block.Body("This is a chronological log of documented incidents, compiled from notes taken at or near the time of each call. It is intended to show the pattern of contact and any escalation of the harassment over time."),
            Block.Gap(4f),
        )
        if (documented.isEmpty()) {
            blocks.add(Block.Body("No incidents have been documented yet. To build this timeline, open the Call log, tap a harassing call, add a note describing what happened — for example \"silent for 30 seconds\", \"shouted threats\", or \"said he knew my address\" — and tag how serious it was (Silent / Spoken / Threatening). Each entry is timestamped to its call automatically, and they will appear here in order."))
            return blocks
        }

        val threatening = documented.count { it.severity == Severity.Threatening }
        val spoken = documented.count { it.severity == Severity.Spoken }
        val silent = documented.count { it.severity == Severity.Silent }
        blocks.add(Block.Heading("Documented Incidents (${documented.size})"))
        if (threatening + spoken + silent > 0) {
            blocks.add(Block.Body("Severity tags across these incidents: $threatening threatening, $spoken spoken, $silent silent."))
            val sevBars = listOf(
                ChartBar("Threatening", threatening, true),
                ChartBar("Spoken", spoken, false),
                ChartBar("Silent", silent, false),
            ).filter { it.value > 0 }
            if (sevBars.isNotEmpty()) blocks.add(Block.BarChart(sevBars))
        }
        documented.forEach { e ->
            val who = e.cachedName?.takeIf { it.isNotBlank() } ?: e.number
            val tags = buildList {
                if (e.severity != Severity.Unset) add(e.severity.label)
                if (e.isSuspicious) add("flagged")
            }
            val tagStr = if (tags.isNotEmpty()) "  [${tags.joinToString(", ")}]" else ""
            blocks.add(Block.Bullet("${incidentFmt.format(Date(e.timestampMillis))} — $who$tagStr"))
            if (!e.note.isNullOrBlank()) blocks.add(Block.Body("     “${e.note}”"))
            blocks.add(Block.Gap(3f))
        }
        blocks.add(Block.Gap(8f))
        blocks.add(Block.Body("Earliest documented incident: ${incidentFmt.format(Date(documented.first().timestampMillis))}. Most recent: ${incidentFmt.format(Date(documented.last().timestampMillis))}."))
        blocks.add(generatedFooter())
        return blocks
    }

    private fun evidenceSummary(profile: UserProfile, stats: CallStats, entries: List<CallEntry>, branches: Map<String, String>): List<Block> {
        val (first, last) = dateRange(entries)
        val blocks = mutableListOf<Block>(
            Block.Title("TraceWorthy — Evidence Summary"),
            Block.Body("Complainant: ${v(profile.fullName, "YOUR FULL NAME")} · ${v(profile.phone, "YOUR PHONE")}"),
            Block.Body("Affected number (receiving the calls): ${v(profile.affectedLine, "AFFECTED NUMBER")}"),
            Block.Body("Reporting period: $first to $last"),
            Block.Body("Generated: ${human.format(Date())}"),
        )
        blocks.addAll(statsSection(entries))
        blocks.add(Block.Heading("Totals"))
        blocks.add(Block.Bullet("Calls logged: ${stats.totalCalls}"))
        blocks.add(Block.Bullet("Flagged (harassment pattern): ${stats.flaggedCalls}"))
        blocks.add(Block.Bullet("Distinct numbers: ${stats.uniqueNumbers}"))
        blocks.add(Block.Bullet("Incoming: ${stats.incoming} · Missed: ${stats.missed} · Rejected: ${stats.rejected}"))
        blocks.add(Block.Heading("Flagged Vs Normal"))
        blocks.add(Block.Pie(stats.flaggedCalls, (stats.totalCalls - stats.flaggedCalls).coerceAtLeast(0)))
        blocks.add(Block.Heading("Top Numbers By Call Count"))
        val topBars = stats.perNumber.take(8).map { n ->
            ChartBar(trunc(n.name?.takeIf { it.isNotBlank() } ?: n.number), n.totalCount, n.flaggedCount > 0)
        }
        if (topBars.isNotEmpty()) blocks.add(Block.BarChart(topBars))
        val scEntries = ScatterColors.last90Days(entries)
        val scTop5 = ScatterColors.top5Numbers(scEntries)
        val scNums = scTop5.map { it.first }
        blocks.add(Block.Heading("Calls Over Time — Last 90 Days (Date × Time Of Day)"))
        blocks.add(Block.Scatter(scEntries.map { ScatterDot(it.timestampMillis, ScatterColors.colorFor(it.number, scNums)) }))
        blocks.add(Block.Body("Each dot is a call — the horizontal position is the date and the vertical position is the time of day. Dots are colored by the top-5 most-called numbers (see legend); other numbers are gray. This shows when calls arrive, including overnight clustering or bursts on particular dates."))
        blocks.addAll(flaggedNumberSection(entries, branches))
        blocks.add(Block.Gap(6f))
        blocks.add(Block.Body("This summary is generated from the device call log. A full per-call CSV is available via the Call log screen's Export."))
        blocks.add(generatedFooter())
        return blocks
    }

}
