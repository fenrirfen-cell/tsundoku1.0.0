package eu.kanade.presentation.manga

import android.content.Context
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

enum class FenrirBookTab { Chapters, Pages, Bookmarks }

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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (ordered.isEmpty()) return
    var chosenId by remember(manga.id) { mutableStateOf(startChapterId ?: ordered.first().id) }
    val chosen = ordered.firstOrNull { it.id == chosenId } ?: ordered.first()
    val pillScroll = rememberScrollState()
    LaunchedEffect(Unit) {
        val idx = ordered.indexOfFirst { it.id == chosenId }
        if (idx > 0) pillScroll.scrollTo((idx - 1).coerceAtLeast(0) * 220)
    }
    if (isNovel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ordered.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { ch ->
                        val current = ch.id == startChapterId
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Card)
                                .border(1.dp, if (current) Red else Rim, RoundedCornerShape(16.dp))
                                .clickable { onOpenChapter(ch) }
                                .padding(12.dp),
                        ) {
                            Text("CHAPTER ${formatNumber(ch.chapterNumber)}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                ch.name,
                                color = if (ch.read) Color(0xFF6E6E78) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                when {
                                    current -> "Reading"
                                    ch.read -> "Read"
                                    else -> "~12 min"
                                },
                                color = if (current) Red else Muted,
                                fontSize = 11.5.sp,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
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
                    .clickable { chosenId = ch.id }
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
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                Injekt.get<UpdateChapter>().await(
                                                    ChapterUpdate(id = chosen.id, lastPageRead = page.index.toLong()),
                                                )
                                            }
                                            context.startActivity(
                                                eu.kanade.tachiyomi.ui.reader.ReaderActivity.newIntent(context, manga.id, chosen.id),
                                            )
                                        }
                                    },
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
        FenrirPagePeek(source, list, peek) { peekAt = null }
    }
}

/** Pages only, big: swipe sideways to flip, tap or swipe down to close. */
@Composable
private fun FenrirPagePeek(source: Source, pages: List<Page>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, pages.lastIndex)) { pages.size }
        Box(
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
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(state = pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 36.dp), pageSpacing = 14.dp) { i ->
                PageImage(
                    source,
                    pages[i],
                    Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
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
    if (marks.isEmpty() && chapterMarks.isEmpty()) {
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
                    .clickable {
                        scope.launch {
                            runCatching { Injekt.get<UpdateChapter>().await(ChapterUpdate(id = ch.id, lastPageRead = m.page.toLong())) }
                            context.startActivity(eu.kanade.tachiyomi.ui.reader.ReaderActivity.newIntent(context, manga.id, ch.id))
                        }
                    }
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
