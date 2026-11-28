package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun LibraryPager(
    state: PagerState,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    selection: Set<Long>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getCategoryForPage: (Int) -> Category,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    onClickManga: (Category, LibraryManga) -> Unit,
    onLongClickManga: (Category, LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    titleMaxLines: Int = 2,
    showUrlInList: Boolean = false,
    paginationEnabled: Boolean = false,
    onCategoryFirstVisible: (Category) -> Unit = {},
    onLoadMore: (Category) -> Unit = {},
    getLoadMoreKey: (Category) -> Long = { 0 },
    isCategoryLoading: (Category) -> Boolean = { false },
) {
    HorizontalPager(
        modifier = Modifier.fillMaxSize(),
        state = state,
        verticalAlignment = Alignment.Top,
    ) { page ->
        if (page !in ((state.currentPage - 1)..(state.currentPage + 1))) {
            // To make sure only one offscreen page is being composed
            return@HorizontalPager
        }
        // Fenrir: revolving bookcase. Each category is one side of a box that turns as you switch.
        val look = FenrirLibraryLook.current.value
        val pageBack = when (look) {
            FenrirLibraryLook.Look.Luxury -> Modifier.luxPageBack(LocalLuxGeom.current)
            FenrirLibraryLook.Look.Regular -> Modifier.background(androidx.compose.ui.graphics.Color(0xFF0B0B0F))
            else -> Modifier.fenrirBookcaseBack()
        }
        Box(Modifier.fillMaxSize().fenrirBookcaseTurn(state, page).then(pageBack)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalFenrirPage provides page) pageBlock@{
        val category = getCategoryForPage(page)
        val items = getItemsForCategory(category)
        val novelShelf = remember(items) { items.count { it.libraryManga.manga.isNovel } * 2 >= items.size }
        androidx.compose.runtime.CompositionLocalProvider(LocalFenrirShelfNovel provides novelShelf) shelfBlock@{
        val fenrirHeaderDp = with(androidx.compose.ui.platform.LocalDensity.current) { (LocalFenrirHeader.current?.heightPx ?: 0).toDp() }
        val listPadding = contentPadding + PaddingValues(top = fenrirHeaderDp)

        if (paginationEnabled) {
            LaunchedEffect(category.id) { onCategoryFirstVisible(category) }
        }

        if (items.isEmpty()) {
            if (paginationEnabled && isCategoryLoading(category)) {
                Box(
                    modifier = Modifier
                        .padding(listPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LibraryPagerEmptyScreen(
                    searchQuery = searchQuery,
                    hasActiveFilters = hasActiveFilters,
                    contentPadding = listPadding,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                )
            }
            return@shelfBlock
        }

        val onLoadMoreForCategory: (() -> Unit)? = if (paginationEnabled) {
            { onLoadMore(category) }
        } else {
            null
        }
        // Generation key (paginated) drives sentinel re-fire; item count otherwise.
        val loadMoreKey = if (paginationEnabled) getLoadMoreKey(category) else items.size.toLong()

        val displayMode by getDisplayMode(page)
        val columns by if (displayMode != LibraryDisplayMode.List) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            remember(isLandscape) { getColumnsForOrientation(isLandscape) }
        } else {
            remember { mutableIntStateOf(0) }
        }

        val onClickManga: (LibraryManga) -> Unit = { onClickManga(category, it) }
        val onLongClickManga: (LibraryManga) -> Unit = { onLongClickManga(category, it) }

        when (displayMode) {
            LibraryDisplayMode.List -> {
                LibraryList(
                    items = items,
                    contentPadding = listPadding,
                    selection = selection,
                    onClick = onClickManga,
                    onLongClick = onLongClickManga,
                    onClickContinueReading = onClickContinueReading,
                    searchQuery = searchQuery,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                    showUrl = showUrlInList,
                    onLoadMore = onLoadMoreForCategory,
                    loadMoreKey = loadMoreKey,
                )
            }
            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                LibraryCompactGrid(
                    items = items,
                    showTitle = displayMode is LibraryDisplayMode.CompactGrid,
                    columns = columns,
                    contentPadding = contentPadding,
                    selection = selection,
                    onClick = onClickManga,
                    onLongClick = onLongClickManga,
                    onClickContinueReading = onClickContinueReading,
                    searchQuery = searchQuery,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                    onLoadMore = onLoadMoreForCategory,
                    loadMoreKey = loadMoreKey,
                )
            }
            LibraryDisplayMode.ComfortableGrid -> {
                LibraryComfortableGrid(
                    items = items,
                    columns = columns,
                    contentPadding = contentPadding,
                    selection = selection,
                    onClick = onClickManga,
                    onLongClick = onLongClickManga,
                    onClickContinueReading = onClickContinueReading,
                    searchQuery = searchQuery,
                    onGlobalSearchClicked = onGlobalSearchClicked,
                    titleMaxLines = titleMaxLines,
                    onLoadMore = onLoadMoreForCategory,
                    loadMoreKey = loadMoreKey,
                )
            }
        }
        }
        }
        }
    }
}

/** Turns a pager page like one side of a box (the revolving bookcase), darkening as it turns away. */
private fun Modifier.fenrirBookcaseTurn(state: PagerState, page: Int): Modifier = this
    .graphicsLayer {
        val o = ((page - state.currentPage) - state.currentPageOffsetFraction).coerceIn(-1f, 1f)
        cameraDistance = 14f * density
        transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
        rotationY = 90f * o
    }
    .drawWithContent {
        drawContent()
        val o = ((page - state.currentPage) - state.currentPageOffsetFraction).coerceIn(-1f, 1f)
        val shade = kotlin.math.abs(o) * 0.45f
        if (shade > 0.01f) drawRect(Color.Black.copy(alpha = shade))
    }

@Composable
private fun LibraryPagerEmptyScreen(
    searchQuery: String?,
    hasActiveFilters: Boolean,
    contentPadding: PaddingValues,
    onGlobalSearchClicked: () -> Unit,
) {
    val msg = when {
        !searchQuery.isNullOrEmpty() -> MR.strings.no_results_found
        hasActiveFilters -> MR.strings.error_no_match
        else -> MR.strings.information_no_manga_category
    }

    Column(
        modifier = Modifier
            .padding(contentPadding + PaddingValues(8.dp))
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!searchQuery.isNullOrEmpty()) {
            GlobalSearchItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }

        if (eu.kanade.tachiyomi.EmbeddedHost.isMugi && searchQuery.isNullOrEmpty() && !hasActiveFilters) {
            // MugiVerse: an empty category shows the red Add a book (supplied artwork), which opens Browse
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val openBrowse: () -> Unit = {
                scope.launch { eu.kanade.tachiyomi.ui.home.HomeScreen.openTab(eu.kanade.tachiyomi.ui.home.HomeScreen.Tab.Browse()) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.library_add_book),
                    contentDescription = "Add a book",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(298f / 346f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = "Add a book", onClick = openBrowse),
                )
                androidx.compose.material3.Text(
                    "No books in this category yet",
                    color = androidx.compose.ui.graphics.Color(0xFFF2D592),
                    fontFamily = eu.kanade.presentation.components.MugiPlayfair,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                androidx.compose.material3.Text(
                    "Tap the book to find something in Browse.",
                    color = androidx.compose.ui.graphics.Color(0xFFD9CDB6),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            EmptyScreen(
                stringRes = msg,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
