package eu.kanade.presentation.library.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.launch

/** Fenrir: true when the shelf on this page holds novels (decorations), false for manga (figures). */
internal val LocalFenrirShelfNovel = compositionLocalOf { true }

private val GhostColor = Color(0xFFE6C896)

private val NovelDecor = listOf(
    R.drawable.fenrir_decor_bookends,
    R.drawable.fenrir_decor_plant,
    R.drawable.fenrir_decor_candle,
    R.drawable.fenrir_decor_stack,
)
private val MangaDecor = listOf(
    R.drawable.fenrir_decor_sword,
    R.drawable.fenrir_decor_mecha,
    R.drawable.fenrir_decor_wolf,
    R.drawable.fenrir_decor_mage,
)

/** Which empty spots get something on them: about two in three, never two the same in a row. */
private fun decorFor(index: Int, novel: Boolean): Int? {
    val list = if (novel) NovelDecor else MangaDecor
    if (index % 3 == 1) return null
    val k = index / 3 * 2 + (if (index % 3 == 2) 1 else 0)
    return list[k.mod(list.size)]
}

/** The spot right after your last book: a dashed outline with "+ Add a book". Opens Browse. */
@Composable
internal fun FenrirAddBookCell(titleMaxLines: Int = 2) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fenrirPlank(5.dp, MangaCover.Book.ratio, 5.dp, bookShadow = false).padding(5.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(MangaCover.Book.ratio)
                .padding(end = 5.dp, top = 3.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    scope.launch {
                        eu.kanade.tachiyomi.ui.home.HomeScreen.openTab(eu.kanade.tachiyomi.ui.home.HomeScreen.Tab.Browse())
                    }
                }
                .drawBehind {
                    drawRoundRect(
                        GhostColor.copy(alpha = 0.55f),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                    drawRoundRect(GhostColor.copy(alpha = 0.05f), cornerRadius = CornerRadius(6.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("+", color = GhostColor.copy(alpha = 0.85f), fontSize = 34.sp, fontWeight = FontWeight.Light)
                Text("Add a book", color = GhostColor.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        ShelfTitleSpace(titleMaxLines)
    }
}

/** An empty spot on a shelf: the plank, and now and then a decoration (or a figure for manga). */
@Composable
internal fun FenrirEmptyShelfCell(titleMaxLines: Int = 2, index: Int = -1) {
    val novel = LocalFenrirShelfNovel.current
    val decor = if (index >= 0) decorFor(index, novel) else null
    Column(Modifier.fenrirPlank(5.dp, MangaCover.Book.ratio, 5.dp, bookShadow = false).padding(5.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(MangaCover.Book.ratio)) {
            if (decor != null) {
                Image(
                    painter = painterResource(decor),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                )
            }
        }
        ShelfTitleSpace(titleMaxLines)
    }
}

@Composable
private fun ShelfTitleSpace(titleMaxLines: Int) {
    Text(
        " ",
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        minLines = titleMaxLines.coerceIn(1, 2),
        maxLines = titleMaxLines.coerceIn(1, 2),
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp),
    )
    Text(" ", fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 2.dp, top = 1.dp, bottom = 4.dp))
}

/** Warm light falling on a shelf spot from the shelf above. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.fenrirShelfLight() {
    drawRect(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(Color(0xFFFFC27A).copy(alpha = 0.11f), Color.Transparent),
            center = Offset(size.width / 2f, 0f),
            radius = size.width * 1.1f,
        ),
        topLeft = Offset(-4.dp.toPx(), 0f),
        size = Size(size.width + 8.dp.toPx(), size.height),
    )
}
