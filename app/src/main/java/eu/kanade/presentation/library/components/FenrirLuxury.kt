package eu.kanade.presentation.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.model.MangaCover as CoverData

/*
 * Fenrir: the Luxury Collection look. A black-and-gold glass display cabinet: collector's-edition
 * books on low stands standing on glass shelves, gold nameplates on the shelf fronts, curved side
 * towers on wide screens. All pictures are drawn from resources (decoded once and cached by
 * Compose), covers come from Coil's cache, so scrolling stays light.
 */

/** Book picture (with its built-in stand): width / height, and where the cover window is. */
internal const val LuxBookRatio = 600f / 635f
private val LuxBookQuad = floatArrayOf(0.2874f, 0.0762f, 0.8266f, 0.1147f, 0.9047f, 0.7928f, 0.3572f, 0.7731f)

/** Cell titles for the current shelf, so every nameplate in a row can be the same height. */
internal class LuxCells(val titles: List<String>, val perRow: Int)
internal val LocalLuxCells = compositionLocalOf<LuxCells?> { null }

private val Champagne = Color(0xFFDEC48C)
private val PlateText = Color(0xFFF6ECD6)

// ---------------------------------------------------------------- the book

/** One collector's edition: real cover in the window, leather and gems in the cover's colour. */
@Composable
internal fun LuxBook(
    cover: CoverData?,
    modifier: Modifier = Modifier,
    blank: Boolean = false,
    onWindow: ((Rect) -> Unit)? = null,
    badges: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(
        modifier.aspectRatio(LuxBookRatio).then(
            if (onWindow != null) {
                Modifier.onGloballyPositioned {
                    val b = it.boundsInRoot()
                    onWindow(Rect(b.left + b.width * 0.29f, b.top + b.height * 0.08f, b.left + b.width * 0.9f, b.top + b.height * 0.79f))
                }
            } else {
                Modifier
            },
        ),
    ) {
        if (blank || cover == null) {
            Image(painterResource(R.drawable.lux_book_add), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            return@BoxWithConstraints
        }
        val accent = fenrirCoverAccent(cover)
        val fw = constraints.maxWidth.toFloat()
        val fh = constraints.maxHeight.toFloat()
        val cw = maxWidth * 0.62f
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(cw, cw * 1.5f)
                .drawWithContent {
                    val m = android.graphics.Matrix()
                    val dst = FloatArray(8) { i -> LuxBookQuad[i] * (if (i % 2 == 0) fw else fh) }
                    m.setPolyToPoly(floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height), 0, dst, 0, 4)
                    drawIntoCanvas { c -> c.nativeCanvas.save(); c.nativeCanvas.concat(m) }
                    drawContent()
                    drawIntoCanvas { it.nativeCanvas.restore() }
                },
        ) {
            MangaCover.Book(data = cover, modifier = Modifier.fillMaxSize(), shape = RectangleShape)
        }
        Image(painterResource(R.drawable.lux_book_base), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Image(
            painterResource(R.drawable.lux_book_leather),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(accent, BlendMode.Modulate),
        )
        badges()
    }
}

/** Back of a luxury book (for the turn when you tap it): leather with the gold wolf. */
@Composable
internal fun LuxBookBack(cover: CoverData, modifier: Modifier = Modifier) {
    val accent = fenrirCoverAccent(cover)
    Box(modifier.aspectRatio(LuxBookRatio).graphicsLayer { scaleX = -1f }) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val p = Path().apply {
                    moveTo(LuxBookQuad[0] * size.width, LuxBookQuad[1] * size.height)
                    lineTo(LuxBookQuad[2] * size.width, LuxBookQuad[3] * size.height)
                    lineTo(LuxBookQuad[4] * size.width, LuxBookQuad[5] * size.height)
                    lineTo(LuxBookQuad[6] * size.width, LuxBookQuad[7] * size.height)
                    close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFF2A1A12), Color(0xFF140C08))))
            },
        )
        Image(painterResource(R.drawable.lux_book_base), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Image(
            painterResource(R.drawable.lux_book_leather), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(accent, BlendMode.Modulate),
        )
        Image(
            painterResource(R.drawable.lux_wolf), null,
            Modifier.align(Alignment.Center).offset(x = (-8).dp, y = (-14).dp).fillMaxWidth(0.34f).alpha(0.85f),
        )
    }
}

