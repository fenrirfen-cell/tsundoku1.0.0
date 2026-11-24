package eu.kanade.tachiyomi.ui.reader.viewer.text.webview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Page-curl overlay for the novel reader's "Page flip" mode. Sits on top of the WebView.
 *
 * forward = true: [bitmap] is a snapshot of the page being turned away. The area it uncovers is
 * left transparent, so the WebView underneath (already on the next page) shows through.
 *
 * forward = false: [bitmap] is a snapshot of the page underneath. The page coming back is the
 * WebView itself (already on the previous page), which shows through inside the unfolded area.
 *
 * Geometry: the fold line is the perpendicular bisector of the corner -> finger segment. The part
 * of the page on the corner's side is reflected across it and drawn as the back of the paper.
 */
@SuppressLint("ViewConstructor")
class PageCurlView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var forward = true

    /** Color of the back of the page (the folded-over part). */
    var backColor: Int = Color.rgb(236, 233, 224)

    var pageWidth = 0f
        private set
    var pageHeight = 0f
        private set
    var cornerY = 0f
        private set
    private var cornerX = 0f
    private var px = 0f
    private var py = 0f

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33000000
    }
    private var animator: ValueAnimator? = null
    private val reflectMatrix = android.graphics.Matrix()
    private val throughPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        isClickable = false
        isFocusable = false
    }

    fun begin(snapshot: Bitmap, forward: Boolean, cornerTop: Boolean, startX: Float, startY: Float) {
        animator?.cancel()
        animator = null
        if (bitmap !== snapshot) bitmap?.recycle()
        bitmap = snapshot
        this.forward = forward
        pageWidth = snapshot.width.toFloat()
        pageHeight = snapshot.height.toFloat()
        cornerX = pageWidth
        cornerY = if (cornerTop) 0f else pageHeight
        setPoint(startX, startY)
    }

    fun dragTo(x: Float, y: Float) {
        if (animator?.isRunning == true) return
        setPoint(x, y)
    }

    /**
     * Fenrir: going back, the finger holds the fold: at the left edge the page is still over the
     * spine, at the right edge it lies flat.
     */
    fun dragBackTo(x: Float, y: Float) = dragTo(2f * x - pageWidth, y)

    /** [smooth] = a whole turn from a tap: even speed, easing in and out like a real page. */
    fun animateTo(tx: Float, ty: Float, arc: Float, durationMs: Long, smooth: Boolean = false, onEnd: () -> Unit) {
        animator?.cancel()
        val fromX = px
        val fromY = py
        var cancelled = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = if (smooth) android.view.animation.AccelerateDecelerateInterpolator() else DecelerateInterpolator(1.6f)
            addUpdateListener {
                val t = it.animatedValue as Float
                px = fromX + (tx - fromX) * t
                py = fromY + (ty - fromY) * t - (sin(PI * t).toFloat() * arc)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    fun clear() {
        animator?.cancel()
        animator = null
        bitmap?.recycle()
        bitmap = null
        invalidate()
    }

    // Keeps the paper "attached" to the spine (left edge), like a real page.
    private fun setPoint(x: Float, y: Float) {
        val w = pageWidth
        val h = pageHeight
        var nx = x
        var ny = y
        val s1y = cornerY
        val s2y = h - cornerY
        var dx = nx
        var dy = ny - s1y
        var d = hypot(dx, dy)
        if (d > w && d > 0f) {
            nx = dx * w / d
            ny = s1y + dy * w / d
        }
        val r2 = hypot(w, h)
        dx = nx
        dy = ny - s2y
        d = hypot(dx, dy)
        if (d > r2 && d > 0f) {
            nx = dx * r2 / d
            ny = s2y + dy * r2 / d
        }
        px = nx
        py = ny
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        if (bmp.isRecycled) return
        val w = pageWidth
        val h = pageHeight
        val dx = cornerX - px
        val dy = cornerY - py
        val len = hypot(dx, dy)
        if (len < 0.5f) {
            // Page lies flat: forward shows the snapshot, backward shows the WebView (draw nothing).
            if (forward) canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
            return
        }
        val nx = dx / len
        val ny = dy / len
        val mx = (cornerX + px) / 2f
        val my = (cornerY + py) / 2f
        val rect = listOf(PointF(0f, 0f), PointF(w, 0f), PointF(w, h), PointF(0f, h))
        val keep = clip(rect, mx, my, nx, ny, 1f)
        val flap = clip(rect, mx, my, nx, ny, -1f)
        val refl = flap.map { p ->
            val d = (p.x - mx) * nx + (p.y - my) * ny
            PointF(p.x - 2f * d * nx, p.y - 2f * d * ny)
        }

        if (forward) {
            if (keep.size > 2) {
                canvas.save()
                canvas.clipPath(pathOf(keep))
                canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
                canvas.restore()
            }
        } else {
            canvas.save()
            if (keep.size > 2) canvas.clipOutPath(pathOf(keep))
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
            canvas.restore()
        }

        // Soft shadow the curl casts onto the page underneath, next to the fold.
        if (keep.size > 2) {
            val sw = minOf(30f * resources.displayMetrics.density, len / 3f)
            canvas.save()
            canvas.clipOutPath(pathOf(keep))
            fillPaint.color = Color.BLACK
            fillPaint.shader = LinearGradient(
                mx, my, mx + nx * sw, my + ny * sw,
                0x50000000, 0x00000000, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, h, fillPaint)
            fillPaint.shader = null
            canvas.restore()
        }

        // Back of the page (the folded-over flap).
        if (refl.size > 2) {
            val path = pathOf(refl)
            canvas.save()
            canvas.clipPath(path)
            fillPaint.shader = null
            fillPaint.color = backColor
            canvas.drawRect(0f, 0f, w, h, fillPaint)
            // Fenrir: like real paper, the print shows faintly through the back (mirrored by the fold)
            canvas.save()
            reflectMatrix.setValues(
                floatArrayOf(
                    1f - 2f * nx * nx, -2f * nx * ny, 2f * (mx * nx + my * ny) * nx,
                    -2f * nx * ny, 1f - 2f * ny * ny, 2f * (mx * nx + my * ny) * ny,
                    0f, 0f, 1f,
                ),
            )
            canvas.concat(reflectMatrix)
            throughPaint.alpha = 30
            canvas.drawBitmap(bmp, 0f, 0f, throughPaint)
            canvas.restore()
            fillPaint.color = Color.BLACK
            fillPaint.shader = LinearGradient(
                mx, my, px, py,
                intArrayOf(0x26000000, 0x0AFFFFFF, 0x08000000),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, h, fillPaint)
            fillPaint.shader = null
            canvas.restore()
            canvas.drawPath(path, edgePaint)
        }
    }

    // Sutherland-Hodgman clip of a convex polygon against one side of the fold line.
    private fun clip(poly: List<PointF>, mx: Float, my: Float, nx: Float, ny: Float, s: Float): List<PointF> {
        fun d(p: PointF) = s * ((p.x - mx) * nx + (p.y - my) * ny)
        val out = ArrayList<PointF>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val da = d(a)
            val db = d(b)
            if (da <= 0f) out.add(a)
            if ((da <= 0f) != (db <= 0f)) {
                val t = da / (da - db)
                out.add(PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        return out
    }

    private fun pathOf(pts: List<PointF>) = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
}
