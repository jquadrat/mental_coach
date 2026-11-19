package com.mentalcoach.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.mentalcoach.domain.Visit
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Localised labels for the exported logbook (from string resources). */
data class LogbookLabels(
    val takeaway: String,
    val meta: String,
    val notes: String,
)

fun logbookDate(v: Visit): String =
    runCatching {
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.LONG)
            .withLocale(v.lang.locale())
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(v.startedAt))
    }.getOrDefault(v.startedAt.take(10))

fun fileStem(v: Visit): String {
    val slug =
        java.text.Normalizer
            .normalize(v.name.lowercase(), java.text.Normalizer.Form.NFKD)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "visit" }
    return "mentalcoach-$slug-${v.startedAt.take(10)}"
}

/** Self-contained HTML logbook (photos embedded as data URIs). */
object HtmlExporter {
    private fun esc(s: String) =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun dataUri(path: String): String? =
        runCatching { "data:image/jpeg;base64," + Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP) }.getOrNull()

    private const val CSS = """
body{margin:0;padding:24px 0;background:#efeadf}
.logbook{--ink:#24302c;--muted:#5d6b66;--paper:#fffdf8;--accent:#2f5d50;--line:#e7e1d4;font-family:Georgia,"Times New Roman",serif;color:var(--ink);background:var(--paper);max-width:820px;margin:0 auto;padding:40px 36px;border-radius:18px;box-shadow:0 10px 40px rgba(0,0,0,.08);line-height:1.6}
.lb-cover{text-align:center;padding-bottom:28px;border-bottom:1px solid var(--line);margin-bottom:28px}
.lb-avatar{width:132px;height:132px;border-radius:50%;object-fit:cover;border:4px solid #fff;box-shadow:0 4px 16px rgba(0,0,0,.12)}
h1{font-size:2rem;margin:16px 0 4px;color:var(--accent);font-weight:600}
.lb-meta{color:var(--muted);font-family:system-ui,sans-serif;font-size:.9rem;letter-spacing:.04em;text-transform:uppercase;margin:0 0 16px}
.lb-entry{margin:0 0 36px;break-inside:avoid}
h2{font-size:1.35rem;margin:0 0 12px;color:var(--accent);font-weight:600}
.lb-photos{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin:0 0 14px}
.lb-photos img{width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:10px;display:block}
.lb-takeaway{background:#f1ede2;border-left:4px solid var(--accent);padding:10px 14px;border-radius:6px;font-style:italic}
.lb-takeaway strong{font-style:normal;font-family:system-ui,sans-serif;font-size:.8rem;letter-spacing:.05em;text-transform:uppercase;color:var(--muted);display:block}
.lb-notes{margin:0 0 14px}.lb-notes strong{font-family:system-ui,sans-serif;font-size:.8rem;letter-spacing:.05em;text-transform:uppercase;color:var(--muted)}.lb-notes ul{margin:4px 0 0;padding-left:20px}
.lb-closing{border-top:1px solid var(--line);padding-top:24px;text-align:center;font-size:1.1rem}
@media (max-width:600px){.logbook{padding:24px 18px;border-radius:0}}
@media print{body{background:none}.logbook{box-shadow:none;padding:0;max-width:none}}"""

    fun export(
        v: Visit,
        labels: LogbookLabels,
    ): String {
        val lb = requireNotNull(v.logbook)
        val entries =
            v.exercises.mapIndexed { i, ex ->
                val e = lb.entries.firstOrNull { it.exerciseIndex == i }
                val photos = ex.selectedPhotos().mapNotNull { dataUri(it) }.joinToString("") { "<img src=\"$it\" alt=\"\">" }
                buildString {
                    append("<section class=\"lb-entry\"><h2>${i + 1}. ${esc(ex.title)}</h2>")
                    if (photos.isNotEmpty()) append("<div class=\"lb-photos\">$photos</div>")
                    append("<p>${esc(e?.narrative.orEmpty())}</p>")
                    if (ex.notes.isNotEmpty()) {
                        append("<div class=\"lb-notes\"><strong>${esc(labels.notes)}</strong><ul>")
                        ex.notes.forEach { append("<li>&bdquo;${esc(it.text)}&ldquo;</li>") }
                        append("</ul></div>")
                    }
                    if (!e?.takeaway.isNullOrBlank()) {
                        append("<p class=\"lb-takeaway\"><strong>${esc(labels.takeaway)}</strong>${esc(e.takeaway)}</p>")
                    }
                    append("</section>")
                }
            }.joinToString("\n")
        val avatar = v.profilePhoto?.let { dataUri(it) }?.let { "<img class=\"lb-avatar\" src=\"$it\" alt=\"\">" }.orEmpty()
        return """<!doctype html><html lang="${v.lang.code}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(lb.title)}</title><style>$CSS</style></head><body><article class="logbook">
<header class="lb-cover">$avatar<h1>${esc(lb.title)}</h1><p class="lb-meta">${esc(labels.meta)}</p><p>${esc(lb.opening)}</p></header>
$entries
<footer class="lb-closing"><p>${esc(lb.closing)}</p></footer>
</article></body></html>"""
    }
}

/** Logbook PDF via android.graphics.pdf.PdfDocument (A4). */
object PdfExporter {
    private const val W = 595
    private const val H = 842
    private const val M = 48f
    private val ACCENT = Color.parseColor("#2F5D50")
    private val INK = Color.parseColor("#24302C")
    private val MUTED = Color.parseColor("#5D6B66")
    private val PAPER = Color.parseColor("#FFFDF8")
    private val TAKEAWAY_BG = Color.parseColor("#F1EDE2")

