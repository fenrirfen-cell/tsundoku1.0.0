package eu.kanade.tachiyomi.ui.reader.viewer.pager

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * AI panel finder: a YOLOv8-nano comic panel detector (Inkwell panel models, Hugging Face),
 * run on the phone with ONNX Runtime. The 12 MB model is downloaded once, on first use.
 */
object PanelAi {

    private const val MODEL_URL =
        "https://huggingface.co/cedarrapidsboy/inkwell-panel-models/resolve/main/best.onnx"
    private const val SIZE = 640
    private const val CONF = 0.35f
    private const val IOU = 0.45f

    @Volatile private var session: OrtSession? = null
    private val downloading = AtomicBoolean(false)
    @Volatile private var failed = false

    private fun modelFile(ctx: Context) = File(ctx.filesDir, "panel-ai/panel-detector.onnx")

    /**
     * Panels in reading order as fractions 0..1 of [bmp], an empty list when the model sees no
     * panels, or null when the model isn't available yet (caller falls back to the simple finder).
     */
    fun detect(ctx: Context, bmp: Bitmap, rtl: Boolean): List<RectF>? {
        val s = session(ctx) ?: return null
        val w = bmp.width
        val h = bmp.height
        val scale = minOf(SIZE.toFloat() / w, SIZE.toFloat() / h)
        val nw = (w * scale).roundToInt().coerceIn(1, SIZE)
        val nh = (h * scale).roundToInt().coerceIn(1, SIZE)
        val padX = (SIZE - nw) / 2
        val padY = (SIZE - nh) / 2

        // Letterbox to 640x640 (grey padding), RGB, 0..1, CHW.
        val input = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(input).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(bmp, null, Rect(padX, padY, padX + nw, padY + nh), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val plane = SIZE * SIZE
        val px = IntArray(plane)
        input.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        input.recycle()
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = px[i]
            data[i] = ((c shr 16) and 255) / 255f
            data[plane + i] = ((c shr 8) and 255) / 255f
            data[2 * plane + i] = (c and 255) / 255f
        }

        val env = OrtEnvironment.getEnvironment()
        val boxes = ArrayList<FloatArray>() // x1, y1, x2, y2 (fractions), conf
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            s.run(mapOf(s.inputNames.iterator().next() to tensor)).use { res ->
                @Suppress("UNCHECKED_CAST")
                val out = (res[0].value as Array<Array<FloatArray>>)[0]
                // Usually [5][8400] (cx, cy, w, h, conf); handle [8400][5] too.
                val channelsFirst = out.size <= 6
                val count = if (channelsFirst) out[0].size else out.size
                for (i in 0 until count) {
                    fun v(k: Int) = if (channelsFirst) out[k][i] else out[i][k]
                    val conf = v(4)
                    if (conf < CONF) continue
                    val cx = v(0)
                    val cy = v(1)
                    val bw = v(2)
                    val bh = v(3)
                    val x1 = ((cx - bw / 2 - padX) / scale / w).coerceIn(0f, 1f)
                    val y1 = ((cy - bh / 2 - padY) / scale / h).coerceIn(0f, 1f)
                    val x2 = ((cx + bw / 2 - padX) / scale / w).coerceIn(0f, 1f)
                    val y2 = ((cy + bh / 2 - padY) / scale / h).coerceIn(0f, 1f)
                    if (x2 - x1 < 0.04f || y2 - y1 < 0.03f) continue
                    boxes.add(floatArrayOf(x1, y1, x2, y2, conf))
                }
            }
        }

        // Non-max suppression, then drop boxes that mostly sit inside a kept one.
        boxes.sortByDescending { it[4] }
        val kept = ArrayList<FloatArray>()
        for (b in boxes) {
            if (kept.none { iou(it, b) > IOU || insideFrac(b, it) > 0.8f }) kept.add(b)
        }

        // Reading order: rows top to bottom, then left-to-right (or right-to-left).
        kept.sortBy { it[1] }
        val rows = ArrayList<MutableList<FloatArray>>()
        var rowBottom = 0f
        var rowMinH = 0f
        for (q in kept) {
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
        val result = ArrayList<RectF>()
        for (row in rows) {
            val sorted = if (rtl) row.sortedByDescending { it[0] } else row.sortedBy { it[0] }
            for (q in sorted) result.add(RectF(q[0], q[1], q[2], q[3]))
        }
        return result
    }

    private fun area(a: FloatArray) = maxOf(0f, a[2] - a[0]) * maxOf(0f, a[3] - a[1])

    private fun interArea(a: FloatArray, b: FloatArray): Float {
        val ix = maxOf(0f, minOf(a[2], b[2]) - maxOf(a[0], b[0]))
        val iy = maxOf(0f, minOf(a[3], b[3]) - maxOf(a[1], b[1]))
        return ix * iy
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val inter = interArea(a, b)
        val union = area(a) + area(b) - inter
        return if (union <= 0f) 0f else inter / union
    }

    /** How much of [a] lies inside [b]. */
    private fun insideFrac(a: FloatArray, b: FloatArray): Float {
        val aa = area(a)
        return if (aa <= 0f) 0f else interArea(a, b) / aa
    }

    private fun session(ctx: Context): OrtSession? {
        session?.let { return it }
        val file = modelFile(ctx)
        if (!file.exists()) {
            startDownload(ctx)
            return null
        }
        synchronized(this) {
            session?.let { return it }
            return try {
                val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options).also { session = it }
            } catch (e: Throwable) {
                file.delete() // corrupt download: fetch it again next time
                null
            }
        }
    }

    private fun startDownload(ctx: Context) {
        if (failed || !downloading.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        toast(app, "Downloading AI panel finder (12 MB)...")
        Thread {
            try {
                val file = modelFile(app)
                file.parentFile?.mkdirs()
                val part = File(file.path + ".part")
                val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                try {
                    conn.inputStream.use { inp -> part.outputStream().use { inp.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                if (part.length() < 1_000_000L) error("Download too small")
                if (!part.renameTo(file)) error("Could not save model")
                toast(app, "AI panel finder ready - it's used for pages opened from now on")
            } catch (e: Throwable) {
                failed = true
                toast(app, "AI panel finder download failed - using the simple finder")
            } finally {
                downloading.set(false)
            }
        }.start()
    }

    private fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}
