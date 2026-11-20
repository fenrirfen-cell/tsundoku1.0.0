package eu.kanade.tachiyomi.ui.reader.viewer.pager

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AI bubble finder: RT-DETR-v2 comic text & bubble detector (ogkalu, Apache-2.0), phone-size int8
 * build, run with ONNX Runtime. Classes: 0 = bubble, 1 = text inside a bubble, 2 = text outside
 * bubbles (captions / narration). The 11 MB model is downloaded once, on first use.
 */
object BubbleAi {

    private const val MODEL_URL =
        "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx"
    private const val SIZE = 640
    private const val CONF = 0.35f

    @Volatile private var session: OrtSession? = null
    private val downloading = AtomicBoolean(false)
    @Volatile private var failed = false

    private fun modelFile(ctx: Context) = File(ctx.filesDir, "panel-ai/bubble-detector.onnx")

    /**
     * Bubbles and captions as fractions 0..1 of [bmp] (slightly padded, unordered), or null when
     * the model isn't available yet.
     */
    fun detect(ctx: Context, bmp: Bitmap): List<RectF>? {
        val s = session(ctx) ?: return null
        val w = bmp.width
        val h = bmp.height
        val scaled = Bitmap.createScaledBitmap(bmp, SIZE, SIZE, true)
        val plane = SIZE * SIZE
        val px = IntArray(plane)
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== bmp) scaled.recycle()
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = px[i]
            data[i] = ((c shr 16) and 255) / 255f
            data[plane + i] = ((c shr 8) and 255) / 255f
            data[2 * plane + i] = (c and 255) / 255f
        }
        val env = OrtEnvironment.getEnvironment()
        val found = ArrayList<FloatArray>() // x1, y1, x2, y2 (fractions), class
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { images ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(w.toLong(), h.toLong())), longArrayOf(1, 2)).use { sizes ->
                s.run(mapOf("images" to images, "orig_target_sizes" to sizes)).use { res ->
                    @Suppress("UNCHECKED_CAST")
                    val labels = (res.get("labels").get().value as Array<LongArray>)[0]
                    @Suppress("UNCHECKED_CAST")
                    val boxes = (res.get("boxes").get().value as Array<Array<FloatArray>>)[0]
                    @Suppress("UNCHECKED_CAST")
                    val scores = (res.get("scores").get().value as Array<FloatArray>)[0]
                    for (i in scores.indices) {
                        if (scores[i] < CONF) continue
                        val b = boxes[i]
                        val x1 = (b[0] / w).coerceIn(0f, 1f)
                        val y1 = (b[1] / h).coerceIn(0f, 1f)
                        val x2 = (b[2] / w).coerceIn(0f, 1f)
                        val y2 = (b[3] / h).coerceIn(0f, 1f)
                        if (x2 - x1 < 0.01f || y2 - y1 < 0.008f) continue
                        found.add(floatArrayOf(x1, y1, x2, y2, labels[i].toFloat(), scores[i]))
                    }
                }
            }
        }
        // Keep bubbles and captions; keep bubble text only when no detected bubble holds it.
        val bubbles = found.filter { it[4].toInt() == 0 }
        val targets = ArrayList<FloatArray>()
        for (b in found.sortedByDescending { it[5] }) {
            val cls = b[4].toInt()
            if (cls == 1 && bubbles.any { insideFrac(b, it) > 0.5f }) continue
            if (targets.any { iou(it, b) > 0.6f || insideFrac(b, it) > 0.8f }) continue
            targets.add(b)
        }
        return targets.map {
            val pw = (it[2] - it[0]) * 0.15f
            val ph = (it[3] - it[1]) * 0.15f
            RectF(
                (it[0] - pw).coerceAtLeast(0f),
                (it[1] - ph).coerceAtLeast(0f),
                (it[2] + pw).coerceAtMost(1f),
                (it[3] + ph).coerceAtMost(1f),
            )
        }
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
                file.delete()
                null
            }
        }
    }

    private fun startDownload(ctx: Context) {
        if (failed || !downloading.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        toast(app, "Downloading AI bubble finder (11 MB)...")
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
                toast(app, "AI bubble finder ready - it's used for pages opened from now on")
            } catch (e: Throwable) {
                failed = true
                toast(app, "AI bubble finder download failed - zooming to panels only")
            } finally {
                downloading.set(false)
            }
        }.start()
    }

    private fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}