// ---------------------------------------------------------------- page, frame, pillars

/** Marble room, the case interior and its glass frame, and (wide screens) the smooth pillars. */
@Composable
internal fun Modifier.luxPageBack(geom: LuxGeom, withCase: Boolean = true): Modifier {
    val marble = ImageBitmap.imageResource(R.drawable.lux_marble)
    val wall = ImageBitmap.imageResource(R.drawable.lux_wall)
    val fl = ImageBitmap.imageResource(R.drawable.lux_frame_l)
    val fr = ImageBitmap.imageResource(R.drawable.lux_frame_r)
    val tl = ImageBitmap.imageResource(R.drawable.lux_tower_l)
    val tr = ImageBitmap.imageResource(R.drawable.lux_tower_r)
    // Fenrir: clipped to this box, or the tall marble picture paints over everything below it
    // (the header's background was covering the whole shelf).
    return this.drawBehind { clipRect(0f, 0f, size.width, size.height) {
        // marble, scaled to the width and repeated down
        val k = size.width / marble.width
        val mh = marble.height * k
        var y = 0f
        while (y < size.height) {
            drawImage(marble, dstOffset = IntOffset(0, y.toInt()), dstSize = IntSize(size.width.toInt() + 1, mh.toInt() + 1), filterQuality = FilterQuality.Low)
            y += mh
        }
        val t = geom.tower.toPx()
        val f = geom.frame.toPx()
        if (t > 0f) {
            // pillars: the towers' own lower cabinet colours, one smooth column each
            val m = 3.dp.toPx()
            drawImage(tl, srcOffset = IntOffset(0, 650), srcSize = IntSize(tl.width, 1), dstOffset = IntOffset(m.toInt(), 0), dstSize = IntSize(t.toInt(), size.height.toInt()))
            drawImage(tr, srcOffset = IntOffset(0, 650), srcSize = IntSize(tr.width, 1), dstOffset = IntOffset((size.width - m - t).toInt(), 0), dstSize = IntSize(t.toInt(), size.height.toInt()))
        }
        if (!withCase) return@clipRect
        val x0 = t + (if (t > 0f) 3.dp.toPx() else 0f)
        val x1 = size.width - x0
        // interior back wall
        drawImage(wall, dstOffset = IntOffset((x0 + f).toInt(), 0), dstSize = IntSize((x1 - x0 - 2 * f).toInt(), size.height.toInt()))
        // glass frame down both sides
        val th = fl.height * (f / fl.width)
        var yy = 0f
        while (yy < size.height) {
            drawImage(fl, dstOffset = IntOffset(x0.toInt(), yy.toInt()), dstSize = IntSize(f.toInt(), th.toInt() + 1))
            drawImage(fr, dstOffset = IntOffset((x1 - f).toInt(), yy.toInt()), dstSize = IntSize(f.toInt(), th.toInt() + 1))
            yy += th
        }
    } }
}

/** Nine-slice a picture into [dst] keeping its corners at [corner] px (source) → [cornerDst] px. */
private fun DrawScope.nine(img: ImageBitmap, dst: Rect, corner: Int, cornerDst: Float) {
    val sw = img.width; val sh = img.height
    val c = corner.coerceAtMost(minOf(sw, sh) / 2 - 1)
    val cd = cornerDst.coerceAtMost(minOf(dst.width, dst.height) / 2f)
    val xs = intArrayOf(0, c, sw - c, sw); val ys = intArrayOf(0, c, sh - c, sh)
    val X = floatArrayOf(dst.left, dst.left + cd, dst.right - cd, dst.right)
    val Y = floatArrayOf(dst.top, dst.top + cd, dst.bottom - cd, dst.bottom)
    for (i in 0..2) for (j in 0..2) {
        val w = (X[i + 1] - X[i]).toInt(); val h = (Y[j + 1] - Y[j]).toInt()
        if (w <= 0 || h <= 0) continue
        drawImage(
            img,
            srcOffset = IntOffset(xs[i], ys[j]), srcSize = IntSize(xs[i + 1] - xs[i], ys[j + 1] - ys[j]),
            dstOffset = IntOffset(X[i].toInt(), Y[j].toInt()), dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Medium,
        )
    }
}

