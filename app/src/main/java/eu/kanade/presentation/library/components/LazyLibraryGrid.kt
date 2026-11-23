package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        FastScrollLazyVerticalGrid(
            columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
            horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
        ) {
            content()
            // Fenrir: finish the bookcase. Empty spots fill the last shelf, then a few empty
            // shelves follow, so the case never just stops where your books do.
            if (shelfFill >= 0) {
                val fill = (perRow - shelfFill % perRow) % perRow + perRow * 3
                items(count = fill, contentType = { "fenrir_empty_shelf" }) {
                    FenrirEmptyShelfCell(titleMaxLines)
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
