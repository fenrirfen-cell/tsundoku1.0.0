package eu.kanade.presentation.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import kotlin.math.roundToInt

/**
 * Fenrir: a book leaving the bookcase. It slides forward, flies to the middle while the room
 * dims, the cover swings open, a couple of pages turn, warm page colour fills the screen, and
 * then the book (or the chapter) opens. Tap to skip.
 */
internal object FenrirBookFlight {
    class Flight(
        val mangaId: Long,
        val cover: tachiyomi.domain.manga.model.MangaCover,
        val start: Rect,
        val onDone: () -> Unit,
        val page: kotlinx.coroutines.Deferred<FenrirFirstPage.Content?>? = null,
        val turn: Boolean = false,
        val title: String = "",
    ) {
        /** Set once the book has opened; a flight that's done never plays again (e.g. on Back). */
        var done = false
    }

    var current by mutableStateOf<Flight?>(null)
        private set

    fun start(
        mangaId: Long,
        cover: tachiyomi.domain.manga.model.MangaCover,
        start: Rect,
        onDone: () -> Unit,
        page: kotlinx.coroutines.Deferred<FenrirFirstPage.Content?>? = null,
        turn: Boolean = false,
        title: String = "",
    ) {
        if (current != null) return
        if (start.width <= 0f) { onDone(); return }
        current = Flight(mangaId, cover, start, onDone, page, turn, title)
    }

    internal fun clear() { current = null }

    fun isFlying(mangaId: Long): Boolean = current?.mangaId == mangaId
}

private val PageCream = Color(0xFFF1E8D2)
private val PageLine = Color(0x40806A48)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun phase(t: Float, start: Float, len: Float) = ((t - start) / len).coerceIn(0f, 1f)
private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