// ---------------------------------------------------------------- header parts

/** The two side towers with their pieces (wide screens only). */
@Composable
internal fun LuxTower(left: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.aspectRatio(280f / 816f)) {
        Image(painterResource(if (left) R.drawable.lux_tower_l else R.drawable.lux_tower_r), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val pieces = if (left) {
            listOf(R.drawable.fenrir_deco_crystal, R.drawable.fenrir_deco_wolf_bust, R.drawable.fenrir_deco_dragon)
        } else {
            listOf(R.drawable.fenrir_deco_katanas, R.drawable.fenrir_deco_moon, R.drawable.fenrir_deco_kitsune)
        }
        listOf(232f, 400f, 568f).zip(pieces).forEach { (sy, res) ->
            val shelfY = maxHeight * (sy / 816f)
            val pw = maxWidth * 0.44f
            Image(
                painterResource(res), null,
                Modifier
                    .offset(x = maxWidth * (if (left) 0.6f else 0.4f) - pw / 2f, y = shelfY - maxHeight * 0.17f)
                    .size(pw, maxHeight * 0.17f),
                contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
            )
        }
    }
}

/** ‹  📖 Name ▾  › on the gold band. */
@Composable
internal fun LuxCategoryBand(onPrev: () -> Unit, onNext: () -> Unit, onName: () -> Unit, name: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(697f / 104f)) {
        Image(painterResource(R.drawable.lux_band), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val w = maxWidth
        val h = maxHeight
        Box(Modifier.offset(x = w * (22f / 697f)).size(w * (82f / 697f), h).clip(CircleShape).clickable(onClick = onPrev), contentAlignment = Alignment.Center) {
            Text("‹", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.offset(x = w * (595f / 697f)).size(w * (82f / 697f), h).clip(CircleShape).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Text("›", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.offset(x = w * (150f / 697f)).size(w * (397f / 697f), h).clip(RoundedCornerShape(50)).clickable(onClick = onName),
            contentAlignment = Alignment.Center,
        ) { name() }
    }
}

/** Continue Reading: two-window cabinet, book on its stand, gold panel, doors that swing open. */
@Composable
internal fun LuxContinueCabinet(manga: LibraryManga, onOpen: () -> Unit) {
    val total = manga.totalChapters.coerceAtLeast(0)
    val read = manga.readCount.coerceIn(0, total.coerceAtLeast(0))
    val progress = if (total > 0) read.toFloat() / total else 0f
    val chapterLine = if (total > 0) "Chapter ${(read + 1).coerceAtMost(total)} of $total" else "Keep reading"
    val cover = remember(manga.manga.id, manga.manga.coverLastModified) { manga.manga.asMangaCover() }
    val doors = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var bookRect by remember { mutableStateOf(Rect.Zero) }
    val flying = FenrirBookFlight.isFlying(manga.manga.id)
    LaunchedEffect(flying) { if (!flying && doors.value > 0f) doors.snapTo(0f) }
    LaunchedEffect(manga.manga.id, manga.readCount) {
        FenrirFirstPage.forget(manga.manga.id)
        FenrirFirstPage.prefetch(manga.manga)
    }
    val tapped: () -> Unit = {
        if (doors.value == 0f && FenrirBookFlight.current == null) {
            scope.launch {
                doors.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
                FenrirBookFlight.start(manga.manga.id, cover, bookRect, onOpen, FenrirFirstPage.prefetch(manga.manga))
            }
        }
    }
    val panel = ImageBitmap.imageResource(R.drawable.lux_cont_panel)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(697f / 370f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = tapped),
    ) {
        Image(painterResource(R.drawable.lux_cabinet), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val w = maxWidth
        val h = maxHeight
        val L = floatArrayOf(30f / 697f, 30f / 370f, 330f / 697f, 332f / 370f)
        val R = floatArrayOf(365f / 697f, 30f / 370f, 670f / 697f, 332f / 370f)
        fun win(r: FloatArray) = Modifier.offset(w * r[0], h * r[1]).size(w * (r[2] - r[0]), h * (r[3] - r[1]))
        // left window: the book on its stand, standing on the floor of the window
        Box(win(L), contentAlignment = Alignment.BottomCenter) {
            LuxBook(
                cover = cover,
                modifier = Modifier
                    .padding(top = 6.dp, bottom = 3.dp)
                    .fillMaxHeight()
                    .luxContactShadow()
                    .alpha(if (flying) 0f else 1f),
                onWindow = { bookRect = it },
            )
        }
        // right window: the gold panel; its painted heading stays, title / chapter / progress drawn on it
        Box(
            win(R).padding(horizontal = 4.dp, vertical = 6.dp).drawBehind {
                val k = size.width / panel.width
                val topH = 223f * k
                val botH = (panel.height - 406) * k
                // top band (with the heading), stretchable middle, bottom band
                drawImage(panel, srcOffset = IntOffset(0, 0), srcSize = IntSize(panel.width, 223), dstSize = IntSize(size.width.toInt(), topH.toInt()))
                drawImage(panel, srcOffset = IntOffset(0, 223), srcSize = IntSize(panel.width, 183), dstOffset = IntOffset(0, topH.toInt()), dstSize = IntSize(size.width.toInt(), (size.height - topH - botH).toInt().coerceAtLeast(1)))
                drawImage(panel, srcOffset = IntOffset(0, 406), srcSize = IntSize(panel.width, panel.height - 406), dstOffset = IntOffset(0, (size.height - botH).toInt()), dstSize = IntSize(size.width.toInt(), botH.toInt()))
                // cover the painted bar; the real one is drawn below
                val barY = size.height - 127f * k
                drawRect(Color(0xFF0A0806), topLeft = Offset(size.width * 0.13f, barY - 22f * k), size = Size(size.width * 0.62f, 44f * k))
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val k = maxWidth / 900f
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = maxWidth * 0.13f, end = maxWidth * 0.12f)
                        .offset(y = k * 223f + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(manga.manga.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, maxLines = 4)
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = maxWidth * 0.13f, end = maxWidth * 0.12f, bottom = k * 127f - 6.dp),
                ) {
                    Text(chapterLine, color = Color(0xFFD8C9A6), fontSize = 10.sp, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Color(0xFF37322B)),
                        ) {
                            Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFFE0222E)))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
        LuxDoor(win(L), left = true, open = doors.value)
        LuxDoor(win(R), left = false, open = doors.value)
    }
}

