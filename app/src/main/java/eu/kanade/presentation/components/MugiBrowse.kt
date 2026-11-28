package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** MugiVerse Read / Browse: walnut and antique-gold tokens from the approved design. */
object MugiBrowse {
    val Background = Color(0xFF160E08)
    val Surface = Color(0xFF241A12)
    val SurfaceElevated = Color(0xFF302217)
    val Gold = Color(0xFFD9AE62)
    val GoldLight = Color(0xFFF5D891)
    val GoldDark = Color(0xFF8C602B)
    val Ivory = Color(0xFFF5E8CE)
    val Secondary = Color(0xFFAAA49A)
    val MutedBorder = Color(0xFF796343)
    val GoldGradient = Brush.verticalGradient(listOf(Color(0xFFE4BC77), Color(0xFF956029), Color(0xFF694018)))
    val WarmGradient = Brush.verticalGradient(listOf(Color(0xFF5A3A1C), Color(0xFF34210F)))
    val CardGradient = Brush.verticalGradient(listOf(Color(0xFF2E2116), Color(0xFF1E150D)))
    val Serif = FontFamily.Serif
}

/** Dark walnut with warm light at the top and faint sea-chart lines from two corners. */
fun Modifier.mugiBrowseBackground(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF241509), MugiBrowse.Background, Color(0xFF0E0905))))
    drawRect(
        Brush.radialGradient(
            listOf(MugiBrowse.Gold.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.8f, 0f),
            radius = size.width * 0.9f,
        ),
    )
    val line = MugiBrowse.Gold.copy(alpha = 0.045f)
    listOf(Offset(size.width, size.height * 0.1f), Offset(0f, size.height * 0.9f)).forEach { c ->
        for (i in 0 until 16) {
            val a = i * PI / 8
            drawLine(line, c, Offset(c.x + cos(a).toFloat() * size.maxDimension, c.y + sin(a).toFloat() * size.maxDimension), 1f)
        }
        drawCircle(MugiBrowse.Gold.copy(alpha = 0.06f), radius = 30.dp.toPx(), center = c, style = Stroke(1f))
    }
}

/** "Browse" in ivory serif after a thin gold line with a compass star. */
@Composable
fun MugiBrowseTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(44.dp).background(Brush.verticalGradient(listOf(Color.Transparent, MugiBrowse.Gold, Color.Transparent))))
        MugiBrowseStar(Modifier.offset(x = (-7).dp, y = 16.dp).size(12.dp))
        Text(
            title,
            color = MugiBrowse.Ivory,
            fontFamily = MugiBrowse.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MugiBrowseStar(modifier: Modifier = Modifier, color: Color = MugiBrowse.Gold) {
    Box(
        modifier.drawBehind {
            val c = center
            val r = size.minDimension / 2
            val k = r * 0.22f
            drawPath(
                Path().apply {
                    moveTo(c.x, c.y - r); lineTo(c.x + k, c.y - k); lineTo(c.x + r, c.y); lineTo(c.x + k, c.y + k)
                    lineTo(c.x, c.y + r); lineTo(c.x - k, c.y + k); lineTo(c.x - r, c.y); lineTo(c.x - k, c.y - k); close()
                },
                color,
            )
        },
    )
}

/** Stacked books, for the Multi / language headings. */
@Composable
fun MugiBooksIcon(modifier: Modifier = Modifier, color: Color = MugiBrowse.Gold) {
    Box(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            val bh = h * 0.22f
            val gap = h * 0.08f
            for (i in 0 until 3) {
                val top = h - (i + 1) * bh - i * gap
                val inset = if (i == 1) w * 0.08f else 0f
                drawRoundRect(color, topLeft = Offset(inset, top), size = androidx.compose.ui.geometry.Size(w - inset * 2, bh), cornerRadius = CornerRadius(bh * 0.3f))
                drawLine(Color(0xFF2A1B0E), Offset(w * 0.18f + inset, top + bh * 0.5f), Offset(w * 0.82f - inset, top + bh * 0.5f), bh * 0.18f)
            }
        },
    )
}

