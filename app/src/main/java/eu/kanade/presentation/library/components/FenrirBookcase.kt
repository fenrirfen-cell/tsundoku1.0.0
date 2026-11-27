package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

/*
 * Fenrir: the Read library as a real wooden bookcase. A carved top rail (the category bar) with a
 * brass nameplate and brass knobs, a walnut back wall with side posts, and planks the books stand
 * on. The wood is real texture (res/drawable-nodpi/fenrir_wood_*), so grain lines up across planks.
 */

internal val FenrirBrassText = Color(0xFF42300F)
internal val FenrirBrassLight = Color(0xFFE8C67A)
private val BrassBrush = Brush.verticalGradient(
    0f to Color(0xFFF0D08A),
    0.22f to Color(0xFFE2BE72),
    0.55f to Color(0xFFC79A4E),
    1f to Color(0xFF9C7433),
)
private val BrassEdge = Color(0xFF6E4F1C)

@Composable
private fun woodImage(id: Int): ImageBitmap = ImageBitmap.imageResource(id)

/** Brass plate look: gradient, dark rim, thin light inner edge. */
internal fun Modifier.fenrirBrass(shape: Shape): Modifier = this
    .clip(shape)
    .background(BrassBrush)
    .border(1.dp, BrassEdge, shape)
    .drawWithContent {
        drawContent()
        // soft shine across the top third
        drawRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.35f to Color.Transparent),
            size = Size(size.width, size.height * 0.45f),
        )
    }

/** The carved top rail of the bookcase: wood texture with the molding baked in. */
internal fun Modifier.fenrirRail(): Modifier = composed {
    val rail = woodImage(R.drawable.fenrir_wood_rail)
    drawBehind {
        drawImage(
            rail,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(rail.width, rail.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Medium,
        )
        // shadow it throws onto what's under it
        drawRect(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent, startY = size.height, endY = size.height + 16.dp.toPx()),
            topLeft = Offset(0f, size.height),
            size = Size(size.width, 16.dp.toPx()),
        )
    }
}

/**
 * Fenrir: the carved crown on top of the Read side (behind the status bar and the top buttons).
 * Same wood as the rail, so it runs straight into the glass case and the bookcase under it.
 */
@Composable
fun fenrirCrownModifier(): Modifier {
    // the same dark walnut as the back of the bookcase, behind the carved crown picture
    val back = woodImage(R.drawable.fenrir_wood_back)
    val brush = remember(back) { ShaderBrush(ImageShader(back, TileMode.Repeated, TileMode.Repeated)) }
    return Modifier.drawBehind {
        drawRect(brush)
        drawRect(Color.Black.copy(alpha = 0.25f))
    }
}

/** Fenrir: the dark wood behind every Read page (darker than the shelves, so lists stay easy to read). */
@Composable
fun fenrirPageWoodModifier(): Modifier {
    val back = woodImage(R.drawable.fenrir_wood_back)
    val brush = remember(back) { ShaderBrush(ImageShader(back, TileMode.Repeated, TileMode.Repeated)) }
    return Modifier.drawBehind {
        drawRect(brush)
        drawRect(Color.Black.copy(alpha = 0.55f))
    }
}

/** Two little screws, one at each end of a brass plate. */
internal fun DrawScope.brassScrews(inset: Dp) {
    val r = 2.6.dp.toPx()
    listOf(inset.toPx(), size.width - inset.toPx()).forEach { x ->
        val c = Offset(x, size.height / 2f)
        drawCircle(Color(0xFF9A7434), r, c)
        drawCircle(BrassEdge, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(0.8.dp.toPx()))
        drawLine(Color(0xFF5A4012), Offset(c.x - r * 0.6f, c.y + r * 0.6f), Offset(c.x + r * 0.6f, c.y - r * 0.6f), 0.9.dp.toPx())
    }
}

