package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import uy.kohesive.injekt.api.get
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover

internal val FenrirRed = Color(0xFFFF4D57)
private val FenrirMuted = Color(0xFF9A9AA6)

/** Glass look shared by the Read side's bars and buttons (same as Watch). */
internal fun Modifier.fenrirGlass(shape: androidx.compose.ui.graphics.Shape): Modifier = this
    .clip(shape)
    .background(Color(0xEB26262E))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.55f to Color.Transparent))
    .border(1.dp, Color.White.copy(alpha = 0.28f), shape)

/**
 * F2: one glass bar for categories. Name of the open one, how many titles have new chapters,
 * page dots, and arrows. Swiping the books left/right also changes category (the pager).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FenrirCategoryBar(
    categories: List<Category>,
    pagerState: PagerState,
    newCountFor: (Category) -> Int,
    onGoTo: (Int) -> Unit,
    countFor: (Category) -> Int = { 0 },
) {
    val current = pagerState.currentPage.coerceIn(0, categories.lastIndex)
    val category = categories[current]
    val newCount = newCountFor(category)
    val look = FenrirLibraryLook.current.value
    if (look != FenrirLibraryLook.Look.Bookshelf) {
        FenrirPlainCategoryBar(categories, current, newCountFor, onGoTo, countFor, luxury = look == FenrirLibraryLook.Look.Luxury)
        return
    }
    // Fenrir: the bookcase's carved top rail, with a brass nameplate and brass knobs.
    // MugiVerse: no rail (the cabin shows through) and a parchment plate.
    val mugi = eu.kanade.tachiyomi.EmbeddedHost.isMugi
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (mugi) 70.dp else 78.dp)
            .then(if (mugi) Modifier else Modifier.fenrirRail())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FenrirBrassKnob(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous category", enabled = current > 0) { onGoTo(current - 1) }
        // Hold the bar: "Jump to" (every category with counts); Edit opens the Categories editor.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var jump by remember { mutableStateOf(false) }
        var manage by remember { mutableStateOf(false) }
        var startNew by remember { mutableStateOf(false) }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = { fenrirCategoryHintSeen(ctx); jump = true }, onLongClick = { fenrirCategoryHintSeen(ctx); jump = true }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (jump) {
                FenrirCategoryJump(
                    categories = categories,
                    current = current,
                    countFor = countFor,
                    newCountFor = newCountFor,
                    onPick = { jump = false; onGoTo(it) },
                    onEdit = { jump = false; startNew = false; manage = true },
                    onNew = { jump = false; startNew = true; manage = true },
                    onDismiss = { jump = false },
                )
            }
            if (manage) {
                FenrirCategoriesDialog(categories = categories, current = category, onDismiss = { manage = false }, startNew = startNew)
            }
            FenrirCategoryButton(
                categories = categories,
                current = current,
                newCountFor = newCountFor,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (categories.size <= 8) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        categories.indices.forEach { i ->
                            val w by animateDpAsState(if (i == current) 16.dp else 6.dp, label = "dot")
                            Box(
                                Modifier
                                    .size(width = w, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i == current) FenrirBrassLight else Color(0xFF2E1C10))
                                    .clickable { onGoTo(i) },
                            )
                        }
                    }
                } else {
                    Text("${current + 1} / ${categories.size}", color = FenrirBrassLight, fontSize = 10.5.sp)
                }
                val books = countFor(category)
                Spacer(Modifier.width(8.dp))
                Text(if (books == 1) "1 book" else "$books books", color = FenrirBrassLight, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (newCount > 0) {
                    Text(" · ", color = FenrirBrassLight, fontSize = 10.5.sp, maxLines = 1)
                    Text("$newCount new", color = Color(0xFFFF6B73), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        FenrirBrassKnob(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next category", enabled = current < categories.lastIndex) { onGoTo(current + 1) }
    }
}

private const val FENRIR_UI_PREFS = "fenrir_ui"
private const val CATEGORY_HINT_KEY = "category_button_hint_seen"
private val fenrirHintVisible = androidx.compose.runtime.mutableStateOf<Boolean?>(null)

private fun fenrirCategoryHintSeen(context: android.content.Context) {
    if (fenrirHintVisible.value == false) return
    fenrirHintVisible.value = false
    context.getSharedPreferences(FENRIR_UI_PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putBoolean(CATEGORY_HINT_KEY, true).apply()
}

/**
 * The category name as a button: grid icon, name, ▾. The name slides in from the side you're
 * going. The first time, a pulsing ring and "Tap here to see all categories" point at it.
 */
