package eu.kanade.presentation.library.components

import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.MangaCover as CoverData

/** Width / height of the tome frame picture (spine, metal frame, stand and ribbon). */
internal const val FenrirTomeRatio = 640f / 700f

/** Where the little stand touches the shelf, as a share of the frame's height. */
internal const val FenrirTomeStand = 0.875f

/** Ratio to give [fenrirPlank] so the plank sits right under the tome's stand. */
internal const val FenrirTomePlankRatio = FenrirTomeRatio / FenrirTomeStand

/** The cover window of the frame (top-left, top-right, bottom-right, bottom-left), as shares of its size. */
private val CoverQuad = floatArrayOf(
    0.2843f, 0.0648f,
    0.7527f, 0.1138f,
    0.8387f, 0.6674f,
    0.3458f, 0.6431f,
)

/** The cover window's bounding box inside a tome of [frame] (used to start the book animations). */
internal fun fenrirTomeCoverRect(frame: Rect): Rect {
    val xs = listOf(CoverQuad[0], CoverQuad[2], CoverQuad[4], CoverQuad[6])
    val ys = listOf(CoverQuad[1], CoverQuad[3], CoverQuad[5], CoverQuad[7])
    return Rect(
        frame.left + frame.width * xs.min(),
        frame.top + frame.height * ys.min(),
        frame.left + frame.width * xs.max(),
        frame.top + frame.height * ys.max(),
    )
}

/**
 * Fenrir: one book as an ornate tome. The real cover is bent to follow the book's angle, the
 * frame is drawn over it, and the runes, glow, gems and ribbon take the cover's own colour.
 * [overlay] is drawn on the cover (badges, progress), bending with it.
 */
@Composable
internal fun FenrirTomeBook(
    cover: CoverData,
    modifier: Modifier = Modifier,
    onFrameBounds: ((Rect) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val accent = fenrirCoverAccent(cover)
    BoxWithConstraints(
        modifier
            .aspectRatio(FenrirTomeRatio)
            .then(if (onFrameBounds != null) Modifier.onGloballyPositioned { onFrameBounds(it.boundsInRoot()) } else Modifier),
    ) {
        val cw = maxWidth * 0.5f
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(cw, cw * 1.5f)
                .drawWithContent {
                    val fw = this@BoxWithConstraints.constraints.maxWidth.toFloat()
                    val fh = this@BoxWithConstraints.constraints.maxHeight.toFloat()
                    val m = android.graphics.Matrix()
                    val dst = FloatArray(8) { i -> CoverQuad[i] * (if (i % 2 == 0) fw else fh) }
                    m.setPolyToPoly(floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height), 0, dst, 0, 4)
                    drawIntoCanvas { c ->
                        c.nativeCanvas.save()
                        c.nativeCanvas.concat(m)
                    }
                    drawContent()
                    drawIntoCanvas { it.nativeCanvas.restore() }
                },
        ) {
            MangaCover.Book(data = cover, modifier = Modifier.fillMaxSize(), shape = RectangleShape)
            overlay()
        }
        Image(
            painter = painterResource(R.drawable.fenrir_tome_base),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.fenrir_tome_tint),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(accent),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A soft dark shadow where a tome's stand meets the shelf. */
internal fun Modifier.fenrirTomeShadow(): Modifier = drawBehind {
    val y = size.height * FenrirTomeStand
    drawOval(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
            center = Offset(size.width * 0.55f, y),
            radius = size.width * 0.45f,
        ),
        topLeft = Offset(size.width * 0.12f, y - size.height * 0.03f),
        size = Size(size.width * 0.86f, size.height * 0.06f),
    )
}

/** Fenrir: the colour picked from a cover (its most vivid colours), cached per cover. */
internal object FenrirCoverAccent {
    private val cache = android.util.LruCache<String, Int>(300)
    val Default = Color(0xFFE0404C)

    fun key(cover: CoverData) = "${cover.mangaId}:${cover.lastModified}:${cover.url}"

    fun cached(cover: CoverData): Color? = cache.get(key(cover))?.let { Color(it) }

    suspend fun load(context: android.content.Context, cover: CoverData): Color {
        cached(cover)?.let { return it }
        val color = runCatching {
            val request = coil3.request.ImageRequest.Builder(context)
                .data(cover)
                .size(40, 60)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result as? coil3.request.SuccessResult)?.image?.toBitmap() ?: return@runCatching null
            withContext(Dispatchers.Default) { vivid(pick(bitmap)) }
        }.getOrNull() ?: return Default
        cache.put(key(cover), color)
        return Color(color)
    }

    /** Average of the most colourful fifth of the pixels. */
    private fun pick(bitmap: Bitmap): Int {
        val small = if (bitmap.width > 48) Bitmap.createScaledBitmap(bitmap, 40, (40f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true) else bitmap
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val scored = px.map { p ->
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (maxOf(r, g, b) - minOf(r, g, b)) to p
        }.sortedByDescending { it.first }
        val top = scored.take((scored.size / 5).coerceAtLeast(1))
        var r = 0L; var g = 0L; var b = 0L
        top.forEach { (_, p) -> r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF }
        val n = top.size
        return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Bright and saturated enough to glow on the dark leather. */
    private fun vivid(c: Int): Int {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(c, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.75f)
        hsl[2] = hsl[2].coerceIn(0.5f, 0.65f)
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }
}

@Composable
internal fun fenrirCoverAccent(cover: CoverData): Color {
    val context = LocalContext.current
    val accent by produceState(FenrirCoverAccent.cached(cover) ?: FenrirCoverAccent.Default, FenrirCoverAccent.key(cover)) {
        value = FenrirCoverAccent.load(context, cover)
    }
    return accent
}
