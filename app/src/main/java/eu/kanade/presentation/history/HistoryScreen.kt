package eu.kanade.presentation.history

import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import eu.kanade.presentation.components.mugiCoverBorder
import eu.kanade.presentation.components.mugiReadScreenBackground
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.FenrirReadUi
import eu.kanade.presentation.components.FenrirSectionLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.history.HistoryFilter
import eu.kanade.tachiyomi.ui.history.HistoryViewModel
import kotlinx.datetime.LocalDate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun HistoryScreen(
    state: HistoryViewModel.State,
    snackbarHostState: SnackbarHostState,
    showFilterChips: Boolean = true,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onDialogChange: (HistoryViewModel.Dialog?) -> Unit,
    onFilterSelected: (HistoryFilter) -> Unit,
    onGroupByNovelChanged: (Boolean) -> Unit,
    onLoadNextPage: () -> Unit,
    showAllFilter: Boolean = true,
    showMangaFilter: Boolean = true,
) {
    // MugiVerse: the captain's reading log (walnut, gold title and buttons, stats panel, list rows)
    val mugi = eu.kanade.tachiyomi.EmbeddedHost.isMugi
    val mugiContext = androidx.compose.ui.platform.LocalContext.current
    var mugiGrid by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(
            mugiContext.getSharedPreferences("fenrir_ui", android.content.Context.MODE_PRIVATE).getBoolean("mugi_history_grid", false),
        )
    }
    Box(if (mugi) Modifier.fillMaxSize().then(Modifier.mugiReadScreenBackground()) else Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = if (mugi) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = { scrollBehavior ->
            val fenrirTopShown by eu.kanade.presentation.components.FenrirBars.shown.collectAsState()
            androidx.compose.animation.AnimatedVisibility(
                visible = fenrirTopShown || state.searchQuery != null,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
            SearchToolbar(
                titleContent = {
                    if (mugi) {
                        eu.kanade.presentation.components.MugiScreenTitle(stringResource(MR.strings.history))
                    } else {
                        AppBarTitle(stringResource(MR.strings.history))
                    }
                },
                backgroundColor = if (mugi) Color.Transparent else null,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onSearchQueryChange,
                actions = {
                    AppBarActions(
                        listOfNotNull(
                            if (mugi) {
                                AppBar.Action(
                                    title = if (mugiGrid) "Show as list" else "Show as grid",
                                    icon = if (mugiGrid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                    onClick = {
                                        mugiGrid = !mugiGrid
                                        mugiContext.getSharedPreferences("fenrir_ui", android.content.Context.MODE_PRIVATE)
                                            .edit().putBoolean("mugi_history_grid", mugiGrid).apply()
                                    },
                                )
                            } else {
                                null
                            },
                            AppBar.Action(
                                title = if (state.groupByNovel) "List View" else "Last only",
                                icon = if (state.groupByNovel) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                onClick = { onGroupByNovelChanged(!state.groupByNovel) },
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.pref_clear_history),
                                icon = Icons.Outlined.DeleteSweep,
                                onClick = {
                                    onDialogChange(HistoryViewModel.Dialog.DeleteAll)
                                },
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
            }
        },
        snackbarHost = { eu.kanade.presentation.components.FenrirIslandSnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val fenrirBarsShown by eu.kanade.presentation.components.FenrirBars.shown.collectAsState()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(contentPadding),
        ) {
            if (showFilterChips && mugi) {
                eu.kanade.presentation.components.MugiReadChipRow {
                    if (showAllFilter) {
                        eu.kanade.presentation.components.MugiReadChip(stringResource(MR.strings.all), state.filter == HistoryFilter.ALL) { onFilterSelected(HistoryFilter.ALL) }
                    }
                    if (showMangaFilter) {
                        eu.kanade.presentation.components.MugiReadChip(stringResource(TDMR.strings.label_manga), state.filter == HistoryFilter.MANGA) { onFilterSelected(HistoryFilter.MANGA) }
                    }
                    eu.kanade.presentation.components.MugiReadChip(stringResource(TDMR.strings.label_novels), state.filter == HistoryFilter.NOVELS) { onFilterSelected(HistoryFilter.NOVELS) }
                }
            } else if (showFilterChips) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showAllFilter) {
                        FilterChip(
                            selected = state.filter == HistoryFilter.ALL,
                            onClick = { onFilterSelected(HistoryFilter.ALL) },
                            label = { Text(stringResource(MR.strings.all)) },
                        )
                    }
                    if (showMangaFilter) {
                        FilterChip(
                            selected = state.filter == HistoryFilter.MANGA,
                            onClick = { onFilterSelected(HistoryFilter.MANGA) },
                            label = { Text(stringResource(TDMR.strings.label_manga)) },
                        )
                    }
                    FilterChip(
                        selected = state.filter == HistoryFilter.NOVELS,
                        onClick = { onFilterSelected(HistoryFilter.NOVELS) },
                        label = { Text(stringResource(TDMR.strings.label_novels)) },
                    )
                }
            }

            state.list.let {
                if (it == null) {
                    LoadingScreen()
                } else if (it.isEmpty()) {
                    val msg = if (!state.searchQuery.isNullOrEmpty()) {
                        MR.strings.no_results_found
                    } else {
                        MR.strings.information_no_recent_manga
                    }
                    EmptyScreen(
                        stringRes = msg,
                    )
                } else {
                    HistoryScreenContent(
                        history = it,
                        onClickCover = { history -> onClickCover(history.mangaId) },
                        onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                        onClickDelete = { item -> onDialogChange(HistoryViewModel.Dialog.Delete(item)) },
                        onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                        onLoadNextPage = onLoadNextPage,
                        mugi = mugi,
                        mugiGrid = mugiGrid,
                    )
                }
            }
        }
            eu.kanade.presentation.components.FenrirScrollPill(
            label = stringResource(MR.strings.history),
            visible = false && !fenrirBarsShown && state.searchQuery == null,
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
            actions = listOf(androidx.compose.material.icons.Icons.Outlined.Search to { eu.kanade.presentation.components.FenrirBars.shown.value = true; onSearchQueryChange("") }),
        )
        }
    }
    }
}