@Composable
private fun FenrirCategoryButton(
    categories: List<Category>,
    current: Int,
    newCountFor: (Category) -> Int,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (fenrirHintVisible.value == null) {
        fenrirHintVisible.value = !ctx.getSharedPreferences(FENRIR_UI_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(CATEGORY_HINT_KEY, false)
    }
    val showHint = fenrirHintVisible.value == true
    val pillShape = RoundedCornerShape(7.dp)
    Box(contentAlignment = Alignment.Center) {
        if (showHint) {
            // pulse three times, then stay still (an endless pulse costs battery)
            val ring = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                repeat(3) {
                    ring.snapTo(0f)
                    ring.animateTo(1f, androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                }
                ring.snapTo(0.35f)
            }
            val t = ring.value
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { scaleX = 1f + 0.18f * t; scaleY = 1f + 0.35f * t; alpha = 1f - t }
                    .border(2.dp, FenrirRed, pillShape),
            )
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.roundToPx() }),
                onDismissRequest = null,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { fenrirCategoryHintSeen(ctx) }) {
                    Box(
                        Modifier
                            .size(width = 12.dp, height = 6.dp)
                            .drawBehind {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
                                }
                                drawPath(path, FenrirRed)
                            },
                    )
                    Text(
                        "Tap here to see all categories",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(FenrirRed)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        val mugi = eu.kanade.tachiyomi.EmbeddedHost.isMugi
        val parchment = androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.mugi_parchment)
        Box(
            Modifier
                .widthIn(min = 176.dp, max = 250.dp)
                .height(if (mugi) 38.dp else 32.dp)
                .then(
                    if (mugi) {
                        Modifier.paint(parchment, contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                    } else {
                        Modifier.fenrirBrass(pillShape).drawBehind { brassScrews(6.dp) }
                    },
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.GridView,
                contentDescription = null,
                tint = FenrirBrassText.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterStart).size(14.dp),
            )
            // the name, engraved and centred on the plate
            androidx.compose.animation.AnimatedContent(
                targetState = current,
                modifier = Modifier.padding(horizontal = 22.dp),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    androidx.compose.animation.ContentTransform(
                        targetContentEnter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it * dir / 2 } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)),
                        initialContentExit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(180)) { -it * dir / 2 } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)),
                        sizeTransform = androidx.compose.animation.SizeTransform(clip = true),
                    )
                },
                contentAlignment = Alignment.Center,
                label = "categoryName",
            ) { index ->
                val c = categories[index.coerceIn(0, categories.lastIndex)]
                Text(
                    c.visualName,
                    style = androidx.compose.ui.text.TextStyle(
                        color = if (mugi) Color(0xFF2E180A) else FenrirBrassText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = if (mugi) androidx.compose.ui.text.font.FontFamily.Serif else null,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        shadow = androidx.compose.ui.graphics.Shadow(Color(0x99FFF0C0), androidx.compose.ui.geometry.Offset(0f, 2f), 0f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFFA02424),
                modifier = Modifier.align(Alignment.CenterEnd).size(18.dp),
            )
        }
    }
}

/** A wooden shelf the covers stand on: drawn under the cover, a little wider than the cell. */
internal fun Modifier.fenrirShelfLedge(coverPadding: androidx.compose.ui.unit.Dp, ratio: Float): Modifier = drawBehind {
    val pad = coverPadding.toPx()
    val coverH = (size.width - pad * 2f) / ratio
    val top = pad + coverH - 2.dp.toPx()
    val h = 7.dp.toPx()
    val reach = 3.dp.toPx()
    // soft shadow under the shelf
    drawRect(
        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent, startY = top + h, endY = top + h + 6.dp.toPx()),
        topLeft = androidx.compose.ui.geometry.Offset(-reach, top + h),
        size = androidx.compose.ui.geometry.Size(size.width + reach * 2f, 6.dp.toPx()),
    )
    drawRect(
        Brush.verticalGradient(0f to Color(0xFFA46C40), 0.25f to Color(0xFF8A5A34), 1f to Color(0xFF5A3B26), startY = top, endY = top + h),
        topLeft = androidx.compose.ui.geometry.Offset(-reach, top),
        size = androidx.compose.ui.geometry.Size(size.width + reach * 2f, h),
    )
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color(0xFFE4E4EA) else Color(0xFF4A4A55), modifier = Modifier.size(24.dp))
    }
}

/** "Continue reading": the book read most recently, with progress and an open-book button. */
@Composable
internal fun FenrirContinueReadingCard(
    manga: LibraryManga,
    onOpen: () -> Unit,
    onClickCover: () -> Unit,
) {
    val total = manga.totalChapters.coerceAtLeast(0)
    val read = manga.readCount.coerceIn(0, total.coerceAtLeast(0))
    val progress = if (total > 0) read.toFloat() / total else 0f
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xFF1A1418))
            .border(1.dp, Color(0xFF55403F), shape)
            .clickable(onClick = onOpen),
    ) {
        // the book's own cover, blurred, as the card's background
        MangaCover.Book(
            data = manga.manga.asMangaCover(),
            modifier = Modifier.matchParentSize()
                .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(26.dp) else Modifier)
                .graphicsLayer { alpha = 0.85f },
        )
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Color(0x8C0A0A0E), 1f to Color(0xB30A0A0E))))
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MangaCover.Book(
                modifier = Modifier.width(80.dp).clip(RoundedCornerShape(10.dp)),
                data = manga.manga.asMangaCover(),
                onClick = onClickCover,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("CONTINUE READING", color = Color(0xFFFFAAB0), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                Spacer(Modifier.height(3.dp))
                Text(manga.manga.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (total > 0) "Chapter ${(read + 1).coerceAtMost(total)} · ${(progress * 100).toInt()}% read" else "Started",
                    color = Color(0xFFC8C8D2), fontSize = 12.sp, maxLines = 1,
                )
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(FenrirRed))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(FenrirRed).clickable(onClick = onOpen).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Continue", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Details", color = Color(0xFFDCDCE4), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClickCover).padding(4.dp),
                    )
                }
            }
        }
    }
}


