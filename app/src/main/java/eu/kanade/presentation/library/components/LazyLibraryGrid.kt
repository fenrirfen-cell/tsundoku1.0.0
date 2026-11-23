package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.util.plus

@Composable
internal fun LazyLibraryGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    contentPadding: PaddingValues,
    shelfFill: Int = -1,
    titleMaxLines: Int = 2,
    content: LazyGridScope.() -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // Fenrir: how many books fit on one shelf (same rule the grid uses below).
        val perRow = if (columns > 0) {
            columns
        } else {
            ((maxWidth - 16.dp + CommonMangaItemDefaults.GridHorizontalSpacer) / (128.dp + CommonMangaItemDefaults.GridHorizontalSpacer)).toInt().coerceAtLeast(1)
        }
        val header = LocalFenrirHeader.current
        val page = LocalFenrirPage.current
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        if (header != null) {
            androidx.compose.runtime.DisposableEffect(gridState, page) {
                header.grids[page] = gridState
                onDispose { if (header.grids[page] === gridState) header.grids.remove(page) }
            }
        }
        val headerDp = with(androidx.compose.ui.platform.LocalDensity.current) { (header?.heightPx ?: 0).toDp() }
        // Fenrir: how many shelves fit on the screen under the header. A spot's height is taken
        // from the grid once it's laid out (a close guess until then).
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cellW = (maxWidth - 16.dp - CommonMangaItemDefaults.GridHorizontalSpacer * (perRow - 1)) / perRow
        val guessH = cellW / eu.kanade.presentation.manga.components.MangaCover.Book.ratio + 64.dp
        val measuredPx by androidx.compose.runtime.remember(gridState) {
            androidx.compose.runtime.derivedStateOf {
                gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType != "fenrir_header_space" && it.contentType != "library_global_search_item" }?.size?.height ?: 0
            }
        }
        val rowH = if (measuredPx > 0) with(density) { measuredPx.toDp() } else guessH
        // Fenrir: keep the last shelf's titles clear of the glass island at the bottom.
        val islandPad = if (header != null) {
            92.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        } else {
            0.dp
        }
        val bottomPad = contentPadding.calculateBottomPadding() + islandPad
        val room = maxHeight - headerDp - bottomPad - 16.dp
        val screenRows = if (room > 0.dp) {
            kotlin.math.ceil(room / (rowH + CommonMangaItemDefaults.GridVerticalSpacer) - 0.2f).toInt().coerceAtLeast(1)
        } else {
            1
        }
        FastScrollLazyVerticalGrid(
            columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp) + PaddingValues(bottom = islandPad),
            verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
            horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
        ) {
            // Fenrir: room for the glass case + rail, which scroll along with this spot.
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = { "fenrir_header_space" }) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(headerDp))
                }
            }
            content()
            // Fenrir: finish the bookcase down to the bottom of the screen (no further): an
            // "Add a book" spot after your books, then empty spots with a few decorations.
            if (shelfFill >= 0) {
                val used = shelfFill + 1
                val rows = maxOf((used + perRow - 1) / perRow, screenRows)
                val fill = rows * perRow - used
                item(contentType = { "fenrir_add_book" }) { FenrirAddBookCell(titleMaxLines) }
                items(count = fill, contentType = { "fenrir_empty_shelf" }) { i ->
                    FenrirEmptyShelfCell(titleMaxLines, i)
                }
            }
        }
    }
}

/**
 * Invisible trailing item that fires [onLoadMore] when it enters composition (scrolled to end).
 * Keyed on [loadKey] (the per-category pagination generation) so it re-fires on every fetched page
 * even when in-memory filters hid the whole page, so a filtered-out page can't dead-end the
 * category. The key stops changing once the category is exhausted. No-op when [onLoadMore] null.
 */
internal fun LazyGridScope.loadMoreSentinel(loadKey: Long, onLoadMore: (() -> Unit)?) {
    if (onLoadMore == null) return
    item(
        span = { GridItemSpan(maxLineSpan) },
        contentType = { "library_load_more" },
    ) {
        LaunchedEffect(loadKey) { onLoadMore() }
    }
}

internal fun LazyListScope.loadMoreSentinel(loadKey: Long, onLoadMore: (() -> Unit)?) {
    if (onLoadMore == null) return
    item(contentType = "library_load_more") {
        LaunchedEffect(loadKey) { onLoadMore() }
    }
}

internal fun LazyGridScope.globalSearchItem(
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    if (!searchQuery.isNullOrEmpty()) {
        item(
            span = { GridItemSpan(maxLineSpan) },
            contentType = { "library_global_search_item" },
        ) {
            GlobalSearchItem(
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }
    }
}