/** A glass door with a thin gold frame and a gold bar handle near the middle. */
@Composable
private fun LuxDoor(modifier: Modifier, left: Boolean, open: Float) {
    val density = LocalDensity.current
    Box(
        modifier.graphicsLayer {
            transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
            rotationY = (if (left) -1f else 1f) * 165f * open
            cameraDistance = 9f * density.density
            alpha = if (open > 0.97f) 0f else 1f
        }.drawBehind {
            if (open <= 0f) {
                // closed: just a faint streak of reflection (the frame and handles are in the picture)
                val p = Path().apply {
                    moveTo(size.width * 0.62f, 0f); lineTo(size.width * 0.74f, 0f); lineTo(size.width * 0.5f, size.height); lineTo(size.width * 0.38f, size.height); close()
                }
                drawPath(p, Color.White.copy(alpha = 0.05f))
                return@drawBehind
            }
            drawRect(Color(0x1AC8DCF0))
            val g = Color(0xFFE2BE6E)
            val t = 1.5.dp.toPx()
            drawRect(g, size = Size(size.width, t)); drawRect(g, Offset(0f, size.height - t), Size(size.width, t))
            drawRect(g, size = Size(t, size.height)); drawRect(g, Offset(size.width - t, 0f), Size(t, size.height))
            val hx = if (left) size.width - 7.dp.toPx() else 4.dp.toPx()
            drawRoundRect(g, Offset(hx, size.height * 0.36f), Size(3.dp.toPx(), size.height * 0.28f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        },
    )
}

/** Soft shadow where something stands on the glass. */
internal fun Modifier.luxContactShadow(): Modifier = drawBehind {
    val y = size.height
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), center = Offset(size.width * 0.55f, y), radius = size.width * 0.48f),
        topLeft = Offset(size.width * 0.08f, y - size.height * 0.035f), size = Size(size.width * 0.9f, size.height * 0.07f),
    )
}

