package com.dosecircle.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.dosecircle.core.CsvExporter
import com.dosecircle.core.DoctorReport
import com.dosecircle.core.HistoryOutcome
import com.dosecircle.core.HistoryRow
import com.dosecircle.core.TimeText
import java.io.File
import java.time.LocalDate

/** Writes shareable files into the cache folder and builds the share sheet intent. Run on a background thread. */
class Exporter(context: Context) {
    private val app = context.applicationContext
    private val dir: File get() = File(app.cacheDir, "exports").apply { mkdirs() }

    fun csv(personName: String, rows: List<HistoryRow>, from: LocalDate, to: LocalDate): Intent {
        val file = File(dir, "DoseCircle-${safe(personName)}-$from-to-$to.csv")
        file.writeText("\uFEFF" + CsvExporter.build(personName, rows), Charsets.UTF_8) // BOM so Excel reads accents correctly
        return share(file, "text/csv", "Dose history for $personName")
    }

    fun pdf(report: DoctorReport, use24h: Boolean): Intent {
        val file = File(dir, "DoseCircle-${safe(report.personName)}-report-${report.to}.pdf")
        writePdf(report, use24h, file)
        return share(file, "application/pdf", "Medication report for ${report.personName}")
    }

    private fun share(file: File, mime: String, subject: String): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "person" }

    // ----- PDF -----

    private class Pen(val doc: PdfDocument) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        var page: PdfDocument.Page? = null
        var y = margin
        private var number = 0

        val title = paint(22f, true)
        val heading = paint(14f, true)
        val body = paint(11f, false)
        val muted = paint(10f, false).apply { color = 0xFF555F59.toInt() }
        val bold = paint(11f, true)

        private fun paint(size: Float, isBold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = 0xFF161D19.toInt()
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            number += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number).create())
            y = margin
        }

        fun ensure(height: Float) {
            if (page == null || y + height > pageHeight - margin) newPage()
        }

        fun line(text: String, paint: Paint, indent: Float = 0f, gap: Float = 4f) {
            val maxWidth = pageWidth - margin * 2 - indent
            var rest = text
            while (rest.isNotEmpty()) {
                val count = paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                var cut = count
                if (count < rest.length) {
                    val space = rest.lastIndexOf(' ', count - 1)
                    if (space > 0) cut = space + 1
                }
                ensure(paint.textSize + gap)
                page!!.canvas.drawText(rest.substring(0, cut).trimEnd(), margin + indent, y + paint.textSize, paint)
                y += paint.textSize + gap
                rest = rest.substring(cut)
            }
        }

        fun space(h: Float) { y += h }

        fun bar(fraction: Double) {
            ensure(14f)
            val w = pageWidth - margin * 2
            val c = page!!.canvas
            val track = Paint().apply { color = 0xFFE4EBE6.toInt() }
            val fill = Paint().apply { color = 0xFF0B6E5B.toInt() }
            c.drawRoundRect(margin, y, margin + w, y + 10f, 5f, 5f, track)
            c.drawRoundRect(margin, y, margin + (w * fraction.coerceIn(0.0, 1.0)).toFloat().coerceAtLeast(10f), y + 10f, 5f, 5f, fill)
            y += 18f
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }
    }

    private fun writePdf(r: DoctorReport, use24h: Boolean, file: File) {
        val doc = PdfDocument()
        try {
            val pen = Pen(doc)
            pen.newPage()
            pen.line("Medication report", pen.title, gap = 6f)
            pen.line("${r.personName}  ·  ${r.from} to ${r.to}", pen.heading, gap = 10f)

            val a = r.adherence
            pen.line("Doses taken: ${a.taken} of ${a.scheduled} (${a.percent}%)", pen.bold)
            pen.bar(if (a.scheduled == 0) 0.0 else a.taken.toDouble() / a.scheduled)
            pen.line("On time or late: ${a.taken} taken (${a.takenLate} of them more than an hour late)  ·  ${a.skipped} skipped  ·  ${a.missed} missed", pen.body)
            pen.space(10f)

            pen.line("Current medicines", pen.heading, gap = 6f)
            if (r.medications.isEmpty()) pen.line("No active medicines.", pen.body)
            for (m in r.medications) {
                pen.ensure(48f)
                pen.line(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), pen.bold, gap = 2f)
                pen.line("${m.dose}  ·  ${m.schedule}", pen.body, indent = 12f, gap = 2f)
                if (m.instructions.isNotBlank()) pen.line(m.instructions, pen.muted, indent = 12f, gap = 2f)
                m.percent?.let { pen.line("Taken as scheduled: $it%", pen.muted, indent = 12f, gap = 2f) }
                pen.space(6f)
            }

            if (r.recentMissed.isNotEmpty()) {
                pen.space(6f)
                pen.line("Recent missed doses", pen.heading, gap = 6f)
                for (row in r.recentMissed) pen.line(describe(row, use24h), pen.body, indent = 12f, gap = 3f)
            }
            if (r.notes.isNotEmpty()) {
                pen.space(10f)
                pen.line("Caregiver notes", pen.heading, gap = 6f)
                for (row in r.notes) pen.line("${describe(row, use24h)} — ${row.note}", pen.body, indent = 12f, gap = 3f)
            }

            pen.space(16f)
            pen.line("Generated by DoseCircle from doses logged by the person or their caregivers. For information only; it is not medical advice.", pen.muted)
            pen.finish()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun describe(row: HistoryRow, use24h: Boolean): String {
        val what = listOf(row.medicationName, row.strength).filter { it.isNotBlank() }.joinToString(" ")
        val outcome = if (row.outcome == HistoryOutcome.MISSED) "missed" else row.outcomeText.lowercase()
        return "${row.date} ${TimeText.clock(row.dueLocal, use24h)}  $what ($outcome)"
    }
}
