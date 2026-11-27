package eu.kanade.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R

/** MugiVerse on the Read side: gold-rimmed bar with the compass in the middle, and its menu. */
object MugiRead {
    val Gold = Color(0xFFC9954D)
    val Ivory = Color(0xFFF4E3C1)
    val Amber = Color(0xFFF3B43F)
    val Red = Color(0xFFE44349)
    val Violet = Color(0xFF9C53E9)
}

enum class MugiReadMode(val title: String, val description: String, val accent: Color, val art: Int) {
    WATCH("Watch", "Movies & TV", MugiRead.Red, R.drawable.mugi_mode_watch),
    ANI_DRAMA("Ani-Drama", "Anime & drama", MugiRead.Violet, R.drawable.mugi_mode_anidrama),
    READ("Read", "Manga & novels", MugiRead.Amber, R.drawable.mugi_mode_read),
}

/**
 * The bar: [left] tabs, the compass, then [right] tabs. Each tab is drawn by the caller (so the
 * existing icons, badges, reselect and long-press behaviour stay).
 */
@Composable
fun MugiReadBar(
    menuOpen: Boolean,
    onCompass: () -> Unit,
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().height(86.dp), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xF21A1410), Color(0xF20C0906))))
                    .border(1.dp, MugiRead.Gold, RoundedCornerShape(34.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // equal halves either side, so the compass sits exactly in the middle
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { left() }
                Spacer(Modifier.width(76.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { right() }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(74.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = if (menuOpen) "Close mode selector" else "Open mode selector", onClick = onCompass),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.mugi_compass), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

/** The compass menu: dims the screen, three medallions on curved lines, parchment labels. */
@Composable
fun MugiReadModeMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (MugiReadMode) -> Unit) {
    BackHandler(enabled = expanded, onBack = onDismiss)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        reveal.animateTo(if (expanded) 1f else 0f, tween(if (expanded) 320 else 190, easing = FastOutSlowInEasing))
    }
    if (!expanded && reveal.value <= 0.001f) return
    val parchment = painterResource(R.drawable.mugi_parchment)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.64f * reveal.value))
                .clickable(
                    enabled = expanded,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close mode selector",
                    onClick = onDismiss,
                ),
        )
        BoxWithConstraints(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 49.dp)
                .fillMaxWidth()
                .height(282.dp),
        ) {
            val menuWidth = minOf(maxWidth, 520.dp)
            val badge = if (menuWidth < 360.dp) 60.dp else 72.dp
            val cardWidth = minOf(120.dp, menuWidth * 0.25f)
            val targets = listOf(MugiReadMode.WATCH to 0.23f, MugiReadMode.ANI_DRAMA to 0.50f, MugiReadMode.READ to 0.77f)
            val tops = listOf(97.dp, 18.dp, 97.dp)
            Box(Modifier.align(Alignment.BottomCenter).width(menuWidth).height(282.dp)) {
                Canvas(Modifier.matchParentSize()) {
                    val origin = Offset(size.width * 0.5f, size.height)
                    val p = reveal.value.coerceIn(0f, 1f)
                    targets.forEachIndexed { i, (m, fx) ->
                        val end = Offset(size.width * fx, (tops[i] + badge + 34.dp).toPx())
                        val c1 = Offset(origin.x + (end.x - origin.x) * 0.10f, origin.y - 79.dp.toPx())
                        val c2 = Offset(end.x, end.y + 64.dp.toPx())
                        val path = Path().apply {
                            moveTo(origin.x, origin.y)
                            for (step in 1..45) {
                                val t = p * step / 45f
                                val v = 1f - t
                                lineTo(
                                    v * v * v * origin.x + 3 * v * v * t * c1.x + 3 * v * t * t * c2.x + t * t * t * end.x,
                                    v * v * v * origin.y + 3 * v * v * t * c1.y + 3 * v * t * t * c2.y + t * t * t * end.y,
                                )
                            }
                        }
                        drawPath(path, m.accent.copy(alpha = 0.10f * p), style = Stroke(9.dp.toPx()))
                        drawPath(path, m.accent.copy(alpha = 0.25f * p), style = Stroke(4.dp.toPx()))
                        drawPath(path, m.accent.copy(alpha = 0.9f * p), style = Stroke(1.3.dp.toPx()))
                    }
                }
                targets.forEachIndexed { i, (m, fx) ->
                    val p = reveal.value.coerceIn(0f, 1f)
                    val x = (menuWidth * fx - cardWidth / 2) * p + (menuWidth / 2 - cardWidth / 2) * (1 - p)
                    val y = tops[i] * p + 210.dp * (1 - p)
                    if (p > 0.05f) {
                        Column(
                            Modifier
                                .offset(x = x, y = y)
                                .width(cardWidth)
                                .clickable(enabled = expanded, role = Role.Button, onClickLabel = "Switch to ${m.title}") { onPick(m) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(badge + 8.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(m.accent.copy(alpha = 0.65f * p), Color.Transparent)), CircleShape))
                                Image(painterResource(m.art), null, Modifier.size(badge))
                            }
                            Box(Modifier.width(cardWidth).height(25.dp), contentAlignment = Alignment.Center) {
                                Image(parchment, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                                Text(
                                    m.title,
                                    color = Color(0xFF2E180A),
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (menuWidth < 360.dp) 11.sp else 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(m.description, color = MugiRead.Ivory, fontFamily = FontFamily.Serif, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