// ---------------------------------------------------------------- the shelf cells

/** Height of a row's nameplates: enough lines for the longest title in that row (never clipped). */
@Composable
private fun luxPlateLines(index: Int, title: String, widthPx: Int): Int {
    val cells = LocalLuxCells.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
    val row = if (cells != null && index >= 0) {
        val start = index / cells.perRow * cells.perRow
        cells.titles.subList(start.coerceAtMost(cells.titles.size), (start + cells.perRow).coerceAtMost(cells.titles.size))
    } else {
        listOf(title)
    }
    return remember(row, widthPx) {
        (row + title).maxOf { t -> measurer.measure(t, style, constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1))).lineCount }
    }.coerceAtLeast(1)
}

/**
 * One spot on a glass shelf: lights at the top, the object standing on the glass, and its
 * nameplate on the shelf front. Every spot in a row has the same height.
 */
@Composable
internal fun LuxShelfCell(
    index: Int,
    title: String,
    modifier: Modifier = Modifier,
    plate: Boolean = true,
    content: @Composable BoxScope.(itemH: Dp) -> Unit,
) {
    val lights = ImageBitmap.imageResource(R.drawable.lux_shelf_lights)
    val glass = ImageBitmap.imageResource(R.drawable.lux_shelf_glass)
    val nameplate = ImageBitmap.imageResource(R.drawable.lux_nameplate)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellW = maxWidth
        val itemH = cellW * 0.98f / LuxBookRatio
        val plateWpx = with(density) { (cellW - 26.dp).roundToPx() }
        val lines = luxPlateLines(index, title, plateWpx)
        val plateH = (14 * lines).dp + 14.dp
        val topPad = 12.dp
        val glassH = 13.dp
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val surface = (topPad + itemH).toPx()
                    // light from the shelf above (centre light of the strip) and its cone
                    val third = lights.width / 3
                    drawImage(lights, srcOffset = IntOffset(third, 0), srcSize = IntSize(third, lights.height), dstSize = IntSize(size.width.toInt(), (8.dp.toPx()).toInt()))
                    val cone = Path().apply {
                        val cx = size.width / 2f
                        moveTo(cx - 8.dp.toPx(), 4.dp.toPx()); lineTo(cx + 8.dp.toPx(), 4.dp.toPx())
                        lineTo(cx + size.width * 0.55f, surface); lineTo(cx - size.width * 0.55f, surface); close()
                    }
                    drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFFCE8C).copy(alpha = 0.20f), Color(0xFFFFCE8C).copy(alpha = 0.04f)), startY = 0f, endY = surface))
                    // glass shelf: middle of the picture, so neighbouring spots join into one shelf
                    val gh = glassH.toPx()
                    val sx0 = (glass.width * 0.12f).toInt(); val sx1 = (glass.width * 0.88f).toInt()
                    drawImage(
                        glass,
                        srcOffset = IntOffset(sx0, 0), srcSize = IntSize(sx1 - sx0, glass.height),
                        dstOffset = IntOffset(-1, (surface - gh * 0.42f).toInt()), dstSize = IntSize(size.width.toInt() + 2, gh.toInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                },
        ) {
            Spacer(Modifier.height(topPad))
            Box(Modifier.fillMaxWidth().height(itemH), contentAlignment = Alignment.BottomCenter) { content(itemH) }
            if (plate) {
                // nameplate on the shelf front
                Box(
                    Modifier
                        .padding(top = glassH * 0.58f, start = 4.dp, end = 4.dp)
                        .fillMaxWidth()
                        .height(plateH)
                        .drawBehind { nine(nameplate, Rect(Offset.Zero, size), 66, 11.dp.toPx()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = PlateText,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(horizontal = 11.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A book on the luxury shelf (tap = turn and open, hold = select). */
@Composable
internal fun LuxGridItem(
    item: eu.kanade.tachiyomi.ui.library.LibraryItem,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    flyOnClick: Boolean,
) {
    val manga = item.libraryManga.manga
    val cover = remember(manga.id, manga.coverLastModified, manga.thumbnailUrl) {
        CoverData(mangaId = manga.id, sourceId = manga.source, isMangaFavorite = manga.favorite, url = manga.thumbnailUrl, lastModified = manga.coverLastModified)
    }
    val rect = remember { mutableStateOf(Rect.Zero) }
    LuxShelfCell(index = index, title = manga.title) {
        LuxBook(
            cover = cover,
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .onGloballyPositioned { rect.value = it.boundsInRoot() }
                .luxContactShadow()
                .alpha(if (FenrirBookFlight.isFlying(manga.id)) 0f else if (isSelected) 0.75f else 1f)
                .combinedClickable(
                    onClick = {
                        if (flyOnClick) FenrirBookFlight.start(manga.id, cover, rect.value, onClick, turn = true, title = manga.title) else onClick()
                    },
                    onLongClick = onLongClick,
                ),
        ) {
            val unread = item.badges.unreadCount
            if (unread > 0 && eu.kanade.tachiyomi.EmbeddedHost.isMugi) {
                MugiChapterCountBadge(unread.toLong(), Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp))
            } else if (unread > 0) {
                Text(
                    if (unread > 999) "999+" else "$unread",
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xFFD7262F)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            if (isSelected) {
                Box(Modifier.matchParentSize().padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FF4D57)))
            }
        }
    }
}

/** "Add a book" in the same frame and stand; opens Browse. */
@Composable
internal fun LuxAddBookCell(index: Int) {
    val scope = rememberCoroutineScope()
    LuxShelfCell(index = index, title = "Add a book") {
        LuxBook(
            cover = null, blank = true,
            modifier = Modifier.fillMaxWidth(0.98f).luxContactShadow().clickable {
                scope.launch { eu.kanade.tachiyomi.ui.home.HomeScreen.openTab(eu.kanade.tachiyomi.ui.home.HomeScreen.Tab.Browse()) }
            },
        )
    }
}

/** A collectible on a low black-and-gold stand. */
@Composable
internal fun LuxDecorCell(index: Int, res: Int?, name: String) {
    if (res == null) {
        LuxShelfCell(index = index, title = name, plate = false) { }
        return
    }
    LuxShelfCell(index = index, title = name) { itemH ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.luxContactShadow()) {
            Image(
                painterResource(res), null,
                Modifier.fillMaxWidth(0.78f).height(itemH * 0.74f).offset(y = 6.dp),
                contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
            )
            Image(painterResource(R.drawable.lux_stand), null, Modifier.fillMaxWidth(0.82f).aspectRatio(500f / 90f), contentScale = ContentScale.FillBounds)
        }
    }
}

/** The cabinet base after the last row. */
@Composable
internal fun LuxBase() {
    val geom = LocalLuxGeom.current
    val base = ImageBitmap.imageResource(R.drawable.lux_base)
    Box(
        Modifier.fillMaxWidth().height(44.dp).drawBehind {
            val ext = (geom.frame + 6.dp).toPx()
            drawImage(base, dstOffset = IntOffset(-ext.toInt(), 0), dstSize = IntSize((size.width + 2 * ext).toInt(), (30.dp.toPx()).toInt()))
        },
    )
}

/** Top of the collection case: thin gold cap and the ceiling lights over the first row. */
@Composable
internal fun LuxCaseTop(geom: LuxGeom) {
    val cap = ImageBitmap.imageResource(R.drawable.lux_frame_top)
    val lights = ImageBitmap.imageResource(R.drawable.lux_case_lights)
    val wall = ImageBitmap.imageResource(R.drawable.lux_wall)
    Box(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .padding(horizontal = geom.tower + (if (geom.wide) 3.dp else 0.dp))
            .height(22.dp)
            .drawBehind {
                val f = geom.frame.toPx()
                drawImage(wall, dstOffset = IntOffset(f.toInt(), 0), dstSize = IntSize((size.width - 2 * f).toInt(), size.height.toInt() + 1))
                drawImage(cap, dstSize = IntSize(size.width.toInt(), (9.dp.toPx()).toInt()))
                drawImage(lights, srcOffset = IntOffset(0, 18), srcSize = IntSize(lights.width, lights.height - 18), dstOffset = IntOffset(f.toInt(), (9.dp.toPx()).toInt()), dstSize = IntSize((size.width - 2 * f).toInt(), (size.height - 9.dp.toPx()).toInt()))
            },
    )
}