/**
 * The top bar that follows your finger: it slides away as you scroll down and comes back as soon
 * as you scroll up, anywhere in the list. It moves with the scroll (not on a timer), so the list
 * never jumps under your finger.
 */
class FenrirFollowBar {
    private var heightPx by androidx.compose.runtime.mutableIntStateOf(0)
    private var offsetPx by androidx.compose.runtime.mutableFloatStateOf(0f)

    fun show() { offsetPx = 0f }

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            if (heightPx == 0 || available.y == 0f) return androidx.compose.ui.geometry.Offset.Zero
            val old = offsetPx
            offsetPx = (offsetPx + available.y).coerceIn(-heightPx.toFloat(), 0f)
            return androidx.compose.ui.geometry.Offset(0f, offsetPx - old)
        }
    }

    val modifier: Modifier = Modifier
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
            heightPx = placeable.height
            val visible = (placeable.height + offsetPx.toInt()).coerceIn(0, placeable.height)
            layout(placeable.width, visible) {
                placeable.place(0, offsetPx.toInt().coerceAtLeast(-placeable.height))
            }
        }
}

/**
 * A header that slides up out of the way as the list below scrolls up, and slides back
 * once the list is scrolled back to its top (like a collapsing toolbar).
 */
/**
 * Quick-return header: moves up exactly as far as the books scroll (until it's out of sight) and
 * comes back as soon as you scroll up. Never consumes scrolling, never changes the list's size.
 */
/**
 * The library's top part (glass case + category rail) moves exactly with the books, like part of
 * the bookcase: each grid starts with an empty spot as tall as the top part, and the top part is
 * shifted by however far that spot has scrolled. No resizing, no popping back mid-list.
 */
internal class FenrirScrollingHeader {
    var heightPx by androidx.compose.runtime.mutableIntStateOf(0)
    val grids = androidx.compose.runtime.mutableStateMapOf<Int, androidx.compose.foundation.lazy.grid.LazyGridState>()

    fun offsetFor(page: Int): Float {
        val st = grids[page] ?: return 0f
        return if (st.firstVisibleItemIndex == 0) -minOf(st.firstVisibleItemScrollOffset, heightPx).toFloat() else -heightPx.toFloat()
    }
}

internal val LocalFenrirHeader = androidx.compose.runtime.staticCompositionLocalOf<FenrirScrollingHeader?> { null }
internal val LocalFenrirPage = androidx.compose.runtime.staticCompositionLocalOf { 0 }

internal class FenrirQuickReturnHeader {
    var heightPx by androidx.compose.runtime.mutableIntStateOf(0)
    var offsetPx by androidx.compose.runtime.mutableFloatStateOf(0f)

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(
            consumed: androidx.compose.ui.geometry.Offset,
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            if (heightPx > 0 && consumed.y != 0f) {
                offsetPx = (offsetPx + consumed.y).coerceIn(-heightPx.toFloat(), 0f)
            }
            return androidx.compose.ui.geometry.Offset.Zero
        }
    }

    suspend fun show() {
        androidx.compose.animation.core.animate(offsetPx, 0f, animationSpec = androidx.compose.animation.core.tween(250)) { v, _ -> offsetPx = v }
    }
}

internal class FenrirCollapsingHeader {
    private var heightPx by androidx.compose.runtime.mutableIntStateOf(0)
    private var offsetPx by androidx.compose.runtime.mutableFloatStateOf(0f)

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            if (heightPx == 0 || available.y == 0f) return androidx.compose.ui.geometry.Offset.Zero
            // Pulling down: bring the categories back first, before the list scrolls or the
            // library refreshes (so a short list never gets stuck with them hidden).
            if (available.y > 0f) {
                if (offsetPx >= 0f) return androidx.compose.ui.geometry.Offset.Zero
                val old = offsetPx
                offsetPx = (offsetPx + available.y).coerceIn(-heightPx.toFloat(), 0f)
                return androidx.compose.ui.geometry.Offset(0f, offsetPx - old)
            }
            // Scrolling the books up: hide the header first.
            val old = offsetPx
            offsetPx = (offsetPx + available.y).coerceIn(-heightPx.toFloat(), 0f)
            return androidx.compose.ui.geometry.Offset(0f, offsetPx - old)
        }

        override fun onPostScroll(
            consumed: androidx.compose.ui.geometry.Offset,
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            // Back at the top and still pulling down: bring the header back.
            if (available.y <= 0f || heightPx == 0) return androidx.compose.ui.geometry.Offset.Zero
            val old = offsetPx
            offsetPx = (offsetPx + available.y).coerceIn(-heightPx.toFloat(), 0f)
            return androidx.compose.ui.geometry.Offset(0f, offsetPx - old)
        }
    }

    val modifier: Modifier = Modifier
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
            heightPx = placeable.height
            val visible = (placeable.height + offsetPx.toInt()).coerceIn(0, placeable.height)
            layout(placeable.width, visible) {
                placeable.place(0, offsetPx.toInt().coerceAtLeast(-placeable.height))
            }
        }
}

