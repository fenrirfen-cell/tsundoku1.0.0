package eu.kanade.presentation.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import uy.kohesive.injekt.api.get
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.Preference
import androidx.compose.runtime.collectAsState
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import tachiyomi.presentation.core.util.collectAsState

/** Fenrir red, the accent used across Fenrir Read. */
val FenrirAccent = Color(0xFFFF4D57)

private val PanelText = Color.White
private val PanelMuted = Color(0xFFB5B5C3)

/**
 * Glass look for the reader's floating panel (same family as the home island): a see-through
 * dark tint, a soft sheen on the top half and a light rim that fades down the sides.
 */
fun Modifier.fenrirReaderGlass(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(30.dp)): Modifier = this
    .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
    .clip(shape)
    .background(Color(0xFF1E1E26).copy(alpha = 0.86f))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.5f to Color.Transparent))
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.14f)),
        ),
        shape,
    )

/**
 * The novel reader's bottom controls: previous / progress / next on top, and five labelled
 * buttons under it - Chapters, Read mode, Text, Listen (read aloud + auto-scroll) and Translate.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FenrirNovelBottomPanel(
    modifier: Modifier = Modifier,
    chapterLabel: String?,
    progress: Int,
    progressLabel: String,
    onProgressChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    enabledPrevious: Boolean,
    onNext: () -> Unit,
    enabledNext: Boolean,
    onChapters: () -> Unit,
    onReadMode: () -> Unit,
    onText: () -> Unit,
    isTtsActive: Boolean,
    ttsControlsVisible: Boolean,
    onToggleTts: () -> Unit,
    isAutoScrolling: Boolean,
    onToggleAutoScroll: () -> Unit,
    hideAutoScroll: Boolean,
    isTranslating: Boolean,
    onToggleTranslation: () -> Unit,
    onLongPressTranslation: () -> Unit,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .fillMaxWidth()
            .fenrirReaderGlass()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Outlined.ChevronLeft, "Previous chapter", enabledPrevious, onPrevious)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        chapterLabel.orEmpty(),
                        color = PanelMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(progressLabel, color = PanelMuted, fontSize = 11.sp, maxLines = 1)
                }
                var dragging by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = dragging ?: progress.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onProgressChange(it.toInt()) }
                        dragging = null
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = FenrirAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier.height(28.dp),
                )
            }
            RoundIconButton(Icons.Outlined.ChevronRight, "Next chapter", enabledNext, onNext)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PanelButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Chapters", false, onChapters, Modifier.weight(1f))
            PanelButton(Icons.AutoMirrored.Outlined.MenuBook, "Read mode", false, onReadMode, Modifier.weight(1f))
            PanelButton(Icons.Outlined.TextFields, "Text", false, onText, Modifier.weight(1f))
            // Listen: read aloud, and auto-scroll (both play the chapter for you).
            Box(Modifier.weight(1f)) {
                var menu by remember { mutableStateOf(false) }
                PanelButton(
                    Icons.Outlined.Headphones,
                    "Listen",
                    isTtsActive || ttsControlsVisible || isAutoScrolling,
                    { if (hideAutoScroll) onToggleTts() else menu = true },
                    Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = Color(0xFF1C1C22),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    DropdownMenuItem(
                        text = { Text(if (ttsControlsVisible) "Stop reading aloud" else "Read aloud", color = PanelText) },
                        leadingIcon = { Icon(Icons.Outlined.Headphones, null, tint = if (ttsControlsVisible) FenrirAccent else PanelText) },
                        onClick = { menu = false; onToggleTts() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isAutoScrolling) "Stop auto-scroll" else "Auto-scroll", color = PanelText) },
                        leadingIcon = {
                            Icon(
                                if (isAutoScrolling) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                null,
                                tint = if (isAutoScrolling) FenrirAccent else PanelText,
                            )
                        },
                        onClick = { menu = false; onToggleAutoScroll() },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTranslating) FenrirAccent.copy(alpha = 0.16f) else Color.Transparent)
                    .combinedClickable(onClick = onToggleTranslation, onLongClick = onLongPressTranslation)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Translate, contentDescription = null, tint = if (isTranslating) FenrirAccent else PanelText)
                Spacer(Modifier.height(3.dp))
                Text("Translate", color = if (isTranslating) PanelText else PanelMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.10f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) PanelText else PanelText.copy(alpha = 0.35f))
    }
}

@Composable
private fun PanelButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) FenrirAccent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) FenrirAccent else PanelText)
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (active) PanelText else PanelMuted, fontSize = 11.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Chapters

data class FenrirChapterItem(
    val id: Long,
    val name: String,
    val number: Float,
    val read: Boolean,
    val bookmark: Boolean,
)

/** Pick any chapter of the book: search by number or name, jump by range, current one marked. */
@Composable
fun FenrirChaptersSheet(
    chapters: List<FenrirChapterItem>,
    currentId: Long?,
    onPick: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        var query by remember { mutableStateOf("") }
        var newestFirst by remember { mutableStateOf(false) }
        val ordered = if (newestFirst) chapters.asReversed() else chapters
        val filtered = remember(ordered, query) {
            val q = query.trim()
            if (q.isEmpty()) {
                ordered
            } else {
                val num = q.toFloatOrNull()
                ordered.filter { c ->
                    (num != null && (c.number == num || c.number.toInt() == num.toInt())) ||
                        c.name.contains(q, ignoreCase = true)
                }
            }
        }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        LaunchedEffect(currentId, newestFirst) {
            val index = ordered.indexOfFirst { it.id == currentId }
            if (index >= 0 && query.isBlank()) listState.scrollToItem((index - 2).coerceAtLeast(0))
        }
        val ranges = remember(ordered) {
            if (ordered.size <= 150) emptyList() else ordered.indices.chunked(if (ordered.size > 2000) 500 else 100)
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chapters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "${chapters.size} · ${chapters.count { it.read }} read",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Chapter number or name") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { newestFirst = !newestFirst },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = if (newestFirst) "Oldest first" else "Newest first")
                }
            }
            if (ranges.isNotEmpty() && query.isBlank()) {
                Spacer(Modifier.height(10.dp))
                val currentIndex = ordered.indexOfFirst { it.id == currentId }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ranges) { range ->
                        val from = ordered[range.first()]
                        val to = ordered[range.last()]
                        val selected = currentIndex in range
                        FilterChip(
                            selected = selected,
                            onClick = {
                                // Jump the list to this range.
                                query = ""
                                scope.launch { listState.scrollToItem(range.first()) }
                            },
                            label = { Text("${numberLabel(from)}–${numberLabel(to)}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FenrirAccent,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 460.dp)) {
                items(filtered, key = { it.id }) { c ->
                    val current = c.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (current) FenrirAccent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onPick(c.id) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            numberLabel(c),
                            color = if (current) FenrirAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(52.dp),
                        )
                        Text(
                            c.name,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (c.read && !current) 0.5f else 1f),
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (current) {
                            Text("READING", color = FenrirAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else if (c.bookmark) {
                            Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun numberLabel(c: FenrirChapterItem): String {
    val n = c.number
    return when {
        n < 0f -> "–"
        n == n.toInt().toFloat() -> n.toInt().toString()
        else -> n.toString()
    }
}

// ---------------------------------------------------------------------------------------------
// How to read

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) FenrirAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) FenrirAccent else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FenrirAccent,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, pref: Preference<Boolean>) {
    val checked by pref.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { pref.set(!checked) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(
            checked = checked,
            onCheckedChange = { pref.set(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = FenrirAccent),
        )
    }
}

@Composable
private fun ScreenSection(viewModel: ReaderSettingsViewModel) {
    val manga by viewModel.mangaFlow.collectAsState()
    val orientation = remember(manga) { ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt()) }
    SectionLabel("Screen")
    val options = listOf(ReaderOrientation.FREE, ReaderOrientation.PORTRAIT, ReaderOrientation.LANDSCAPE)
    ChipRow(
        options = listOf("Auto-rotate", "Portrait", "Landscape"),
        selected = options.indexOf(orientation).let { if (it < 0) 0 else it },
        onSelect = { viewModel.onChangeOrientation(options[it]) },
    )
}

@Composable
private fun ModeTabs(manga: Boolean) {
    // The kind you're reading decides which tab shows; the other is shown greyed for orientation.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        listOf("Novel", "Manga").forEachIndexed { i, label ->
            val on = (i == 1) == manga
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) FenrirAccent else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** How to read a novel: Scroll / Pages / Page flip, Endless or one chapter at a time, screen. */
@Composable
fun FenrirNovelModeSheet(viewModel: ReaderSettingsViewModel, onDismissRequest: () -> Unit) {
    val prefs = viewModel.preferences
    val paged by prefs.novelPagedMode.collectAsState()
    val flip by prefs.novelPagedFlipEffect.collectAsState()
    val endless by prefs.novelInfiniteScroll.collectAsState()
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("How to read", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            ModeTabs(manga = false)
            SectionLabel("Layout")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceCard("Scroll", "Swipe up through the chapter", !paged, {
                    prefs.novelPagedMode.set(false)
                    prefs.novelPagedFlipEffect.set(false)
                }, Modifier.weight(1f))
                ChoiceCard("Pages", "Slide page by page", paged && !flip, {
                    prefs.novelPagedMode.set(true)
                    prefs.novelPagedFlipEffect.set(false)
                }, Modifier.weight(1f))
                ChoiceCard("Page flip", "Turn pages like a book", paged && flip, {
                    prefs.novelPagedMode.set(true)
                    prefs.novelPagedFlipEffect.set(true)
                }, Modifier.weight(1f))
            }
            // Fenrir: LNReader-style "Paged reading (Experimental)": the chapter is laid out as
            // real pages that fill top to bottom ("1/11"), drawn by the web renderer.
            val renderMode by prefs.novelRenderingMode.collectAsState()
            val realPages = paged && renderMode == "webview"
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { setRealPages(prefs, !realPages) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Paged reading (Experimental)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Real book pages that fill top to bottom, with 1/11 at the bottom. Turn the last page to go to the next chapter.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = realPages,
                    onCheckedChange = { setRealPages(prefs, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = FenrirAccent),
                )
            }
            if (!paged) {
                SectionLabel("Chapters")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceCard("Endless", "Next chapter follows on", endless, {
                        prefs.novelInfiniteScroll.set(true)
                    }, Modifier.weight(1f))
                    ChoiceCard("One at a time", "Stop at the chapter end", !endless, {
                        prefs.novelInfiniteScroll.set(false)
                    }, Modifier.weight(1f))
                }
            } else {
                Spacer(Modifier.height(10.dp))
                SwitchRow("Swipe to turn pages", pref = prefs.novelPagedSwipeEnabled)
            }
            ScreenSection(viewModel)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// NOVEL is picked automatically for novel sources; DEFAULT is the app-wide setting.
private val FenrirMangaModes = listOf(
    Triple(ReadingMode.WEBTOON, "Webtoon", "Long strip"),
    Triple(ReadingMode.RIGHT_TO_LEFT, "Right → left", "Manga style"),
    Triple(ReadingMode.LEFT_TO_RIGHT, "Left → right", "Comic style"),
    Triple(ReadingMode.VERTICAL, "Vertical", "Page by page down"),
    Triple(ReadingMode.CONTINUOUS_VERTICAL, "Continuous", "Pages with gaps"),
)

/** How to read manga: page layout, panel by panel (with speech bubbles / AI helper), screen. */
@Composable
fun FenrirMangaModeSheet(
    viewModel: ReaderSettingsViewModel,
    onDismissRequest: () -> Unit,
) {
    val prefs = viewModel.preferences
    val manga by viewModel.mangaFlow.collectAsState()
    val readingMode = remember(manga) { ReadingMode.fromPreference(manga?.readingMode?.toInt()) }
    val panelByPanel by prefs.pagerPanelByPanel.collectAsState()
    val bubbles by prefs.pagerPanelBubbles.collectAsState()
    val ai by prefs.pagerPanelAi.collectAsState()
    val behind by prefs.pagerBubbleBehind.collectAsState()
    val mask by prefs.pagerPanelMask.collectAsState()
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("How to read", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            ModeTabs(manga = true)
            SectionLabel("Page layout")
            val bookFlip by prefs.pagerBookFlip.collectAsState()
            val horizontal = readingMode == ReadingMode.RIGHT_TO_LEFT || readingMode == ReadingMode.LEFT_TO_RIGHT
            val bookOn = bookFlip && horizontal
            // The five layouts plus "Book": pages that curl over like a real book.
            FenrirMangaModes.chunked(3).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { (mode, title, sub) ->
                        ChoiceCard(title, sub, readingMode == mode && !bookOn, {
                            prefs.pagerBookFlip.set(false)
                            viewModel.onChangeReadingMode(mode)
                        }, Modifier.weight(1f))
                    }
                    if (r == 1) {
                        ChoiceCard("Book", "Pages curl over", bookOn, {
                            prefs.pagerBookFlip.set(true)
                            if (!horizontal) viewModel.onChangeReadingMode(ReadingMode.RIGHT_TO_LEFT)
                        }, Modifier.weight(1f))
                    }
                    repeat(3 - row.size - (if (r == 1) 1 else 0)) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (bookOn) {
                SectionLabel("Turn pages")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceCard("Right → left", "Manga", readingMode == ReadingMode.RIGHT_TO_LEFT, {
                        viewModel.onChangeReadingMode(ReadingMode.RIGHT_TO_LEFT)
                    }, Modifier.weight(1f))
                    ChoiceCard("Left → right", "Comics", readingMode == ReadingMode.LEFT_TO_RIGHT, {
                        viewModel.onChangeReadingMode(ReadingMode.LEFT_TO_RIGHT)
                    }, Modifier.weight(1f))
                }
            }

            SectionLabel("While reading, show")
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
            ) {
                SwitchRow("Page number", "5 / 22 at the bottom", prefs.showPageNumber)
                SwitchRow("Chapter", "Ch. 938", prefs.pagerInfoChapter)
                SwitchRow("Time", "10:42 PM", prefs.pagerInfoTime)
                SwitchRow("Battery", "🔋 64%", prefs.pagerInfoBattery)
            }

            SectionLabel("Panel by panel")
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
            ) {
                SwitchRow("Panel by panel", "Zooms through the page one step at a time", prefs.pagerPanelByPanel)
                if (panelByPanel) {
                    SwitchRow("Show full page first", pref = prefs.pagerPanelFullPageFirst)
                    Text("Zoom to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp, 8.dp, 4.dp, 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceCard("Panels", "Each panel", !bubbles, { prefs.pagerPanelBubbles.set(false) }, Modifier.weight(1f))
                        ChoiceCard("Speech bubbles", "AI · 11 MB download", bubbles, { prefs.pagerPanelBubbles.set(true) }, Modifier.weight(1f))
                    }
                    if (bubbles) {
                        Text("Behind the bubbles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp, 10.dp, 4.dp, 6.dp))
                        ChipRow(listOf("Whole page", "Panel"), behind) { prefs.pagerBubbleBehind.set(it) }
                        if (behind == 1) {
                            SwitchRow("Whole panel before its bubbles", pref = prefs.pagerBubblePanelFirst)
                            SwitchRow("Whole panel after its bubbles", pref = prefs.pagerBubblePanelLast)
                        }
                    }
                    Text("Find panels with", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp, 10.dp, 4.dp, 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceCard("Basic", "Built in", !ai, { prefs.pagerPanelAi.set(false) }, Modifier.weight(1f))
                        ChoiceCard("AI helper", "12 MB download", ai, { prefs.pagerPanelAi.set(true) }, Modifier.weight(1f))
                    }
                    Text("Outside the panel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp, 10.dp, 4.dp, 6.dp))
                    ChipRow(listOf("Show", "Dim", "Black"), mask) { prefs.pagerPanelMask.set(it) }
                }
            }
            ScreenSection(viewModel)
            Spacer(Modifier.height(16.dp))
        }
    }
}


/**
 * Paged reading (Experimental) on: pages + the web renderer (it can lay a chapter out as real
 * pages). Off: back to the renderer you had before, and scrolling if that's what you had.
 */
private fun setRealPages(prefs: eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences, on: Boolean) {
    val store = runCatching {
        uy.kohesive.injekt.Injekt.get<android.app.Application>().getSharedPreferences("fenrir_ui", android.content.Context.MODE_PRIVATE)
    }.getOrNull()
    if (on) {
        store?.edit()
            ?.putString("real_pages_prev_render", prefs.novelRenderingMode.get())
            ?.putBoolean("real_pages_prev_paged", prefs.novelPagedMode.get())
            ?.apply()
        prefs.novelPagedMode.set(true)
        if (prefs.novelRenderingMode.get() != "webview") prefs.novelRenderingMode.set("webview")
    } else {
        val prevRender = store?.getString("real_pages_prev_render", "default") ?: "default"
        val prevPaged = store?.getBoolean("real_pages_prev_paged", false) ?: false
        prefs.novelPagedMode.set(prevPaged)
        prefs.novelRenderingMode.set(if (prevRender == "webview") "default" else prevRender)
    }
}
