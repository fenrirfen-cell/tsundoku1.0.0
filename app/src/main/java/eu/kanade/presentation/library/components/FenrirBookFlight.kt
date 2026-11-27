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
import androidx.compose.foundation.layout.wrapContentSize
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
import uy.kohesive.injekt.api.get
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
    // Only a flight that was already done when we got here is skipped (e.g. coming Back). One that
    // finishes while it's on screen keeps its last frame until the reader is up; dropping it here
    // flashed the library between the open book and the reader.
    val doneBefore = remember(flight) { flight.done }
    if (doneBefore) {
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
    // Fenrir: the page that grows into the reader wears the reader's own colours, font and size,
    // so the hand-over to the reader doesn't jump (it used to be cream serif, then a white page).
    val readerLook = rememberFenrirReaderLook()
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
        val bookAlpha = smooth(phase(t, 650f, 90f))

        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(wDp, hDp)
                .graphicsLayer {
                    // hidden while the tome carries the cover (the flat cover beside the tome's bent
                    // one looked like two books); shown just before the cover swings open
                    alpha = bookAlpha
                    shadowElevation = (6f + 18f * fly) * density.density
                    shape = RoundedCornerShape(4.dp)
                    // Fenrir 3D: the whole book leans toward you as the cover swings open, then
                    // settles flat as the page grows into the reader
                    rotationY = 16f * kotlin.math.sin(open * Math.PI.toFloat()) * (1f - g)
                    rotationX = 6f * kotlin.math.sin(open * Math.PI.toFloat()) * (1f - g)
                    cameraDistance = 9f * density.density
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
                is FenrirFirstPage.Content.Text -> {
                    // When you're part way into the chapter the reader opens where you stopped, not at
                    // its first line: the words fade off as the page grows, leaving a clean page in the
                    // reader's colours for the reader to take over.
                    val words = if (pc.started) 1f - smooth(phase(t, 1250f, 170f)) else 1f
                    val bodySize = lerp(6.5f, readerLook.fontSize, g)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.lerp(PageCream, readerLook.background, g))
                            .padding(horizontal = 10.dp + 6.dp * g, vertical = 12.dp + 28.dp * g)
                            .graphicsLayer { alpha = words },
                    ) {
                        Text(
                            pc.chapter,
                            color = androidx.compose.ui.graphics.lerp(Color(0xFF50281E), readerLook.text, g),
                            fontSize = (9f + (readerLook.fontSize + 2f - 9f) * g).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = readerLook.family,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp + 10.dp * g))
                        Text(
                            pc.text,
                            color = androidx.compose.ui.graphics.lerp(Color(0xFF2A1E18), readerLook.text, g),
                            fontSize = bodySize.sp,
                            lineHeight = (bodySize * 1.45f).sp,
                            fontFamily = readerLook.family,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                null -> Box(Modifier.fillMaxSize().background(PageCream).drawBehind { pageLines(true) })
            }
            // Fenrir 3D: the edges of the page block down the right side and along the bottom,
            // and the shadow the lifting cover casts on the page; both go as the page grows.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val fade = 1f - g
                    if (fade <= 0.01f) return@drawBehind
                    val strip = 5.dp.toPx()
                    var x = size.width - strip
                    while (x < size.width) {
                        drawLine(Color(0x55806A48).copy(alpha = 0.33f * fade), Offset(x, 0f), Offset(x, size.height), 1f)
                        x += 1.6f
                    }
                    var y = size.height - strip * 0.8f
                    while (y < size.height) {
                        drawLine(Color(0x55806A48).copy(alpha = 0.33f * fade), Offset(0f, y), Offset(size.width, y), 1f)
                        y += 1.6f
                    }
                    val shade = 0.45f * kotlin.math.sin(open * Math.PI.toFloat()) * fade
                    if (shade > 0.01f) {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = shade), Color.Transparent),
                                startX = 0f,
                                endX = size.width * (0.35f + 0.5f * (1f - open)),
                            ),
                        )
                    }
                },
            )
            // pages fanning over just after the cover, each catching the light as it turns
            listOf(0, 1, 2, 3).forEach { k ->
                val p = FastOutSlowInEasing.transform(phase(t, 860f + k * 85f, 330f))
                if (p in 0.01f..0.99f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = -176f * p
                                cameraDistance = 7f * density.density
                            }
                            .background(PageCream)
                            .drawBehind {
                                pageLines(false)
                                // darker as the page stands up, lighter as it lies down on the left
                                val light = kotlin.math.sin(p * Math.PI.toFloat())
                                drawRect(
                                    Brush.horizontalGradient(
                                        listOf(Color.Black.copy(alpha = 0.28f * light), Color.Black.copy(alpha = 0.06f * light)),
                                    ),
                                )
                            },
                    )
                }
            }
            // the cover: a hard board swinging open on its hinge, shaded by the angle
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -176f * open
                        cameraDistance = 7f * density.density
                    },
            ) {
                if (open < 0.5f) {
                    MangaCover.Book(
                        data = flight.cover,
                        modifier = Modifier.fillMaxSize().fenrirHardcover(),
                        shape = RectangleShape,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * open * 2f)))
                } else {
                    // inside of the cover board: endpaper, darker by the hinge
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF6A4A34), Color(0xFF3A281C)),
                                ),
                            )
                            .drawBehind {
                                drawRect(Color.Black.copy(alpha = 0.45f * (1f - open) * 2f))
                            },
                    )
                }
            }
        }
        // Fenrir: the tome (frame, spine, ribbon) round the flying cover, so it leaves the cabinet
        // looking like the book you saw; it melts away just as the cover swings open.
        val tomeAlpha = 1f - smooth(phase(t, 700f, 60f))
        if (tomeAlpha > 0.01f && g == 0f) {
            val cbw = FenrirTomeCoverBox[2] - FenrirTomeCoverBox[0]
            val frameW = bookW / cbw
            val frameH = frameW / FenrirTomeRatio
            val fl = left - FenrirTomeCoverBox[0] * frameW
            val ft = top - FenrirTomeCoverBox[1] * frameH
            FenrirTomeBook(
                cover = flight.cover,
                modifier = Modifier
                    .offset { IntOffset(fl.roundToInt(), ft.roundToInt()) }
                    .size(with(density) { frameW.toDp() }, with(density) { frameH.toDp() })
                    .graphicsLayer {
                        alpha = tomeAlpha
                        // Fenrir 3D: it swings round as it leaves the case (spine toward you mid-
                        // flight) and faces you square by the time it opens
                        val arc = kotlin.math.sin(fly * Math.PI.toFloat())
                        rotationY = -32f * arc
                        rotationZ = -5f * arc
                        rotationX = 8f * arc
                        cameraDistance = 8f * density.density
                    },
            )
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

    // Fenrir: your ornate cabinet frame; the book stands in the left window, the placard fills the right.
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .aspectRatio(CabinetRatio)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = tapped),
    ) {
        val w = maxWidth
        val h = maxHeight
        fun win(r: FloatArray) = Modifier
            .offset(w * r[0], h * r[1])
            .size(w * (r[2] - r[0]), h * (r[3] - r[1]))
        val leftH = h * (CabinetLeft[3] - CabinetLeft[1])
        val leftW = w * (CabinetLeft[2] - CabinetLeft[0])
        // left window: velvet under a spotlight, and the book standing on the floor
        Box(
            win(CabinetLeft).drawBehind {
                drawRect(Color(0xFF2C0A12))
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFB26A3A).copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.height * 1.1f,
                    ),
                )
                val v = 12.dp.toPx()
                drawRect(
                    Brush.verticalGradient(0f to Color(0xFF6E1A26), 1f to Color(0xFF3A0C14), startY = size.height - v, endY = size.height),
                    topLeft = Offset(0f, size.height - v),
                    size = Size(size.width, v),
                )
            },
        ) {
            Spotlight(Modifier.align(Alignment.TopCenter))
            // as tall as the window allows: the stand rests on the floor, the top just under the frame
            val bookH = minOf((leftH - 6.dp) / FenrirTomeStand, leftW * 0.82f / FenrirTomeRatio)
            val bookTop = leftH - 3.dp - bookH * FenrirTomeStand
            FenrirTomeBook(
                cover = cover,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = bookTop)
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .height(bookH)
                    .fenrirTomeShadow()
                    .alpha(if (flying) 0f else 1f),
                onFrameBounds = { bookRect = fenrirTomeCoverRect(it) },
            )
        }
        // right window: the gold placard
        Box(
            win(CabinetRight).fenrirBrass(RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
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
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5A3E16)),
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFFC42830)))
                }
            }
        }
        // the glass door over the book, hinged at its left edge (its own layer, under the frame)
        GlassDoor(win(CabinetLeft), left = true, open = doors.value, knobGlow = glow)
        // the carved frame on top of everything
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.fenrir_cabinet),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The cabinet picture's shape and its two windows (left, top, right, bottom as shares of its size). */
private const val CabinetRatio = 1440f / 595f
private val CabinetLeft = floatArrayOf(0.0704f, 0.1878f, 0.4765f, 0.8122f)
private val CabinetRight = floatArrayOf(0.5153f, 0.1878f, 0.9326f, 0.8122f)

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
                drawRect(Color(0x30101828))
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
    // It sits above every screen now (so the bottom bar and top bar don't poke through), so once
    // the book's page is on its way in, the turned book melts away over it instead of lingering.
    val leave = remember(flight) { Animatable(1f) }
    LaunchedEffect(finished) {
        if (finished) {
            delay(220)
            leave.animateTo(0f, tween(280, easing = LinearEasing))
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
            .graphicsLayer { alpha = if (placed) leave.value else 0f }
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
        val endW = minOf(wPx * 0.62f, with(density) { 280.dp.toPx() })
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
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(wDp, hDp)
                .graphicsLayer {
                    rotationY = angle
                    cameraDistance = 12f * density.density
                    alpha = 1f - fade
                },
        ) {
            val luxury = FenrirLibraryLook.current.value == FenrirLibraryLook.Look.Luxury
            if (luxury) {
                if (angle < 90f) {
                    LuxBook(cover = flight.cover, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) { LuxBookBack(flight.cover, Modifier.fillMaxSize()) }
                }
            } else if (angle < 90f) {
                // the tome as it stood on the shelf
                FenrirTomeBook(cover = flight.cover, modifier = Modifier.fillMaxSize())
            } else {
                // its back: same frame from behind, wolf and FENRIR in the book's colour
                // (mirrored here so it reads the right way round once turned)
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) {
                    FenrirTomeBack(cover = flight.cover, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The novel reader's page: background and text colours, text size and font family. */
internal class FenrirReaderLook(
    val background: Color,
    val text: Color,
    val fontSize: Float,
    val family: androidx.compose.ui.text.font.FontFamily,
)

@Composable
private fun rememberFenrirReaderLook(): FenrirReaderLook {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching {
            val prefs = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences>()
            val activity = context as? android.app.Activity
            val (bg, fg) = if (activity != null) {
                eu.kanade.tachiyomi.ui.reader.viewer.text.shared.ThemeUtils.getThemeColors(activity, prefs, prefs.novelTheme.get())
            } else {
                0xFFFFFFFF.toInt() to 0xFF000000.toInt()
            }
            val name = prefs.novelFontFamily.get().lowercase()
            val family = when {
                "mono" in name -> androidx.compose.ui.text.font.FontFamily.Monospace
                "serif" in name && "sans" !in name -> androidx.compose.ui.text.font.FontFamily.Serif
                else -> androidx.compose.ui.text.font.FontFamily.SansSerif
            }
            FenrirReaderLook(Color(bg), Color(fg), prefs.novelFontSize.get().coerceIn(10, 40).toFloat(), family)
        }.getOrElse { FenrirReaderLook(Color.White, Color.Black, 16f, androidx.compose.ui.text.font.FontFamily.SansSerif) }
    }
}

/**
 * MugiVerse: Continue Reading in the carved gold frame (parchment title on top). Your 3D book on
 * the left flies out and opens just like from the glass case; title, chapter and progress beside it.
 */
@Composable
internal fun MugiContinueReadingFrame(manga: LibraryManga, onOpen: () -> Unit) {
    val total = manga.totalChapters.coerceAtLeast(0)
    val read = manga.readCount.coerceIn(0, total.coerceAtLeast(0))
    val progress = if (total > 0) read.toFloat() / total else 0f
    val chapterLine = if (total > 0) "Chapter ${(read + 1).coerceAtMost(total)} of $total" else "Keep reading"
    val cover = remember(manga.manga.id, manga.manga.coverLastModified) { manga.manga.asMangaCover() }
    var bookRect by remember { mutableStateOf(Rect.Zero) }
    val flying = FenrirBookFlight.isFlying(manga.manga.id)
    LaunchedEffect(manga.manga.id, manga.readCount) {
        FenrirFirstPage.forget(manga.manga.id)
        FenrirFirstPage.prefetch(manga.manga)
    }
    val tapped: () -> Unit = {
        if (FenrirBookFlight.current == null) {
            FenrirBookFlight.start(manga.manga.id, cover, bookRect, onOpen, FenrirFirstPage.prefetch(manga.manga))
        }
    }
    val ivory = Color(0xFFF4E3C1)
    val serif = androidx.compose.ui.text.font.FontFamily.Serif
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .aspectRatio(682f / 410f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = tapped),
    ) {
        val w = maxWidth
        val h = maxHeight
        // dark inside, then the frame over it
        Box(
            Modifier
                .padding(start = w * 0.085f, end = w * 0.085f, top = h * 0.29f, bottom = h * 0.12f)
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xD928170F)),
        )
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.mugi_reading_frame),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = w * 0.13f, end = w * 0.14f, top = h * 0.36f, bottom = h * 0.17f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bookH = h * 0.46f
            Box(Modifier.width(bookH * FenrirTomeRatio).height(bookH)) {
                FenrirTomeBook(
                    cover = cover,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .wrapContentSize(Alignment.Center, unbounded = true)
                        .height(bookH)
                        .fenrirTomeShadow()
                        .alpha(if (flying) 0f else 1f),
                    onFrameBounds = { bookRect = fenrirTomeCoverRect(it) },
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(manga.manga.title, color = ivory, fontFamily = serif, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(chapterLine, color = Color(0xFFCDBA92), fontFamily = serif, fontSize = 9.5.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color(0xFF3A2A18))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFF3C75F), Color(0xFFE2A43A)))))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${(progress * 100).toInt()}%", color = ivory, fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
        // the parchment title across the top of the frame
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = h * 0.2f)
                .width(w * 0.42f)
                .height(h * 0.13f),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.mugi_parchment),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Text("Continue Reading", color = Color(0xFF2E180A), fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}
