package eu.kanade.presentation.library.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.manga.model.MangaCover as CoverData

/** MugiVerse Captain's Vault palette. */
object VaultColors {
    val Background = Color(0xFF170D08)
    val Walnut = Color(0xFF392012)
    val Gold = Color(0xFFD4AF65)
    val Ivory = Color(0xFFF5E8D0)
    val BookmarkUnread = Color(0xFF397BC4)
    val BookmarkReading = Color(0xFFB82C38)
    val BookmarkCompleted = Color(0xFFE5B94F)

    /** Restrained leather colours for the spine. */
    val Leathers = listOf(Color(0xFF722C32), Color(0xFF24476B), Color(0xFF34523B), Color(0xFF504064), Color(0xFF65452E))
}

enum class BookReadingStatus { UNREAD, READING, COMPLETED }

fun BookReadingStatus.bookmarkColor(): Color = when (this) {
    BookReadingStatus.UNREAD -> VaultColors.BookmarkUnread
    BookReadingStatus.READING -> VaultColors.BookmarkReading
    BookReadingStatus.COMPLETED -> VaultColors.BookmarkCompleted
}

/**
 * Completed only when every chapter is read AND the series itself is finished; a caught-up
 * ongoing series is still "reading".
 */
fun bookStatusOf(read: Long, total: Long, seriesStatus: Long): BookReadingStatus = when {
    read <= 0 -> BookReadingStatus.UNREAD
    total > 0 && read >= total && (seriesStatus == 2L || seriesStatus == 4L) -> BookReadingStatus.COMPLETED
    else -> BookReadingStatus.READING
}

@Composable
fun BookStatusRibbon(status: BookReadingStatus, modifier: Modifier = Modifier) {
    val c = status.bookmarkColor()
    Canvas(
        modifier
            .size(width = 8.dp, height = 13.dp)
            .semantics {
                contentDescription = when (status) {
                    BookReadingStatus.UNREAD -> "Unread"
                    BookReadingStatus.READING -> "Currently reading"
                    BookReadingStatus.COMPLETED -> "Completed"
                }
            },
    ) {
        val ribbon = Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
            lineTo(size.width / 2f, size.height * 0.72f); lineTo(0f, size.height); close()
        }
        drawPath(ribbon, Brush.horizontalGradient(listOf(c, c.copy(alpha = 0.8f), c)))
    }
}

@Composable
fun VaultBookProgress(completed: Long, total: Long?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        if (total != null && total > 0) "$completed/$total" else "$completed",
        color = VaultColors.Ivory,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.88f))
            .border(1.dp, VaultColors.Gold, shape)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    )
}

/**
 * A collector's-edition book seen almost from the front: the real cover (~90% of the width), a
 * slim leather spine with two raised bands, cream page edges, thin gold corners, a contact shadow,
 * a small fabric bookmark and a progress badge. Same outer size as the ornate tome, so shelves,
 * badges and the opening flight keep working.
 */
@Composable
fun MugiCollectorBook(
    cover: CoverData,
    status: BookReadingStatus,
    readCount: Long,
    totalChapters: Long,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    onFrameBounds: ((Rect) -> Unit)? = null,
    badges: @Composable BoxScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val leather = VaultColors.Leathers[(cover.mangaId % VaultColors.Leathers.size).toInt().let { if (it < 0) -it else it }]
    BoxWithConstraints(
        modifier
            .aspectRatio(FenrirTomeRatio)
            .then(if (onFrameBounds != null) Modifier.onGloballyPositioned { onFrameBounds(it.boundsInRoot()) } else Modifier),
    ) {
        val bookH = maxHeight * 0.84f
        val coverW = bookH * (2f / 3f)
        val spineW = coverW * 0.09f
        val pagesW = coverW * 0.035f
        val bookW = spineW + coverW + pagesW
        val left = (maxWidth - bookW) / 2
        // contact shadow on the shelf
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val y = bookH.toPx() + 1.dp.toPx()
                    drawOval(
                        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), center = Offset(size.width / 2, y), radius = bookW.toPx() * 0.7f),
                        topLeft = Offset(left.toPx() - 4.dp.toPx(), y - 5.dp.toPx()),
                        size = Size(bookW.toPx() + 8.dp.toPx(), 10.dp.toPx()),
                    )
                },
        )
        // spine (leather, two raised bands)
        Box(
            Modifier
                .offset(x = left)
                .size(spineW, bookH)
                .drawBehind {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(leather.copy(red = leather.red * 0.6f, green = leather.green * 0.6f, blue = leather.blue * 0.6f), leather, leather.copy(red = leather.red * 0.75f, green = leather.green * 0.75f, blue = leather.blue * 0.75f))),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    // leather grain
                    var y = 3f
                    while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y + 2f), 1f); y += 5f }
                    listOf(0.2f, 0.8f).forEach { f ->
                        drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, size.height * f), Size(size.width, 2.5.dp.toPx()))
                        drawRect(VaultColors.Gold.copy(alpha = 0.7f), Offset(0f, size.height * f - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                    }
                },
        )
        // cream page edges (right side and a sliver at the bottom)
        Box(
            Modifier
                .offset(x = left + spineW + coverW - 1.dp, y = 2.dp)
                .size(pagesW + 1.dp, bookH - 3.dp)
                .drawBehind {
                    drawRect(Color(0xFFEDE0C4))
                    var x = 1f
                    while (x < size.width) { drawLine(Color(0x33806A48), Offset(x, 0f), Offset(x, size.height), 0.7f); x += 2f }
                },
        )
        // the cover itself (flat, never distorted)
        Box(
            Modifier
                .offset(x = left + spineW)
                .size(coverW, bookH)
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
        ) {
            MangaCover.Book(data = cover, modifier = Modifier.fillMaxSize(), shape = RectangleShape)
            // a little light from above, and the hinge shadow by the spine
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), endX = 40f)))
            // thin gold corner protectors
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val c = 7.dp.toPx()
                    val g = VaultColors.Gold
                    val w = size.width
                    val h = size.height
                    listOf(
                        listOf(Offset(w - c, 0f), Offset(w, 0f), Offset(w, c)),
                        listOf(Offset(w - c, h), Offset(w, h), Offset(w, h - c)),
                    ).forEach { pts ->
                        val p = Path().apply { moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y); close() }
                        drawPath(p, g)
                    }
                    drawRect(g.copy(alpha = 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                },
            )
            overlay()
            if (showProgress && (readCount > 0 || totalChapters > 0)) {
                VaultBookProgress(readCount, totalChapters, Modifier.align(Alignment.BottomEnd).padding(3.dp))
            }
        }
        // fabric bookmark hanging from the page edge
        BookStatusRibbon(status, Modifier.offset(x = left + spineW + coverW * 0.72f, y = bookH - 1.dp))
        badges()
    }
}


/**
 * MugiVerse: the unread-chapter count as a small parchment tag (dark red numbers) for the top
 * corner of a book. Unread chapters only, never total progress.
 */
@androidx.compose.runtime.Composable
fun MugiChapterCountBadge(count: Long, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    if (count <= 0) return
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
    androidx.compose.material3.Text(
        if (count > 999) "999+" else count.toString(),
        color = androidx.compose.ui.graphics.Color(0xFF731C20),
        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFFE8B5), androidx.compose.ui.graphics.Color(0xFFD3AB62))))
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFFAA7938), shape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
