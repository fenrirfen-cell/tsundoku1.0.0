package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.nameWithTypeTag
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.components.material.padding

@Composable
fun GlobalSearchScreen(
    state: SearchViewModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    searchKind: FenrirSearchKind? = null,
    onSearchKind: (FenrirSearchKind) -> Unit = {},
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                hideSourceFilter = false,
                sourceFilter = state.sourceFilter,
                pinGroups = state.pinGroups,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        GlobalSearchContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            header = searchKind?.let { kind -> { FenrirSearchKindRow(kind, onSearchKind) } },
        )
    }
}

@Composable
internal fun GlobalSearchContent(
    items: Map<Source, SearchItemResult>,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    fromSourceId: Long? = null,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        if (header != null) item(key = "fenrir-kind") { header() }
        items.forEach { (source, result) ->
            item(key = source.id) {
                val name = source.nameWithTypeTag()
                GlobalSearchResultItem(
                    title = fromSourceId?.let { "▶ $name".takeIf { source.id == fromSourceId } } ?: name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                ) {
                    when (result) {
                        SearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is SearchItemResult.Success -> {
                            GlobalSearchCardRow(
                                titles = result.result,
                                getManga = getManga,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }
                        is SearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}

/** What a global search looks through. Remembered, so the library search opens the way you left it. */
enum class FenrirSearchKind(val label: String) {
    Manga("Manga"), Novels("Novels"), Both("Both");

    companion object {
        private val prefs by lazy {
            uy.kohesive.injekt.Injekt.get<android.app.Application>()
                .getSharedPreferences("fenrir_browse", android.content.Context.MODE_PRIVATE)
        }
        fun last(default: FenrirSearchKind): FenrirSearchKind =
            prefs.getString("search_kind", null)?.let { n -> entries.firstOrNull { it.name == n } } ?: default
        fun remember(kind: FenrirSearchKind) { prefs.edit().putString("search_kind", kind.name).apply() }

        fun screen(query: String, kind: FenrirSearchKind): cafe.adriel.voyager.core.screen.Screen = when (kind) {
            Manga -> eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen(query)
            Novels -> eu.kanade.tachiyomi.ui.browse.source.globalsearch.NovelGlobalSearchScreen(query)
            Both -> eu.kanade.tachiyomi.ui.browse.source.globalsearch.AllGlobalSearchScreen(query)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FenrirSearchKindRow(selected: FenrirSearchKind, onPick: (FenrirSearchKind) -> Unit) {
    // Same chips, spacing and left edge as the Pinned / All / Has results row above it.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = androidx.compose.material3.MaterialTheme.padding.small)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.MaterialTheme.padding.small),
    ) {
        FenrirSearchKind.entries.forEach { k ->
            androidx.compose.material3.FilterChip(
                selected = k == selected,
                onClick = { if (k != selected) onPick(k) },
                label = { Text(k.label) },
            )
        }
    }
}