@Composable
internal fun FenrirBookFlightOverlay() {
    val flight = FenrirBookFlight.current ?: return
    if (flight.done) {
        androidx.compose.runtime.SideEffect { FenrirBookFlight.clear() }
        return
    }
    if (flight.turn) {
        FenrirBookTurnOverlay(flight)
        return
    }
    val clock = remember(flight) { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var finished by remember(flight) { mutableStateOf(false) }
    var placed by remember(flight) { mutableStateOf(false) }
    LaunchedEffect(finished) {
        if (finished) {
            delay(1500)
            FenrirBookFlight.clear()
        }
    }
    val total = 1550f
    val scope = rememberCoroutineScope()
    fun finish() {
        if (finished) return
        finished = true
        // Mark it done (so Back never replays it), but keep the last frame on screen while the
        // next page slides in: letting go here flashed the library for a moment.
        flight.done = true
        flight.onDone()
    }
    var content by remember(flight) { mutableStateOf<FenrirFirstPage.Content?>(null) }
    LaunchedEffect(flight) {
        val p = flight.page ?: return@LaunchedEffect
        content = runCatching { p.await() }.getOrNull()
    }
    LaunchedEffect(flight) {
        clock.animateTo(700f, tween(700, easing = LinearEasing))
        // just before the cover opens: give the chapter's first page a moment to arrive
        val p = flight.page
        if (p != null && !p.isCompleted) runCatching { kotlinx.coroutines.withTimeoutOrNull(1200) { p.await() } }
        clock.animateTo(total, tween((total - 700f).toInt(), easing = LinearEasing))
        finish()
    }
    val t = clock.value
    val slide = smooth(phase(t, 0f, 250f))
    val fly = FastOutSlowInEasing.transform(phase(t, 200f, 550f))
    val open = FastOutSlowInEasing.transform(phase(t, 750f, 500f))
    // the open page grows into the reader (or fades to paper when there's no page to show)
    val grow = smooth(phase(t, 1250f, 300f))

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot(); placed = true }
            // nothing is drawn until we know where the overlay sits (the first frame jumped)
            .graphicsLayer { alpha = if (placed) 1f else 0f }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // dim the library
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f * fly)))

        val s = flight.start
        val startL = s.left - origin.x
        val startT = s.top - origin.y - with(density) { 8.dp.toPx() } * slide
        val startW = s.width * (1f + 0.08f * slide)
        val endW = minOf(wPx * 0.52f, with(density) { 230.dp.toPx() })
        val ratio = s.height / s.width
        val bookW = lerp(startW, endW, fly)
        val bookH = bookW * ratio
        val endL = (wPx - endW) / 2f
        val endT = (hPx - endW * ratio) / 2f - with(density) { 20.dp.toPx() }
        val c = content
        val g = if (c != null) grow else 0f
        val left = lerp(lerp(startL, endL, fly), 0f, g)
        val top = lerp(lerp(startT, endT, fly), 0f, g)
        val wDp = with(density) { lerp(bookW, wPx, g).toDp() }
        val hDp = with(density) { lerp(bookH, hPx, g).toDp() }
        val flood = if (c == null) grow else 0f

        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(wDp, hDp)
                .graphicsLayer {
                    shadowElevation = (6f + 18f * fly) * density.density
                    shape = RoundedCornerShape(4.dp)
                },
        ) {
            // the first page of the chapter you're on, under the cover
            when (val pc = content) {
                is FenrirFirstPage.Content.Image -> Box(Modifier.fillMaxSize().background(Color.White)) {
                    androidx.compose.foundation.Image(
                        bitmap = pc.bitmap,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is FenrirFirstPage.Content.Text -> Column(
                    Modifier.fillMaxSize().background(PageCream).padding(horizontal = 10.dp + 14.dp * g, vertical = 12.dp + 20.dp * g),
                ) {
                    Text(
                        pc.chapter,
                        color = Color(0xFF50281E),
                        fontSize = (9f + 9f * g).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pc.text,
                        color = Color(0xFF2A1E18),
                        fontSize = (6.5f + 8f * g).sp,
                        lineHeight = (9f + 11f * g).sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        overflow = TextOverflow.Clip,
                    )
                }
                null -> Box(Modifier.fillMaxSize().background(PageCream).drawBehind { pageLines(true) })
            }
            // a couple of pages turning just after the cover
            listOf(0, 1).forEach { k ->
                val p = FastOutSlowInEasing.transform(phase(t, 880f + k * 110f, 300f))
                if (p in 0.01f..0.99f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = -172f * p
                                cameraDistance = 10f * density.density
                            }
                            .background(PageCream)
                            .drawBehind { pageLines(false) },
                    )
                }
            }
            // the cover
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -172f * open
                        cameraDistance = 10f * density.density
                    },
            ) {
                if (open < 0.5f) {
                    MangaCover.Book(
                        data = flight.cover,
                        modifier = Modifier.fillMaxSize().fenrirHardcover(),
                        shape = RectangleShape,
                    )
                } else {
                    // inside of the cover board
                    Box(Modifier.fillMaxSize().background(Color(0xFF4A3426)))
                }
            }
        }
        if (flood > 0f) Box(Modifier.fillMaxSize().background(PageCream.copy(alpha = flood)))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pageLines(heading: Boolean) {
    val padX = size.width * 0.12f
    var y = size.height * if (heading) 0.24f else 0.12f
    if (heading) {
        drawRect(Color(0x80603C28), topLeft = Offset(padX, size.height * 0.09f), size = Size(size.width * 0.38f, size.height * 0.035f))
    }
    while (y < size.height * 0.9f) {
        drawLine(PageLine, Offset(padX, y), Offset(size.width - padX, y), 1.2f)
        y += size.height * 0.065f
    }
}

/**
 * Continue reading as a glass display case on top of the bookcase: the book stands on red velvet
 * under its own spotlight in the left pane, a brass museum placard (title, chapter, progress)
 * in the right pane. Tap it: both glass doors swing all the way open, then the book flies out
 * and opens into the chapter.
 */
