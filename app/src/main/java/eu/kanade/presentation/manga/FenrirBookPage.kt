package eu.kanade.presentation.manga

import android.content.Context
import androidx.compose.material.icons.outlined.RateReview
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val Red = Color(0xFFFF4D57)
private val Muted = Color(0xFF9A9AA6)
private val Card = Color(0xFF17171D)
private val Rim = Color(0xFF2A2A34)

enum class FenrirBookTab { Chapters, Pages, Bookmarks, Reviews }

/** Page bookmarks (chapter + page + optional note), kept on the phone per book. */
object FenrirPageBookmarks {
    data class Mark(val chapterId: Long, val page: Int, val at: Long, val note: String)

    val version = MutableStateFlow(0)
    private val prefs by lazy {
        Injekt.get<android.app.Application>().getSharedPreferences("fenrir_page_bookmarks", Context.MODE_PRIVATE)
    }

    fun list(mangaId: Long): List<Mark> =
        prefs.getStringSet("m_$mangaId", emptySet()).orEmpty().mapNotNull { line ->
            val p = line.split('|', limit = 4)
            val ch = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            Mark(ch, p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2)?.toLongOrNull() ?: 0L, p.getOrElse(3) { "" })
        }.sortedByDescending { it.at }

    fun has(mangaId: Long, chapterId: Long, page: Int) = list(mangaId).any { it.chapterId == chapterId && it.page == page }

    fun toggle(mangaId: Long, chapterId: Long, page: Int) {
        val now = list(mangaId)
        val next = if (now.any { it.chapterId == chapterId && it.page == page }) {
            now.filterNot { it.chapterId == chapterId && it.page == page }
        } else {
            now + Mark(chapterId, page, System.currentTimeMillis(), "")
        }
        prefs.edit().putStringSet("m_$mangaId", next.map { "${it.chapterId}|${it.page}|${it.at}|${it.note}" }.toSet()).apply()
        version.value++
    }
}

