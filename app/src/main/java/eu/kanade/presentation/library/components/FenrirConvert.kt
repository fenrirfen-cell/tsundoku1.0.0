package eu.kanade.presentation.library.components

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Turns comic archives (CBZ, CBR, 7z), PDFs and DRM-free Kindle files (MOBI, AZW3) into EPUBs
 * the importer can read. Everything runs on the phone; nothing is uploaded.
 */
object FenrirConvert {
    class Locked : Exception("This book is locked (DRM)")

    private val imageExt = Regex(""".*\.(jpe?g|png|webp|gif|avif)$""", RegexOption.IGNORE_CASE)

    /** Natural order: page2 before page10. */
    private fun natural(a: String, b: String): Int {
        val ra = Regex("""\d+|\D+""").findAll(a.lowercase()).map { it.value }.toList()
        val rb = Regex("""\d+|\D+""").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]; val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y)
            if (c != 0) return c
        }
        return ra.size - rb.size
    }

    /** Comic archive (zip / rar / 7z) → picture EPUB. */
    fun archiveToEpub(src: File, title: String, out: File) {
        val images = mutableListOf<Pair<String, ByteArray>>()
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            mihon.core.archive.ArchiveReader(pfd).use { reader ->
                val names = reader.useEntries { seq -> seq.filter { it.isFile && imageExt.matches(it.name) }.map { it.name }.toList() }
                names.sortedWith { a, b -> natural(a, b) }.forEach { name ->
                    reader.getInputStream(name)?.use { images += name.substringAfterLast('/') to it.readBytes() }
                }
            }
        }
        require(images.isNotEmpty()) { "No pictures inside" }
        writeImageEpub(title, images, out)
    }

    /** PDF → picture EPUB (each page drawn as a picture, so the layout stays exactly as it was). */
    fun pdfToEpub(src: File, title: String, out: File, maxPages: Int = 2000) {
        val images = mutableListOf<Pair<String, ByteArray>>()
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { pdf ->
                for (i in 0 until minOf(pdf.pageCount, maxPages)) {
                    pdf.openPage(i).use { page ->
                        val w = 1200
                        val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
                        bmp.recycle()
                        images += "page-%04d.jpg".format(i + 1) to bytes
                    }
                }
            }
        }
        require(images.isNotEmpty()) { "The PDF has no pages" }
        writeImageEpub(title, images, out)
    }

    /** DRM-free MOBI / AZW3 → text EPUB (PalmDOC-compressed books; pictures are left out). */
    fun mobiToEpub(src: File, out: File): String {
        RandomAccessFile(src, "r").use { f ->
            val header = ByteArray(78).also { f.readFully(it) }
            val name = String(header, 0, 32, Charsets.ISO_8859_1).trimEnd('\u0000')
            val count = u16(header, 76)
            val offsets = IntArray(count) { f.readInt().also { f.readInt() } }
            fun record(i: Int): ByteArray {
                val start = offsets[i].toLong()
                val end = if (i + 1 < count) offsets[i + 1].toLong() else f.length()
                f.seek(start)
                return ByteArray((end - start).toInt()).also { f.readFully(it) }
            }
            val r0 = record(0)
            val compression = u16(r0, 0)
            val textRecords = u16(r0, 8)
            val encryption = u16(r0, 12)
            if (encryption != 0) throw Locked()
            require(compression == 1 || compression == 2) { "This Kindle file uses a format that can't be converted yet" }
            var title = name.replace('_', ' ')
            var author: String? = null
            // MOBI header + EXTH for the real title / author
            if (r0.size > 20 && String(r0, 16, 4, Charsets.ISO_8859_1) == "MOBI") {
                val mobiLen = u32(r0, 20)
                val extraFlags = if (mobiLen >= 0xE4 && r0.size >= 16 + 0xF4) u16(r0, 16 + 0xF2) else 0
                val exthFlag = u32(r0, 16 + 0x70)
                val fullNameOff = u32(r0, 16 + 0x44); val fullNameLen = u32(r0, 16 + 0x48)
                if (fullNameOff in 1 until r0.size && fullNameLen in 1..512 && fullNameOff + fullNameLen <= r0.size) {
                    title = String(r0, fullNameOff, fullNameLen, Charsets.UTF_8)
                }
                if (exthFlag and 0x40 != 0) {
                    val e = 16 + mobiLen
                    if (e + 12 <= r0.size && String(r0, e, 4, Charsets.ISO_8859_1) == "EXTH") {
                        val n = u32(r0, e + 8); var p = e + 12
                        repeat(n) {
                            if (p + 8 > r0.size) return@repeat
                            val type = u32(r0, p); val len = u32(r0, p + 4)
                            if (len < 8 || p + len > r0.size) return@repeat
                            val v = String(r0, p + 8, len - 8, Charsets.UTF_8)
                            if (type == 100) author = v
                            if (type == 503) title = v
                            p += len
                        }
                    }
                }
                val text = ByteArrayOutputStream()
                for (i in 1..textRecords.coerceAtMost(count - 1)) {
                    var rec = record(i)
                    rec = trimTrailing(rec, extraFlags)
                    text.write(if (compression == 2) palmDoc(rec) else rec)
                }
                writeTextEpub(title, author, String(text.toByteArray(), Charsets.UTF_8), out)
                return title
            }
            val text = ByteArrayOutputStream()
            for (i in 1..textRecords.coerceAtMost(count - 1)) text.write(if (compression == 2) palmDoc(record(i)) else record(i))
            writeTextEpub(title, author, String(text.toByteArray(), Charsets.ISO_8859_1), out)
            return title
        }
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
        ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    /** Drops the trailing entries MOBI adds to each text record. */
    private fun trimTrailing(rec: ByteArray, flags: Int): ByteArray {
        var size = rec.size
        var f = flags shr 1
        while (f != 0) {
            if (f and 1 != 0 && size > 0) {
                var n = 0; var shift = 0; var pos = size - 1
                // backward variable-width integer
                var count = 0
                while (pos >= 0 && count < 4) {
                    val b = rec[pos].toInt() and 0xFF
                    n = n or ((b and 0x7F) shl shift)
                    shift += 7; count++
                    if (b and 0x80 != 0) break
                    pos--
                }
                size -= n.coerceIn(0, size)
            }
            f = f shr 1
        }
        if (flags and 1 != 0 && size > 0) size -= ((rec[size - 1].toInt() and 0x3) + 1).coerceAtMost(size)
        return rec.copyOf(size.coerceAtLeast(0))
    }

    /** PalmDOC (LZ77-style) decompression. */
    private fun palmDoc(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size * 2)
        val buf = ArrayList<Byte>(input.size * 2)
        var i = 0
        while (i < input.size) {
            val c = input[i++].toInt() and 0xFF
            when {
                c == 0 || c in 0x09..0x7F -> buf.add(c.toByte())
                c in 0x01..0x08 -> repeat(c) { if (i < input.size) buf.add(input[i++]) }
                c in 0x80..0xBF -> {
                    if (i >= input.size) break
                    val pair = (c shl 8) or (input[i++].toInt() and 0xFF)
                    val dist = (pair shr 3) and 0x7FF
                    val len = (pair and 0x7) + 3
                    repeat(len) { if (dist in 1..buf.size) buf.add(buf[buf.size - dist]) }
                }
                else -> { buf.add(' '.code.toByte()); buf.add((c xor 0x80).toByte()) }
            }
        }
        buf.forEach { out.write(it.toInt()) }
        return out.toByteArray()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun ZipOutputStream.stored(name: String, bytes: ByteArray) {
        val e = ZipEntry(name).apply {
            method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = bytes.size.toLong()
            crc = CRC32().also { it.update(bytes) }.value
        }
        putNextEntry(e); write(bytes); closeEntry()
    }

    private fun ZipOutputStream.text(name: String, s: String) {
        putNextEntry(ZipEntry(name)); write(s.toByteArray(Charsets.UTF_8)); closeEntry()
    }

    private fun start(zip: ZipOutputStream) {
        zip.stored("mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
        zip.text(
            "META-INF/container.xml",
            """<?xml version="1.0" encoding="UTF-8"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
        )
    }

    private fun opf(title: String, author: String?, manifest: String, spine: String, coverId: String?) = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">urn:uuid:${UUID.randomUUID()}</dc:identifier><dc:title>${esc(title)}</dc:title><dc:language>en</dc:language>${author?.let { "<dc:creator>${esc(it)}</dc:creator>" } ?: ""}
<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>${coverId?.let { "<meta name=\"cover\" content=\"$it\"/>" } ?: ""}</metadata>
<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$manifest</manifest><spine>$spine</spine></package>"""

    private fun nav(entries: List<Pair<String, String>>) = """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol>${entries.joinToString("") { "<li><a href=\"${it.second}\">${esc(it.first)}</a></li>" }}</ol></nav></body></html>"""

    private fun writeImageEpub(title: String, images: List<Pair<String, ByteArray>>, out: File) {
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            start(zip)
            val manifest = StringBuilder(); val spine = StringBuilder(); val toc = mutableListOf<Pair<String, String>>()
            images.forEachIndexed { i, (name, bytes) ->
                val ext = name.substringAfterLast('.', "jpg").lowercase().let { if (it == "jpeg") "jpg" else it }
                val mime = when (ext) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "avif" -> "image/avif"; else -> "image/jpeg" }
                val img = "images/p%04d.%s".format(i + 1, ext)
                val page = "p%04d.xhtml".format(i + 1)
                zip.putNextEntry(ZipEntry("OEBPS/$img")); zip.write(bytes); zip.closeEntry()
                zip.text(
                    "OEBPS/$page",
                    """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${i + 1}</title><style>body{margin:0;padding:0;text-align:center}img{max-width:100%;height:auto}</style></head><body><img src="$img" alt="Page ${i + 1}"/></body></html>""",
                )
                manifest.append("<item id=\"img$i\" href=\"$img\" media-type=\"$mime\"${if (i == 0) " properties=\"cover-image\"" else ""}/><item id=\"pg$i\" href=\"$page\" media-type=\"application/xhtml+xml\"/>")
                spine.append("<itemref idref=\"pg$i\"/>")
                if (i == 0 || (i + 1) % 20 == 0) toc += "Page ${i + 1}" to page
            }
            zip.text("OEBPS/nav.xhtml", nav(toc))
            zip.text("OEBPS/content.opf", opf(title, null, manifest.toString(), spine.toString(), "img0"))
        }
    }

    private fun writeTextEpub(title: String, author: String?, html: String, out: File) {
        val cleaned = html
            .replace(Regex("""<mbp:pagebreak\s*/?>""", RegexOption.IGNORE_CASE), "\u0000")
            .replace(Regex("""</?mbp:[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), "")
        val parts = cleaned.split('\u0000').map { it.trim() }.filter { it.replace(Regex("<[^>]+>"), "").isNotBlank() }
            .ifEmpty { listOf(cleaned) }
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            start(zip)
            val manifest = StringBuilder(); val spine = StringBuilder(); val toc = mutableListOf<Pair<String, String>>()
            parts.forEachIndexed { i, part ->
                val doc = org.jsoup.Jsoup.parseBodyFragment(part)
                doc.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml).escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                val heading = doc.select("h1,h2,h3").firstOrNull()?.text()?.take(80)?.ifBlank { null } ?: "Chapter ${i + 1}"
                val page = "c%04d.xhtml".format(i + 1)
                zip.text(
                    "OEBPS/$page",
                    """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${esc(heading)}</title></head><body>${doc.body().html()}</body></html>""",
                )
                manifest.append("<item id=\"c$i\" href=\"$page\" media-type=\"application/xhtml+xml\"/>")
                spine.append("<itemref idref=\"c$i\"/>")
                toc += heading to page
            }
            zip.text("OEBPS/nav.xhtml", nav(toc))
            zip.text("OEBPS/content.opf", opf(title, author, manifest.toString(), spine.toString(), null))
        }
    }
}
