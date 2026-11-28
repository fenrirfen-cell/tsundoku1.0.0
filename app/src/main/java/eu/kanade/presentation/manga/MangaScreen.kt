package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ChapterHeader
import eu.kanade.presentation.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.manga.components.MangaActionRow
import eu.kanade.presentation.manga.components.MangaBottomActionMenu
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.MangaInfoBox
import eu.kanade.presentation.manga.components.MangaToolbar
import eu.kanade.presentation.manga.components.MissingChapterCountListItem
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.tachiyomi.ui.manga.MangaViewModel
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.missingChaptersCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Instant

@Composable
fun MangaScreen(
    state: MangaViewModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onToggleChapterOrder: () -> Unit = {},
    onChapterOrderForAll: () -> Unit = {},
    onRefresh: () -> Unit,
    onForceRefresh: (() -> Unit)? = null,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onSimilarNovelsClicked: (() -> Unit)?,
    onFindDuplicatesClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,
    onEditClicked: (() -> Unit)? = null,
    onClearCustomInfoClicked: (() -> Unit)? = null,
    onTranslateClicked: (() -> Unit)? = null,
    onTranslateDownloadedClicked: (() -> Unit)? = null,
    onExportEpubClicked: (() -> Unit)? = null,
    onSearchChaptersClicked: (() -> Unit)? = null,
    onWordCountClicked: (() -> Unit)? = null,
    showSourceName: Boolean = true,
    onToggleSourceNameVisibility: (() -> Unit)? = null,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onMultiRemoveFromDbClicked: (List<Chapter>) -> Unit,
    onMultiDeleteTranslationClicked: (List<Chapter>) -> Unit,
    onTranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,
    onRetranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    if (!isTabletUi) {
        MangaScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onToggleChapterOrder = onToggleChapterOrder,
            onChapterOrderForAll = onChapterOrderForAll,
            onRefresh = onRefresh,
            onForceRefresh = onForceRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onSimilarNovelsClicked = onSimilarNovelsClicked,
            onFindDuplicatesClicked = onFindDuplicatesClicked,
            onEditNotesClicked = onEditNotesClicked,
            onEditClicked = onEditClicked,
            onClearCustomInfoClicked = onClearCustomInfoClicked,
            onTranslateClicked = onTranslateClicked,
            onTranslateDownloadedClicked = onTranslateDownloadedClicked,
            onExportEpubClicked = onExportEpubClicked,
            onSearchChaptersClicked = onSearchChaptersClicked,
            onWordCountClicked = onWordCountClicked,
            showSourceName = showSourceName,
            onToggleSourceNameVisibility = onToggleSourceNameVisibility,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onMultiRemoveFromDbClicked = onMultiRemoveFromDbClicked,
            onMultiDeleteTranslationClicked = onMultiDeleteTranslationClicked,
            onTranslateSelectedClicked = onTranslateSelectedClicked,
            onRetranslateSelectedClicked = onRetranslateSelectedClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
        )
    } else {
        MangaScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            nextUpdate = nextUpdate,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onToggleChapterOrder = onToggleChapterOrder,
            onChapterOrderForAll = onChapterOrderForAll,
            onRefresh = onRefresh,
            onForceRefresh = onForceRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onSimilarNovelsClicked = onSimilarNovelsClicked,
            onFindDuplicatesClicked = onFindDuplicatesClicked,
            onEditNotesClicked = onEditNotesClicked,
            onEditClicked = onEditClicked,
            onClearCustomInfoClicked = onClearCustomInfoClicked,
            onTranslateClicked = onTranslateClicked,
            onTranslateDownloadedClicked = onTranslateDownloadedClicked,
            onExportEpubClicked = onExportEpubClicked,
            onSearchChaptersClicked = onSearchChaptersClicked,
            onWordCountClicked = onWordCountClicked,
            showSourceName = showSourceName,
            onToggleSourceNameVisibility = onToggleSourceNameVisibility,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onMultiRemoveFromDbClicked = onMultiRemoveFromDbClicked,
            onMultiDeleteTranslationClicked = onMultiDeleteTranslationClicked,
            onTranslateSelectedClicked = onTranslateSelectedClicked,
            onRetranslateSelectedClicked = onRetranslateSelectedClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
        )
    }
}