/** Sources | Extensions | Migrate: 46dp capsule, thin gold outline, picked one in antique gold. */
@Composable
fun MugiBrowseTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    badges: List<Int?> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(46.dp)
            .clip(shape)
            .background(Color(0x99120B06))
            .border(1.dp, MugiBrowse.Gold.copy(alpha = 0.8f), shape)
            .padding(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val a by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "mugiTab")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .drawBehind {
                        if (a > 0f) {
                            drawRoundRect(MugiBrowse.GoldGradient, cornerRadius = CornerRadius(22.dp.toPx()), alpha = a)
                            drawRoundRect(MugiBrowse.GoldLight.copy(alpha = 0.6f * a), cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                    }
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = MugiBrowse.Ivory,
                        fontFamily = MugiBrowse.Serif,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                    val n = badges.getOrNull(i)
                    if (n != null && n > 0) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(CircleShape).background(MugiBrowse.Gold).padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(n.toString(), color = Color(0xFF2A1A08), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Novel | Manga: 36dp pills, the picked one gold-outlined on warm brown. */
@Composable
fun MugiKindFilter(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    badges: List<Int?> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val a by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "mugiKind")
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier
                    .height(36.dp)
                    .widthIn(min = 96.dp)
                    .clip(shape)
                    .background(Color(0x8C1A120B))
                    .drawBehind { if (a > 0f) drawRect(MugiBrowse.WarmGradient, alpha = a) }
                    .border(1.dp, if (on) MugiBrowse.Gold else MugiBrowse.MutedBorder.copy(alpha = 0.7f), shape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    label,
                    color = if (on) MugiBrowse.Ivory else MugiBrowse.Secondary,
                    fontFamily = MugiBrowse.Serif,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 15.sp,
                )
                val n = badges.getOrNull(i)
                if (n != null && n > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MugiBrowse.Gold))
                }
            }
        }
    }
}

/** Gold icon, ivory serif heading, thin gold line. */
@Composable
fun MugiBrowseHeading(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MugiBooksIcon(Modifier.size(width = 26.dp, height = 22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = MugiBrowse.Ivory, fontFamily = MugiBrowse.Serif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MugiBrowse.Gold.copy(alpha = 0.7f), MugiBrowse.Gold.copy(alpha = 0.1f)))))
    }
}

/** Compact gold/brown "Latest" pill with a 48dp touch area; its tap never opens the card. */
@Composable
fun MugiLatestPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Latest", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Latest",
            color = MugiBrowse.Gold,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF4A3218), Color(0xFF2E1F0F))))
                .border(1.dp, MugiBrowse.Gold.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

/** Pinned source card: real logo, name (2 lines), Latest pill and a chevron. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MugiSourceCard(
    name: String,
    logo: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLatest: (() -> Unit)?,
    statusDot: Color?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "mugiCardPress")
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 90.dp)
            .clip(shape)
            .background(MugiBrowse.CardGradient)
            .border(1.dp, MugiBrowse.Gold.copy(alpha = 0.45f), shape)
            .combinedClickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(Modifier.size(42.dp).clip(CircleShape)) { logo() }
            if (statusDot != null) {
                Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(statusDot).border(1.dp, MugiBrowse.Background, CircleShape))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = MugiBrowse.Ivory,
                fontFamily = MugiBrowse.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onLatest != null) MugiLatestPill(onLatest, Modifier.offset(x = (-2).dp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MugiBrowse.Ivory.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Two columns on phones; more on wide screens. */
@Composable
fun <T> MugiSourceGrid(items: List<T>, modifier: Modifier = Modifier, card: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val cols = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { card(it, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One source in a list: logo, name, Latest and whatever actions follow (filter, pin). */
@Composable
fun MugiSourceRow(
    name: String,
    logo: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLatest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickableCompat(onClick, onLongClick)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape)) { logo() }
            Spacer(Modifier.width(14.dp))
            Text(
                name,
                color = MugiBrowse.Ivory,
                fontFamily = MugiBrowse.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onLatest != null) MugiLatestPill(onLatest)
            actions()
        }
        Box(Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp).height(1.dp).background(MugiBrowse.MutedBorder.copy(alpha = 0.25f)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
