package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
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
    val header = remember { FenrirQuickReturnHeader() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(currentPage) { categories.size }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }
    val headerHeightDp = with(density) { header.heightPx.toDp() }
    LaunchedEffect(pagerState.currentPage) { header.show() }
    LaunchedEffect(Unit) { fenrirAutoCheckAppUpdate(context) }

    Box(
        modifier = Modifier
            .padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            )
            .clipToBounds()
            .nestedScroll(header.connection),
    ) {
        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
            indicatorPadding = PaddingValues(top = headerHeightDp),
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
                contentPadding = PaddingValues(top = headerHeightDp, bottom = contentPadding.calculateBottomPadding()),
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

        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { header.heightPx = it.height }
                .graphicsLayer { translationY = header.offsetPx },
        ) {
            // Fenrir: "Update available" pill (Read side gets app updates too).
            FenrirUpdatePill()

            // Fenrir: "● 1 source down · 4 books ›" when a source your books use stopped working.
            if (searchQuery.isNullOrEmpty() && selection.isEmpty()) {
                val allBooks = categories.flatMap { getItemsForCategory(it) }.map { it.libraryManga }
                FenrirSourceDownChip(books = allBooks, onOpenBook = onClickManga)
                if (allBooks.isNotEmpty()) FenrirAlertsCard()
            }

            // Fenrir: "Continue reading" glass display case for the most recently read book.
            if (searchQuery.isNullOrEmpty() && selection.isEmpty()) {
                val lastRead = run {
                    categories.asSequence()
                        .flatMap { getItemsForCategory(it).asSequence() }
                        .map { it.libraryManga }
                        .filter { it.lastRead > 0 && it.unreadCount > 0 }
                        .maxByOrNull { it.lastRead }
                }
                if (lastRead != null) {
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
                FenrirCategoryBar(
                    categories = categories,
                    pagerState = pagerState,
                    newCountFor = { category -> getItemsForCategory(category).count { it.unreadCount > 0 } },
                    onGoTo = { scope.launch { pagerState.animateScrollToPage(it) } },
                    countFor = { category -> getItemsForCategory(category).size },
                )
            }
        }

        if (isQueryRunning && selection.isEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }

        // Fenrir: a book flying off the shelf and opening (shelf books and the glass case).
        FenrirBookFlightOverlay()

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