/**
 * Library cover like the preview: rounded cover, red unread badge, reading progress line,
 * title under the cover and "18% read" / "Done" / "New" under the title.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FenrirLibraryGridItem(
    item: eu.kanade.tachiyomi.ui.library.LibraryItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    titleMaxLines: Int = 2,
    flyOnClick: Boolean = false,
    index: Int = -1,
) {
    when (FenrirLibraryLook.current.value) {
        FenrirLibraryLook.Look.Luxury -> {
            LuxGridItem(item, index, isSelected, onClick, onLongClick, flyOnClick)
            return
        }
        FenrirLibraryLook.Look.Regular -> {
            FenrirRegularGridItem(item, isSelected, onClick, onLongClick)
            return
        }
        else -> Unit
    }
    val lm = item.libraryManga
    val manga = lm.manga
    val total = lm.totalChapters
    val read = lm.readCount.coerceAtMost(total.coerceAtLeast(0))
    val progress = if (total > 0) read.toFloat() / total else 0f
    val status = when {
        total > 0 && read >= total -> "Done"
        read <= 0 -> "New"
        else -> "${(progress * 100).toInt().coerceIn(1, 99)}% read"
    }
    val shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    val bookRect = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val coverData = remember(manga.id, manga.coverLastModified, manga.thumbnailUrl) {
        tachiyomi.domain.manga.model.MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        )
    }
    Column(
        Modifier
            .fenrirPlank(5.dp, FenrirTomePlankRatio, 0.dp, bookShadow = false)
            .padding(5.dp)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = {
                    if (flyOnClick) {
                        // Fenrir: the book lifts off the shelf and turns round to its back cover,
                        // then its page opens.
                        FenrirBookFlight.start(manga.id, coverData, bookRect.value, onClick, turn = true, title = manga.title)
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongClick,
            ),
    ) {
        // Fenrir: the book as an ornate tome standing on the shelf; the cover follows its angle.
        FenrirTomeBook(
            cover = coverData,
            modifier = Modifier
                .fillMaxWidth()
                .fenrirTomeShadow()
                .alpha(if (FenrirBookFlight.isFlying(manga.id)) 0f else if (isSelected) 0.8f else 1f),
            onFrameBounds = { bookRect.value = it },
            badges = {
                // Fenrir: unread count as a small tag on the top corner of the book (flat, not bent
                // with the cover, and off the art)
                val unread = item.badges.unreadCount
                if (unread > 0) {
                    Text(
                        if (unread > 999) "999+" else "$unread",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FenrirRed)
                            .border(1.dp, Color(0xFFF0CD7A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (item.badges.downloadCount > 0) {
                    Text(
                        "↓${item.badges.downloadCount}",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 2.dp, start = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE61C1C22))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            },
        ) {
            if (isSelected) {
                Box(Modifier.fillMaxSize().border(4.dp, FenrirRed))
            }
        }
        Text(
            manga.title,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
            maxLines = titleMaxLines.coerceIn(1, 2),
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            // Fenrir: centred under the book
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 2.dp),
        )
        // Fenrir: "98% read" with the ★ rating beside it (off the cover, so it doesn't hide the art).
        Row(
            Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                status,
                color = if (status == "New") Color(0xFFFF6B73) else Color(0xFFC9B49A),
                fontSize = 11.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            FenrirRatingBadge(title = manga.title, novel = manga.isNovel)
        }
    }
}

private fun Modifier.androidxShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.shadow(6.dp, shape))


private var fenrirAutoChecked = false

/** Once per app start, when the Read library first shows: quietly look for a new app version. */
internal fun fenrirAutoCheckAppUpdate(context: android.content.Context) {
    if (fenrirAutoChecked) return
    fenrirAutoChecked = true
    fenrirCheckAppUpdate(context)
}

/** Pull to refresh on the Read side: ask the host app for a new version (a pill shows if found). */
internal fun fenrirCheckAppUpdate(@Suppress("UNUSED_PARAMETER") context: android.content.Context) {
    eu.kanade.tachiyomi.EmbeddedHost.checkAppUpdate?.invoke { }
}

