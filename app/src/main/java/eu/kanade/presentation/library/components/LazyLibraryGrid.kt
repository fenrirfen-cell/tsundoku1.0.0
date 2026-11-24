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
        val guessH = cellW / FenrirTomeRatio + 50.dp
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
        val look = FenrirLibraryLook.current.value
        val lux = look == FenrirLibraryLook.Look.Luxury
        val geom = LocalLuxGeom.current
        // Fenrir (Luxury): shelf spots touch each other so the glass and the lights run on; the
        // collection sits inside the case frame (and between the towers on wide screens).
        val luxCols = if (lux) (if (columns > 0) columns else 3) else 0
        val fillCount = if (shelfFill >= 0 && look != FenrirLibraryLook.Look.Regular) {
            val used = shelfFill + 1
            val per = if (lux) luxCols else perRow
            val rows = maxOf((used + per - 1) / per, if (lux) 2 else screenRows)
            rows * per - used
        } else {
            -1
        }
        val bookTitles = LocalLuxBookTitles.current
        val novelShelf = LocalFenrirShelfNovel.current
        val luxCells = androidx.compose.runtime.remember(bookTitles, fillCount, luxCols, novelShelf) {
            if (!lux) {
                null
            } else {
                LuxCells(
                    titles = bookTitles + (if (fillCount >= 0) listOf("Add a book") + List(fillCount) { i -> decorName(decorFor(i, novelShelf)) } else emptyList()),
                    perRow = luxCols,
                )
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalLuxCells provides luxCells) {
        FastScrollLazyVerticalGrid(
            columns = if (lux) GridCells.Fixed(luxCols) else if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = if (lux) {
                contentPadding + PaddingValues(start = geom.side, end = geom.side, top = 0.dp, bottom = 8.dp) + PaddingValues(bottom = islandPad + 24.dp)
            } else {
                contentPadding + PaddingValues(8.dp) + PaddingValues(bottom = islandPad)
            },
            verticalArrangement = Arrangement.spacedBy(if (lux) 0.dp else CommonMangaItemDefaults.GridVerticalSpacer),
            horizontalArrangement = Arrangement.spacedBy(if (lux) 0.dp else CommonMangaItemDefaults.GridHorizontalSpacer),
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
            if (fillCount >= 0) {
                val bookCount = shelfFill
                item(contentType = { "fenrir_add_book" }) {
                    if (lux) LuxAddBookCell(bookCount) else FenrirAddBookCell(titleMaxLines)
                }
                items(count = fillCount, contentType = { "fenrir_empty_shelf" }) { i ->
                    if (lux) {
                        val res = decorFor(i, novelShelf)
                        LuxDecorCell(bookCount + 1 + i, res, decorName(res))
                    } else {
                        FenrirEmptyShelfCell(titleMaxLines, i)
                    }
                }
            }
            // Fenrir (Luxury): the cabinet base right under the last row
            if (lux) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = { "lux_base" }) { LuxBase() }
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