@Composable
private fun MangaScreenSmallImpl(
    state: MangaViewModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onToggleChapterOrder: () -> Unit = {},
    onChapterOrderForAll: () -> Unit = {},
    onRefresh: () -> Unit,
    onForceRefresh: (() -> Unit)? = null,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onSimilarNovelsClicked: (() -> Unit)?,
    onFindDuplicatesClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,
    onEditClicked: (() -> Unit)?,
    onClearCustomInfoClicked: (() -> Unit)?,
    onTranslateClicked: (() -> Unit)?,
    onTranslateDownloadedClicked: (() -> Unit)?,
    onExportEpubClicked: (() -> Unit)?,
    onSearchChaptersClicked: (() -> Unit)?,
    onWordCountClicked: (() -> Unit)?,
    showSourceName: Boolean,
    onToggleSourceNameVisibility: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onMultiRemoveFromDbClicked: (List<Chapter>) -> Unit,
    onMultiDeleteTranslationClicked: (List<Chapter>) -> Unit,
    onTranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,
    onRetranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val (chapters, listItem, isAnySelected) = remember(state) {
        Triple(
            first = state.processedChapters,
            second = state.chapterListItems,
            third = state.isAnySelected,
        )
    }

    val chapterListState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollActions = remember(listItem, state.chapters, state.manga) {
        chapterScrollActions(
            scope = scrollScope,
            listState = chapterListState,
            headerItems = ONE_PANE_HEADER_ITEMS,
            listItem = listItem,
            nextUnreadId = { state.chapters.getNextUnread(state.manga)?.id },
            onNoTarget = { context.toast(TDMR.strings.manga_no_unread_chapter) },
        )
    }

    BackHandler(enabled = isAnySelected) {
        onAllChapterSelected(false)
    }

    // Fenrir: Chapters / Pages / Bookmarks in the bottom pill.
    var fenrirTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(FenrirBookTab.Chapters) }
    val fenrirOrdered = remember(chapters, state.manga) {
        val list = chapters.map { it.chapter }
        if (state.manga.sortDescending()) list.reversed() else list
    }
    val fenrirTarget = remember(state.chapters, state.manga) { state.chapters.getNextUnread(state.manga) }
    // Fenrir: on the Pages tab the button follows the chapter you pick; Continue asks when you went back.
    var fenrirPicked by remember(state.manga.id) { mutableStateOf<tachiyomi.domain.chapter.model.Chapter?>(null) }
    var fenrirPopup by remember(state.manga.id) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(fenrirTab) { if (fenrirTab != FenrirBookTab.Pages) fenrirPicked = null }
    val fenrirContext = androidx.compose.ui.platform.LocalContext.current
    val fenrirContinue = rememberFenrirContinue(state.manga, fenrirOrdered) {
        if (fenrirTarget != null) onContinueReading() else fenrirOrdered.firstOrNull()?.let(onChapterClicked)
    }

    Scaffold(
        topBar = {
            val selectedChapterCount = remember(chapters) {
                chapters.count { it.selected }
            }
            val isFirstItemVisible by remember {
                derivedStateOf { chapterListState.firstVisibleItemIndex == 0 }
            }
            val isFirstItemScrolled by remember {
                derivedStateOf { chapterListState.firstVisibleItemScrollOffset > 0 }
            }
            val titleAlpha by animateFloatAsState(
                if (!isFirstItemVisible) 1f else 0f,
                label = "Top Bar Title",
            )
            val backgroundAlpha by animateFloatAsState(
                if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                label = "Top Bar Background",
            )
            MangaToolbar(
                title = state.manga.title,
                vaultMangaId = state.manga.id.takeIf { state.manga.favorite },
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = onFilterClicked,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickSimilarNovels = onSimilarNovelsClicked,
                onClickFindDuplicates = onFindDuplicatesClicked,
                onClickEditNotes = onEditNotesClicked,
                onClickEdit = onEditClicked,
                onClickClearCustomInfo = onClearCustomInfoClicked,
                onClickTranslate = onTranslateClicked,
                onClickTranslateDownloaded = onTranslateDownloadedClicked,
                onClickExportEpub = onExportEpubClicked,
                onClickSearchChapters = onSearchChaptersClicked,
                onClickWordCount = onWordCountClicked,
                onClickScrollToTop = scrollActions.toTop,
                onClickScrollToLastRead = scrollActions.toNextUnread,
                onClickScrollToBottom = scrollActions.toBottom,
                actionModeCounter = selectedChapterCount,
                onCancelActionMode = { onAllChapterSelected(false) },
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                titleAlphaProvider = { titleAlpha },
                backgroundAlphaProvider = { backgroundAlpha },
            )
        },
        bottomBar = {
            val selectedChapters = remember(chapters) {
                chapters.filter { it.selected }
            }
            if (!isAnySelected) {
                val anyRead = remember(state.chapters) { state.chapters.fastAny { it.chapter.read } }
                FenrirBookBar(
                    continueLabel = when {
                        fenrirTab == FenrirBookTab.Pages && fenrirPicked != null -> "Read Ch. ${fenrirChapterNumber(fenrirPicked!!)}"
                        fenrirTarget != null && anyRead -> "Continue · Ch. ${fenrirChapterNumber(fenrirTarget)}"
                        fenrirTarget != null -> "Start · Ch. ${fenrirChapterNumber(fenrirTarget)}"
                        fenrirOrdered.isNotEmpty() -> "Read again"
                        else -> null
                    },
                    onContinue = {
                        val picked = fenrirPicked
                        if (fenrirTab == FenrirBookTab.Pages && picked != null) fenrirOpenAt(fenrirContext, state.manga.id, picked, 0) else fenrirContinue()
                    },
                    bookmarksOpen = fenrirPopup == "bookmarks",
                    onBookmarks = { fenrirPopup = if (fenrirPopup == "bookmarks") null else "bookmarks" },
                    menuOpen = fenrirPopup == "menu",
                    onMenu = { fenrirPopup = if (fenrirPopup == "menu") null else "menu" },
                )
            }
            if (isAnySelected) SharedMangaBottomActionMenu(
                selected = selectedChapters,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                onMultiRemoveFromDbClicked = onMultiRemoveFromDbClicked,
                onDeleteRangeClicked = null, // TODO: Implement delete range dialog
                onMultiDeleteTranslationClicked = onMultiDeleteTranslationClicked,
                onTranslateSelectedClicked = onTranslateSelectedClicked,
                onRetranslateSelectedClicked = onRetranslateSelectedClicked,
                fillFraction = 1f,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            onForceRefresh = onForceRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(top = topPadding),
        ) {
            val layoutDirection = LocalLayoutDirection.current
            VerticalFastScroller(
                listState = chapterListState,
                topContentPadding = topPadding,
                endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    state = chapterListState,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = MangaScreenItem.INFO_BOX,
                        contentType = MangaScreenItem.INFO_BOX,
                    ) {
                        MangaInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            manga = state.manga,
                            sourceName = if (showSourceName) remember { state.source.getNameForMangaInfo() } else "",
                            isStubSource = remember { state.source is StubSource },
                            categories = state.categories,
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                    }

                    // Fenrir: ♡ Library chip and the info card (source, language, reading time, progress).
                    item(key = "fenrir-library-chip", contentType = "fenrir-library-chip") {
                        FenrirLibraryChip(
                            favorite = state.manga.favorite,
                            onClick = onAddToLibraryClicked,
                            onLongClick = onEditCategoryClicked,
                        )
                    }
                    item(key = "fenrir-info-card", contentType = "fenrir-info-card") {
                        FenrirBookInfoCard(
                            manga = state.manga,
                            source = state.source,
                            chapters = remember(state.chapters) { state.chapters.map { it.chapter } },
                            isNovel = state.isNovel,
                        )
                    }

                    item(
                        key = MangaScreenItem.ACTION_ROW,
                        contentType = MangaScreenItem.ACTION_ROW,
                    ) {
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.manga.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onSearchChaptersClicked = onSearchChaptersClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                            showLibraryButton = false,
                        )
                    }

                    item(
                        key = MangaScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = MangaScreenItem.DESCRIPTION_WITH_TAG,
                    ) {
                        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
                        val sortMangaTags by libraryPreferences.sortMangaTags.changes().collectAsState(
                            initial = false,
                        )
                        ExpandableMangaDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.manga.description,
                            tagsProvider = {
                                val tags = state.manga.genre
                                if (sortMangaTags && tags != null) {
                                    tags.sortedBy { it.lowercase() }
                                } else {
                                    tags
                                }
                            },
                            notes = state.manga.notes,
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            onEditNotes = onEditNotesClicked,
                        )
                    }

                    // Fenrir: "You may like" (sequels, side stories, same author, popular on the source).
                    item(key = "fenrir-you-may-like", contentType = "fenrir-you-may-like") {
                        val fenrirNavigator = cafe.adriel.voyager.navigator.LocalNavigator.current
                        eu.kanade.presentation.library.components.FenrirYouMayLike(
                            manga = state.manga,
                            onOpenManga = { id -> fenrirNavigator?.push(eu.kanade.tachiyomi.ui.manga.MangaScreen(id)) },
                        )
                    }

                    item(key = "fenrir-reviews-card", contentType = "fenrir-reviews-card") {
                        run {
                            androidx.compose.runtime.remember(state.manga.id) {
                                eu.kanade.presentation.library.components.FenrirRatings.hintWebnovel(
                                    state.manga.title,
                                    eu.kanade.presentation.library.components.FenrirRatings.webnovelUrlOf(state.manga),
                                )
                            }
                            eu.kanade.presentation.library.components.FenrirReviewsCard(state.manga.title, state.isNovel) { fenrirTab = FenrirBookTab.Reviews }
                        }
                    }
                    item(key = "fenrir-tabs", contentType = "fenrir-tabs") {
                        FenrirBookTabs(fenrirTab, { fenrirTab = it })
                    }
                    when (fenrirTab) {
                        FenrirBookTab.Chapters -> {
                    item(key = "fenrir-skipped", contentType = "fenrir-skipped") {
                        FenrirSkippedChip(
                            mangaId = state.manga.id,
                            ordered = fenrirOrdered,
                            onMarkRead = { onMultiMarkAsReadClicked(it, true) },
                        )
                    }
                    item(
                        key = MangaScreenItem.CHAPTER_HEADER,
                        contentType = MangaScreenItem.CHAPTER_HEADER,
                    ) {
                        val missingChapterCount = remember(chapters) {
                            chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                        }
                        ChapterHeader(
                            enabled = !isAnySelected,
                            chapterCount = chapters.size,
                            missingChapterCount = missingChapterCount,
                            onClick = onFilterClicked,
                        )
                    }

                    item(key = "fenrir-order", contentType = "fenrir-order") {
                        FenrirChapterOrderRow(state.manga.sortDescending(), onToggleChapterOrder)
                    }
                    sharedChapterItems(
                        manga = state.manga,
                        chapters = listItem,
                        isAnyChapterSelected = chapters.fastAny { it.selected },
                        chapterSwipeStartAction = chapterSwipeStartAction,
                        chapterSwipeEndAction = chapterSwipeEndAction,
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter,
                        onChapterSelected = onChapterSelected,
                        onChapterSwipe = onChapterSwipe,
                        isNovel = state.isNovel,
                    )
                        }
                        FenrirBookTab.Pages -> item(key = "fenrir-pages", contentType = "fenrir-pages") {
                            FenrirPagesTab(
                                manga = state.manga,
                                source = state.source,
                                ordered = fenrirOrdered,
                                startChapterId = fenrirTarget?.id,
                                isNovel = state.isNovel,
                                onOpenChapter = onChapterClicked,
                                onPickChapter = { fenrirPicked = it },
                            )
                        }
                        FenrirBookTab.Bookmarks -> {}
                        FenrirBookTab.Reviews -> item(key = "fenrir-reviews", contentType = "fenrir-reviews") {
                            androidx.compose.foundation.layout.Box(Modifier.fillParentMaxHeight()) {
                                eu.kanade.presentation.library.components.FenrirReviewsPanel(state.manga.title, state.isNovel, Modifier.fillMaxSize())
                            }
                        }
                    }
                    item(key = "fenrir-bottom-space") { androidx.compose.foundation.layout.Spacer(Modifier.height(96.dp)) }
                }
            }
        }
            // Fenrir: the tabs pin under the top bar once scrolled up to it.
        val fenrirDensity = androidx.compose.ui.platform.LocalDensity.current
        val fenrirTopPx = with(fenrirDensity) { topPadding.toPx() }
        val fenrirTabsPinned by remember {
            androidx.compose.runtime.derivedStateOf {
                val items = chapterListState.layoutInfo.visibleItemsInfo
                val tabs = items.firstOrNull { it.key == "fenrir-tabs" }
                if (tabs != null) {
                    tabs.offset < fenrirTopPx
                } else {
                    val k = items.firstOrNull()?.key
                    k == MangaScreenItem.CHAPTER_HEADER || (k is String && (k.startsWith("chapter-") || k.startsWith("missing-count") ||
                        k in setOf("fenrir-skipped", "fenrir-pages", "fenrir-reviews", "fenrir-bottom-space")))
                }
            }
        }
        if (fenrirTabsPinned) {
            FenrirBookTabs(fenrirTab, { fenrirTab = it }, Modifier.padding(top = topPadding))
        }
        // Fenrir: 🔖 bookmarks and ⋯ reading options pop up above the bottom bar.
        FenrirBookPopup(
            title = when (fenrirPopup) {
                "bookmarks" -> "🔖 Bookmarks"
                "menu" -> "Read"
                else -> null
            },
            onDismiss = { fenrirPopup = null },
        ) {
            when (fenrirPopup) {
                "bookmarks" -> FenrirBookmarksTab(
                    manga = state.manga,
                    ordered = fenrirOrdered,
                    onOpenChapter = { fenrirPopup = null; onChapterClicked(it) },
                )
                "menu" -> FenrirReadMenu(
                    manga = state.manga,
                    ordered = fenrirOrdered,
                    onIncognito = {
                        runCatching { Injekt.get<eu.kanade.domain.base.BasePreferences>().incognitoMode.set(true) }
                        if (fenrirTarget != null) onContinueReading() else fenrirOrdered.firstOrNull()?.let(onChapterClicked)
                    },
                    onFromStart = { fenrirOrdered.firstOrNull()?.let(onChapterClicked) },
                    onDownloadNext5 = onDownloadActionClicked?.let { dl -> { dl(DownloadAction.NEXT_5_CHAPTERS) } },
                    close = { fenrirPopup = null },
                    newestFirst = state.manga.sortDescending(),
                    onToggleOrder = onToggleChapterOrder,
                    onOrderForAll = onChapterOrderForAll,
                )
            }
        }

    }
}

