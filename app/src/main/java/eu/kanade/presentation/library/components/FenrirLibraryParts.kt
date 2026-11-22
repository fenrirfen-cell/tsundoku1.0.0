package eu.kanade.presentation.library.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
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
@Composable
internal fun FenrirCategoryBar(
    categories: List<Category>,
    pagerState: PagerState,
    newCountFor: (Category) -> Int,
    onGoTo: (Int) -> Unit,
) {
    val current = pagerState.currentPage.coerceIn(0, categories.lastIndex)
    val category = categories[current]
    val newCount = newCountFor(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(50.dp)
            .fenrirGlass(RoundedCornerShape(25.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous category", enabled = current > 0) { onGoTo(current - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.visualName,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (newCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("● $newCount new", color = FenrirRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (categories.size <= 8) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    categories.indices.forEach { i ->
                        val w by animateDpAsState(if (i == current) 16.dp else 6.dp, label = "dot")
                        Box(
                            Modifier
                                .size(width = w, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (i == current) FenrirRed else Color(0xFF44444F))
                                .clickable { onGoTo(i) },
                        )
                    }
                }
            } else {
                Text("${current + 1} / ${categories.size}", color = FenrirMuted, fontSize = 10.5.sp)
            }
        }
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next category", enabled = current < categories.lastIndex) { onGoTo(current + 1) }
    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(0f to FenrirRed.copy(alpha = 0.18f), 0.55f to Color(0xF21C1C22)))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            modifier = Modifier.height(72.dp),
            data = manga.manga.asMangaCover(),
            onClick = onClickCover,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CONTINUE READING", color = FenrirMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            Text(manga.manga.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (total > 0) "$read of $total chapters read" else "Started",
                color = Color(0xFFB8B8C2),
                fontSize = 12.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.9f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(FenrirRed))
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(FenrirRed).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Continue reading", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}


/**
 * A header that slides up out of the way as the list below scrolls up, and slides back
 * once the list is scrolled back to its top (like a collapsing toolbar).
 */
internal class FenrirCollapsingHeader {
    private var heightPx by androidx.compose.runtime.mutableIntStateOf(0)
    private var offsetPx by androidx.compose.runtime.mutableFloatStateOf(0f)

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            // Scrolling the books up: hide the header first.
            if (available.y >= 0f || heightPx == 0) return androidx.compose.ui.geometry.Offset.Zero
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
) {
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
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(MangaCover.Book.ratio)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, FenrirRed, shape)
                    } else {
                        Modifier
                    },
                )
                .androidxShadow(shape)
                .clip(shape),
        ) {
            MangaCover.Book(
                modifier = Modifier.fillMaxWidth().then(if (isSelected) Modifier.alpha(0.76f) else Modifier),
                data = tachiyomi.domain.manga.model.MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
            )
            // Fenrir: ★ rating badge (tap for why); nothing shows without a sure match.
            FenrirRatingBadge(
                title = manga.title,
                novel = manga.isNovel,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = 14.dp),
            )
            val unread = item.badges.unreadCount
            if (unread > 0) {
                Text(
                    "$unread",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(FenrirRed)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            if (item.badges.downloadCount > 0) {
                Text(
                    "↓${item.badges.downloadCount}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xCC1C1C22))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (total > 0 && read > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(FenrirRed))
                }
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
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 7.dp),
        )
        Text(
            status,
            color = if (status == "New") FenrirRed else FenrirMuted,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp, top = 1.dp, bottom = 4.dp),
        )
    }
}

private fun Modifier.androidxShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.shadow(6.dp, shape))


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
