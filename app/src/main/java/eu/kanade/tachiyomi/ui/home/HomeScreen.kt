package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.EmbeddedModeSwitchBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.library.NovelsTab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.channels.Channel
import androidx.compose.ui.draw.drawBehind
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object HomeScreen : Screen() {

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    @Suppress("ConstPropertyName")
    private const val TabFadeDuration = 200

    @Suppress("ConstPropertyName")
    private const val TabNavigatorKey = "HomeTabs"

    private val TABS = listOf(
        NovelsTab,
        LibraryTab,
        UpdatesTab,
        HistoryTab,
        BrowseTab,
        MoreTab,
    )

    private val JOINED_TABS = listOf(
        NovelsTab,
        UpdatesTab,
        HistoryTab,
        BrowseTab,
        MoreTab,
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val libraryPreferences = remember { Injekt.get<tachiyomi.domain.library.service.LibraryPreferences>() }
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val isJoined by libraryPreferences.joinedLibrary.collectAsState()
        val hideMangaUi by basePreferences.hideMangaUi.collectAsState()
        val tabs = if (isJoined || hideMangaUi) JOINED_TABS else TABS
        TabNavigator(
            tab = NovelsTab,
            key = TabNavigatorKey,
        ) { tabNavigator ->
            // Provide usable navigator to content screen
            CompositionLocalProvider(LocalNavigator provides navigator) {
                // Fenrir: like the Watch side, scrolling down tucks away the FENRIR READ header
                // and the glass bar; scrolling up brings them back.
                val barsShown by eu.kanade.presentation.components.FenrirBars.shown.collectAsState()
                LaunchedEffect(tabNavigator.current) { eu.kanade.presentation.components.FenrirBars.shown.value = true }
                // The header follows the finger (no timed animation resizing the screen mid-scroll);
                // the island only flips after a clear swipe in one direction, so it doesn't flicker.
                val followBar = remember { eu.kanade.presentation.library.components.FenrirFollowBar() }
                LaunchedEffect(tabNavigator.current) { followBar.show() }
                val barsScroll = remember {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        var run = 0f
                        override fun onPreScroll(
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                        ): androidx.compose.ui.geometry.Offset {
                            // Fenrir: the top bars stay put (hiding/showing them pushed the books
                            // up and down while scrolling).
                            return androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
                Scaffold(
                    // Watch / Read switch; only drawn when embedded in Nuvio.
                    topBar = {
                        androidx.compose.foundation.layout.Column {
                        // Fenrir: the crown (with the Watch / Read switch) only on the library; the other
                        // pages keep their own plain tops.
                        val onLibrary = tabNavigator.current == LibraryTab || tabNavigator.current == NovelsTab
                        if (onLibrary || !eu.kanade.tachiyomi.EmbeddedHost.isFenrirStyle) {
                            androidx.compose.foundation.layout.Box(Modifier.then(followBar.modifier)) {
                                EmbeddedModeSwitchBar()
                            }
                        }
                        }
                    },
                    // Fenrir: the glass island on every screen size (no side rail on tablets and folds).
                    // Fenrir: no bottomBar slot (it drew a black strip); the glass island floats over the content.
                    contentWindowInsets = WindowInsets(0),
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .consumeWindowInsets(contentPadding)
                            .nestedScroll(barsScroll),
                    ) {
                        val mugi = eu.kanade.tachiyomi.EmbeddedHost.isMugi
                        val mugiMenu = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                        val bottomNavVisible by produceState(initialValue = true) {
                            showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                        }
                        // Fenrir: what's behind the island, so its glass can blur it (like the Watch side).
                        val fenrirHaze = dev.chrisbanes.haze.rememberHazeState()
                        // Fenrir: every Read page sits on the same dark wood as the library, with gold
                        // headings and gold-edged lines (the pages' own dark backgrounds go see-through).
                        val fenrirWood = false // wood and gold pages were tried; the other pages stay plain
                        val baseScheme = MaterialTheme.colorScheme
                        val woodScheme = remember(baseScheme, fenrirWood) {
                            if (!fenrirWood) baseScheme else baseScheme.copy(
                                background = androidx.compose.ui.graphics.Color.Transparent,
                                surface = androidx.compose.ui.graphics.Color.Transparent,
                                outline = androidx.compose.ui.graphics.Color(0xFF9A7638),
                                outlineVariant = androidx.compose.ui.graphics.Color(0xFF5C4524),
                            )
                        }
                        val woodBack = if (fenrirWood) eu.kanade.presentation.library.components.fenrirPageWoodModifier() else Modifier
                        MaterialTheme(colorScheme = woodScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            tachiyomi.presentation.core.components.LocalListGroupHeaderColor provides
                                if (fenrirWood) androidx.compose.ui.graphics.Color(0xFFE8B95A) else androidx.compose.ui.graphics.Color.Unspecified,
                        ) {
                        AnimatedContent(
                            modifier = Modifier.then(woodBack).hazeSource(state = fenrirHaze),
                            targetState = tabNavigator.current,
                            transitionSpec = {
                                materialFadeThroughIn(initialScale = 1f, durationMillis = TabFadeDuration) togetherWith
                                    materialFadeThroughOut(durationMillis = TabFadeDuration)
                            },
                            label = "tabContent",
                        ) {
                            tabNavigator.saveableState(key = "currentTab", it) {
                                it.Content()
                            }
                        }
                        }
                        }
                        // Fenrir: "may not work · Install anyway?" for novel plugins whose site is down.
                        eu.kanade.presentation.browse.FenrirInstallCheckDialog()
                        // MugiVerse: the compass menu (Watch / Ani-Drama / Read), over the page.
                        if (mugi) {
                            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
                            eu.kanade.presentation.components.MugiReadModeMenu(
                                expanded = mugiMenu.value,
                                onDismiss = { mugiMenu.value = false },
                                onPick = { m ->
                                    mugiMenu.value = false
                                    when (m) {
                                        eu.kanade.presentation.components.MugiReadMode.WATCH ->
                                            activity?.let { eu.kanade.tachiyomi.EmbeddedHost.openWatch?.invoke(it) }
                                        eu.kanade.presentation.components.MugiReadMode.ANI_DRAMA ->
                                            activity?.let { eu.kanade.tachiyomi.EmbeddedHost.openAniDrama?.invoke(it) }
                                        eu.kanade.presentation.components.MugiReadMode.READ -> Unit
                                    }
                                },
                            )
                        }
                        run {
                            AnimatedVisibility(
                                visible = (mugiMenu.value || bottomNavVisible && barsShown) &&
                                    eu.kanade.presentation.library.components.FenrirBookFlight.current == null,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it } +
                                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
                                exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(260)) { it } +
                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                            ) {
                                if (mugi) {
                                    MugiNavBar(tabs, mugiMenu.value) { mugiMenu.value = !mugiMenu.value }
                                } else {
                                    FenrirGlassNavBar(tabs, fenrirHaze)
                                }
                            }
                        }
                    }
                }
            }

            val goToNovelsTab = { tabNavigator.current = NovelsTab }

            BackHandler(enabled = tabNavigator.current != NovelsTab, onBack = goToNovelsTab)

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        goToNovelsTab()
                        NovelsTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = when (it) {
                            is Tab.Library -> if (isJoined || hideMangaUi) NovelsTab else LibraryTab
                            Tab.Updates -> UpdatesTab
                            Tab.History -> HistoryTab
                            is Tab.Browse -> {
                                if (it.toExtensions) {
                                    BrowseTab.showExtension()
                                }
                                BrowseTab
                            }
                            is Tab.More -> MoreTab
                        }

                        if (it is Tab.Library && it.mangaIdToOpen != null) {
                            navigator.push(MangaScreen(it.mangaIdToOpen))
                        }
                        if (it is Tab.More && it.toDownloads) {
                            navigator.push(DownloadQueueScreen())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FenrirGlassNavBar(tabs: List<eu.kanade.presentation.util.Tab>, hazeState: dev.chrisbanes.haze.HazeState) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.Companion.navigationBars)
                .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .height(68.dp),
            ) {
                // Fenrir: the same see-through glass as the Watch side's island (blurs what's behind)
                androidx.compose.foundation.layout.Box(Modifier.matchParentSize().clip(shape)) {
                    eu.kanade.presentation.components.glass.GlassBarSurface(hazeState, Modifier.matchParentSize())
                }
                androidx.compose.foundation.layout.Row(
                    Modifier.matchParentSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.fastForEach { GlassNavItem(it) }
                }
            }
        }
    }

    // MugiVerse: Library · Updates · compass · History · Browse · More, gold rim, amber highlight.
    @Composable
    private fun MugiNavBar(tabs: List<eu.kanade.presentation.util.Tab>, menuOpen: Boolean, onCompass: () -> Unit) {
        val amber = androidx.compose.ui.graphics.Color(0xFFE4BC77) // MugiVerse antique gold
        // MugiVerse: More lives in the header (⋯ More next to the bell); the bar keeps four tabs
        val tabNavigator = LocalTabNavigator.current
        val onMore = tabNavigator.current == MoreTab
        androidx.compose.runtime.SideEffect {
            eu.kanade.presentation.components.MugiReadMore.open = { tabNavigator.current = MoreTab }
            eu.kanade.presentation.components.MugiReadMore.selected = onMore
        }
        val shown = tabs.filter { it != MoreTab }
        eu.kanade.presentation.components.MugiReadBar(
            menuOpen = menuOpen,
            onCompass = onCompass,
            left = { shown.take(2).fastForEach { GlassNavItem(it, amber, plain = true, enabled = !menuOpen) } },
            right = { shown.drop(2).fastForEach { GlassNavItem(it, amber, plain = true, enabled = !menuOpen) } },
        )
    }

    @Composable
    private fun RowScope.GlassNavItem(
        tab: eu.kanade.presentation.util.Tab,
        accent: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFF4D57),
        plain: Boolean = false,
        enabled: Boolean = true,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val tint = if (selected) accent else if (plain) androidx.compose.ui.graphics.Color(0xFFE0D5C3) else androidx.compose.ui.graphics.Color(0xFFC9C9D2)
        androidx.compose.foundation.layout.Column(
            Modifier
                .weight(1f)
                .height(56.dp)
                .then(
                    if (selected && !plain) {
                        Modifier
                            .drawBehind {
                                drawRoundRect(
                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                        listOf(accent.copy(alpha = 0.32f), androidx.compose.ui.graphics.Color.Transparent),
                                        center = center,
                                        radius = size.maxDimension * 0.62f,
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                )
                            }
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .border(
                                0.8.dp,
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.32f), androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f)),
                                ),
                                androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                            )
                    } else {
                        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                    },
                )
                .clickable(enabled = enabled) {
                    if (!selected) {
                        tabNavigator.current = tab
                    } else {
                        scope.launch { tab.onReselect(navigator) }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tint) {
                NavigationIconItem(tab)
            }
            Text(
                text = tab.options.title,
                color = tint,
                fontSize = 10.5.sp,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(tab: eu.kanade.presentation.util.Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationBarItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
        )
    }

    @Composable
    fun NavigationRailItem(tab: eu.kanade.presentation.util.Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationRailItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
        )
    }

    @Composable
    private fun NavigationIconItem(tab: eu.kanade.presentation.util.Tab) {
        BadgedBox(
            badge = {
                when {
                    tab is UpdatesTab -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<LibraryPreferences>()
                            combine(
                                pref.newShowUpdatesCount.changes(),
                                pref.newUpdatesCount.changes(),
                            ) { show, count -> if (show) count else 0 }
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            Injekt.get<SourcePreferences>().extensionUpdatesCount.changes()
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Icon(
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
            )
        }
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false) : Tab
        data class More(val toDownloads: Boolean) : Tab
    }
}