@Composable
private fun HistoryScreenContent(
    history: List<HistoryUiModel>,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
    onLoadNextPage: () -> Unit,
    mugi: Boolean = false,
    mugiGrid: Boolean = false,
) {
    // Fenrir: days as sections, each day a grid of covers (3 per row) with an open-book button.
    val sections = remember(history) {
        val out = mutableListOf<Pair<LocalDate?, MutableList<HistoryWithRelations>>>()
        history.forEach { model ->
            when (model) {
                is HistoryUiModel.Header -> out += model.date to mutableListOf()
                is HistoryUiModel.Item -> {
                    if (out.isEmpty()) out += (null as LocalDate?) to mutableListOf()
                    out.last().second += model.item
                }
            }
        }
        out.filter { it.second.isNotEmpty() }
    }
    val weekAgo = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
    val thisWeek = remember(history) {
        history.mapNotNull { (it as? HistoryUiModel.Item)?.item }.filter { (it.readAt?.time ?: 0) >= weekAgo }
    }
    // Fenrir: room at the end so the last covers can scroll up above the glass island
    FastScrollLazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp)) {
        item(key = "fenrir-history-stats") {
            val minutes = thisWeek.sumOf { it.readDuration } / 60000
            val time = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
            if (mugi) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                eu.kanade.presentation.components.MugiReadingStatsPanel(
                    chapters = thisWeek.size,
                    titles = thisWeek.map { it.mangaId }.distinct().size,
                    readingTime = time,
                    // tap: Captain's Log (the full stats, streaks and bounties)
                    modifier = if (eu.kanade.tachiyomi.EmbeddedHost.openVoyage != null) {
                        Modifier.clickable(onClickLabel = "Open Captain's Log") { eu.kanade.tachiyomi.EmbeddedHost.voyage(ctx, "LOG") }
                    } else {
                        Modifier
                    },
                )
                return@item
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(0f to FenrirReadUi.Accent.copy(alpha = 0.16f), 0.6f to FenrirReadUi.Card))
                    .border(1.dp, FenrirReadUi.CardBorder, RoundedCornerShape(18.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    "${thisWeek.size}" to "chapters this week",
                    "${thisWeek.map { it.mangaId }.distinct().size}" to "titles",
                    time to "reading",
                ).forEach { (big, small) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(big, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(small, color = FenrirReadUi.Muted, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        sections.forEachIndexed { sectionIndex, (date, items) ->
            item(key = "fenrir-h-$sectionIndex-$date") {
                if (mugi) {
                    eu.kanade.presentation.components.MugiSectionTitle(
                        if (date != null) relativeDateText(date) else "Earlier",
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
                    )
                } else {
                    FenrirSectionLabel(if (date != null) relativeDateText(date) else "Earlier")
                }
            }
            if (mugi && !mugiGrid) {
                items.forEachIndexed { i, entry ->
                    item(key = "mugi-h-$sectionIndex-$i-${entry.id}") {
                        MugiHistoryRow(
                            entry = entry,
                            onResume = { onClickResume(entry) },
                            onOpen = { onClickCover(entry) },
                            onDelete = { onClickDelete(entry) },
                            showSeparator = i < items.lastIndex,
                        )
                    }
                }
                return@forEachIndexed
            }
            items.chunked(3).forEachIndexed { rowIndex, row ->
                item(key = "fenrir-h-$sectionIndex-$rowIndex") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { entry ->
                            HistoryCoverCard(
                                entry = entry,
                                onResume = { onClickResume(entry) },
                                onOpen = { onClickCover(entry) },
                                onDelete = { onClickDelete(entry) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item(key = "history-load-more") {
            LaunchedEffect(Unit) {
                onLoadNextPage()
            }
        }
    }
}

/** MugiVerse: a reading-log row (gold-framed cover, serif title, "Ch. 265 · 8:14 PM", gold resume button). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MugiHistoryRow(
    entry: HistoryWithRelations,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    showSeparator: Boolean,
) {
    val time = remember(entry.readAt) {
        entry.readAt?.let { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(it) }.orEmpty()
    }
    val chapter = if (entry.chapterNumber >= 0) {
        val n = entry.chapterNumber
        "Ch. " + if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
    } else {
        ""
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onResume, onLongClick = onDelete)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            eu.kanade.presentation.manga.components.MangaCover.Book(
                modifier = Modifier.width(86.dp).then(Modifier.mugiCoverBorder()),
                data = entry.coverData,
                onClick = onOpen,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    color = eu.kanade.presentation.components.MugiScreenColors.Ivory,
                    fontFamily = eu.kanade.presentation.components.MugiPlayfair,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    listOf(chapter, time).filter { it.isNotBlank() }.joinToString(" · "),
                    color = eu.kanade.presentation.components.MugiScreenColors.Secondary,
                    fontFamily = eu.kanade.presentation.components.MugiPlayfair,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
            eu.kanade.presentation.components.MugiHeaderAction(
                icon = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook,
                description = "Resume reading",
                filled = true,
                onClick = onResume,
            )
        }
        if (showSeparator) eu.kanade.presentation.components.MugiRowSeparator(start = 116.dp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryCoverCard(
    entry: HistoryWithRelations,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = remember(entry.readAt) {
        entry.readAt?.let { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(it) }.orEmpty()
    }
    val chapter = if (entry.chapterNumber >= 0) {
        val n = entry.chapterNumber
        "Ch. " + if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
    } else {
        ""
    }
    Column(modifier.combinedClickable(onClick = onResume, onLongClick = onDelete)) {
        Box {
            eu.kanade.presentation.manga.components.MangaCover.Book(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                data = entry.coverData,
                onClick = onOpen,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FenrirReadUi.Accent)
                    .clickable(onClick = onResume),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            entry.title,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            listOf(chapter, time).filter { it.isNotBlank() }.joinToString(" · "),
            color = Color(0xFF8E8E99),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
}

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryviewModelStateProvider::class)
    historyState: HistoryViewModel.State,
) {
    TachiyomiPreviewTheme {
        HistoryScreen(
            state = historyState,
            snackbarHostState = SnackbarHostState(),
            onSearchQueryChange = {},
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onDialogChange = {},
            onClickFavorite = {},
            onFilterSelected = {},
            onGroupByNovelChanged = {},
            onLoadNextPage = {},
            showMangaFilter = true,
        )
    }
}
