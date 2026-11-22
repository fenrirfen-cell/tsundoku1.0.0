package eu.kanade.presentation.library.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover

internal val FenrirRed = Color(0xFFFF4D57)
private val FenrirMuted = Color(0xFF9A9AA6)

/** Glass look shared by the Read side's bars and buttons (same as Watch). */
internal fun Modifier.fenrirGlass(shape: androidx.compose.ui.graphics.Shape): Modifier = this
    .clip(shape)
    .background(Color(0xEB26262E))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.55f to Color.Transparent))
    .border(1.dp, Color.White.copy(alpha = 0.28f), shape)

/**
 * F2: one glass bar for categories. Name of the open one, how many titles have new chapters,
 * page dots, and arrows. Swiping the books left/right also changes category (the pager).
 */
@Composable
internal fun FenrirCategoryBar(
    categories: List<Category>,
    pagerState: PagerState,
    newCountFor: (Category) -> Int,
    onGoTo: (Int) -> Unit,
) {
    val current = pagerState.currentPage.coerceIn(0, categories.lastIndex)
    val category = categories[current]
    val newCount = newCountFor(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(50.dp)
            .fenrirGlass(RoundedCornerShape(25.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous category", enabled = current > 0) { onGoTo(current - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.visualName,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (newCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("● $newCount new", color = FenrirRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (categories.size <= 8) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    categories.indices.forEach { i ->
                        val w by animateDpAsState(if (i == current) 16.dp else 6.dp, label = "dot")
                        Box(
                            Modifier
                                .size(width = w, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (i == current) FenrirRed else Color(0xFF44444F))
                                .clickable { onGoTo(i) },
                        )
                    }
                }
            } else {
                Text("${current + 1} / ${categories.size}", color = FenrirMuted, fontSize = 10.5.sp)
            }
        }
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next category", enabled = current < categories.lastIndex) { onGoTo(current + 1) }
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color(0xFFE4E4EA) else Color(0xFF4A4A55), modifier = Modifier.size(24.dp))
    }
}

/** "Continue reading": the book read most recently, with progress and an open-book button. */
@Composable
internal fun FenrirContinueReadingCard(
    manga: LibraryManga,
    onOpen: () -> Unit,
    onClickCover: () -> Unit,
) {
    val total = manga.totalChapters.coerceAtLeast(0)
    val read = manga.readCount.coerceIn(0, total.coerceAtLeast(0))
    val progress = if (total > 0) read.toFloat() / total else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(0f to FenrirRed.copy(alpha = 0.18f), 0.55f to Color(0xF21C1C22)))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            modifier = Modifier.height(72.dp),
            data = manga.manga.asMangaCover(),
            onClick = onClickCover,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CONTINUE READING", color = FenrirMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            Text(manga.manga.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (total > 0) "$read of $total chapters read" else "Started",
                color = Color(0xFFB8B8C2),
                fontSize = 12.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.9f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(FenrirRed))
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(FenrirRed).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Continue reading", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