/** Round brass knob used for the ‹ › arrows on the rail. */
@Composable
internal fun FenrirBrassKnob(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .drawBehind {
                // shadow on the wood under the knob
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f + 3.dp.toPx()),
                        radius = size.minDimension * 0.62f,
                    ),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width / 2f, size.height / 2f + 3.dp.toPx()),
                )
            }
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFFF6DC9C),
                        0.45f to Color(0xFFD2A658),
                        1f to Color(0xFF8A6428),
                        center = Offset(size.width * 0.36f, size.height * 0.3f),
                        radius = r * 1.5f,
                    ),
                    r - 1.dp.toPx(),
                )
                drawCircle(BrassEdge, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        // engraved: a light line just under a dark one
        Icon(icon, null, tint = Color(0x88FFF0C0), modifier = Modifier.size(22.dp).androidxOffsetY(1.dp))
        Icon(icon, label, tint = FenrirBrassText, modifier = Modifier.size(22.dp))
    }
}

private fun Modifier.androidxOffsetY(dy: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.place(0, dy.roundToPx()) }
    },
)

/**
 * The back of the bookcase behind the books: walnut back wall, a shadow under the top rail, and
 * the two side posts drawn over the edges.
 */
internal fun Modifier.fenrirBookcaseBack(): Modifier = composed {
    val back = woodImage(R.drawable.fenrir_wood_back)
    val post = woodImage(R.drawable.fenrir_wood_post)
    val backBrush = remember(back) { ShaderBrush(ImageShader(back, TileMode.Repeated, TileMode.Repeated)) }
    val postBrush = remember(post) { ShaderBrush(ImageShader(post, TileMode.Clamp, TileMode.Repeated)) }
    drawWithContent {
        drawRect(backBrush)
        // darker toward the sides, like light falling on the middle of the case
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                0.22f to Color.Transparent,
                0.78f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.45f),
            ),
        )
        drawContent()
        val pw = 10.dp.toPx()
        // posts: scale the post texture to the post width
        val sx = pw / post.width
        listOf(0f, size.width - pw).forEach { x ->
            drawContext.canvas.save()
            drawContext.transform.translate(x, 0f)
            drawContext.transform.scale(sx, 1f, Offset.Zero)
            drawRect(postBrush, size = Size(post.width.toFloat(), size.height))
            drawContext.canvas.restore()
        }
    }
}

/**
 * A plank under one cell of books: top surface + front edge, with shadows. The texture is sampled
 * by the cell's spot on screen, so the grain runs on across cells like one long board.
 */