/** Small glass pill at the top: "Update available · r72  [Update] ×", fills red while downloading. */
@Composable
fun FenrirUpdatePill(modifier: Modifier = Modifier) {
    val info by eu.kanade.tachiyomi.EmbeddedHost.appUpdate.collectAsState()
    androidx.compose.animation.AnimatedVisibility(
        visible = info != null,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
    ) {
        val u = info ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(24.dp)
        val progress = if (u.downloading) (u.progress ?: 0f).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .padding(top = 4.dp, bottom = 4.dp, start = 16.dp, end = 16.dp)
                .height(46.dp)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(Color(0xF21C1C22))
                .drawBehind {
                    if (progress > 0f) {
                        drawRect(FenrirRed.copy(alpha = 0.35f), size = size.copy(width = size.width * progress))
                    }
                }
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    when {
                        u.error != null -> "Update failed"
                        u.downloading && u.progress != null -> "Downloading ${(u.progress * 100).toInt().coerceIn(0, 100)}%"
                        u.downloading -> "Starting download…"
                        u.ready -> "Update ready"
                        else -> "Update available"
                    },
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(u.error ?: u.version, color = FenrirMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            if (!u.downloading) {
                Text(
                    when {
                        u.ready -> "Install"
                        u.error != null -> "Retry"
                        else -> "Update"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FenrirRed)
                        .clickable { eu.kanade.tachiyomi.EmbeddedHost.startAppUpdate?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { eu.kanade.tachiyomi.EmbeddedHost.dismissAppUpdate?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = Color(0xFFC9C9D2), fontSize = 14.sp)
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
        }
        }
    }
}


/**
 * Round button beside the Read pill when a new Fenrir Read version is out: a red dot, a progress
 * ring while downloading, and a small menu (Update / Not now). Returns true when it's shown.
 */
@Composable
fun FenrirUpdateButton(): Boolean {
    val info by eu.kanade.tachiyomi.EmbeddedHost.appUpdate.collectAsState()
    val u = info ?: return false
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(42.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xF22A2A32))
                .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                .clickable {
                    if (u.ready) eu.kanade.tachiyomi.EmbeddedHost.startAppUpdate?.invoke() else menu = true
                },
            contentAlignment = Alignment.Center,
        ) {
            if (u.downloading) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { (u.progress ?: 0f).coerceIn(0f, 1f) },
                    color = FenrirRed,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(34.dp),
                )
            }
            androidx.compose.material3.Icon(
                imageVector = if (u.ready) androidx.compose.material.icons.Icons.Outlined.Check else androidx.compose.material.icons.Icons.Outlined.Download,
                contentDescription = "Update available",
                tint = FenrirRed,
                modifier = Modifier.size(20.dp),
            )
            if (!u.downloading) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(FenrirRed),
                )
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                when {
                    u.error != null -> "Update failed"
                    u.downloading && u.progress != null -> "Downloading ${(u.progress * 100).toInt().coerceIn(0, 100)}%"
                    u.downloading -> "Starting download…"
                    else -> "New version"
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Text(
                u.error ?: u.version,
                color = FenrirMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
            if (!u.downloading) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(if (u.error != null) "Try again" else "Update now", color = FenrirRed, fontWeight = FontWeight.SemiBold) },
                    onClick = { menu = false; eu.kanade.tachiyomi.EmbeddedHost.startAppUpdate?.invoke() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Not now") },
                    onClick = { menu = false; eu.kanade.tachiyomi.EmbeddedHost.dismissAppUpdate?.invoke() },
                )
            }
        }
    }
    return true
}