@Composable
internal fun FenrirContinueReadingCase(manga: LibraryManga, onOpen: () -> Unit) {
    val total = manga.totalChapters.coerceAtLeast(0)
    val read = manga.readCount.coerceIn(0, total.coerceAtLeast(0))
    val progress = if (total > 0) read.toFloat() / total else 0f
    val chapterLine = if (total > 0) "Chapter ${(read + 1).coerceAtMost(total)} of $total" else "Keep reading"
    val cover = remember(manga.manga.id, manga.manga.coverLastModified) { manga.manga.asMangaCover() }
    val doors = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var bookRect by remember { mutableStateOf(Rect.Zero) }
    val flying = FenrirBookFlight.isFlying(manga.manga.id)
    // close the doors again once the book has gone (ready for when you come back)
    LaunchedEffect(flying) { if (!flying && doors.value > 0f) doors.snapTo(0f) }

    // start fetching the first page of the chapter you're on, so it's there when the book opens
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
    // a still glow on the knobs (an endless pulse kept the screen redrawing and used battery)
    val glow = 0.6f

    Box(
        Modifier
            .fillMaxWidth()
            .height(182.dp)
            .fenrirRail()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = tapped)
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 10.dp),
    ) {
        // the inside of the case
        Box(
            Modifier
                .fillMaxSize()
                .border(2.dp, Color(0xFF2A1A10))
                .drawBehind {
                    drawRect(Color(0xFF2C0A12))
                    listOf(0.25f to 1f, 0.75f to 0.6f).forEach { (fx, k) ->
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color(0xFFB26A3A).copy(alpha = 0.55f * k), Color.Transparent),
                                center = Offset(size.width * fx, 0f),
                                radius = size.height * 1.05f,
                            ),
                        )
                    }
                    // red velvet along the bottom
                    val v = 16.dp.toPx()
                    drawRect(
                        Brush.verticalGradient(0f to Color(0xFF6E1A26), 1f to Color(0xFF3A0C14), startY = size.height - v, endY = size.height),
                        topLeft = Offset(0f, size.height - v),
                        size = Size(size.width, v),
                    )
                    // bevelled edge of the case
                    drawRect(Color(0x55FFE2B8), size = Size(size.width, 1.5.dp.toPx()))
                    drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, size.height - 1.5.dp.toPx()), size = Size(size.width, 1.5.dp.toPx()))
                },
        ) {
            Row(Modifier.fillMaxSize()) {
                // left pane: the book under its spotlight
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Spotlight(Modifier.align(Alignment.TopCenter))
                    Box(
                        Modifier
                            .padding(bottom = 7.dp)
                            .width(94.dp)
                            .aspectRatio(MangaCover.Book.ratio)
                            .drawBehind {
                                // the book's shadow on the velvet
                                drawOval(
                                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), center = Offset(size.width / 2f, size.height), radius = size.width * 0.75f),
                                    topLeft = Offset(-size.width * 0.2f, size.height - 6.dp.toPx()),
                                    size = Size(size.width * 1.4f, 12.dp.toPx()),
                                )
                            }
                            .onGloballyPositioned { bookRect = it.boundsInRoot() }
                            .alpha(if (flying) 0f else 1f)
                            .fenrirPageBlock(5.dp),
                    ) {
                        MangaCover.Book(
                            data = cover,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 5.dp, top = 3.dp)
                                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                                .fenrirHardcover(),
                            shape = RectangleShape,
                        )
                    }
                }
                // right pane: the brass placard
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Spotlight(Modifier.align(Alignment.TopCenter))
                    Column(
                        Modifier
                            .padding(bottom = 13.dp, start = 6.dp, end = 6.dp)
                            .fillMaxWidth()
                            .fenrirBrass(RoundedCornerShape(6.dp))
                            .drawBehind { brassScrews(6.dp) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Engraved("CONTINUE READING", 8.5f, FontWeight.Bold, letterSpacing = 0.8f)
                        Engraved(manga.manga.title, 13f, FontWeight.ExtraBold, maxLines = 2)
                        Engraved(chapterLine, 10f, FontWeight.Medium)
                        Box(
                            Modifier
                                .padding(top = 3.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF5A3E16)),
                        ) {
                            Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFFC42830)))
                        }
                    }
                }
            }
            // the two glass doors, hinged at the outer edges, meeting between the panes
            Row(Modifier.fillMaxSize()) {
                GlassDoor(Modifier.weight(1f).fillMaxHeight(), left = true, open = doors.value, knobGlow = glow)
                GlassDoor(Modifier.weight(1f).fillMaxHeight(), left = false, open = doors.value, knobGlow = glow)
            }
        }
    }
}

@Composable
private fun Spotlight(modifier: Modifier) {
    Box(modifier.size(width = 24.dp, height = 6.dp).clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)).fenrirBrass(RectangleShape))
}

