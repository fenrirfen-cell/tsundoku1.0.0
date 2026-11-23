package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
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
    // Fenrir: the category bar and Continue reading card scroll away with the books and
    // come back when you scroll back to the top.
    val headerCollapse = remember { FenrirCollapsingHeader() }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            )
            .nestedScroll(headerCollapse.connection),
    ) {
        val pagerState = rememberPagerState(currentPage) { categories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        Column(Modifier.then(headerCollapse.modifier)) {

        // Fenrir: "● 1 source down · 4 books ›" when a source your books use stopped working.
        if (searchQuery.isNullOrEmpty() && selection.isEmpty()) {
            val allBooks = categories.flatMap { getItemsForCategory(it) }.map { it.libraryManga }
            FenrirSourceDownChip(books = allBooks, onOpenBook = onClickManga)
            if (allBooks.isNotEmpty()) FenrirAlertsCard()
        }

        if (showPageTabs && categories.isNotEmpty() && (categories.size > 1 || !categories.first().isSystemCategory)) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            // Fenrir: one glass bar (name, new count, dots, arrows); swipe to change category.
            FenrirCategoryBar(
                categories = categories,
                pagerState = pagerState,
                newCountFor = { category -> getItemsForCategory(category).count { it.unreadCount > 0 } },
                onGoTo = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        }

        // Fenrir: "Continue reading" card for the most recently read book.
        if (searchQuery.isNullOrEmpty() && selection.isEmpty()) {
            val lastRead = run {
                categories.asSequence()
                    .flatMap { getItemsForCategory(it).asSequence() }
                    .map { it.libraryManga }
                    .filter { it.lastRead > 0 && it.unreadCount > 0 }
                    .maxByOrNull { it.lastRead }
            }
            if (lastRead != null) {
                FenrirContinueReadingCard(
                    manga = lastRead,
                    onOpen = {
                        val resume = onContinueReadingClicked
                        if (resume != null) resume(lastRead) else onClickManga(lastRead.manga.id)
                    },
                    onClickCover = { onClickManga(lastRead.manga.id) },
                )
            }
        }

        }

        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
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

        if (isQueryRunning && selection.isEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