/**
 * Small chip at the top of the Library when a source some of your books use stopped working:
 * "● 1 source down · 4 books ›". Tap for the books and Move them (opens Migrate for that source).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FenrirSourceDownChip(books: List<LibraryManga>, onOpenBook: (Long) -> Unit) {
    val off by eu.kanade.presentation.browse.FenrirReadHealth.turnedOff.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { eu.kanade.presentation.browse.FenrirReadHealth.checkIfDue() }
    val offIds = off.map { it.id }.toSet()
    val hit = books.filter { it.manga.source in offIds }.distinctBy { it.manga.id }
    if (hit.isEmpty()) return
    val bySource = hit.groupBy { it.manga.source }
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current
    Row(
        Modifier
            .padding(start = 16.dp, top = 2.dp, bottom = 6.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(FenrirRed.copy(alpha = 0.14f))
            .border(1.dp, FenrirRed.copy(alpha = 0.4f), CircleShape)
            .clickable { open = true }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(FenrirRed))
        Spacer(Modifier.width(6.dp))
        Text(
            "${bySource.size} source${if (bySource.size > 1) "s" else ""} down · ${hit.size} book${if (hit.size > 1) "s" else ""} ›",
            color = Color(0xFFFF8A91),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    if (open) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { open = false },
            containerColor = Color(0xFF15151B),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                bySource.forEach { (sourceId, list) ->
                    val name = off.firstOrNull { it.id == sourceId }?.name ?: "A source"
                    val reason = off.firstOrNull { it.id == sourceId }?.reason ?: "Not loading"
                    Text("$name stopped working", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("$reason · ${list.size} of your books use it", color = FenrirMuted, fontSize = 12.5.sp)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 12.dp),
                    ) {
                        items(list.size) { i ->
                            val m = list[i]
                            Column(Modifier.width(76.dp).clickable { open = false; onOpenBook(m.manga.id) }) {
                                MangaCover.Book(
                                    data = m.manga.asMangaCover(),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                )
                                Text(m.manga.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            "Move them",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(FenrirRed)
                                .clickable {
                                    open = false
                                    navigator?.push(eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen(sourceId))
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                        Text(
                            "Wait for it",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF26262E))
                                .clickable { open = false }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
                Text("Sources that come back are turned on again by themselves.", color = FenrirMuted, fontSize = 12.sp)
            }
        }
    }
}


/** "Jump to": every category with its book count and new-chapter count; tap one to go there. */
@Composable
private fun FenrirCategoryJump(
    categories: List<Category>,
    current: Int,
    countFor: (Category) -> Int,
    newCountFor: (Category) -> Int,
    onPick: (Int) -> Unit,
    onEdit: () -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF15151B))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Text("Jump to", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(categories.size) { i ->
                    val c = categories[i]
                    val on = i == current
                    val count = remember(c.id) { runCatching { countFor(c) }.getOrDefault(0) }
                    val fresh = remember(c.id) { runCatching { newCountFor(c) }.getOrDefault(0) }
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) FenrirRed.copy(alpha = 0.16f) else Color(0xFF1C1C23))
                            .border(1.dp, if (on) FenrirRed else Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .clickable { onPick(i) }
                            .padding(12.dp),
                    ) {
                        Text(c.visualName, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 2.dp)) {
                            Text("$count book${if (count == 1) "" else "s"}", color = FenrirMuted, fontSize = 11.5.sp)
                            if (fresh > 0) Text(" · ● $fresh new", color = FenrirRed, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "✎  Edit categories",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(CircleShape).background(Color(0xFF26262E)).clickable(onClick = onEdit).padding(vertical = 11.dp),
                )
                Text(
                    "＋  New",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(CircleShape).background(Color(0xFF26262E)).clickable(onClick = onNew).padding(vertical = 11.dp),
                )
            }
        }
    }
}

