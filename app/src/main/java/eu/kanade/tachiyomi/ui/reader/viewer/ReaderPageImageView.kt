package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private val alwaysDecodeLongStripWithSSIV by lazy {
        Injekt.get<BasePreferences>().alwaysDecodeLongStripWithSSIV.get()
    }

    private var pageView: View? = null

    private var config: Config? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
        pendingWhenReady?.let { action ->
            pendingWhenReady = null
            post { action() }
        }
    }

    @CallSuper
    open fun onImageLoadError(error: Throwable?) {
        onImageLoadError?.invoke(error)
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        if (
            config != null &&
            config!!.landscapeZoom &&
            config!!.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                val point = when (config!!.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                    ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = height.toFloat() / sHeight.toFloat()
                animateScaleAndCenter(targetScale, point)!!
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config) {
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    fun recycle() = pageView?.let {
        when (it) {
            is SubsamplingScaleImageView -> it.recycle()
            is AppCompatImageView -> it.dispose()
        }
        it.isVisible = false
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    // ---- Panel by panel support ----------------------------------------------------------------
    private var pendingWhenReady: (() -> Unit)? = null

    /** Runs [action] now if the (non-animated) image is ready, otherwise right after it loads. */
    fun whenImageReady(action: () -> Unit) {
        val view = pageView as? SubsamplingScaleImageView
        if (view != null && view.isReady) action() else pendingWhenReady = action
    }

    fun isRegionZoomSupported(): Boolean = pageView is SubsamplingScaleImageView

    /**
     * Zooms so [region] (fractions 0..1 of the image) fills the screen.
     * [content] is the area kept by "crop borders" (same fractions), or null when not cropping.
     */
    /** 0 = don't darken outside the panel, up to 255 = black. Set before [zoomToRegion]. */
    var panelMaskAlpha = 0
    private var panelMask: PanelMaskView? = null

    private fun showPanelMask(sourceRegion: RectF, sourceShape: FloatArray? = null) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (panelMaskAlpha <= 0) {
            hidePanelMask()
            return
        }
        val mask = panelMask ?: PanelMaskView(context).also {
            panelMask = it
            it.alpha = 0f
            addView(it, MATCH_PARENT, MATCH_PARENT)
        }
        mask.target = view
        mask.shape = sourceShape
        mask.region = sourceRegion
        mask.setDarkness(panelMaskAlpha)
        mask.bringToFront()
        mask.invalidate()
        mask.animate().alpha(1f).setDuration(250).start()
    }

    /** Maps a panel outline coordinate (fraction of the image) to image source coordinates. */
    private fun shapeToSource(v: Float, isX: Boolean, content: RectF?, sw: Float, sh: Float): Float {
        val f = if (content != null && content.width() > 0f && content.height() > 0f) {
            if (isX) (v - content.left) / content.width() else (v - content.top) / content.height()
        } else {
            v
        }
        return f.coerceIn(0f, 1f) * (if (isX) sw else sh)
    }
    // ---- Bubble pop-ups ------------------------------------------------------------------------
    private var bubblePopup: BubblePopupView? = null
    private var bubbleToken = 0

    /**
     * Pops [bubble] (fractions 0..1 of the page image) up enlarged over a dimmed page, cut from the
     * full-quality page [bytes]. [content] is the "crop borders" area (or null), for positioning.
     */
    fun showBubblePopup(bytes: ByteArray?, bubble: RectF, content: RectF?) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (bytes == null || !view.isReady) return
        val token = ++bubbleToken
        val sw = view.sWidth.toFloat()
        val sh = view.sHeight.toFloat()
        val src = RectF(
            shapeToSource(bubble.left, true, content, sw, sh),
            shapeToSource(bubble.top, false, content, sw, sh),
            shapeToSource(bubble.right, true, content, sw, sh),
            shapeToSource(bubble.bottom, false, content, sw, sh),
        )
        val maxW = maxOf(width, 1)
        Thread {
            val bmp: Bitmap? = try {
                @Suppress("DEPRECATION")
                val dec = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
                if (dec == null) {
                    null
                } else {
                    try {
                        val fw = dec.width
                        val fh = dec.height
                        val r = Rect(
                            (bubble.left * fw).toInt().coerceIn(0, fw),
                            (bubble.top * fh).toInt().coerceIn(0, fh),
                            (bubble.right * fw).toInt().coerceIn(0, fw),
                            (bubble.bottom * fh).toInt().coerceIn(0, fh),
                        )
                        if (r.width() <= 0 || r.height() <= 0) {
                            null
                        } else {
                            var sample = 1
                            while (r.width() / (sample * 2) >= maxW) sample *= 2
                            dec.decodeRegion(r, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    } finally {
                        dec.recycle()
                    }
                }
            } catch (e: Throwable) {
                null
            }
            post {
                if (token != bubbleToken || bmp == null) {
                    bmp?.recycle()
                    return@post
                }
                val popup = bubblePopup ?: BubblePopupView(context).also {
                    bubblePopup = it
                    it.alpha = 0f
                    addView(it, MATCH_PARENT, MATCH_PARENT)
                }
                val old = popup.bitmap
                popup.target = view
                popup.sourceRect = src
                popup.bitmap = bmp
                if (old != null && old !== bmp) old.recycle()
                popup.bringToFront()
                popup.animatePop()
            }
        }.start()
    }

    fun hideBubblePopup() {
        bubbleToken++
        val popup = bubblePopup ?: return
        popup.animate().alpha(0f).setDuration(150).withEndAction {
            popup.bitmap?.recycle()
            popup.bitmap = null
            popup.invalidate()
        }.start()
    }
    fun hidePanelMask() {
        val mask = panelMask ?: return
        mask.animate().alpha(0f).setDuration(200).withEndAction { mask.region = null }.start()
    }
    fun zoomToRegion(region: RectF, content: RectF?, durationMs: Long = 350L, shape: FloatArray? = null, maskRegion: RectF? = null) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (!view.isReady || view.width == 0 || view.height == 0) return
        val area = if (content != null && content.width() > 0f && content.height() > 0f) {
            RectF(
                (region.left - content.left) / content.width(),
                (region.top - content.top) / content.height(),
                (region.right - content.left) / content.width(),
                (region.bottom - content.top) / content.height(),
            )
        } else {
            region
        }
        val sw = view.sWidth.toFloat()
        val sh = view.sHeight.toFloat()
        val l = area.left.coerceIn(0f, 1f) * sw
        val t = area.top.coerceIn(0f, 1f) * sh
        val r = area.right.coerceIn(0f, 1f) * sw
        val b = area.bottom.coerceIn(0f, 1f) * sh
        showPanelMask(maskRegion?.let { mr -> RectF(shapeToSource(mr.left, true, content, sw, sh), shapeToSource(mr.top, false, content, sw, sh), shapeToSource(mr.right, true, content, sw, sh), shapeToSource(mr.bottom, false, content, sw, sh)) } ?: RectF(l, t, r, b), shape?.let { q -> FloatArray(8) { i -> shapeToSource(q[i], i % 2 == 0, content, sw, sh) } })
        val rw = (r - l).coerceAtLeast(1f)
        val rh = (b - t).coerceAtLeast(1f)
        val minS = view.minScale
        val maxS = maxOf(minS, view.maxScale)
        val target = (minOf(view.width / rw, view.height / rh) * 0.97f).coerceIn(minS, maxS)
        view.animateScaleAndCenter(target, PointF((l + r) / 2f, (t + b) / 2f))
            ?.withDuration(durationMs)
            ?.withEasing(EASE_IN_OUT_QUAD)
            ?.withInterruptible(false)
            ?.start()
    }

    fun zoomToFullPage(durationMs: Long = 350L) {
        hidePanelMask()
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (!view.isReady) return
        view.animateScaleAndCenter(view.minScale, PointF(view.sWidth / 2f, view.sHeight / 2f))
            ?.withDuration(durationMs)
            ?.withEasing(EASE_IN_OUT_QUAD)
            ?.withInterruptible(false)
            ?.start()
    }
    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // Not used
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(scale * 2)

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError(e)
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                setImage(ImageSource.bitmap(data.bitmap))
                isVisible = true
            }
            is BufferedSource -> {
                if (!isWebtoon || alwaysDecodeLongStripWithSSIV) {
                    setHardwareConfig(ImageUtil.canUseHardwareBitmap(data))
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                    return@apply
                }

                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            setImage(ImageSource.bitmap(image.bitmap))
                            isVisible = true
                        },
                    )
                    .listener(
                        onError = { _, result ->
                            onImageLoadError(result.throwable)
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .customDecoder(true)
                    .crossfade(false)
                    .build()
                    .let(context.imageLoader::enqueue)
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .crossfade(false)
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun Int.getSystemScaledDuration(): Int {
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }
}

private const val MAX_ZOOM_SCALE = 5F

/** Dims the page and draws one bubble, enlarged, near where it sits on the page. */
private class BubblePopupView(context: Context) : View(context) {
    var target: SubsamplingScaleImageView? = null
    var sourceRect: RectF? = null
    var bitmap: Bitmap? = null
    private var pop = 1f
    private val density = context.resources.displayMetrics.density
    private val dimPaint = Paint().apply { color = Color.BLACK; alpha = 170 }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    fun animatePop() {
        animate().alpha(1f).setDuration(160).start()
        ValueAnimator.ofFloat(0.85f, 1f).apply {
            duration = 240
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                pop = it.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        if (bmp.isRecycled) return
        val view = target ?: return
        val src = sourceRect ?: return
        if (!view.isReady) return
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        val p1 = view.sourceToViewCoord(src.left, src.top) ?: return
        val p2 = view.sourceToViewCoord(src.right, src.bottom) ?: return
        val cx = (p1.x + p2.x) / 2f
        val cy = (p1.y + p2.y) / 2f
        val margin = 12f * density
        val fit = minOf((w * 0.92f) / bmp.width, (h * 0.45f) / bmp.height)
        val onScreen = (p2.x - p1.x) / bmp.width
        val cap = minOf((w - 2 * margin) / bmp.width, (h - 2 * margin) / bmp.height)
        val s = minOf(maxOf(fit, onScreen * 1.1f), cap) * pop
        val dw = bmp.width * s
        val dh = bmp.height * s
        val left = (cx - dw / 2f).coerceIn(margin, maxOf(margin, w - dw - margin))
        val top = (cy - dh / 2f).coerceIn(margin, maxOf(margin, h - dh - margin))
        val dst = RectF(left, top, left + dw, top + dh)
        val radius = 14f * density
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(bmp, null, dst, bmpPaint)
        canvas.restore()
        canvas.drawRoundRect(dst, radius, radius, borderPaint)
        postInvalidateOnAnimation()
    }
}

/** Darkens everything outside [region] (image coordinates); follows the image while it zooms or pans. */
private class PanelMaskView(context: Context) : View(context) {
    var target: SubsamplingScaleImageView? = null
    var shape: FloatArray? = null
    var region: RectF? = null
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint().apply { color = Color.BLACK }

    fun setDarkness(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
    }

    override fun onDraw(canvas: Canvas) {
        val view = target ?: return
        val r = region ?: return
        if (!view.isReady) return
        val sh = shape
        if (sh != null) {
            val path = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            }
            for (k in 0 until 4) {
                val pt = view.sourceToViewCoord(sh[2 * k], sh[2 * k + 1]) ?: return
                if (k == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.close()
            canvas.drawPath(path, paint)
            postInvalidateOnAnimation()
            return
        }
        val p1 = view.sourceToViewCoord(r.left, r.top) ?: return
        val p2 = view.sourceToViewCoord(r.right, r.bottom) ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val l = p1.x.coerceIn(0f, w)
        val t = p1.y.coerceIn(0f, h)
        val rr = p2.x.coerceIn(l, w)
        val b = p2.y.coerceIn(t, h)
        canvas.drawRect(0f, 0f, w, t, paint)
        canvas.drawRect(0f, b, w, h, paint)
        canvas.drawRect(0f, t, l, b, paint)
        canvas.drawRect(rr, t, w, b, paint)
        postInvalidateOnAnimation()
    }
}
