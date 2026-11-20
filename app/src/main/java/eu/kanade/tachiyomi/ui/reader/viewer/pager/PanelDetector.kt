package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import java.io.InputStream

/**
 * Finds comic panels by looking for gutters (white or black gaps) and cutting the page
 * recursively along them (XY-cut). Offline, no ML.
 *
 * - A clean gutter is a row / column that is almost entirely white or almost entirely black.
 * - A gutter partly blocked by art (a character or bubble poking through) still counts when most
 *   of it is white/black, it has no thin frame lines crossing it, and it has panel art or a frame
 *   right above and below it.
 * - Skies, scenery and textured backgrounds are never white/black enough, so full-bleed pages
 *   come back with no panels and are just shown whole.
 */
object PanelDetector {

    /** [panels] in reading order and [content] (page without margins), as fractions 0..1 of the image. */
    class Result(val panels: List<RectF>, val content: RectF, val shapes: List<FloatArray?> = emptyList(), val bubbles: List<List<RectF>> = emptyList())

    private const val MAX_SIDE = 1000
    private const val MAX_PANELS = 40
    private const val WHITE = 200
    private const val BLACK = 40

    fun detect(open: () -> InputStream, rtl: Boolean, aiContext: Context? = null, bubbleContext: Context? = null): Result? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val ow = bounds.outWidth
        val oh = bounds.outHeight
        if (ow <= 0 || oh <= 0) return null
        var sample = 1
        while (maxOf(ow, oh) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = open().use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return try {
            detect(bmp, rtl, aiContext, bubbleContext)
        } finally {
            bmp.recycle()
        }
    }

    fun detect(bmp: Bitmap, rtl: Boolean, aiContext: Context? = null, bubbleContext: Context? = null): Result? {
        val base = detectPanels(bmp, rtl, aiContext)
        if (bubbleContext == null) return base
        val found = try {
            BubbleAi.detect(bubbleContext, bmp)
        } catch (e: Throwable) {
            null
        }
        if (found.isNullOrEmpty()) return base
        // No panels found: treat the whole page as one panel so its bubbles can still be read.
        val panels = base?.panels ?: listOf(RectF(0f, 0f, 1f, 1f))
        val content = base?.content ?: RectF(0f, 0f, 1f, 1f)
        val shapes = base?.shapes ?: emptyList()
        val perPanel = List(panels.size) { ArrayList<RectF>() }
        for (b in found) {
            var bestI = -1
            var bestF = 0f
            for (i in panels.indices) {
                val f = overlapFrac(b, panels[i])
                if (f > bestF) {
                    bestF = f
                    bestI = i
                }
            }
            if (bestI >= 0 && bestF >= 0.5f) perPanel[bestI].add(b)
        }
        return Result(panels, content, shapes, perPanel.map { orderRows(it, rtl) })
    }

