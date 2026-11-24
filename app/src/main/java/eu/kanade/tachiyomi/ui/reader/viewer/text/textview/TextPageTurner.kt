package eu.kanade.tachiyomi.ui.reader.viewer.text.textview

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import eu.kanade.tachiyomi.ui.reader.viewer.text.webview.PageCurlView
import kotlin.math.abs

/**
 * Page turning for the TextView novel reader ("Sliding" and "Page flip" reading modes).
 *
 * A page is one screen of text. A turn snapshots the current screen, moves the scroll view to the
 * next/previous page instantly underneath, then animates the snapshot away: a page curl
 * (Page flip) or a sideways slide (Sliding). Cancelling a drag animates back and restores the
 * original position. Progress saving and chapter loading stay with the scroll view's own logic.
 */
class TextPageTurner(
    private val activity: Activity,
    private val container: FrameLayout,
    private val target: View,
    private val isCurl: () -> Boolean,
    private val swipeEnabled: () -> Boolean,
    private val commitFraction: () -> Float,
    private val backColor: () -> Int,
    private val getPos: () -> Int,
    private val setPos: (Int) -> Unit,
    private val nextPos: (forward: Boolean) -> Int?,
    private val onEdge: (forward: Boolean) -> Unit,
) {
    private var curlView: PageCurlView? = null
    private var slideView: ImageView? = null
    private var slideBitmap: Bitmap? = null

    private var state = 0 // 0 idle, 1 capturing, 2 dragging, 3 animating
    private var forward = true
    private var basePos = 0
    private var useCurl = true
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var determined = false
    private var edgeDrag = false
    private var edgeForward = true
    private var pendingCommit: Boolean? = null

    /** True while this turner owns the current gesture (or an animation is running). */
    val isTracking: Boolean get() = state != 0 || edgeDrag

    /** Tap zones / keys: turn one page with the animation. */
    fun turn(forward: Boolean) {
        if (state != 0) return
        val to = nextPos(forward)
        if (to == null) {
            onEdge(forward)
            return
        }
        start(forward, to, interactive = false, cornerTop = false)
    }

    /** Returns true when the gesture is handled here and must not reach the scroll view. */
    fun onTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (state != 0) return true
                startX = ev.x
                startY = ev.y
                lastX = ev.x
                lastY = ev.y
                determined = false
                edgeDrag = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = ev.x
                lastY = ev.y
                when (state) {
                    1, 3 -> return true
                    2 -> {
                        dragTo(ev.x, ev.y)
                        return true
                    }
                }
                if (edgeDrag) return true
                if (!determined && swipeEnabled()) {
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    val slop = ViewConfiguration.get(activity).scaledTouchSlop
                    if (abs(dx) > slop || abs(dy) > slop) {
                        determined = true
                        if (abs(dx) <= abs(dy)) return false
                        val fwd = dx < 0
                        val to = nextPos(fwd)
                        if (to == null) {
                            // End/start of the chapter: a long enough swipe changes chapter.
                            edgeDrag = true
                            edgeForward = fwd
                            return true
                        }
                        start(fwd, to, interactive = true, cornerTop = startY < target.height / 2f)
                        return true
                    }
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val cancelled = ev.actionMasked == MotionEvent.ACTION_CANCEL
                val dx = ev.x - startX
                val dist = target.width * commitFraction()
                if (edgeDrag) {
                    edgeDrag = false
                    if (!cancelled && (if (edgeForward) dx < -dist else dx > dist)) onEdge(edgeForward)
                    return true
                }
                val commit = !cancelled && (if (forward) dx < -dist else dx > dist)
                when (state) {
                    1 -> {
                        pendingCommit = commit
                        return true
                    }
                    2 -> {
                        finish(commit)
                        return true
                    }
                    3 -> return true
                }
                return false
            }
        }
        return false
    }

    private fun overlayParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(target.width, target.height).apply {
            leftMargin = target.left
            topMargin = target.top
        }

    private fun start(fwd: Boolean, to: Int, interactive: Boolean, cornerTop: Boolean) {
        state = 1
        forward = fwd
        basePos = getPos()
        pendingCommit = null
        useCurl = isCurl()
        capture { bmp ->
            if (state != 1) {
                bmp?.recycle()
                return@capture
            }
            if (bmp == null) {
                // No snapshot possible: change the page without animation.
                state = 0
                setPos(to)
                return@capture
            }
            if (useCurl) {
                val v = curlView ?: PageCurlView(activity).also {
                    container.addView(it, overlayParams())
                    curlView = it
                }
                v.layoutParams = overlayParams()
                v.backColor = backColor()
                val w = bmp.width.toFloat()
                val h = bmp.height.toFloat()
                val sx = if (interactive) (if (fwd) lastX else 2f * lastX - w) else if (fwd) w - 1f else -w
                val sy = if (interactive) lastY else if (cornerTop) 1f else h - 1f
                v.begin(bmp, fwd, cornerTop, sx, sy)
                v.bringToFront()
                v.visibility = View.VISIBLE
            } else {
                val iv = slideView ?: ImageView(activity).also {
                    it.scaleType = ImageView.ScaleType.FIT_XY
                    it.elevation = 6f * activity.resources.displayMetrics.density
                    container.addView(it, overlayParams())
                    slideView = it
                }
                iv.layoutParams = overlayParams()
                slideBitmap?.recycle()
                slideBitmap = bmp
                iv.setImageBitmap(bmp)
                iv.translationX = 0f
                iv.bringToFront()
                iv.visibility = View.VISIBLE
            }
            state = 2
            setPos(to)
            val pending = pendingCommit
            when {
                !interactive -> finish(true, tap = true)
                pending != null -> finish(pending)
                else -> dragTo(lastX, lastY)
            }
        }
    }

    private fun dragTo(x: Float, y: Float) {
        if (useCurl) {
            if (forward) curlView?.dragTo(x, y) else curlView?.dragBackTo(x, y)
        } else {
            slideView?.let {
                val dx = x - startX
                it.translationX = if (forward) dx.coerceAtMost(0f) else dx.coerceAtLeast(0f)
            }
        }
    }

    private fun finish(commit: Boolean, tap: Boolean = false) {
        state = 3
        if (useCurl) {
            val v = curlView ?: return end()
            val w = v.pageWidth
            val turnedAway = if (forward) commit else !commit
            val lift = v.pageHeight * 0.12f
            val arc = if (!commit) 0f else if (v.cornerY == 0f) -lift else lift
            v.animateTo(if (turnedAway) -w else w, v.cornerY, arc, if (tap) 520L else if (commit) 420L else 260L, smooth = tap) {
                afterAnimation(commit)
            }
        } else {
            val iv = slideView ?: return end()
            val w = iv.width.toFloat()
            val to = if (!commit) 0f else if (forward) -w else w
            iv.animate()
                .translationX(to)
                .setDuration(if (commit) 280L else 200L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { afterAnimation(commit) }
                .start()
        }
    }

    private fun afterAnimation(commit: Boolean) {
        if (commit) {
            end()
        } else {
            setPos(basePos)
            target.postDelayed({ end() }, 60)
        }
    }

    private fun end() {
        curlView?.let {
            it.visibility = View.INVISIBLE
            it.clear()
        }
        slideView?.let {
            it.animate().cancel()
            it.visibility = View.INVISIBLE
            it.setImageDrawable(null)
            it.translationX = 0f
        }
        slideBitmap?.recycle()
        slideBitmap = null
        state = 0
    }

    private fun capture(onResult: (Bitmap?) -> Unit) {
        val w = target.width
        val h = target.height
        if (w <= 0 || h <= 0) {
            onResult(null)
            return
        }
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            onResult(null)
            return
        }
        val loc = IntArray(2)
        target.getLocationInWindow(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
        try {
            PixelCopy.request(
                activity.window,
                rect,
                bmp,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        onResult(bmp)
                    } else {
                        bmp.recycle()
                        onResult(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Throwable) {
            bmp.recycle()
            onResult(null)
        }
    }
}