@Composable
private fun Engraved(text: String, size: Float, weight: FontWeight, maxLines: Int = 1, letterSpacing: Float = 0f) {
    Text(
        text,
        style = TextStyle(
            color = FenrirBrassText,
            fontSize = size.sp,
            fontWeight = weight,
            letterSpacing = letterSpacing.sp,
            textAlign = TextAlign.Center,
            lineHeight = (size + 3f).sp,
            shadow = Shadow(Color(0x99FFF0C0), Offset(0f, 1.5f), 0f),
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One glass door: thin wooden frame, glass with reflections, a small brass knob by the middle. */
@Composable
private fun GlassDoor(modifier: Modifier, left: Boolean, open: Float, knobGlow: Float) {
    val density = LocalDensity.current
    Box(
        modifier
            .graphicsLayer {
                transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
                rotationY = (if (left) -1f else 1f) * 168f * open
                cameraDistance = 9f * density.density
                alpha = if (open > 0.97f) 0f else 1f
            }
            .drawBehind {
                val frame = 4.dp.toPx()
                // glass
                drawRect(Color(0x1AC8DCFF))
                val path = Path().apply {
                    val w = size.width; val h = size.height
                    moveTo(w * 0.52f, 0f); lineTo(w * 0.72f, 0f); lineTo(w * 0.40f, h); lineTo(w * 0.20f, h); close()
                }
                drawPath(path, Color.White.copy(alpha = 0.10f))
                val path2 = Path().apply {
                    val w = size.width; val h = size.height
                    moveTo(w * 0.78f, 0f); lineTo(w * 0.83f, 0f); lineTo(w * 0.51f, h); lineTo(w * 0.46f, h); close()
                }
                drawPath(path2, Color.White.copy(alpha = 0.08f))
                // wooden frame
                val wood = Color(0xFF7A4E2C)
                drawRect(wood, size = Size(size.width, frame))
                drawRect(wood, topLeft = Offset(0f, size.height - frame), size = Size(size.width, frame))
                drawRect(wood, size = Size(frame, size.height))
                drawRect(wood, topLeft = Offset(size.width - frame, 0f), size = Size(frame, size.height))
                drawRect(Color(0x55FFE2B8), size = Size(size.width, 1.dp.toPx()))
                // knob near the meeting edge, softly catching the light
                val kx = if (left) size.width - 10.dp.toPx() else 10.dp.toPx()
                val c = Offset(kx, size.height / 2f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6AA).copy(alpha = 0.5f * knobGlow * (1f - open)), Color.Transparent), center = c, radius = 14.dp.toPx()), 14.dp.toPx(), c)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF6DC9C), Color(0xFF8A6428)), center = Offset(c.x - 1.dp.toPx(), c.y - 1.5.dp.toPx()), radius = 6.dp.toPx()), 4.5.dp.toPx(), c)
                drawCircle(Color(0xFF6E4F1C), 4.5.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            },
    )
}

/**
 * Fenrir: tapping a book on the shelf. It lifts off the shelf and comes forward while turning
 * round in 3D (spine and page edges show as it turns) to its back cover, then the book's page
 * opens. About 0.8s. Tap to skip.
 */