    /** How much of [a] lies inside [b]. */
    private fun overlapFrac(a: RectF, b: RectF): Float {
        val ix = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left))
        val iy = maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val aa = a.width() * a.height()
        return if (aa <= 0f) 0f else ix * iy / aa
    }

    /** Reading order: rows top to bottom, then left-to-right (right-to-left for manga). */
    private fun orderRows(items: List<RectF>, rtl: Boolean): List<RectF> {
        val sorted = items.sortedBy { it.top }
        val rows = ArrayList<MutableList<RectF>>()
        var rowBottom = 0f
        var rowMinH = 0f
        for (q in sorted) {
            val qh = q.height()
            if (rows.isNotEmpty() && q.top < rowBottom - 0.5f * minOf(qh, rowMinH)) {
                rows.last().add(q)
                rowBottom = maxOf(rowBottom, q.bottom)
                rowMinH = minOf(rowMinH, qh)
            } else {
                rows.add(mutableListOf(q))
                rowBottom = q.bottom
                rowMinH = qh
            }
        }
        return rows.flatMap { row -> if (rtl) row.sortedByDescending { it.left } else row.sortedBy { it.left } }
    }
    private fun detectPanels(bmp: Bitmap, rtl: Boolean, aiContext: Context?): Result? {
        val w = bmp.width
        val h = bmp.height
        if (w < 32 || h < 32) return null
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        // 1 = white, 2 = black, 0 = anything else
        val kind = ByteArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val l = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
            kind[i] = if (l >= WHITE) 1 else if (l <= BLACK) 2 else 0
        }
        val ctx = Ctx(w, h, kind, maxOf(3, (minOf(w, h) * 0.005f).toInt()))

        val content = ctx.trim(Box(0, 0, w, h)) ?: return null
        val contentRect = RectF(content.l.toFloat() / w, content.t.toFloat() / h, content.r.toFloat() / w, content.b.toFloat() / h)
        if (aiContext != null) {
            val ai = try {
                PanelAi.detect(aiContext, bmp, rtl)
            } catch (e: Throwable) {
                null
            }
            // null = model not ready yet -> fall through to the simple finder below.
            if (ai != null) {
                if (ai.size < 2) return null
                val aiShapes = ai.map { r ->
                    val box = Box((r.left * w).toInt(), (r.top * h).toInt(), minOf(w, (r.right * w).toInt()), minOf(h, (r.bottom * h).toInt()))
                    ctx.quad(box)?.let { q -> FloatArray(8) { i -> if (i % 2 == 0) q[i] / w else q[i] / h } }
                }
                return Result(ai, contentRect, aiShapes)
            }
        }
        val out = ArrayList<Box>()
        ctx.cut(content, out, 0, rtl)
        val cw = content.r - content.l
        val ch = content.b - content.t
        val contentArea = cw.toLong() * ch
        // Big leftovers (slanted / irregular gutters): split them by islands of art surrounded by white.
        val split = ArrayList<Box>()
        for (box in out) {
            val area = (box.r - box.l).toLong() * (box.b - box.t)
            val parts = if (area >= contentArea * 0.3) ctx.islands(box, rtl) else null
            if (parts != null) split.addAll(parts) else split.add(box)
        }
        val panels = split.filter {
            (it.r - it.l).toLong() * (it.b - it.t) >= contentArea * 0.012 &&
                (it.r - it.l) >= cw * 0.06f &&
                (it.b - it.t) >= ch * 0.04f
        }
        if (panels.size < 2 || panels.size > MAX_PANELS) return null
        if (panels.any { (it.r - it.l).toLong() * (it.b - it.t) > contentArea * 0.85 }) return null
        val pad = 2
        val shapes = panels.map { b -> ctx.quad(b)?.let { q -> FloatArray(8) { i -> if (i % 2 == 0) q[i] / w else q[i] / h } } }
        return Result(
            panels.map {
                RectF(
                    maxOf(0, it.l - pad).toFloat() / w,
                    maxOf(0, it.t - pad).toFloat() / h,
                    minOf(w, it.r + pad).toFloat() / w,
                    minOf(h, it.b + pad).toFloat() / h,
                )
            },
            RectF(content.l.toFloat() / w, content.t.toFloat() / h, content.r.toFloat() / w, content.b.toFloat() / h),
            shapes,
        )
    }

    private class Box(val l: Int, val t: Int, val r: Int, val b: Int)

    private class Ctx(val w: Int, val h: Int, val kind: ByteArray, val minGap: Int) {

        private fun cell(line: Int, pos: Int, alongY: Boolean): Int =
            if (alongY) kind[line * w + pos].toInt() else kind[pos * w + line].toInt()

        /** 1 / 2 when the line is (almost) entirely white / black over [from, to), else 0. */
        private fun strictKind(white: Int, black: Int, n: Int): Int {
            val need = n - maxOf(1, n / 100) // allow ~1% stray pixels (JPEG noise, page edges)
            return if (white >= need) 1 else if (black >= need) 2 else 0
        }

        private fun lineKind(line: Int, from: Int, to: Int, alongY: Boolean): Int {
            var wh = 0
            var bl = 0
            for (p in from until to) {
                when (cell(line, p, alongY)) {
                    1 -> wh++
                    2 -> bl++
                }
            }
            return strictKind(wh, bl, to - from)
        }

        /** Number of thin (frame-line-like) runs of non-[color] cells along the line. */
        private fun thinRuns(line: Int, from: Int, to: Int, alongY: Boolean, color: Int): Int {
            val lim = maxOf(2, (to - from) / 100)
            var runs = 0
            var p = from
            while (p < to) {
                if (cell(line, p, alongY) != color) {
                    var q = p
                    while (q < to && cell(line, q, alongY) != color) q++
                    if (q - p <= lim) runs++
                    p = q
                } else {
                    p++
                }
            }
            return runs
        }

        /** Shrinks [box] past gutter lines at its edges, stopping at a colour change (a panel frame). */
        fun trim(box: Box): Box? {
            var t = box.t
            var b = box.b
            var l = box.l
            var r = box.r
            var c = 0
            while (t < b) {
                val k = lineKind(t, l, r, true)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                t++
            }
            if (t >= b) return null
            c = 0
            while (b > t) {
                val k = lineKind(b - 1, l, r, true)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                b--
            }
            c = 0
            while (l < r) {
                val k = lineKind(l, t, b, false)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                l++
            }
            c = 0
            while (r > l) {
                val k = lineKind(r - 1, t, b, false)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                r--
            }
            return if (r > l) Box(l, t, r, b) else null
        }

        /** Per line of [box] (rows if [alongY], else columns): 0 = content, 1 = white gutter, 2 = black gutter. */
        private fun gutters(box: Box, alongY: Boolean): IntArray {
            val st = if (alongY) box.t else box.l
            val en = if (alongY) box.b else box.r
            val from = if (alongY) box.l else box.t
            val to = if (alongY) box.r else box.b
            val n = en - st
            val wh = IntArray(n)
            val bl = IntArray(n)
            val g = IntArray(n)
            val len = (to - from).coerceAtLeast(1)
            for (k in 0 until n) {
                var a = 0
                var d = 0
                for (p in from until to) {
                    when (cell(st + k, p, alongY)) {
                        1 -> a++
                        2 -> d++
                    }
                }
                wh[k] = a
                bl[k] = d
                g[k] = strictKind(a, d, len)
            }
            for (color in 1..2) {
                val fr = FloatArray(n) { k ->
                    val f = (if (color == 1) wh[k] else bl[k]).toFloat() / len
                    if (f >= 0.6f && g[k] == 0 && thinRuns(st + k, from, to, alongY, color) > 1) 0.5f else f
                }
                var i = 0
                while (i < n) {
                    if (fr[i] >= 0.6f) {
                        var j = i
                        while (j < n && fr[j] >= 0.6f) j++
                        if (j - i >= minGap) {
                            var before = 1f
                            for (q in maxOf(0, i - 3) until i) before = minOf(before, fr[q])
                            var after = 1f
                            for (q in j until minOf(n, j + 3)) after = minOf(after, fr[q])
                            val okBefore = i == 0 || before < 0.4f
                            val okAfter = j == n || after < 0.4f
                            if (okBefore && okAfter) {
                                for (q in i until j) if (g[q] == 0) g[q] = color
                            }
                        }
                        i = j
                    } else {
                        i++
                    }
                }
            }
            return g
        }

        /** Content runs along Y (rows) or X (columns), split wherever a gutter is at least [minGap] wide. */
        fun segments(box: Box, alongY: Boolean): List<IntRange> {
            val st = if (alongY) box.t else box.l
            val n = (if (alongY) box.b else box.r) - st
            val g = gutters(box, alongY)
            val segs = ArrayList<IntRange>()
            var s = -1
            var gap = 0
            for (k in 0 until n) {
                if (g[k] == 0) {
                    if (s < 0) {
                        s = k
                        while (s > 0 && g[s - 1] != g[0]) s--
                    } else if (gap >= minGap) {
                        // Keep frame lines with their panel: stop at the gutter's own colour.
                        val mid = g[k - gap + gap / 2]
                        var e = k - gap
                        while (e < k && g[e] != mid) e++
                        var ns = k
                        while (ns > e && g[ns - 1] != mid) ns--
                        segs += (st + s) until (st + e)
                        s = ns
                    }
                    gap = 0
                } else if (s >= 0) {
                    gap++
                }
            }
            if (s >= 0) {
                var e = n - gap
                while (e < n && g[e] != g[n - 1]) e++
                segs += (st + s) until (st + e)
            }
            return segs
        }

        /**
         * Splits [box] into "islands": connected areas of non-white pixels (panel art + frame)
         * separated by white. Handles slanted and irregular gutters. Returns null if < 2 islands.
         */
        fun islands(box: Box, rtl: Boolean): List<Box>? {
            val bw = box.r - box.l
            val bh = box.b - box.t
            if (bw <= 0 || bh <= 0) return null
            val size = bw * bh
            val label = ByteArray(size) // 0 = unvisited, 1 = visited
            val queue = IntArray(size)
            val found = ArrayList<IntArray>() // l, t, r, b (absolute, exclusive r/b)
            val area = size.toLong()
            fun ink(i: Int): Boolean = kind[(box.t + i / bw) * w + box.l + i % bw].toInt() != 1
            for (start in 0 until size) {
                if (label[start].toInt() != 0) continue
                label[start] = 1
                if (!ink(start)) continue
                var head = 0
                var tail = 0
                queue[tail++] = start
                var minX = bw
                var minY = bh
                var maxX = -1
                var maxY = -1
                while (head < tail) {
                    val p = queue[head++]
                    val px = p % bw
                    val py = p / bw
                    if (px < minX) minX = px
                    if (px > maxX) maxX = px
                    if (py < minY) minY = py
                    if (py > maxY) maxY = py
                    if (px > 0) {
                        val q = p - 1
                        if (label[q].toInt() == 0) { label[q] = 1; if (ink(q)) queue[tail++] = q }
                    }
                    if (px < bw - 1) {
                        val q = p + 1
                        if (label[q].toInt() == 0) { label[q] = 1; if (ink(q)) queue[tail++] = q }
                    }
                    if (py > 0) {
                        val q = p - bw
                        if (label[q].toInt() == 0) { label[q] = 1; if (ink(q)) queue[tail++] = q }
                    }
                    if (py < bh - 1) {
                        val q = p + bw
                        if (label[q].toInt() == 0) { label[q] = 1; if (ink(q)) queue[tail++] = q }
                    }
                }
                val iw = maxX - minX + 1
                val ih = maxY - minY + 1
                if (iw.toLong() * ih >= area * 0.03 && iw >= bw * 0.08f && ih >= bh * 0.05f) {
                    found.add(intArrayOf(box.l + minX, box.t + minY, box.l + maxX + 1, box.t + maxY + 1))
                }
            }
            // Merge islands that mostly sit inside another (e.g. a bubble poking out of its panel).
            var merged = true
            while (merged) {
                merged = false
                loop@ for (i in found.indices) {
                    for (j in found.indices) {
                        if (i == j) continue
                        val a = found[i]
                        val c = found[j]
                        val ix = maxOf(0, minOf(a[2], c[2]) - maxOf(a[0], c[0]))
                        val iy = maxOf(0, minOf(a[3], c[3]) - maxOf(a[1], c[1]))
                        val sa = (a[2] - a[0]).toLong() * (a[3] - a[1])
                        if (ix.toLong() * iy >= sa * 0.6) {
                            c[0] = minOf(a[0], c[0])
                            c[1] = minOf(a[1], c[1])
                            c[2] = maxOf(a[2], c[2])
                            c[3] = maxOf(a[3], c[3])
                            found.removeAt(i)
                            merged = true
                            break@loop
                        }
                    }
                }
            }
            if (found.size < 2) return null
            // Reading order: group into rows (top to bottom), then left-to-right (or right-to-left).
            found.sortBy { it[1] }
            val rows = ArrayList<MutableList<IntArray>>()
            var rowBottom = 0
            var rowMinH = 0
            for (q in found) {
                val qh = q[3] - q[1]
                if (rows.isNotEmpty() && q[1] < rowBottom - 0.5f * minOf(qh, rowMinH)) {
                    rows.last().add(q)
                    rowBottom = maxOf(rowBottom, q[3])
                    rowMinH = minOf(rowMinH, qh)
                } else {
                    rows.add(mutableListOf(q))
                    rowBottom = q[3]
                    rowMinH = qh
                }
            }
            val result = ArrayList<Box>()
            for (row in rows) {
                val sorted = if (rtl) row.sortedByDescending { it[0] } else row.sortedBy { it[0] }
                for (q in sorted) result += Box(q[0], q[1], q[2], q[3])
            }
            return result
        }

        /**
         * The real outline of the panel inside [box]: the biggest connected area of art, reduced to its
         * four extreme corners (handles tilted panels). Image pixels, order TL, TR, BR, BL.
         * Null when the panel is simply the rectangle [box].
         */
        fun quad(box: Box): FloatArray? {
            val bw = box.r - box.l
            val bh = box.b - box.t
            if (bw < 8 || bh < 8) return null
            val size = bw * bh
            val seen = ByteArray(size)
            val queue = IntArray(size)
            fun ink(i: Int): Boolean = kind[(box.t + i / bw) * w + box.l + i % bw].toInt() != 1
            var bestCount = 0
            var best: IntArray? = null
            var bestBox = 0L
            for (start in 0 until size) {
                if (seen[start].toInt() != 0) continue
                seen[start] = 1
                if (!ink(start)) continue
                var head = 0
                var tail = 0
                queue[tail++] = start
                var sMin = Int.MAX_VALUE
                var sMax = Int.MIN_VALUE
                var dMin = Int.MAX_VALUE
                var dMax = Int.MIN_VALUE
                var tl = start
                var br = start
                var tr = start
                var bl = start
                var minX = bw
                var minY = bh
                var maxX = -1
                var maxY = -1
                while (head < tail) {
                    val p = queue[head++]
                    val px = p % bw
                    val py = p / bw
                    val s = px + py
                    val d = px - py
                    if (s < sMin) { sMin = s; tl = p }
                    if (s > sMax) { sMax = s; br = p }
                    if (d > dMax) { dMax = d; tr = p }
                    if (d < dMin) { dMin = d; bl = p }
                    if (px < minX) minX = px
                    if (px > maxX) maxX = px
                    if (py < minY) minY = py
                    if (py > maxY) maxY = py
                    if (px > 0) { val q = p - 1; if (seen[q].toInt() == 0) { seen[q] = 1; if (ink(q)) queue[tail++] = q } }
                    if (px < bw - 1) { val q = p + 1; if (seen[q].toInt() == 0) { seen[q] = 1; if (ink(q)) queue[tail++] = q } }
                    if (py > 0) { val q = p - bw; if (seen[q].toInt() == 0) { seen[q] = 1; if (ink(q)) queue[tail++] = q } }
                    if (py < bh - 1) { val q = p + bw; if (seen[q].toInt() == 0) { seen[q] = 1; if (ink(q)) queue[tail++] = q } }
                }
                if (tail > bestCount) {
                    bestCount = tail
                    best = intArrayOf(tl, tr, br, bl)
                    bestBox = (maxX - minX + 1).toLong() * (maxY - minY + 1)
                }
            }
            val corners = best ?: return null
            if (bestBox < size * 0.6) return null // the main island isn't the panel; keep the rectangle
            val q = FloatArray(8)
            for (k in 0 until 4) {
                q[2 * k] = box.l + corners[k] % bw + 0.5f
                q[2 * k + 1] = box.t + corners[k] / bw + 0.5f
            }
            // Shoelace area: a near-full rectangle isn't worth a custom outline.
            var area = 0f
            for (k in 0 until 4) {
                val n = (k + 1) % 4
                area += q[2 * k] * q[2 * n + 1] - q[2 * n] * q[2 * k + 1]
            }
            if (kotlin.math.abs(area) / 2f >= size * 0.93f) return null
            return q
        }
        /** True when [box] is surrounded by black frame lines: it's a finished panel. */
        private fun framed(box: Box): Boolean {
            if (box.t < 1 || box.l < 1 || box.b >= h || box.r >= w) return false
            fun blackFrac(line: Int, from: Int, to: Int, alongY: Boolean): Float {
                var d = 0
                for (p in from until to) if (cell(line, p, alongY) == 2) d++
                return d.toFloat() / (to - from).coerceAtLeast(1)
            }
            return blackFrac(box.t - 1, box.l, box.r, true) >= 0.9f &&
                blackFrac(box.b, box.l, box.r, true) >= 0.9f &&
                blackFrac(box.l - 1, box.t, box.b, false) >= 0.9f &&
                blackFrac(box.r, box.t, box.b, false) >= 0.9f
        }

        fun cut(box: Box, out: MutableList<Box>, depth: Int, rtl: Boolean) {
            if (depth > 8 || out.size > MAX_PANELS || (depth > 0 && framed(box))) {
                out += box
                return
            }
            val rows = segments(box, true)
            if (rows.size > 1) {
                for (s in rows) trim(Box(box.l, s.first, box.r, s.last + 1))?.let { cut(it, out, depth + 1, rtl) }
                return
            }
            val cols = segments(box, false)
            if (cols.size > 1) {
                val ordered = if (rtl) cols.asReversed() else cols
                for (s in ordered) trim(Box(s.first, box.t, s.last + 1, box.b))?.let { cut(it, out, depth + 1, rtl) }
                return
            }
            out += box
        }
    }
}