@Composable
fun MangaScreenLargeImpl(
    state: MangaViewModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onToggleChapterOrder: () -> Unit = {},
    onChapterOrderForAll: () -> Unit = {},
    onRefresh: () -> Unit,
    onForceRefresh: (() -> Unit)? = null,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onSimilarNovelsClicked: (() -> Unit)?,
    onFindDuplicatesClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,
    onEditClicked: (() -> Unit)?,
    onClearCustomInfoClicked: (() -> Unit)?,
    onTranslateClicked: (() -> Unit)?,
    onTranslateDownloadedClicked: (() -> Unit)?,
    onExportEpubClicked: (() -> Unit)?,
    onSearchChaptersClicked: (() -> Unit)?,
    onWordCountClicked: (() -> Unit)?,
    showSourceName: Boolean,
    onToggleSourceNameVisibility: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onMultiRemoveFromDbClicked: (List<Chapter>) -> Unit,
    onMultiDeleteTranslationClicked: (List<Chapter>) -> Unit,
    onTranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,
    onRetranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,

    // For swipe actions
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val (chapters, listItem, isAnySelected) = remember(state) {
        Triple(
            first = state.processedChapters,
            second = state.chapterListItems,
            third = state.isAnySelected,
        )
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }

    val chapterListState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollActions = remember(listItem, state.chapters, state.manga) {
        chapterScrollActions(
            scope = scrollScope,
            listState = chapterListState,
            headerItems = TWO_PANE_HEADER_ITEMS,
            listItem = listItem,
            nextUnreadId = { state.chapters.getNextUnread(state.manga)?.id },
            onNoTarget = { context.toast(TDMR.strings.manga_no_unread_chapter) },
        )
    }

    BackHandler(enabled = isAnySelected) {
        onAllChapterSelected(false)
    }

    // Fenrir: same book page as on phones — Chapters / Pages / Bookmarks in the bottom pill.
    var fenrirTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(FenrirBookTab.Chapters) }
    val fenrirOrdered = remember(chapters, state.manga) {
        val list = chapters.map { it.chapter }
        if (state.manga.sortDescending()) list.reversed() else list
    }
    val fenrirTarget = remember(state.chapters, state.manga) { state.chapters.getNextUnread(state.manga) }
    // Fenrir: on the Pages tab the button follows the chapter you pick; Continue asks when you went back.
    var fenrirPicked by remember(state.manga.id) { mutableStateOf<tachiyomi.domain.chapter.model.Chapter?>(null) }
    var fenrirPopup by remember(state.manga.id) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(fenrirTab) { if (fenrirTab != FenrirBookTab.Pages) fenrirPicked = null }
    val fenrirContext = androidx.compose.ui.platform.LocalContext.current
    val fenrirContinue = rememberFenrirContinue(state.manga, fenrirOrdered) {
        if (fenrirTarget != null) onContinueReading() else fenrirOrdered.firstOrNull()?.let(onChapterClicked)
    }

    Scaffold(
        topBar = {
            val selectedChapterCount = remember(chapters) {
                chapters.count { it.selected }
            }
            MangaToolbar(
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                title = state.manga.title,
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = onFilterButtonClicked,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickSimilarNovels = onSimilarNovelsClicked,
                onClickFindDuplicates = onFindDuplicatesClicked,
                onClickEditNotes = onEditNotesClicked,
                onClickEdit = onEditClicked,
                onClickClearCustomInfo = onClearCustomInfoClicked,
                onClickTranslate = onTranslateClicked,
                onClickTranslateDownloaded = onTranslateDownloadedClicked,
                onClickExportEpub = onExportEpubClicked,
                onClickSearchChapters = onSearchChaptersClicked,
                onClickWordCount = onWordCountClicked,
                onClickScrollToTop = scrollActions.toTop,
                onClickScrollToLastRead = scrollActions.toNextUnread,
                onClickScrollToBottom = scrollActions.toBottom,
                onCancelActionMode = { onAllChapterSelected(false) },
                actionModeCounter = selectedChapterCount,
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                titleAlphaProvider = { 1f },
                backgroundAlphaProvider = { 1f },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                val selectedChapters = remember(chapters) {
                    chapters.filter { it.selected }
                }
                if (!isAnySelected) {
                    val anyRead = remember(state.chapters) { state.chapters.fastAny { it.chapter.read } }
                    Box(Modifier.fillMaxWidth(0.5f)) {
                        FenrirBookBar(
                            continueLabel = when {
                                fenrirTab == FenrirBookTab.Pages && fenrirPicked != null -> "Read Ch. ${fenrirChapterNumber(fenrirPicked!!)}"
                                fenrirTarget != null && anyRead -> "Continue · Ch. ${fenrirChapterNumber(fenrirTarget)}"
                                fenrirTarget != null -> "Start · Ch. ${fenrirChapterNumber(fenrirTarget)}"
                                fenrirOrdered.isNotEmpty() -> "Read again"
                                else -> null
                            },
                            onContinue = {
                                val picked = fenrirPicked
                                if (fenrirTab == FenrirBookTab.Pages && picked != null) fenrirOpenAt(fenrirContext, state.manga.id, picked, 0) else fenrirContinue()
                            },
                            bookmarksOpen = fenrirPopup == "bookmarks",
                            onBookmarks = { fenrirPopup = if (fenrirPopup == "bookmarks") null else "bookmarks" },
                            menuOpen = fenrirPopup == "menu",
                            onMenu = { fenrirPopup = if (fenrirPopup == "menu") null else "menu" },
                        )
                    }
                }
                if (isAnySelected) SharedMangaBottomActionMenu(
                    selected = selectedChapters,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                    onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                    onDownloadChapter = onDownloadChapter,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    onMultiRemoveFromDbClicked = onMultiRemoveFromDbClicked,
                    onDeleteRangeClicked = null, // TODO: Implement delete range dialog
                    onMultiDeleteTranslationClicked = onMultiDeleteTranslationClicked,
                    onTranslateSelectedClicked = onTranslateSelectedClicked,
                    onRetranslateSelectedClicked = onRetranslateSelectedClicked,
                    fillFraction = 0.5f,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            onForceRefresh = onForceRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(
                start = insetPadding.calculateStartPadding(layoutDirection),
                top = with(density) { topBarHeight.toDp() },
                end = insetPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            TwoPanelBox(
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
                startContent = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = contentPadding.calculateBottomPadding()),
                    ) {
                        MangaInfoBox(
                            isTabletUi = true,
                            appBarPadding = contentPadding.calculateTopPadding(),
                            manga = state.manga,
                            sourceName = if (showSourceName) remember { state.source.getNameForMangaInfo() } else "",
                            isStubSource = remember { state.source is StubSource },
                            categories = state.categories,
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                        FenrirLibraryChip(
                            favorite = state.manga.favorite,
                            onClick = onAddToLibraryClicked,
                            onLongClick = onEditCategoryClicked,
                        )
                        FenrirBookInfoCard(
                            manga = state.manga,
                            source = state.source,
                            chapters = remember(state.chapters) { state.chapters.map { it.chapter } },
                            isNovel = state.isNovel,
                        )
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.manga.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onSearchChaptersClicked = onSearchChaptersClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                            showLibraryButton = false,
                        )
                        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
                        val sortMangaTags by libraryPreferences.sortMangaTags.changes().collectAsState(
                            initial = false,
                        )
                        ExpandableMangaDescription(
                            defaultExpandState = true,
                            description = state.manga.description,
                            tagsProvider = {
                                val tags = state.manga.genre
                                if (sortMangaTags && tags != null) {
                                    tags.sortedBy { it.lowercase() }
                                } else {
                                    tags
                                }
                            },
                            notes = state.manga.notes,
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            onEditNotes = onEditNotesClicked,
                        )
                        val fenrirNavigator = cafe.adriel.voyager.navigator.LocalNavigator.current
                        eu.kanade.presentation.library.components.FenrirYouMayLike(
                            manga = state.manga,
                            onOpenManga = { id -> fenrirNavigator?.push(eu.kanade.tachiyomi.ui.manga.MangaScreen(id)) },
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(96.dp))
                    }
                },
                endContent = {
                    VerticalFastScroller(
                        listState = chapterListState,
                        topContentPadding = contentPadding.calculateTopPadding(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            state = chapterListState,
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            item(key = "fenrir-reviews-card", contentType = "fenrir-reviews-card") {
                        run {
                            androidx.compose.runtime.remember(state.manga.id) {
                                eu.kanade.presentation.library.components.FenrirRatings.hintWebnovel(
                                    state.manga.title,
                                    eu.kanade.presentation.library.components.FenrirRatings.webnovelUrlOf(state.manga),
                                )
                            }
                            eu.kanade.presentation.library.components.FenrirReviewsCard(state.manga.title, state.isNovel) { fenrirTab = FenrirBookTab.Reviews }
                        }
                    }
                    item(key = "fenrir-tabs", contentType = "fenrir-tabs") {
                        FenrirBookTabs(fenrirTab, { fenrirTab = it })
                    }
                    when (fenrirTab) {
                                FenrirBookTab.Chapters -> {
                            item(key = "fenrir-skipped", contentType = "fenrir-skipped") {
                                FenrirSkippedChip(
                                    mangaId = state.manga.id,
                                    ordered = fenrirOrdered,
                                    onMarkRead = { onMultiMarkAsReadClicked(it, true) },
                                )
                            }
                            item(
                                key = MangaScreenItem.CHAPTER_HEADER,
                                contentType = MangaScreenItem.CHAPTER_HEADER,
                            ) {
                                val missingChapterCount = remember(chapters) {
                                    chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                                }
                                ChapterHeader(
                                    enabled = !isAnySelected,
                                    chapterCount = chapters.size,
                                    missingChapterCount = missingChapterCount,
                                    onClick = onFilterButtonClicked,
                                )
                            }

                            item(key = "fenrir-order", contentType = "fenrir-order") {
                        FenrirChapterOrderRow(state.manga.sortDescending(), onToggleChapterOrder)
                    }
                    sharedChapterItems(
                                manga = state.manga,
                                chapters = listItem,
                                isAnyChapterSelected = chapters.fastAny { it.selected },
                                chapterSwipeStartAction = chapterSwipeStartAction,
                                chapterSwipeEndAction = chapterSwipeEndAction,
                                onChapterClicked = onChapterClicked,
                                onDownloadChapter = onDownloadChapter,
                                onChapterSelected = onChapterSelected,
                                onChapterSwipe = onChapterSwipe,
                                isNovel = state.isNovel,
                            )
                                }
                                FenrirBookTab.Pages -> item(key = "fenrir-pages", contentType = "fenrir-pages") {
                                    FenrirPagesTab(
                                        manga = state.manga,
                                        source = state.source,
                                        ordered = fenrirOrdered,
                                        startChapterId = fenrirTarget?.id,
                                        isNovel = state.isNovel,
                                        onOpenChapter = onChapterClicked,
                                        onPickChapter = { fenrirPicked = it },
                                    )
                                }
                                FenrirBookTab.Bookmarks -> {}
                                FenrirBookTab.Reviews -> item(key = "fenrir-reviews", contentType = "fenrir-reviews") {
                            androidx.compose.foundation.layout.Box(Modifier.fillParentMaxHeight()) {
                                eu.kanade.presentation.library.components.FenrirReviewsPanel(state.manga.title, state.isNovel, Modifier.fillMaxSize())
                            }
                        }
                            }
                            item(key = "fenrir-bottom-space") { androidx.compose.foundation.layout.Spacer(Modifier.height(96.dp)) }
                        }
                    }
                },
            )
        }
            // Fenrir: 🔖 bookmarks and ⋯ reading options pop up above the bottom bar.
        FenrirBookPopup(
            title = when (fenrirPopup) {
                "bookmarks" -> "🔖 Bookmarks"
                "menu" -> "Read"
                else -> null
            },
            onDismiss = { fenrirPopup = null },
        ) {
            when (fenrirPopup) {
                "bookmarks" -> FenrirBookmarksTab(
                    manga = state.manga,
                    ordered = fenrirOrdered,
                    onOpenChapter = { fenrirPopup = null; onChapterClicked(it) },
                )
                "menu" -> FenrirReadMenu(
                    manga = state.manga,
                    ordered = fenrirOrdered,
                    onIncognito = {
                        runCatching { Injekt.get<eu.kanade.domain.base.BasePreferences>().incognitoMode.set(true) }
                        if (fenrirTarget != null) onContinueReading() else fenrirOrdered.firstOrNull()?.let(onChapterClicked)
                    },
                    onFromStart = { fenrirOrdered.firstOrNull()?.let(onChapterClicked) },
                    onDownloadNext5 = onDownloadActionClicked?.let { dl -> { dl(DownloadAction.NEXT_5_CHAPTERS) } },
                    close = { fenrirPopup = null },
                    newestFirst = state.manga.sortDescending(),
                    onToggleOrder = onToggleChapterOrder,
                    onOrderForAll = onChapterOrderForAll,
                )
            }
        }

    }
}

