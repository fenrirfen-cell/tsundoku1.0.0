package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TabbedScreen(
    titleRes: StringResource,
    tabs: List<TabContent>,
    state: PagerState = rememberPagerState { tabs.size },
    searchQuery: String? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    /** Fenrir: (section, kind) per tab, e.g. ("Sources", "Novel"); shown as a switch + small toggle. */
    sectionOf: ((Int) -> Pair<String, String>)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // MugiVerse Browse: walnut background, serif title, gold capsule tabs and Novel / Manga pills
    val mugi = sectionOf != null && eu.kanade.tachiyomi.EmbeddedHost.isMugi

    androidx.compose.foundation.layout.Box(
        if (mugi) Modifier.fillMaxSize().mugiBrowseBackground() else Modifier.fillMaxSize(),
    ) {
    Scaffold(
        containerColor = if (mugi) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            val tab = tabs[state.currentPage]
            val searchEnabled = tab.searchEnabled

            SearchToolbar(
                titleContent = { if (mugi) MugiBrowseTitle(stringResource(titleRes)) else AppBarTitle(stringResource(titleRes)) },
                backgroundColor = if (mugi) androidx.compose.ui.graphics.Color.Transparent else null,
                searchEnabled = searchEnabled,
                searchQuery = if (searchEnabled) searchQuery else null,
                onChangeSearchQuery = onChangeSearchQuery,
                actions = { AppBarActions(tab.actions) },
            )
        },
        snackbarHost = { eu.kanade.presentation.components.FenrirIslandSnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
        ) {
            if (sectionOf != null) {
                // Fenrir: "Sources | Extensions | Migrate" switch, then a small Novel / Manga toggle.
                val pairs = tabs.indices.map(sectionOf)
                val sections = pairs.map { it.first }.distinct()
                val (curSection, curKind) = pairs[state.currentPage.coerceIn(0, pairs.lastIndex)]
                val onSection: (Int) -> Unit = { i ->
                    val target = pairs.indexOfFirst { it.first == sections[i] && it.second == curKind }
                        .takeIf { it >= 0 } ?: pairs.indexOfFirst { it.first == sections[i] }
                    scope.launch { state.animateScrollToPage(target) }
                }
                val sectionBadges = sections.map { sec ->
                    tabs.indices.filter { pairs[it].first == sec }.sumOf { tabs[it].badgeNumber ?: 0 }
                }
                if (mugi) {
                    MugiBrowseTabs(sections, sections.indexOf(curSection), onSection, sectionBadges, Modifier.zIndex(1f))
                } else {
                    FenrirSegmentSwitch(
                        labels = sections,
                        selected = sections.indexOf(curSection),
                        onSelect = onSection,
                        badges = sectionBadges,
                        modifier = Modifier.zIndex(1f),
                    )
                }
                val kindTabs = tabs.indices.filter { pairs[it].first == curSection }
                if (kindTabs.size > 1 && mugi) {
                    MugiKindFilter(
                        labels = kindTabs.map { pairs[it].second },
                        selected = kindTabs.indexOf(state.currentPage),
                        onSelect = { i -> scope.launch { state.animateScrollToPage(kindTabs[i]) } },
                        badges = kindTabs.map { tabs[it].badgeNumber },
                    )
                } else if (kindTabs.size > 1) {
                    FenrirKindToggle(
                        labels = kindTabs.map { pairs[it].second },
                        badges = kindTabs.map { tabs[it].badgeNumber },
                        selected = kindTabs.indexOf(state.currentPage),
                        onSelect = { i -> scope.launch { state.animateScrollToPage(kindTabs[i]) } },
                    )
                }
            } else {
                // Fenrir: tabs as one glass switch (scrolls sideways when there are many).
                FenrirSegmentSwitch(
                    labels = tabs.map { stringResource(it.titleRes) },
                    selected = state.currentPage,
                    onSelect = { index -> scope.launch { state.animateScrollToPage(index) } },
                    badges = tabs.map { it.badgeNumber },
                    modifier = Modifier.zIndex(1f),
                )
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content(
                    // Fenrir: room under the last row so the island never covers it.
                    PaddingValues(bottom = contentPadding.calculateBottomPadding() + 112.dp),
                    snackbarHostState,
                )
            }
        }
    }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val actions: List<AppBar.AppBarAction> = listOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
)