@Composable
private fun FenrirBookTurnOverlay(flight: FenrirBookFlight.Flight) {
    val clock = remember(flight) { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var finished by remember(flight) { mutableStateOf(false) }
    var placed by remember(flight) { mutableStateOf(false) }
    LaunchedEffect(finished) {
        if (finished) {
            delay(1500)
            FenrirBookFlight.clear()
        }
    }
    val total = 760f
    fun finish() {
        if (finished) return
        finished = true
        // Mark it done (so Back never replays it), but keep the last frame on screen while the
        // next page slides in: letting go here flashed the library for a moment.
        flight.done = true
        flight.onDone()
    }
    LaunchedEffect(flight) {
        clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        finish()
    }
    val t = clock.value
    val lift = smooth(phase(t, 0f, 200f))
    val fly = FastOutSlowInEasing.transform(phase(t, 60f, 560f))
    val spin = FastOutSlowInEasing.transform(phase(t, 120f, 600f))
    val fade = 0f

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot(); placed = true }
            // nothing is drawn until we know where the overlay sits (the first frame jumped)
            .graphicsLayer { alpha = if (placed) 1f else 0f }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f * fly)))

        val s = flight.start
        val ratio = s.height / s.width
        val startL = s.left - origin.x
        val startT = s.top - origin.y - with(density) { 10.dp.toPx() } * lift
        val startW = s.width * (1f + 0.06f * lift)
        val endW = minOf(wPx * 0.5f, with(density) { 220.dp.toPx() })
        val bookW = lerp(startW, endW, fly)
        val bookH = bookW * ratio
        val left = lerp(startL, (wPx - endW) / 2f, fly)
        val top = lerp(startT, (hPx - endW * ratio) / 2f - with(density) { 24.dp.toPx() }, fly)
        val angle = 180f * spin
        val rad = Math.toRadians(angle.toDouble())
        val edge = kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
        val depth = bookW * 0.16f
        val wDp = with(density) { bookW.toDp() }
        val hDp = with(density) { bookH.toDp() }
        // the book's thickness while it's side-on: spine first, then the page edges
        val sideW = depth * edge
        if (sideW > 1f) {
            val sideLeft = left + bookW / 2f - sideW / 2f
            Box(
                Modifier
                    .offset { IntOffset(sideLeft.roundToInt(), top.roundToInt()) }
                    .size(with(density) { sideW.toDp() }, hDp)
                    .graphicsLayer { alpha = 1f - fade }
                    .drawBehind {
                        if (angle < 90f) {
                            // spine: leather with two raised bands
                            drawRect(Brush.horizontalGradient(listOf(Color(0xFF3A1C14), Color(0xFF6A3826), Color(0xFF3A1C14))))
                            listOf(0.18f, 0.82f).forEach { fy ->
                                drawRect(Color(0xFFC9A14E).copy(alpha = 0.7f), topLeft = Offset(0f, size.height * fy), size = Size(size.width, 2.dp.toPx()))
                            }
                        } else {
                            drawRect(PageCream)
                            var y = 2.dp.toPx()
                            while (y < size.height) {
                                drawLine(PageLine, Offset(0f, y), Offset(size.width, y), 1f)
                                y += 3.dp.toPx()
                            }
                        }
                    },
            )
        }
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(wDp, hDp)
                .graphicsLayer {
                    rotationY = angle
                    cameraDistance = 12f * density.density
                    shadowElevation = (6f + 20f * fly) * density.density
                    shape = RoundedCornerShape(4.dp)
                    alpha = 1f - fade
                },
        ) {
            if (angle < 90f) {
                MangaCover.Book(
                    data = flight.cover,
                    modifier = Modifier.fillMaxSize().fenrirHardcover(),
                    shape = RectangleShape,
                )
            } else {
                // the back cover (drawn mirrored so it reads the right way round once turned)
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) {
                    MangaCover.Book(
                        data = flight.cover,
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                    )
                    Box(Modifier.fillMaxSize().background(Color(0xE6201410)))
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            flight.title,
                            color = Color(0xFFE8C67A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.width(40.dp).height(1.dp).background(Color(0xFFE8C67A).copy(alpha = 0.6f)))
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .drawBehind {
                                    var y = 4.dp.toPx()
                                    var i = 0
                                    while (y < size.height - 4.dp.toPx()) {
                                        val w = if (i % 5 == 4) 0.6f else 1f
                                        drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width * w, y), 1.6f)
                                        y += 9.dp.toPx()
                                        i++
                                    }
                                },
                        )
                        Spacer(Modifier.height(8.dp))
                        // a little barcode in the corner
                        Row(Modifier.align(Alignment.End).height(18.dp).background(Color(0xFFF1E8D2)).padding(horizontal = 3.dp, vertical = 2.dp)) {
                            listOf(1, 2, 1, 1, 3, 1, 2, 1, 1, 2, 1, 3).forEachIndexed { i, b ->
                                Box(Modifier.width((b * 0.8f).dp).fillMaxHeight().background(if (i % 2 == 0) Color(0xFF1A1A1A) else Color.Transparent))
                            }
                        }
                    }
                }
            }
            // light sweeping across while it turns
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.22f * edge), Color.Transparent, Color.Black.copy(alpha = 0.35f * edge)),
                    ),
                ),
            )
        }
    }
}