// Items each layout's LazyColumn shows before the chapter rows.
private const val ONE_PANE_HEADER_ITEMS = 4
private const val TWO_PANE_HEADER_ITEMS = 1

private class ChapterScrollActions(
    val toTop: () -> Unit,
    val toNextUnread: () -> Unit,
    val toBottom: () -> Unit,
)

private fun chapterScrollActions(
    scope: CoroutineScope,
    listState: LazyListState,
    headerItems: Int,
    listItem: List<ChapterList>,
    nextUnreadId: () -> Long?,
    onNoTarget: () -> Unit,
) = ChapterScrollActions(
    toTop = { scope.launch { listState.animateScrollToItem(0) } },
    toNextUnread = {
        val id = nextUnreadId()
        val index = listItem.indexOfFirst { it is ChapterList.Item && it.chapter.id == id }
        if (id == null || index < 0) {
            onNoTarget()
        } else {
            scope.launch {
                val halfHeight = listState.layoutInfo.viewportSize.height / 2
                listState.animateScrollToItem(headerItems + index, scrollOffset = -halfHeight)
            }
        }
    },
    toBottom = { scope.launch { listState.animateScrollToItem(headerItems + listItem.lastIndex) } },
)

@Composable
private fun SharedMangaBottomActionMenu(
    selected: List<ChapterList.Item>,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onMultiRemoveFromDbClicked: (List<Chapter>) -> Unit,
    onDeleteRangeClicked: (() -> Unit)?,
    onMultiDeleteTranslationClicked: ((List<Chapter>) -> Unit)? = null,
    onTranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,
    onRetranslateSelectedClicked: ((List<Chapter>) -> Unit)? = null,
    fillFraction: Float,
    modifier: Modifier = Modifier,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAll { it.chapter.bookmark } },
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAny { it.chapter.read || it.chapter.lastPageRead > 0L } },
        onMarkPreviousAsReadClicked = {
            onMarkPreviousAsReadClicked(selected[0].chapter)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadChapter!!(selected.toList(), ChapterDownloadAction.START)
        }.takeIf {
            onDownloadChapter != null && selected.fastAny { it.downloadState != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.chapter })
        }.takeIf {
            selected.fastAny { it.downloadState == Download.State.DOWNLOADED }
        },
        onRemoveFromDbClicked = {
            onMultiRemoveFromDbClicked(selected.fastMap { it.chapter })
        },
        onDeleteRangeClicked = onDeleteRangeClicked,
        onDeleteTranslationClicked = onMultiDeleteTranslationClicked?.let {
            {
                it(selected.fastMap { item -> item.chapter })
            }.takeIf {
                selected.fastAny { item -> item.hasTranslation }
            }
        },
        onTranslateClicked = onTranslateSelectedClicked?.let {
            {
                it(selected.fastMap { item -> item.chapter })
            }.takeIf {
                selected.fastAny { item -> item.downloadState == Download.State.DOWNLOADED }
            }
        },
        onRetranslateClicked = onRetranslateSelectedClicked?.let {
            {
                it(selected.fastMap { item -> item.chapter })
            }.takeIf {
                selected.fastAny { item -> item.hasTranslation }
            }
        },
    )
}

