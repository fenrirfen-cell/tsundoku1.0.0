package eu.kanade.presentation.library.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import eu.kanade.presentation.components.mugiCabin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.imageResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    hasActiveFilters: Boolean,
    isQueryRunning: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onResumeBook: ((LibraryManga) -> Unit)? = null,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    titleMaxLines: Int = 2,
    showUrlInList: Boolean = false,
    paginationEnabled: Boolean = false,
    onCategoryFirstVisible: (Category) -> Unit = {},
    onLoadMore: (Category) -> Unit = {},
    getLoadMoreKey: (Category) -> Long = { 0 },
    isCategoryLoading: (Category) -> Boolean = { false },
) {
    // Fenrir: the top part (Continue reading case + category rail) slides away over the books as
    // you scroll down and slides back as soon as you scroll up. It moves on top of the list instead
    // of resizing it, so scrolling stays smooth and never jumps.
    val scrollHeader = remember { FenrirScrollingHeader() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(currentPage) { categories.size }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }
    LaunchedEffect(Unit) { fenrirAutoCheckAppUpdate(context) }

    Box(
        modifier = Modifier
            .padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            )
            .clipToBounds(),
    ) {
      val look = FenrirLibraryLook.current.value
      // MugiVerse: the captain's cabin (Bookshelf look): gold frame, parchment category, cabin behind
      val mugiRead = eu.kanade.tachiyomi.EmbeddedHost.isMugi && look == FenrirLibraryLook.Look.Bookshelf
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
      // Fenrir (Luxury): two layouts picked from the width you have (folded / unfolded, split screen)
      val wide = maxWidth >= 600.dp
      val geom = if (wide) LuxGeom(true, maxWidth * 0.13f, 11.dp) else LuxGeom(false, 0.dp, 6.dp)
      androidx.compose.runtime.CompositionLocalProvider(LocalFenrirHeader provides scrollHeader, LocalLuxGeom provides geom) {
      FenrirLibraryLookPicker()
      Box(Modifier.fillMaxSize()) {
        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
            indicatorPadding = PaddingValues(top = with(density) { scrollHeader.heightPx.toDp() }),
            onRefresh = {
                // Fenrir: also check quietly for a new app version (only says something if there is one).
                fenrirCheckAppUpdate(context)
                val started = onRefresh()
                if (!started) return@PullRefresh
                scope.launch {
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
        ) {
            LibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getCategoryForPage = { page -> categories[page] },
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getItemsForCategory = getItemsForCategory,
                onClickManga = { category, manga ->
                    if (selection.isNotEmpty()) {
                        onToggleSelection(category, manga)
                    } else {
                        onClickManga(manga.manga.id)
                    }
                },
                onLongClickManga = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
                titleMaxLines = titleMaxLines,
                showUrlInList = showUrlInList,
                paginationEnabled = paginationEnabled,
                onCategoryFirstVisible = onCategoryFirstVisible,
                onLoadMore = onLoadMore,
                getLoadMoreKey = getLoadMoreKey,
                isCategoryLoading = isCategoryLoading,
            )
        }

        // Fenrir: the glass case + category rail, moving exactly with the books of this category.
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { scrollHeader.heightPx = it.height }
                .graphicsLayer { translationY = scrollHeader.offsetFor(pagerState.currentPage) }
                .then(
                    when (look) {
                        FenrirLibraryLook.Look.Luxury -> Modifier.luxPageBack(geom, withCase = false)
                        FenrirLibraryLook.Look.Regular -> Modifier.background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(androidx.compose.ui.graphics.Color(0xFF2C1317), androidx.compose.ui.graphics.Color(0xFF0B0B0F)),
                            ),
                        )
                        else -> if (mugiRead) Modifier.mugiCabin(header = false) else Modifier.fenrirBookcaseBack()
                    },
                ),
        ) {
                        // Fenrir: "● 1 source down · 4 books ›" when a source your books use stopped working.
            if (searchQuery.isNullOrEmpty() && selection.isEmpty()) {
                val allBooks = categories.flatMap { getItemsForCategory(it) }.map { it.libraryManga }
                FenrirSourceDownChip(books = allBooks, onOpenBook = onClickManga)
                if (allBooks.isNotEmpty()) FenrirAlertsCard()
            }

            // Fenrir (Luxury, wide): the curved side towers next to the cabinet
            if (look == FenrirLibraryLook.Look.Luxury && geom.wide) {
                Box(Modifier.fillMaxWidth().height(0.dp)) {
                    LuxTower(true, Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(start = 3.dp).wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true).width(geom.tower))
                    LuxTower(false, Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(end = 3.dp).wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true).width(geom.tower))
                }
            }
            // MugiVerse: the large "Read" page title under the logo, above Continue Reading
            if (mugiRead && look == FenrirLibraryLook.Look.Bookshelf) {
                androidx.compose.material3.Text(
                    "Read",
                    color = androidx.compose.ui.graphics.Color(0xFFF2D592),
                    fontFamily = eu.kanade.presentation.components.MugiCinzel,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 32.sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                )
            }
            // MugiVerse: the carved wooden header trim (supplied artwork) above Continue Reading
            if (mugiRead && look == FenrirLibraryLook.Look.Bookshelf && eu.kanade.tachiyomi.EmbeddedHost.showDecorations?.invoke() != false) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.library_header_trim),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).aspectRatio(1100f / 164f),
                )
            }
            // Fenrir: "Continue reading" glass display case for the most recently read book.
            if (searchQuery.isNullOrEmpty() && selection.isEmpty()) {
                val shownBooks = categories.asSequence()
                    .flatMap { getItemsForCategory(it).asSequence() }
                    .map { it.libraryManga }
                val listPick = shownBooks
                    .filter { it.lastRead > 0 && it.unreadCount > 0 }
                    .maxByOrNull { it.lastRead }
                // Fenrir: from reading history (fresh), not only the books the list shows right now
                val kinds = shownBooks.map { it.manga.isNovel }.toSet().takeIf { it.isNotEmpty() }
                val lastRead = rememberFenrirContinueBook(listPick, kinds)
                if (lastRead != null && look != FenrirLibraryLook.Look.Bookshelf) {
                    val open = {
                        val resume = onResumeBook ?: onContinueReadingClicked
                        if (resume != null) resume(lastRead) else onClickManga(lastRead.manga.id)
                    }
                    if (look == FenrirLibraryLook.Look.Luxury) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = geom.side - 4.dp)) { LuxContinueCabinet(lastRead) { open() } }
                    } else {
                        FenrirContinueReadingCard(lastRead, onOpen = { open() }, onClickCover = { onClickManga(lastRead.manga.id) })
                    }
                } else if (lastRead != null && mugiRead) {
                    MugiContinueReadingFrame(
                        manga = lastRead,
                        onOpen = {
                            val resume = onResumeBook ?: onContinueReadingClicked
                            if (resume != null) resume(lastRead) else onClickManga(lastRead.manga.id)
                        },
                    )
                } else if (lastRead != null) {
                    FenrirContinueReadingCase(
                        manga = lastRead,
                        onOpen = {
                            // straight into the next chapter
                            val resume = onResumeBook ?: onContinueReadingClicked
                            if (resume != null) resume(lastRead) else onClickManga(lastRead.manga.id)
                        },
                    )
                }
            }

            if (showPageTabs && categories.isNotEmpty() && (categories.size > 1 || !categories.first().isSystemCategory)) {
                LaunchedEffect(categories) {
                    if (categories.size <= pagerState.currentPage) {
                        pagerState.scrollToPage(categories.size - 1)
                    }
                }
                // Fenrir: the bookcase's top rail (name, new count, dots, brass knobs); swipe to change category.
                if (look == FenrirLibraryLook.Look.Luxury) Box(Modifier.fillMaxWidth().padding(horizontal = geom.side - 4.dp)) {
                FenrirCategoryBar(
                    categories = categories,
                    pagerState = pagerState,
                    newCountFor = { category -> getItemsForCategory(category).count { it.unreadCount > 0 } },
                    onGoTo = { scope.launch { pagerState.animateScrollToPage(it) } },
                    countFor = { category -> getItemsForCategory(category).size },
                )
                } else FenrirCategoryBar(
                    categories = categories,
                    pagerState = pagerState,
                    newCountFor = { category -> getItemsForCategory(category).count { it.unreadCount > 0 } },
                    onGoTo = { scope.launch { pagerState.animateScrollToPage(it) } },
                    countFor = { category -> getItemsForCategory(category).size },
                )
            }
            // Fenrir (Luxury): the case's top cap and ceiling lights, joining the band to the shelves
            if (look == FenrirLibraryLook.Look.Luxury) LuxCaseTop(geom)
            // Fenrir: ivy hanging from the rail over the top corners of the bookcase.
            if (look == FenrirLibraryLook.Look.Bookshelf && !mugiRead) Box(Modifier.fillMaxWidth().height(0.dp)) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.fenrir_ivy_left),
                    contentDescription = null,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart)
                        .wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true).width(30.dp).height(56.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alignment = androidx.compose.ui.Alignment.TopStart,
                )
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.fenrir_ivy_right),
                    contentDescription = null,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                        .wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true).width(30.dp).height(60.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alignment = androidx.compose.ui.Alignment.TopEnd,
                )
            }
        }
      }
      }
      }

        if (isQueryRunning && selection.isEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }

        // Fenrir: the book-flight overlay is drawn by MainActivity, above the top and bottom bars.

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}


/** MugiVerse: the cabin picture (bottom part) behind Continue Reading and the category plate. */
@Composable
private fun Modifier.mugiCabinBack(): Modifier {
    val img = androidx.compose.ui.graphics.ImageBitmap.imageResource(eu.kanade.tachiyomi.R.drawable.mugi_library_cabin)
    return this.drawBehind {
        drawRect(androidx.compose.ui.graphics.Color(0xFF28170F))
        // scale to cover, keep the bottom (the lower shelves) under the frame
        val scale = maxOf(size.width / img.width, size.height / img.height)
        val w = img.width * scale
        val h = img.height * scale
        drawImage(
            img,
            dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2f).toInt(), (size.height - h).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
        )
        drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f))
    }
}