    private class Writer(
        val doc: PdfDocument,
    ) {
        var page: PdfDocument.Page? = null
        var y = M
        var n = 0
        val canvas: Canvas get() = page!!.canvas

        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            canvas.drawColor(PAPER)
            y = M
        }

        fun ensure(h: Float) {
            if (page == null || y + h > H - M) newPage()
        }

        fun text(
            s: String,
            paint: TextPaint,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
            after: Float = 10f,
            inset: Float = 0f,
        ) {
            if (s.isBlank()) return
            val width = (W - 2 * M - 2 * inset).toInt()
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width).setAlignment(align).setLineSpacing(0f, 1.25f).build()
            // Split across pages line by line when the block does not fit.
            var line = 0
            while (line < layout.lineCount) {
                ensure(layout.getLineBottom(line) - layout.getLineTop(line).toFloat())
                val top = layout.getLineTop(line)
                var last = line
                while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= H - M) last++
                canvas.save()
                canvas.translate(M + inset, y)
                canvas.clipRect(0, 0, width, layout.getLineBottom(last) - top)
                canvas.translate(0f, -top.toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += layout.getLineBottom(last) - top
                line = last + 1
            }
            y += after
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
        }
    }

    private fun paint(
        size: Float,
        color: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        serif: Boolean = true,
    ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        val style =
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, style)
    }

    private fun decode(
        path: String,
        maxSide: Int = 800,
    ): Bitmap? =
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        }.getOrNull()

    /** Draws [bmp] center-cropped into [dst]. */
    private fun drawCover(
        c: Canvas,
        bmp: Bitmap,
        dst: RectF,
    ) {
        val ratio = dst.width() / dst.height()
        val src =
            if (bmp.width.toFloat() / bmp.height > ratio) {
                val w = (bmp.height * ratio).toInt()
                Rect((bmp.width - w) / 2, 0, (bmp.width + w) / 2, bmp.height)
            } else {
                val h = (bmp.width / ratio).toInt()
                Rect(0, (bmp.height - h) / 2, bmp.width, (bmp.height + h) / 2)
            }
        c.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun export(
        v: Visit,
        labels: LogbookLabels,
        out: OutputStream,
    ) {
        val lb = requireNotNull(v.logbook)
        val doc = PdfDocument()
        val w = Writer(doc)
        try {
            w.newPage()
            v.profilePhoto?.let(::decode)?.let { bmp ->
                val size = 132f
                val left = (W - size) / 2
                val dst = RectF(left, w.y, left + size, w.y + size)
                val shader =
                    BitmapShader(Bitmap.createScaledBitmap(bmp, size.toInt(), size.toInt(), true), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
                w.canvas.save()
                w.canvas.translate(left, w.y)
                w.canvas.drawOval(RectF(0f, 0f, size, size), p)
                w.canvas.restore()
                w.canvas.drawOval(dst, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    color = Color.WHITE
                })
                w.y += size + 16f
            }
            val center = Layout.Alignment.ALIGN_CENTER
            w.text(lb.title, paint(26f, ACCENT, bold = true), center, 4f)
            w.text(labels.meta.uppercase(v.lang.locale()), paint(10f, MUTED, serif = false), center, 14f)
            w.text(lb.opening, paint(12.5f, INK), center, 20f)
            divider(w)

            val body = paint(12f, INK)
            v.exercises.forEachIndexed { i, ex ->
                val e = lb.entries.firstOrNull { it.exerciseIndex == i }
                w.ensure(220f)
                w.text("${i + 1}. ${ex.title}", paint(17f, ACCENT, bold = true), after = 10f)
                val photos = ex.selectedPhotos().mapNotNull { decode(it) }
                if (photos.isNotEmpty()) {
                    val gap = 12f
                    val pw = (W - 2 * M - gap) / 2
                    val ph = pw * 3 / 4
                    w.ensure(ph + 14f)
                    photos.take(2).forEachIndexed { k, bmp ->
                        val left = M + k * (pw + gap)
                        drawCover(w.canvas, bmp, RectF(left, w.y, left + pw, w.y + ph))
                        bmp.recycle()
                    }
                    w.y += ph + 14f
                }
                w.text(e?.narrative.orEmpty(), body, after = 8f)
                if (ex.notes.isNotEmpty()) {
                    w.text(labels.notes.uppercase(v.lang.locale()), paint(9f, MUTED, serif = false), after = 2f)
                    ex.notes.forEach { w.text("„${it.text}“", paint(12f, INK, italic = true), after = 2f, inset = 10f) }
                    w.y += 8f
                }
                if (!e?.takeaway.isNullOrBlank()) {
                    val label = paint(9f, MUTED, serif = false)
                    val take = paint(12f, INK, italic = true)
                    val width = (W - 2 * M - 28).toInt()
                    val h =
                        StaticLayout.Builder.obtain(e.takeaway, 0, e.takeaway.length, take, width).build().height + 34f
                    w.ensure(h)
                    w.canvas.drawRect(M, w.y, W - M, w.y + h, Paint().apply { color = TAKEAWAY_BG })
                    w.canvas.drawRect(M, w.y, M + 4, w.y + h, Paint().apply { color = ACCENT })
                    w.y += 8f
                    w.text(labels.takeaway.uppercase(v.lang.locale()), label, after = 2f, inset = 14f)
                    w.text(e.takeaway, take, after = 10f, inset = 14f)
                }
                w.y += 18f
            }
            divider(w)
            w.text(lb.closing, paint(13f, INK), center, 0f)
            w.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun divider(w: Writer) {
        w.ensure(24f)
        w.canvas.drawLine(M, w.y, W - M, w.y, Paint().apply { color = Color.parseColor("#E7E1D4") })
        w.y += 24f
    }
}