private fun LazyListScope.sharedChapterItems(
    manga: Manga,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    isNovel: Boolean = false,
) {
    items(
        items = chapters,
        key = { item ->
            when (item) {
                is ChapterList.MissingCount -> "missing-count-${item.id}"
                is ChapterList.Item -> "chapter-${item.id}"
            }
        },
        contentType = { MangaScreenItem.CHAPTER },
    ) { item ->
        val haptic = LocalHapticFeedback.current

        when (item) {
            is ChapterList.MissingCount -> {
                MissingChapterCountListItem(count = item.count)
            }
            is ChapterList.Item -> {
                MangaChapterListItem(
                    title = when (manga.displayMode) {
                        Manga.CHAPTER_DISPLAY_NUMBER -> stringResource(
                            MR.strings.display_mode_chapter,
                            formatChapterNumber(item.chapter.chapterNumber),
                        )
                        Manga.CHAPTER_DISPLAY_BOTH -> {
                            val chapterNum = stringResource(
                                MR.strings.display_mode_chapter,
                                formatChapterNumber(item.chapter.chapterNumber),
                            )
                            if (item.chapter.name.isNotBlank() &&
                                !item.chapter.name.equals(chapterNum, ignoreCase = true)
                            ) {
                                "$chapterNum - ${item.chapter.name}"
                            } else {
                                chapterNum
                            }
                        }
                        else -> item.chapter.name
                    },
                    date = relativeDateText(item.chapter.dateUpload),
                    readProgress = item.chapter.lastPageRead
                        .takeIf { !item.chapter.read && it > 0L }
                        ?.let {
                            if (isNovel) {
                                // For novels, lastPageRead stores progress percentage (0-100)
                                stringResource(
                                    TDMR.strings.chapter_progress_novel,
                                    it.toInt(),
                                )
                            } else {
                                stringResource(
                                    MR.strings.chapter_progress,
                                    it + 1,
                                )
                            }
                        },
                    scanlator = item.chapter.scanlator.takeIf { !it.isNullOrBlank() },
                    read = item.chapter.read,
                    bookmark = item.chapter.bookmark,
                    hasTranslation = item.hasTranslation,
                    selected = item.selected,
                    downloadIndicatorEnabled = !isAnyChapterSelected && !manga.isLocal(),
                    downloadStateProvider = { item.downloadState },
                    downloadProgressProvider = { item.downloadProgress },
                    chapterSwipeStartAction = chapterSwipeStartAction,
                    chapterSwipeEndAction = chapterSwipeEndAction,
                    onLongClick = {
                        onChapterSelected(item, !item.selected, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        onChapterItemClick(
                            chapterItem = item,
                            isAnyChapterSelected = isAnyChapterSelected,
                            onToggleSelection = { onChapterSelected(item, !item.selected, false) },
                            onChapterClicked = onChapterClicked,
                        )
                    },
                    onDownloadClick = if (onDownloadChapter != null) {
                        { onDownloadChapter(listOf(item), it) }
                    } else {
                        null
                    },
                    onChapterSwipe = {
                        onChapterSwipe(item, it)
                    },
                )
            }
        }
    }
}

private fun onChapterItemClick(
    chapterItem: ChapterList.Item,
    isAnyChapterSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onChapterClicked: (Chapter) -> Unit,
) {
    when {
        chapterItem.selected -> onToggleSelection(false)
        isAnyChapterSelected -> onToggleSelection(true)
        else -> onChapterClicked(chapterItem.chapter)
    }
}


private fun fenrirChapterNumber(chapter: Chapter): String =
    chapter.chapterNumber.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.let { if (it == "-1") "?" else it }