/** The ♡ Library chip under the title (red ♥ In library once added). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FenrirLibraryChip(favorite: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(start = 16.dp, top = 10.dp)
            .height(38.dp)
            .clip(CircleShape)
            .background(if (favorite) Red.copy(alpha = 0.18f) else Color(0xFF1C1C23))
            .border(1.dp, if (favorite) Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.16f), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = if (favorite) Color(0xFFFF8A91) else Color(0xFFE4E4EA),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (favorite) "In library" else "Library",
            color = if (favorite) Color(0xFFFF8A91) else Color(0xFFE4E4EA),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun minutesPerChapter(isNovel: Boolean) = if (isNovel) 12.0 else 3.3

private fun formatTime(minutes: Double): String {
    val m = minutes.toInt()
    return when {
        m < 60 -> "$m min"
        m < 60 * 24 -> "${m / 60} h ${m % 60} m"
        else -> "${m / 60} h"
    }
}

/** Source, language, chapters with total reading time, and your progress. */
@Composable
fun FenrirBookInfoCard(manga: Manga, source: Source, chapters: List<Chapter>, isNovel: Boolean) {
    val status by eu.kanade.presentation.browse.FenrirReadHealth.status.collectAsState()
    val off by eu.kanade.presentation.browse.FenrirReadHealth.turnedOff.collectAsState()
    val health = when {
        off.any { it.id == source.id } -> Red to "not working"
        status[source.id] == eu.kanade.presentation.browse.FenrirReadHealth.Status.Slow -> Color(0xFFFFB547) to "slow"
        status[source.id] == eu.kanade.presentation.browse.FenrirReadHealth.Status.Working -> Color(0xFF4CD787) to "working"
        else -> null
    }
    val total = chapters.size
    val read = chapters.count { it.read }
    val left = total - read
    val per = minutesPerChapter(isNovel)
    val lastRead = chapters.filter { it.read }.maxByOrNull { it.chapterNumber }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .border(1.dp, Rim, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        InfoRow("Source") {
            Text(source.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (health != null) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(health.first))
                Spacer(Modifier.width(4.dp))
                Text(health.second, color = health.first, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // Rating (AniList / MyAnimeList / Webnovel); tap for why it's rated that way.
        val rating by produceState<eu.kanade.presentation.library.components.FenrirRatings.Rating?>(null, manga.title) {
            value = eu.kanade.presentation.library.components.FenrirRatings.get(manga.title, isNovel)
        }
        rating?.let { r ->
            var openWhy by remember { mutableStateOf(false) }
            if (isNovel) {
                // Novels: no score up front, just the readers' reviews.
                InfoRow("Reviews") {
                    Text(
                        (r.reviewCount?.let { "Read $it reviews" } ?: "Read reviews") + " · ${r.site} ›",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { openWhy = true },
                    )
                }
            } else {
                InfoRow("Rating") {
                    Row(Modifier.clickable { openWhy = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text("★ ", color = eu.kanade.presentation.library.components.fenrirRatingColor(r.score), fontSize = 14.sp)
                        Text(String.format(java.util.Locale.US, "%.1f", r.score), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(" · ${r.site} ›", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            if (openWhy) eu.kanade.presentation.library.components.FenrirRatingSheet(manga.title, r) { openWhy = false }
        }
        val lang = remember(source.lang) {
            runCatching { java.util.Locale.forLanguageTag(source.lang).displayLanguage }.getOrNull()?.takeIf { it.isNotBlank() } ?: source.lang
        }
        if (lang.isNotBlank()) InfoRow("Language") { Text(lang, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        if (total > 0) {
            InfoRow("Chapters") {
                Text("$total chapters · about ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(formatTime(total * per), color = Color(0xFFFF8A91), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (read > 0) {
            val chLabel = lastRead?.let { "Ch. ${formatNumber(it.chapterNumber)} · " }.orEmpty()
            InfoRow("Your progress") {
                Text(
                    "$chLabel$left left · ~${formatTime(left * per)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatNumber(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

@Composable
private fun InfoRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.width(112.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** "Ch. 12 and 19 still unread · Mark read" when Continue would skip older unread chapters. */
@Composable
fun FenrirSkippedChip(mangaId: Long, ordered: List<Chapter>, onMarkRead: (List<Chapter>) -> Unit) {
    var hidden by remember(mangaId) { mutableStateOf(false) }
    val skipped = remember(ordered) {
        eu.kanade.tachiyomi.util.chapter.FenrirContinue.skippedBefore(mangaId, ordered)
    }
    if (hidden || skipped.isEmpty()) return
    val names = skipped.take(3).joinToString(", ") { formatNumber(it.chapterNumber) } + if (skipped.size > 3) " +${skipped.size - 3}" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(42.dp)
            .clip(CircleShape)
            .background(Color(0x1AFFB547))
            .border(1.dp, Color(0x59FFB547), CircleShape)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Ch. $names still unread",
            color = Color(0xFFFFD08A),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Mark read",
            color = Color(0xFF1A1205),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clip(CircleShape).background(Color(0xFFFFB547)).clickable { onMarkRead(skipped); hidden = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text("✕", color = Muted, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).clickable { hidden = true }.padding(8.dp))
    }
}

/** One pill: Chapters / Pages / Bookmarks on the left, Continue ▾ on the right. */
@Composable
fun FenrirBookPillBar(
    tab: FenrirBookTab,
    onTab: (FenrirBookTab) -> Unit,
    continueLabel: String?,
    onContinue: () -> Unit,
    onIncognito: () -> Unit,
    onFromStart: () -> Unit,
    onDownloadNext5: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF00B0B0F))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xF01C1C22))
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.13f), 0.6f to Color.Transparent))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(32.dp))
                .padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabIcon(Icons.AutoMirrored.Outlined.List, "Chapters", tab == FenrirBookTab.Chapters) { onTab(FenrirBookTab.Chapters) }
            TabIcon(Icons.Outlined.GridView, "Pages", tab == FenrirBookTab.Pages) { onTab(FenrirBookTab.Pages) }
            TabIcon(Icons.Outlined.BookmarkBorder, "Bookmarks", tab == FenrirBookTab.Bookmarks) { onTab(FenrirBookTab.Bookmarks) }
            TabIcon(Icons.Outlined.RateReview, "Reviews", tab == FenrirBookTab.Reviews) { onTab(FenrirBookTab.Reviews) }
            Spacer(Modifier.weight(1f))
            if (continueLabel != null) {
                Box {
                    Row(Modifier.height(46.dp).clip(CircleShape).background(Red), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.height(46.dp).clickable(onClick = onContinue).padding(start = 12.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(continueLabel, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Box(Modifier.width(1.dp).height(46.dp).background(Color.Black.copy(alpha = 0.25f)))
                        Box(Modifier.size(width = 36.dp, height = 46.dp).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "More reading options", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Read incognito") }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) }, onClick = { menu = false; onIncognito() })
                        DropdownMenuItem(text = { Text("Start from chapter 1") }, leadingIcon = { Icon(Icons.Outlined.SkipPrevious, null) }, onClick = { menu = false; onFromStart() })
                        if (onDownloadNext5 != null) {
                            DropdownMenuItem(text = { Text("Download next 5") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { menu = false; onDownloadNext5() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) Red.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Red else Color(0xFFC9C9D2), modifier = Modifier.size(21.dp))
    }
}

/** Loads a chapter's pages: the downloaded files when there are some, otherwise from the source. */
private suspend fun loadPages(source: Source, manga: Manga, chapter: Chapter): List<Page> = withContext(Dispatchers.IO) {
    val dm = Injekt.get<DownloadManager>()
    val downloaded = runCatching {
        dm.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.title, manga.source)
    }.getOrDefault(false)
    if (downloaded) {
        runCatching { dm.buildPageList(source, manga, chapter) }.getOrNull()?.let { return@withContext it }
    }
    runCatching { source.getPageList(chapter.toSChapter()) }.getOrDefault(emptyList())
}

@Composable
private fun PageImage(source: Source, page: Page, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val data by produceState<Any?>(initialValue = page.uri ?: page.imageUrl, page) {
        if (value == null && source is HttpSource) {
            value = runCatching { withContext(Dispatchers.IO) { source.getImageUrl(page) } }.getOrNull()
        }
    }
    val model = remember(data) {
        val d = data ?: return@remember null
        val builder = ImageRequest.Builder(context).data(d)
        if (source is HttpSource && d is String) {
            builder.httpHeaders(
                NetworkHeaders.Builder().apply { source.headers.forEach { (k, v) -> set(k, v) } }.build(),
            )
        }
        builder.build()
    }
    Box(modifier.background(Color(0xFF22232C)), contentAlignment = Alignment.Center) {
        if (model == null) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Red)
        } else {
            AsyncImage(model = model, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Pages: chapter pills on top, the chosen chapter's numbered pages below. Tap a page to open the
 * reader there; hold a page for a pages-only preview; the corner ribbon bookmarks a page.
 * Novels have no pages, so they get chapter cards instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FenrirPagesTab(
    manga: Manga,
    source: Source,
    ordered: List<Chapter>,
    startChapterId: Long?,
    isNovel: Boolean,
    onOpenChapter: (Chapter) -> Unit,
    onPickChapter: (Chapter) -> Unit = {},
) {
    val context = LocalContext.current
    if (ordered.isEmpty()) return
    var chosenId by remember(manga.id) { mutableStateOf(startChapterId ?: ordered.first().id) }
    val chosen = ordered.firstOrNull { it.id == chosenId } ?: ordered.first()
    val pillScroll = rememberScrollState()
    LaunchedEffect(Unit) {
        val idx = ordered.indexOfFirst { it.id == chosenId }
        if (idx > 0) pillScroll.scrollTo((idx - 1).coerceAtLeast(0) * 220)
    }
    if (isNovel) {
        FenrirNovelPages(manga, source, ordered, startChapterId, onOpenChapter)
        return
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(pillScroll).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ordered.forEach { ch ->
            val on = ch.id == chosen.id
            Text(
                "Ch. ${formatNumber(ch.chapterNumber)}",
                color = when {
                    on -> Color(0xFFFF8A91)
                    ch.read -> Color(0xFF6E6E78)
                    else -> Color.White
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) Red.copy(alpha = 0.18f) else if (ch.read) Color(0xFF1A1A20) else Color(0xFF22222B))
                    .border(1.dp, if (on) Red.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                    .clickable { chosenId = ch.id; onPickChapter(ch) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
    }
    val pages by produceState<List<Page>?>(initialValue = null, chosen.id) {
        value = null
        value = loadPages(source, manga, chosen)
    }
    val marksVersion by FenrirPageBookmarks.version.collectAsState()
    var peekAt by remember { mutableStateOf<Int?>(null) }
    val list = pages
    when {
        list == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Red)
        }
        list.isEmpty() -> Text(
            "Couldn't load this chapter's pages.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(20.dp),
        )
        else -> Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val current = if (chosen.read) -1 else chosen.lastPageRead.toInt()
            list.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { page ->
                        val marked = remember(marksVersion) { FenrirPageBookmarks.has(manga.id, chosen.id, page.index) }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(3f / 4.2f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (page.index == current) 2.5.dp else 1.dp,
                                    if (page.index == current) Red else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(12.dp),
                                )
                                .combinedClickable(
                                    onClick = { fenrirOpenAt(context, manga.id, chosen, page.index) },
                                    onLongClick = { peekAt = page.index },
                                ),
                        ) {
                            PageImage(source, page, Modifier.fillMaxSize())
                            Icon(
                                if (marked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark page",
                                tint = if (marked) Red else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable { FenrirPageBookmarks.toggle(manga.id, chosen.id, page.index) }
                                    .padding(5.dp),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(7.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xD914141A))
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${page.index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    val peek = peekAt
    if (peek != null && list != null && list.isNotEmpty()) {
        FenrirPagePeek(
            source,
            list,
            peek,
            onRead = { i -> peekAt = null; fenrirOpenAt(context, manga.id, chosen, i) },
            isMarked = { i -> FenrirPageBookmarks.has(manga.id, chosen.id, i) },
            onMark = { i -> FenrirPageBookmarks.toggle(manga.id, chosen.id, i) },
            chapterLabel = "Ch. ${formatNumber(chosen.chapterNumber)}",
        ) { peekAt = null }
    }
}

/** Pages big: swipe sideways to flip, tap outside or swipe down to close, Read from this page. */
@Composable
private fun FenrirPagePeek(
    source: Source,
    pages: List<Page>,
    start: Int,
    onRead: (Int) -> Unit,
    isMarked: (Int) -> Boolean,
    onMark: (Int) -> Unit,
    chapterLabel: String,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, pages.lastIndex)) { pages.size }
        val marksVersion by FenrirPageBookmarks.version.collectAsState()
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .pointerInput(Unit) {
                    var drag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (drag > 120f) onClose() },
                        onVerticalDrag = { _, dy -> drag += dy },
                    )
                }
                .clickable(onClick = onClose)
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("$chapterLabel · page ${pager.currentPage + 1} of ${pages.size}", color = Color(0xFFC9C9D2), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            HorizontalPager(
                state = pager,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 36.dp),
                pageSpacing = 14.dp,
                modifier = Modifier.weight(1f, fill = false),
            ) { i ->
                PageImage(
                    source,
                    pages[i],
                    Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(Red).clickable { onRead(pager.currentPage) }.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Read from page ${pager.currentPage + 1}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                val marked = remember(marksVersion, pager.currentPage) { isMarked(pager.currentPage) }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { onMark(pager.currentPage) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (marked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark page",
                        tint = if (marked) Red else Color.White,
                    )
                }
            }
        }
    }
}

/** Bookmarked pages (newest first) and bookmarked chapters. */
@Composable
fun FenrirBookmarksTab(
    manga: Manga,
    ordered: List<Chapter>,
    onOpenChapter: (Chapter) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by FenrirPageBookmarks.version.collectAsState()
    val marks = remember(version, manga.id) { FenrirPageBookmarks.list(manga.id) }
    val byId = remember(ordered) { ordered.associateBy { it.id } }
    val chapterMarks = ordered.filter { it.bookmark }
    val spotVersion by FenrirSpot.version.collectAsState()
    val lastSpot = remember(spotVersion, ordered) { FenrirSpot.last(manga.id)?.let { (id, at) -> ordered.firstOrNull { it.id == id }?.let { it to at } } }
    val far = remember(spotVersion, ordered) { FenrirSpot.spots(manga.id, ordered) }
    if (lastSpot != null) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("AUTOMATIC", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 2.dp))
            val (ch, at) = lastSpot
            AutoSpotRow(
                "Where you stopped · Ch. ${formatNumber(ch.chapterNumber)}" + if (!ch.read && ch.lastPageRead > 0) " · page ${ch.lastPageRead + 1}" else "",
                ch.name + whenText(at),
            ) { fenrirOpenAt(context, manga.id, ch, if (ch.read) 0 else ch.lastPageRead.toInt()) }
            if (far != null) {
                AutoSpotRow(
                    "Furthest read · Ch. ${formatNumber(far.far.chapterNumber)}" + (far.farPage?.takeIf { it > 0 }?.let { " · page ${it + 1}" } ?: ""),
                    far.far.name,
                ) { fenrirOpenAt(context, manga.id, far.far, far.farPage) }
            }
        }
    }
    if (marks.isEmpty() && chapterMarks.isEmpty()) {
        if (lastSpot != null) return
        Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No bookmarks yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "In Pages, tap the ribbon on a page to bookmark it.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        marks.forEach { m ->
            val ch = byId[m.chapterId] ?: return@forEach
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { fenrirOpenAt(context, manga.id, ch, m.page) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chapter ${formatNumber(ch.chapterNumber)} · page ${m.page + 1}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(ch.name, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Remove",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.clip(CircleShape).clickable { FenrirPageBookmarks.toggle(manga.id, ch.id, m.page) }.padding(8.dp),
                )
            }
        }
        chapterMarks.forEach { ch ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenChapter(ch) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chapter ${formatNumber(ch.chapterNumber)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(ch.name, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AutoSpotRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = Color(0xFFFFB547), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---------------------------------------------------------------- reading spots */

/** One-shot "open this chapter at this page" for the reader (also for chapters already read). */
object FenrirReaderJump {
    private val pending = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    fun set(chapterId: Long, page: Int) { pending[chapterId] = page }
    fun take(chapterId: Long): Int? = pending.remove(chapterId)
}

fun fenrirOpenAt(context: Context, mangaId: Long, chapter: Chapter, page: Int?) {
    if (page != null) FenrirReaderJump.set(chapter.id, page)
    context.startActivity(eu.kanade.tachiyomi.ui.reader.ReaderActivity.newIntent(context, mangaId, chapter.id))
}

/**
 * The automatic bookmark: the chapter you were in when you closed the app or backed out. The page
 * itself is the chapter's saved progress, so reopening lands on the right page.
 */
object FenrirSpot {
    private val prefs by lazy {
        Injekt.get<android.app.Application>().getSharedPreferences("fenrir_spots", Context.MODE_PRIVATE)
    }
    val version = MutableStateFlow(0)

    fun saveLast(mangaId: Long, chapterId: Long) {
        runCatching {
            prefs.edit().putString("last_$mangaId", "$chapterId|${System.currentTimeMillis()}").apply()
            version.value++
        }
    }

    /** chapter id to when. */
    fun last(mangaId: Long): Pair<Long, Long>? = runCatching {
        val p = prefs.getString("last_$mangaId", null)?.split('|') ?: return null
        (p[0].toLongOrNull() ?: return null) to (p.getOrNull(1)?.toLongOrNull() ?: 0L)
    }.getOrNull()

    /** "last", "far" or null (ask). */
    fun choice(mangaId: Long): String? = runCatching { prefs.getString("choice_$mangaId", null) }.getOrNull()
    fun setChoice(mangaId: Long, c: String?) { runCatching { prefs.edit().putString("choice_$mangaId", c).apply() } }

    data class Spots(val last: Chapter, val lastAt: Long, val far: Chapter, val farPage: Int?)

    /** Where you just were vs. the furthest you've read, when those differ (you went back). */
    fun spots(mangaId: Long, ordered: List<Chapter>): Spots? {
        val (lastId, at) = last(mangaId) ?: return null
        val lastIdx = ordered.indexOfFirst { it.id == lastId }.takeIf { it >= 0 } ?: return null
        val farIdx = ordered.indexOfLast { it.read || it.lastPageRead > 0 }
        if (farIdx <= lastIdx) return null
        val f = ordered[farIdx]
        return if (f.read && farIdx < ordered.lastIndex) {
            Spots(ordered[lastIdx], at, ordered[farIdx + 1], 0)
        } else {
            Spots(ordered[lastIdx], at, f, f.lastPageRead.toInt())
        }
    }
}

private fun whenText(at: Long): String {
    if (at <= 0) return ""
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
    return when (days) {
        0 -> " · today $time"
        1 -> " · yesterday"
        else -> " · $days days ago"
    }
}

/**
 * Continue button behaviour: when you went back to an earlier chapter, asks whether to continue
 * there or jump to the furthest you've read (unless you ticked "Remember for this book").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFenrirContinue(manga: Manga, ordered: List<Chapter>, fallback: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var ask by remember { mutableStateOf<FenrirSpot.Spots?>(null) }
    val open: (Chapter, Int?) -> Unit = { ch, page -> fenrirOpenAt(context, manga.id, ch, page) }
    ask?.let { s ->
        var keep by remember { mutableStateOf(false) }
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { ask = null }, containerColor = Color(0xFF15151B)) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
                Text("Where do you want to continue?", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("You went back to an earlier chapter", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp, bottom = 14.dp))
                SpotOption(
                    "↩",
                    "Ch. ${formatNumber(s.last.chapterNumber)}" + if (!s.last.read) " · page ${s.last.lastPageRead + 1}" else "",
                    "Where you just were" + whenText(s.lastAt),
                    true,
                ) {
                    if (keep) FenrirSpot.setChoice(manga.id, "last")
                    ask = null
                    open(s.last, if (s.last.read) 0 else s.last.lastPageRead.toInt())
                }
                Spacer(Modifier.height(10.dp))
                SpotOption(
                    "⏩",
                    "Ch. ${formatNumber(s.far.chapterNumber)}" + (s.farPage?.takeIf { it > 0 }?.let { " · page ${it + 1}" } ?: ""),
                    "Furthest you've read",
                    false,
                ) {
                    if (keep) FenrirSpot.setChoice(manga.id, "far")
                    ask = null
                    open(s.far, s.farPage)
                }
                Row(
                    Modifier.padding(top = 14.dp).clip(RoundedCornerShape(8.dp)).clickable { keep = !keep }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text("Remember for this book", color = Color(0xFFC9C9D2), fontSize = 13.sp)
                }
            }
        }
    }
    return {
        val s = runCatching { FenrirSpot.spots(manga.id, ordered) }.getOrNull()
        if (s == null) {
            // No going back involved: reopen where you stopped (exact page), else the usual next chapter.
            val lastId = FenrirSpot.last(manga.id)?.first
            val last = ordered.firstOrNull { it.id == lastId }
            if (last != null && !last.read) open(last, last.lastPageRead.toInt()) else fallback()
        } else {
            when (FenrirSpot.choice(manga.id)) {
                "last" -> open(s.last, if (s.last.read) 0 else s.last.lastPageRead.toInt())
                "far" -> open(s.far, s.farPage)
                else -> ask = s
            }
        }
    }
}

@Composable
private fun SpotOption(icon: String, title: String, sub: String, main: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (main) Red.copy(alpha = 0.12f) else Color(0xFF1C1C23))
            .border(if (main) 1.5.dp else 1.dp, if (main) Red else Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp, color = Color.White)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = Color(0xFFC9C9D2), fontSize = 12.sp)
        }
        Text("›", color = if (main) Red else Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/* ---------------------------------------------------------------- novel chapter previews */

/** Opening lines + word count of a novel chapter; loaded quietly, cached, never throws. */
object FenrirNovelSnippets {
    data class Snip(val text: String, val words: Int)
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, Snip>()
    private val failed = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Long, Boolean>())
    private val gate = kotlinx.coroutines.sync.Semaphore(2)

    fun cached(id: Long): Snip? = cache[id]

    suspend fun load(context: Context, source: Source, manga: Manga, chapter: Chapter): Snip? {
        cache[chapter.id]?.let { return it }
        if (chapter.id in failed) return null
        gate.acquire()
        try {
            return withContext(Dispatchers.IO) {
                runCatching {
                    val page = loadPages(source, manga, chapter).firstOrNull() ?: return@runCatching null
                    val raw = page.text?.takeIf { it.isNotBlank() }
                        ?: page.uri?.let { u -> context.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } }
                        ?: withTimeoutOrNull(20_000) { source.fetchPageText(page) }
                        ?: return@runCatching null
                    val text = org.jsoup.Jsoup.parse(raw).text().trim()
                        .let { t -> if (t.startsWith(chapter.name, true)) t.substring(chapter.name.length).trim() else t }
                    if (text.isBlank()) return@runCatching null
                    val words = text.split(Regex("\\s+")).size
                    Snip(text.take(1600), words).also { cache[chapter.id] = it }
                }.getOrNull().also { if (it == null) failed += chapter.id }
            }
        } finally {
            gate.release()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FenrirNovelPages(manga: Manga, source: Source, ordered: List<Chapter>, startChapterId: Long?, onOpenChapter: (Chapter) -> Unit) {
    val context = LocalContext.current
    val groups = remember(ordered) { ordered.chunked(40) }
    var group by remember(manga.id) {
        mutableStateOf(groups.indexOfFirst { g -> g.any { it.id == startChapterId } }.coerceAtLeast(0))
    }
    var preview by remember { mutableStateOf<Chapter?>(null) }
    val startIdx = ordered.indexOfFirst { it.id == startChapterId }
    Text(
        "Opening lines of each chapter · tap to read · hold to preview",
        color = Muted,
        fontSize = 12.5.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
    if (groups.size > 1) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEachIndexed { i, g ->
                val on = i == group
                Text(
                    "${formatNumber(g.first().chapterNumber)}–${formatNumber(g.last().chapterNumber)}",
                    color = if (on) Color.White else Color(0xFFD5D5DE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(if (on) Red else Color(0xFF22222B)).clickable { group = i }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
    }
    val dm = remember { Injekt.get<DownloadManager>() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.getOrNull(group).orEmpty().forEach { ch ->
            val idx = ordered.indexOf(ch)
            val current = ch.id == startChapterId
            // Opening lines load for downloaded chapters and the ones around where you are.
            val auto = remember(ch.id) {
                kotlin.math.abs(idx - startIdx) <= 4 ||
                    runCatching { dm.isChapterDownloaded(ch.name, ch.scanlator, ch.url, manga.title, manga.source) }.getOrDefault(false)
            }
            val snip by produceState(FenrirNovelSnippets.cached(ch.id), ch.id) {
                if (value == null && auto) value = FenrirNovelSnippets.load(context, source, manga, ch)
            }
            val s = snip
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .border(1.dp, if (current) Red else Rim, RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = { onOpenChapter(ch) }, onLongClick = { preview = ch })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CH. ${formatNumber(ch.chapterNumber)}", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            current && !ch.read && ch.lastPageRead > 0 -> "Reading"
                            current -> "Up next"
                            ch.read -> "Read"
                            else -> "New"
                        },
                        color = if (ch.read && !current) Color(0xFF6E6E78) else Red,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    ch.name,
                    color = if (ch.read && !current) Color(0xFF9A9AA6) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (s != null) {
                    Text(
                        s.text.take(240),
                        color = Color(0xFFC9C9D2),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val mins = s?.let { (it.words / 230.0).coerceAtLeast(1.0).toInt() } ?: 12
                    Text(
                        if (s != null) "⏱ $mins min · ${"%,d".format(s.words)} words" else "⏱ ~$mins min · hold to preview",
                        color = Color(0xFF8E8E99),
                        fontSize = 11.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Read ›", color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    preview?.let { ch ->
        val snip by produceState(FenrirNovelSnippets.cached(ch.id), ch.id) {
            if (value == null) value = FenrirNovelSnippets.load(context, source, manga, ch)
        }
        var loadingDone by remember(ch.id) { mutableStateOf(false) }
        LaunchedEffect(ch.id) { kotlinx.coroutines.delay(20_000); loadingDone = true }
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable { preview = null }.padding(horizontal = 22.dp, vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFF3EEE3))
                        .verticalScroll(rememberScrollState()).padding(22.dp),
                ) {
                    Text(
                        "CHAPTER ${formatNumber(ch.chapterNumber)}" + (snip?.let { " · ${(it.words / 230.0).coerceAtLeast(1.0).toInt()} MIN" } ?: ""),
                        color = Color(0xFF8A7F70),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(ch.name, color = Color(0xFF2A2622), fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                    val s = snip
                    when {
                        s != null -> Text(s.text + "…", color = Color(0xFF2A2622), fontSize = 15.sp, lineHeight = 25.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        !loadingDone -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Red)
                        else -> Text("Couldn't load a preview of this chapter.", color = Color(0xFF8A7F70), fontSize = 14.sp)
                    }
                }
                Text(
                    "📖  Start reading Ch. ${formatNumber(ch.chapterNumber)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 16.dp).clip(CircleShape).background(Red)
                        .clickable { preview = null; onOpenChapter(ch) }.padding(horizontal = 24.dp, vertical = 13.dp),
                )
            }
        }
    }
}
