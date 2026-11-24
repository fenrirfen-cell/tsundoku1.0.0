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
    R.drawable.fenrir_deco_wolf_bookends,
    R.drawable.fenrir_deco_moon,
    R.drawable.fenrir_deco_chained_book,
    R.drawable.fenrir_deco_sakura,
    R.drawable.fenrir_deco_crystal,
    R.drawable.fenrir_deco_lantern,
    R.drawable.fenrir_deco_chest,
    R.drawable.fenrir_deco_cat_books,
    R.drawable.fenrir_deco_glass_dome,
    R.drawable.fenrir_deco_wolf_chain,
)
private val MangaDecor = listOf(
    R.drawable.fenrir_deco_katanas,
    R.drawable.fenrir_deco_dragon,
    R.drawable.fenrir_deco_kitsune,
    R.drawable.fenrir_deco_samurai,
    R.drawable.fenrir_deco_blue_fire,
    R.drawable.fenrir_deco_portal,
    R.drawable.fenrir_deco_wolf_bust,
    R.drawable.fenrir_deco_arcade,
    R.drawable.fenrir_deco_headphones,
    R.drawable.fenrir_deco_black_cat,
    R.drawable.fenrir_deco_pixel_cat,
    R.drawable.fenrir_deco_clapper,
)

/** Which empty spots get something on them: about two in three, never two the same in a row. */
internal fun decorFor(index: Int, novel: Boolean): Int? {
    val list = if (novel) NovelDecor else MangaDecor
    if (index % 3 == 1) return null
    val k = index / 3 * 2 + (if (index % 3 == 2) 1 else 0)
    return list[k.mod(list.size)]
}

/** A shelf spot the size of one tome, with [content] standing on the shelf line. */
@Composable
private fun ShelfSpot(titleMaxLines: Int, label: String? = null, onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Column(
        Modifier
            .fenrirPlank(5.dp, FenrirTomePlankRatio, 0.dp, bookShadow = false)
            .padding(5.dp)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick) else Modifier),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(FenrirTomeRatio)) {
            Box(Modifier.fillMaxSize().padding(bottom = maxHeight * (1f - FenrirTomeStand)), content = content)
        }
        if (label != null) {
            Text(
                label,
                color = GhostColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
                minLines = titleMaxLines.coerceIn(1, 2),
                maxLines = titleMaxLines.coerceIn(1, 2),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 2.dp),
            )
            Text(" ", fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 2.dp, top = 1.dp, bottom = 4.dp))
        } else {
            ShelfTitleSpace(titleMaxLines)
        }
    }
}

/** The spot right after your last book: the glowing gold wolf tome with a +. Opens Browse. */
@Composable
internal fun FenrirAddBookCell(titleMaxLines: Int = 2) {
    val scope = rememberCoroutineScope()
    ShelfSpot(
        titleMaxLines,
        label = "Add a book",
        onClick = {
            scope.launch {
                eu.kanade.tachiyomi.ui.home.HomeScreen.openTab(eu.kanade.tachiyomi.ui.home.HomeScreen.Tab.Browse())
            }
        },
    ) {
        Image(
            painter = painterResource(R.drawable.fenrir_tome_add),
            contentDescription = "Add a book",
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.8f)
                .fillMaxSize(),
        )
    }
}

/** An empty spot on a shelf: the plank, and now and then a decoration (or a figure for manga). */
@Composable
internal fun FenrirEmptyShelfCell(titleMaxLines: Int = 2, index: Int = -1) {
    val novel = LocalFenrirShelfNovel.current
    val decor = if (index >= 0) decorFor(index, novel) else null
    ShelfSpot(titleMaxLines) {
        if (decor != null) {
            Image(
                painter = painterResource(decor),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.86f).fillMaxSize(0.8f),
            )
        }
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

/** A spotlight under the shelf above: a small lit puck at the top and a warm cone of light. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.fenrirShelfLight() {
    val cx = size.width / 2f
    val top = -2.dp.toPx()
    val coneH = size.height * 0.75f
    val cone = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - 9.dp.toPx(), top)
        lineTo(cx + 9.dp.toPx(), top)
        lineTo(cx + size.width * 0.55f, top + coneH)
        lineTo(cx - size.width * 0.55f, top + coneH)
        close()
    }
    drawPath(
        cone,
        androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color(0xFFFFC47A).copy(alpha = 0.22f),
            1f to Color.Transparent,
            startY = top,
            endY = top + coneH,
        ),
    )
    // glow round the puck, then the puck itself
    drawOval(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(Color(0xFFFFC878).copy(alpha = 0.55f), Color.Transparent),
            center = Offset(cx, top + 2.dp.toPx()),
            radius = 26.dp.toPx(),
        ),
        topLeft = Offset(cx - 26.dp.toPx(), top - 8.dp.toPx()),
        size = Size(52.dp.toPx(), 20.dp.toPx()),
    )
    drawOval(Color(0xFFFFEDC4), topLeft = Offset(cx - 12.dp.toPx(), top), size = Size(24.dp.toPx(), 4.dp.toPx()))
}

/** Plain names for the decorations (shown on the Luxury nameplates). */
internal fun decorName(res: Int?): String = when (res) {
    R.drawable.fenrir_deco_wolf_bookends -> "Wolf bookends"
    R.drawable.fenrir_deco_moon -> "Moon lamp"
    R.drawable.fenrir_deco_chained_book -> "Chained tome"
    R.drawable.fenrir_deco_sakura -> "Sakura tree"
    R.drawable.fenrir_deco_crystal -> "Crystal"
    R.drawable.fenrir_deco_lantern -> "Lantern"
    R.drawable.fenrir_deco_chest -> "Treasure chest"
    R.drawable.fenrir_deco_cat_books -> "Cat on books"
    R.drawable.fenrir_deco_glass_dome -> "Glass dome"
    R.drawable.fenrir_deco_wolf_chain -> "Chained wolf"
    R.drawable.fenrir_deco_katanas -> "Katanas"
    R.drawable.fenrir_deco_dragon -> "Dragon"
    R.drawable.fenrir_deco_kitsune -> "Fox mask"
    R.drawable.fenrir_deco_samurai -> "Samurai helmet"
    R.drawable.fenrir_deco_blue_fire -> "Blue flame"
    R.drawable.fenrir_deco_portal -> "Portal"
    R.drawable.fenrir_deco_wolf_bust -> "Wolf bust"
    R.drawable.fenrir_deco_arcade -> "Arcade cabinet"
    R.drawable.fenrir_deco_headphones -> "Headphones"
    R.drawable.fenrir_deco_black_cat -> "Black cat"
    R.drawable.fenrir_deco_pixel_cat -> "Pixel cat"
    R.drawable.fenrir_deco_clapper -> "Clapperboard"
    else -> ""
}

/** Book titles of the shelf being drawn (for equal nameplate heights in Luxury). */
internal val LocalLuxBookTitles = androidx.compose.runtime.compositionLocalOf<List<String>> { emptyList() }
