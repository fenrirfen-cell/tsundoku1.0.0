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
        // read / total chapters, on one line (a long count used to wrap and read as "1163/157 1")
        if (total != null && total > 0) "${completed.coerceAtMost(total)}/$total" else "$completed",
        color = VaultColors.Ivory,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.88f))
            .border(1.dp, VaultColors.Gold, shape)
            .padding(horizontal = 4.dp, vertical = 2.dp),
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
    // one of the five supplied leather frames, always the same one for a given book
    val frame = (cover.mangaId % CollectorFrames.size).toInt().let { if (it < 0) -it else it }
    val opening = CollectorOpenings[frame]
    BoxWithConstraints(
        modifier
            .aspectRatio(FenrirTomeRatio)
            .then(if (onFrameBounds != null) Modifier.onGloballyPositioned { onFrameBounds(it.boundsInRoot()) } else Modifier),
    ) {
        // the frame artwork is 402 × 560; it stands on the bottom of the slot
        val fh = maxHeight * 0.97f
        val fw = fh * (402f / 560f)
        val left = (maxWidth - fw) / 2
        val top = maxHeight - fh
        // contact shadow on the shelf
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawOval(
                        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width / 2, y), radius = fw.toPx() * 0.7f),
                        topLeft = Offset(left.toPx() - 2.dp.toPx(), y - 5.dp.toPx()),
                        size = Size(fw.toPx() + 4.dp.toPx(), 9.dp.toPx()),
                    )
                },
        )
        // the real cover, seen through the frame's opening (a hair larger so no gap shows)
        Box(
            Modifier
                .offset(x = left + fw * opening[0] - 1.dp, y = top + fh * opening[1] - 1.dp)
                .size(fw * (opening[2] - opening[0]) + 2.dp, fh * (opening[3] - opening[1]) + 2.dp)
                .clip(RectangleShape),
        ) {
            MangaCover.Book(data = cover, modifier = Modifier.fillMaxSize(), shape = RectangleShape)
            overlay()
            if (showProgress && (readCount > 0 || totalChapters > 0)) {
                VaultBookProgress(readCount, totalChapters, Modifier.align(Alignment.BottomEnd).padding(3.dp))
            }
        }
        // the leather frame overlay (spine, page edges, gold corners)
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(CollectorFrames[frame]),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.offset(x = left, y = top).size(fw, fh),
        )
        // the reading-status bookmark hanging below the book (supplied fabric bookmarks)
        val markW = fw * 0.11f
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(
                when (status) {
                    BookReadingStatus.UNREAD -> eu.kanade.tachiyomi.R.drawable.book_bookmark_blue
                    BookReadingStatus.READING -> eu.kanade.tachiyomi.R.drawable.book_bookmark_red
                    BookReadingStatus.COMPLETED -> eu.kanade.tachiyomi.R.drawable.book_bookmark_yellow
                },
            ),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.offset(x = left + fw * 0.70f, y = top + fh * 0.90f).size(markW, markW * (133f / 69f)),
        )
        badges()
    }
}

/** The five supplied collector's-edition frames. */
private val CollectorFrames = listOf(
    eu.kanade.tachiyomi.R.drawable.collector_book_red,
    eu.kanade.tachiyomi.R.drawable.collector_book_blue,
    eu.kanade.tachiyomi.R.drawable.collector_book_green,
    eu.kanade.tachiyomi.R.drawable.collector_book_purple,
    eu.kanade.tachiyomi.R.drawable.collector_book_brown,
)

/** Each frame's transparent cover opening (left, top, right, bottom as shares), measured from the artwork. */
private val CollectorOpenings = listOf(
    floatArrayOf(0.1965f, 0.1250f, 0.9129f, 0.9393f),
    floatArrayOf(0.2040f, 0.1250f, 0.9179f, 0.9393f),
    floatArrayOf(0.2040f, 0.1250f, 0.9179f, 0.9393f),
    floatArrayOf(0.2090f, 0.1250f, 0.9204f, 0.9393f),
    floatArrayOf(0.2090f, 0.1250f, 0.9229f, 0.9393f),
)


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
        fontSize = 9.sp,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFFE8B5), androidx.compose.ui.graphics.Color(0xFFD3AB62))))
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFFAA7938), shape)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
