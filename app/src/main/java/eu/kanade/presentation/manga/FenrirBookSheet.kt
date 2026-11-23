package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.library.components.FenrirReviewsPanel
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * One sheet for Pages, Bookmarks and Reviews (switches at the top). Opens over the chapter list,
 * under the pill bar. Drag the top up for full screen, down to close; tap the dimmed page or Back
 * to close.
 */
@Composable
fun FenrirBookSheet(
    section: FenrirBookTab?,
    onSection: (FenrirBookTab?) -> Unit,
    manga: Manga,
    source: Source,
    ordered: List<Chapter>,
    startChapterId: Long?,
    isNovel: Boolean,
    onOpenChapter: (Chapter) -> Unit,
    onPickChapter: (Chapter) -> Unit,
) {
    var shown by remember { mutableStateOf(section ?: FenrirBookTab.Pages) }
    if (section != null && section != FenrirBookTab.Chapters) shown = section
    val open = section != null && section != FenrirBookTab.Chapters
    var full by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    BackHandler(enabled = open) { onSection(null) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSection(null) },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (full) 0.94f else 0.64f)
                    .offset { IntOffset(0, dragY.coerceAtLeast(0f).toInt()) }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF0F0F14))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                // The top (handle + switches) is what you drag; the list below scrolls normally.
                Column(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                when {
                                    dragY > 240f -> { onSection(null); full = false }
                                    dragY > 80f && full -> full = false
                                    dragY < -80f -> full = true
                                }
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                            onVerticalDrag = { _, dy -> dragY += dy },
                        )
                    },
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 9.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color(0xFF4A4A55)))
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clip(CircleShape)
                            .background(Color(0xFF17171D)).border(1.dp, Color(0xFF2A2A34), CircleShape).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            FenrirBookTab.Pages to "▦ Pages",
                            FenrirBookTab.Bookmarks to "🔖 Bookmarks",
                            FenrirBookTab.Reviews to "💬 Reviews",
                        ).forEach { (tab, label) ->
                            val on = tab == shown
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(CircleShape)
                                    .background(if (on) Color(0xFFFF4D57) else Color.Transparent)
                                    .clickable { onSection(tab) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (on) Color.White else Color(0xFFA9A9B4), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
                when (shown) {
                    FenrirBookTab.Reviews -> FenrirReviewsPanel(manga.title, isNovel, Modifier.fillMaxSize())
                    FenrirBookTab.Bookmarks -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        FenrirBookmarksTab(manga = manga, ordered = ordered, onOpenChapter = onOpenChapter)
                        Spacer(Modifier.height(110.dp))
                    }
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        FenrirPagesTab(
                            manga = manga,
                            source = source,
                            ordered = ordered,
                            startChapterId = startChapterId,
                            isNovel = isNovel,
                            onOpenChapter = onOpenChapter,
                            onPickChapter = onPickChapter,
                        )
                        Spacer(Modifier.height(110.dp))
                    }
                }
            }
        }
    }
}