internal fun Modifier.fenrirPlank(coverPadding: Dp, ratio: Float, pageInset: Dp, bookShadow: Boolean = true): Modifier = composed {
    val plank = woodImage(R.drawable.fenrir_wood_plank)
    val screenW = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    // Where this spot sits across the screen. Kept as state so the plank is drawn again once the
    // spot moves (a category page laid out off to the side used to keep a plank sampled from
    // outside the wood picture, so its shelves showed up empty). Taken modulo the screen width,
    // so a page that hasn't slid in yet still gets the right piece of wood.
    val xState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val mugiShelf = eu.kanade.tachiyomi.EmbeddedHost.isMugi
    this
        .onGloballyPositioned {
            val raw = it.positionInRoot().x
            xState.floatValue = ((raw % screenW) + screenW) % screenW
        }
        .drawBehind {
            val pad = coverPadding.toPx()
            val coverW = size.width - pad * 2f
            val coverH = coverW / ratio
            val bottom = pad + coverH
            val topDepth = 6.dp.toPx()
            val front = 8.dp.toPx()
            val top = bottom - topDepth * 0.55f
            val x = xState.floatValue
            val edge = 26.dp.toPx()
            val gap = 2.dp.toPx()
            val left = if (x < edge) -x else -gap
            val right = if (x + size.width > screenW - edge) screenW - x else size.width + gap
            val w = right - left
            val h = topDepth + front
            val k = plank.width / screenW
            // MugiVerse: no spotlights over the shelves (the cabin's lanterns light them)
            if (!mugiShelf) fenrirShelfLight()
            val sx = ((x + left) * k).toInt().coerceIn(0, plank.width - 1)
            val sw = (w * k).toInt().coerceIn(1, plank.width - sx)
            drawImage(
                plank,
                srcOffset = IntOffset(sx, 0),
                srcSize = IntSize(sw, plank.height),
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                filterQuality = FilterQuality.Medium,
            )
            // top surface: darker at the back
            drawRect(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.38f), 1f to Color.Transparent, startY = top, endY = top + topDepth),
                topLeft = Offset(left, top),
                size = Size(w, topDepth),
            )
            // lit front edge, then the front face going darker
            drawRect(Color(0x55FFE2B8), topLeft = Offset(left, top + topDepth), size = Size(w, 1.dp.toPx()))
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f), startY = top + topDepth, endY = top + h),
                topLeft = Offset(left, top + topDepth + 1.dp.toPx()),
                size = Size(w, front - 1.dp.toPx()),
            )
            // shadow the plank throws on the back wall
            drawRect(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent, startY = top + h, endY = top + h + 14.dp.toPx()),
                topLeft = Offset(left, top + h),
                size = Size(w, 14.dp.toPx()),
            )
            // the book's own shadow on the back wall and on the plank (not on an empty spot)
            if (bookShadow) drawRect(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.4f), 1f to Color.Transparent, startX = pad + coverW, endX = pad + coverW + 9.dp.toPx()),
                topLeft = Offset(pad + coverW, pad + 6.dp.toPx()),
                size = Size(9.dp.toPx(), coverH - 6.dp.toPx() - topDepth * 0.4f),
            )
            if (bookShadow) drawOval(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(pad + coverW / 2f, bottom),
                    radius = coverW * 0.62f,
                ),
                topLeft = Offset(pad - 4.dp.toPx(), bottom - 4.dp.toPx()),
                size = Size(coverW + 8.dp.toPx(), 8.dp.toPx()),
            )
        }
}

/** The page block of a standing hardcover: a cream edge on the right and along the top. */
internal fun Modifier.fenrirPageBlock(inset: Dp): Modifier = drawBehind {
    val s = inset.toPx()
    val t = s * 0.6f
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(2.dp.toPx(), t)
        lineTo(s, 0f)
        lineTo(w, 0f)
        lineTo(w, h - t)
        lineTo(w - s, h)
        lineTo(w - s, t)
        close()
    }
    drawPath(path, Color(0xFFE6DBC2))
    // page lines
    var y = 1.5.dp.toPx()
    while (y < h - t) {
        drawLine(Color(0x33705A3A), Offset(w - s, y + t), Offset(w, y), 0.6.dp.toPx())
        y += 2.5.dp.toPx()
    }
    var x = s + 2.dp.toPx()
    while (x < w) {
        drawLine(Color(0x22705A3A), Offset(x - s, t), Offset(x, 0f), 0.6.dp.toPx())
        x += 3.dp.toPx()
    }
    drawLine(Color(0xFF8C7756), Offset(w, 0f), Offset(w, h - t), 1.dp.toPx())
}

/** Hardcover details over a cover: dark spine edge and the hinge groove. */
internal fun Modifier.fenrirHardcover(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent, endX = 7.dp.toPx()),
        size = Size(7.dp.toPx(), size.height),
    )
    val gx = 8.dp.toPx()
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(gx, 0f), Offset(gx, size.height), 1.2.dp.toPx())
    drawLine(Color.White.copy(alpha = 0.18f), Offset(gx + 1.3.dp.toPx(), 0f), Offset(gx + 1.3.dp.toPx(), size.height), 0.8.dp.toPx())
    drawRect(
        Brush.verticalGradient(0.8f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f)),
    )
}