/** The Categories pop-up: drag ≡ to reorder, ✎ rename, 🗑 delete, ＋ new. Saves as you go. */
@Composable
private fun FenrirCategoriesDialog(categories: List<Category>, current: Category, onDismiss: () -> Unit, startNew: Boolean = false) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val list = remember { androidx.compose.runtime.mutableStateListOf<Category>() }
    // Follow the library's list (renames/new/deleted) unless a drag is in progress.
    var dragId by remember { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(categories) {
        if (dragId == null) {
            list.clear()
            list.addAll(categories.filterNot { it.isSystemCategory })
        }
    }
    var dragY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var rowPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var editing by remember { mutableStateOf<Long?>(if (startNew) -1L else null) } // category id, or -1 for "new"
    var deleting by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    val type = current.contentType.takeIf { !current.isSystemCategory } ?: Category.CONTENT_TYPE_ALL

    fun saveOrder(moved: Category) {
        val at = list.indexOfFirst { it.id == moved.id }
        val prev = list.getOrNull(at - 1)
        val next = list.getOrNull(at + 1)
        scope.launch {
            runCatching {
                val all = uy.kohesive.injekt.Injekt.get<tachiyomi.domain.category.interactor.GetCategories>().await()
                    .filterNot { it.isSystemCategory }.sortedBy { it.order }
                val rest = all.filterNot { it.id == moved.id }
                val newIndex = when {
                    prev != null -> rest.indexOfFirst { it.id == prev.id } + 1
                    next != null -> rest.indexOfFirst { it.id == next.id }
                    else -> return@runCatching
                }.coerceIn(0, rest.size)
                uy.kohesive.injekt.Injekt.get<tachiyomi.domain.category.interactor.ReorderCategory>().await(moved, newIndex)
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF15151B))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Categories", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("Drag ≡ to reorder", color = FenrirMuted, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp), userScrollEnabled = dragId == null) {
                items(list.size, key = { list[it].id }) { i ->
                    val c = list[i]
                    val dragging = dragId == c.id
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragY else 0f; scaleX = if (dragging) 1.03f else 1f; scaleY = scaleX }
                            .onSizeChanged { rowPx = it.height.toFloat().coerceAtLeast(1f) }
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (dragging) Color(0xFF2A2A34) else Color(0xFF1C1C23))
                            .border(1.dp, if (c.id == current.id) FenrirRed else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        when {
                            editing == c.id -> InlineName(name, { name = it }, "Rename", categories, c.id,
                                onSave = { n ->
                                    editing = null
                                    scope.launch { runCatching { uy.kohesive.injekt.Injekt.get<tachiyomi.domain.category.interactor.RenameCategory>().await(c, n) } }
                                },
                                onCancel = { editing = null },
                            )
                            deleting == c.id -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Delete “${c.visualName}”? Books stay in your library.", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("Cancel", color = FenrirMuted, fontSize = 13.sp, modifier = Modifier.clickable { deleting = null }.padding(8.dp))
                                Text("Delete", color = FenrirRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                    deleting = null
                                    scope.launch { runCatching { uy.kohesive.injekt.Injekt.get<tachiyomi.domain.category.interactor.DeleteCategory>().await(c.id) } }
                                }.padding(8.dp))
                            }
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "≡",
                                    color = if (dragging) Color.White else FenrirMuted,
                                    fontSize = 20.sp,
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 40.dp)
                                        .pointerInput(c.id) {
                                            detectDragGestures(
                                                onDragStart = { dragId = c.id; dragY = 0f },
                                                onDragEnd = {
                                                    val moved = list.firstOrNull { it.id == dragId }
                                                    dragId = null; dragY = 0f
                                                    moved?.let(::saveOrder)
                                                },
                                                onDragCancel = { dragId = null; dragY = 0f },
                                            ) { change, amount ->
                                                change.consume()
                                                dragY += amount.y
                                                val at = list.indexOfFirst { it.id == dragId }
                                                if (at < 0) return@detectDragGestures
                                                if (dragY > rowPx / 2 && at < list.lastIndex) {
                                                    list.add(at + 1, list.removeAt(at)); dragY -= rowPx
                                                } else if (dragY < -rowPx / 2 && at > 0) {
                                                    list.add(at - 1, list.removeAt(at)); dragY += rowPx
                                                }
                                            }
                                        }
                                        .wrapContentSize(Alignment.Center),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Text(c.visualName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("✎", color = Color(0xFFC9C9D2), fontSize = 16.sp,
                                    modifier = Modifier.clip(CircleShape).clickable { name = c.name; editing = c.id; deleting = null }.padding(8.dp))
                                Text("🗑", fontSize = 15.sp,
                                    modifier = Modifier.clip(CircleShape).clickable { deleting = c.id; editing = null }.padding(8.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (editing == -1L) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C23)).padding(10.dp)) {
                    InlineName(name, { name = it }, "New category", categories, -1L,
                        onSave = { n ->
                            editing = null
                            scope.launch { runCatching { uy.kohesive.injekt.Injekt.get<tachiyomi.domain.category.interactor.CreateCategoryWithName>().await(n, type) } }
                        },
                        onCancel = { editing = null },
                    )
                }
            } else {
                Text(
                    "＋  New category",
                    color = Color(0xFFC9C9D2),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clickable { name = ""; editing = -1L; deleting = null }.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                Text("Done", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(CircleShape).background(FenrirRed).clickable(onClick = onDismiss).padding(horizontal = 24.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun InlineName(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    categories: List<Category>,
    selfId: Long,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val taken = categories.any { it.name.equals(value.trim(), true) && it.id != selfId }
    Text(label, color = FenrirMuted, fontSize = 11.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onValue, singleLine = true, modifier = Modifier.weight(1f))
        Text("Cancel", color = FenrirMuted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
        Text(
            "Save",
            color = if (value.isNotBlank() && !taken) FenrirRed else FenrirMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(enabled = value.isNotBlank() && !taken) { onSave(value.trim()) }.padding(8.dp),
        )
    }
    if (taken) Text("That name is already used", color = FenrirRed, fontSize = 11.5.sp)
}

/**
 * New-chapter alerts: automatic checks start off (and the Read side never asked for notification
 * permission), so nothing ever notified. This card turns both on in one tap; shows until set up
 * or dismissed.
 */
@Composable
internal fun FenrirAlertsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val libPrefs = remember { uy.kohesive.injekt.Injekt.get<tachiyomi.domain.library.service.LibraryPreferences>() }
    val prefs = remember { context.getSharedPreferences("fenrir_vault", android.content.Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean("alerts_card_dismissed", false)) }
    fun allowed() = android.os.Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var interval by remember { mutableStateOf(libPrefs.autoUpdateInterval.get()) }
    var granted by remember { mutableStateOf(allowed()) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted = it || allowed() }
    if (dismissed || (interval > 0 && granted)) return
    var pick by remember { mutableStateOf(12) }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .fenrirGlass(RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔔", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Get told about new chapters", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (interval > 0) "Checks are on, but notifications aren't allowed yet" else "Fenrir Vault isn't checking your library for new chapters yet",
                    color = FenrirMuted,
                    fontSize = 12.sp,
                )
            }
        }
        if (interval <= 0) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6 to "Every 6 h", 12 to "Every 12 h", 24 to "Daily").forEach { (h, label) ->
                    val on = pick == h
                    Text(
                        label,
                        color = if (on) Color.White else Color(0xFFC9C9D2),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(if (on) FenrirRed else Color(0xFF26262E)).clickable { pick = h }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Not now",
                color = FenrirMuted,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    prefs.edit().putBoolean("alerts_card_dismissed", true).apply()
                    dismissed = true
                }.padding(8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Turn on",
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(FenrirRed).clickable {
                    if (interval <= 0) {
                        runCatching {
                            libPrefs.autoUpdateInterval.set(pick)
                            eu.kanade.tachiyomi.data.library.LibraryUpdateJob.setupTask(context, pick)
                        }
                        interval = pick
                    }
                    if (!allowed() && android.os.Build.VERSION.SDK_INT >= 33) {
                        ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        granted = true
                    }
                }.padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
        Text(
            "Change it any time in Settings → Library → Automatic updates.",
            color = Color(0xFF6E6E78),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}


/** Fenrir: the category band for the Luxury and Regular looks (same jump / edit dialogs). */
@Composable
private fun FenrirPlainCategoryBar(
    categories: List<Category>,
    current: Int,
    newCountFor: (Category) -> Int,
    onGoTo: (Int) -> Unit,
    countFor: (Category) -> Int,
    luxury: Boolean,
) {
    val category = categories[current]
    var jump by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var startNew by remember { mutableStateOf(false) }
    if (jump) {
        FenrirCategoryJump(
            categories = categories, current = current, countFor = countFor, newCountFor = newCountFor,
            onPick = { jump = false; onGoTo(it) },
            onEdit = { jump = false; startNew = false; manage = true },
            onNew = { jump = false; startNew = true; manage = true },
            onDismiss = { jump = false },
        )
    }
    if (manage) FenrirCategoriesDialog(categories = categories, current = category, onDismiss = { manage = false }, startNew = startNew)
    val name: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = Color(0xFFE2343F),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(category.visualName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
    if (luxury) {
        LuxCategoryBand(
            onPrev = { if (current > 0) onGoTo(current - 1) },
            onNext = { if (current < categories.lastIndex) onGoTo(current + 1) },
            onName = { jump = true },
            name = name,
        )
    } else {
        // Fenrir (Regular): a row of pill tabs, one per category, with how many books each has.
        // Tap the selected one again for the category menu (jump, edit, new).
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(current) { listState.animateScrollToItem(current.coerceAtLeast(0)) }
        Column(Modifier.fillMaxWidth()) {
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories.size) { i ->
                    val c = categories[i]
                    val sel = i == current
                    Row(
                        Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (sel) Color.White else Color(0xFF1A1A21))
                            .then(if (sel) Modifier else Modifier.border(1.dp, Color(0xFF373742), RoundedCornerShape(17.dp)))
                            .clickable { if (sel) jump = true else onGoTo(i) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.visualName, color = if (sel) Color(0xFF111116) else Color(0xFFE2E2E8), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Text("${countFor(c)}", color = if (sel) Color(0xFF7A7A86) else Color(0xFF8A8A98), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        if (sel) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Category menu", tint = Color(0xFF111116), modifier = Modifier.padding(start = 2.dp).size(16.dp))
                        }
                    }
                }
            }
            // "24 books" and the sort / filter button
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val n = countFor(category)
                Text(if (n == 1) "1 book" else "$n books", color = Color(0xFF8C8C98), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { eu.kanade.presentation.components.FenrirReadMenu.current.value?.filter?.invoke() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sort & filter", color = Color(0xFFDCDCE4), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFDCDCE4), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}


/** Fenrir: the Regular look: a clean cover card with type, unread count and progress. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FenrirRegularGridItem(
    item: eu.kanade.tachiyomi.ui.library.LibraryItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val manga = item.libraryManga.manga
    val cover = remember(manga.id, manga.coverLastModified, manga.thumbnailUrl) {
        tachiyomi.domain.manga.model.MangaCover(mangaId = manga.id, sourceId = manga.source, isMangaFavorite = manga.favorite, url = manga.thumbnailUrl, lastModified = manga.coverLastModified)
    }
    val total = item.libraryManga.totalChapters
    val read = item.libraryManga.readCount.coerceAtMost(total.coerceAtLeast(item.libraryManga.readCount))
    val done = total > 0 && read >= total
    val progress = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Color(0xFF1A1A21)),
        ) {
            eu.kanade.presentation.manga.components.MangaCover.Book(
                data = cover,
                modifier = Modifier.matchParentSize(),
            )
            // darker bottom so the progress line reads on any cover
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
            Text(
                if (manga.isNovel) "NOVEL" else "MANGA",
                color = Color(0xFFE4E4EA), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                    .clip(RoundedCornerShape(7.dp)).background(Color(0xE6141419)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            val unread = item.badges.unreadCount
            if (unread > 0) {
                Text(
                    if (unread > 999) "999+" else "$unread", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                        .clip(RoundedCornerShape(9.dp)).background(FenrirRed).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            if (progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 7.dp)
                        .fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(if (done) Color(0xFF78DC8C) else FenrirRed))
                }
            }
            if (isSelected) {
                Box(Modifier.matchParentSize().background(FenrirRed.copy(alpha = 0.25f)).border(3.dp, FenrirRed, shape))
            }
        }
        Text(
            manga.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            when {
                done -> "✓ Finished"
                total > 0 && read > 0 -> "Ch $read / $total"
                total > 0 -> "$total chapters · New"
                else -> "New"
            },
            color = if (done) Color(0xFF78DC8C) else Color(0xFF8C8C98), fontSize = 11.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, modifier = Modifier.padding(start = 2.dp, top = 1.dp),
        )
    }
}
