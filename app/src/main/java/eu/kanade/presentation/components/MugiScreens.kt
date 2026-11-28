package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** MugiVerse History / Updates: shared colours from the approved design. */
object MugiScreenColors {
    val Gold = Color(0xFFD4AF65)
    val BrightGold = Color(0xFFFFD478)
    val DarkGold = Color(0xFFA87832)
    val Ivory = Color(0xFFF5E8D0)
    val Secondary = Color(0xFFBDAE98)
    val ReadBackground = Color(0xFF160D08)
    val ReadSurface = Color(0xFF28190F)
    val Red = Color(0xFFAB2028)
    val DarkRed = Color(0xFF591219)
    val DarkBrown = Color(0xFF2B1A0C)
    val GoldGradient = Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFD4AF65), Color(0xFFA87832)))
}

/** Thin gold frame for covers and posters (not for the ornate 3D books). */
fun Modifier.mugiCoverBorder(color: Color = MugiScreenColors.Gold, radius: Dp = 10.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).border(1.dp, color, shape)
}

/** "History" / "Updates": gold serif with a short gold underline. */
@Composable
fun MugiScreenTitle(title: String) {
    Column {
        Text(
            title,
            color = Color(0xFFF2D592),
            fontFamily = MugiCinzel,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(top = 1.dp)
                .width(96.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(MugiScreenColors.Gold, Color.Transparent))),
        )
    }
}

/** Walnut screen background (shared with Browse). */
fun Modifier.mugiReadScreenBackground(): Modifier = mugiBrowseBackground()

/** Italic serif heading with a gold line that fades out. */
@Composable
fun MugiSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = MugiScreenColors.Ivory,
            fontFamily = MugiPlayfair,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            fontSize = 19.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MugiScreenColors.Gold, Color.Transparent))))
    }
}

/** Gold-outlined round button with a 48dp touch target. */
@Composable
fun MugiHeaderAction(icon: ImageVector, description: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .then(if (filled) Modifier.background(MugiScreenColors.GoldGradient) else Modifier.background(Color(0x66140B05)))
                .border(1.dp, MugiScreenColors.Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = if (filled) MugiScreenColors.DarkBrown else MugiScreenColors.Gold, modifier = Modifier.size(20.dp))
        }
    }
}

/** All | Manga | Novels chips: antique gold with dark brown letters when picked. */
@Composable
fun MugiReadChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "mugiReadChip")
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .widthIn(min = 64.dp)
            .clip(shape)
            .background(Color(0xFF231509))
            .drawBehind { if (a > 0f) drawRect(MugiScreenColors.GoldGradient, alpha = a) }
            .border(1.dp, MugiScreenColors.Gold.copy(alpha = if (selected) 1f else 0.7f), shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MugiScreenColors.DarkBrown else MugiScreenColors.Ivory,
            fontFamily = MugiPlayfair,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun MugiReadChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** New chapters | Calendar: gold capsule, picked half in gold with dark brown text. */
@Composable
fun MugiReadSegmented(
    labels: List<String>,
    icons: List<ImageVector?>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(48.dp)
            .clip(shape)
            .background(Color(0xFF1E120A))
            .border(1.dp, MugiScreenColors.Gold.copy(alpha = 0.85f), shape),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val a by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "mugiSeg")
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .drawBehind { if (a > 0f) drawRoundRect(MugiScreenColors.GoldGradient, cornerRadius = CornerRadius(24.dp.toPx()), alpha = a) }
                    .selectable(selected = on, role = Role.Tab) { onSelect(i) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val tint = if (on) MugiScreenColors.DarkBrown else MugiScreenColors.Ivory
                icons.getOrNull(i)?.let {
                    Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, color = tint, fontFamily = MugiPlayfair, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

/** NEW: bright gold on dark red with a gold edge. */
@Composable
fun MugiNewBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Text(
        "NEW",
        color = MugiScreenColors.BrightGold,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(shape)
            .background(MugiScreenColors.DarkRed)
            .border(1.dp, MugiScreenColors.Gold, shape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/** A date written on the parchment strip, with a thin gold line to the edge. */
@Composable
fun MugiParchmentDate(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(32.dp).widthIn(min = 140.dp), contentAlignment = Alignment.Center) {
            Image(
                painterResource(eu.kanade.tachiyomi.R.drawable.mugi_parchment),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
            Text(
                text,
                color = Color(0xFF38200F),
                fontFamily = MugiPlayfair,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MugiScreenColors.Gold.copy(alpha = 0.8f), Color.Transparent))))
    }
}

/** Thin gold line between rows. */
@Composable
fun MugiRowSeparator(modifier: Modifier = Modifier, start: Dp = 16.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = start, end = 16.dp)
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(MugiScreenColors.Gold.copy(alpha = 0.35f), MugiScreenColors.Gold.copy(alpha = 0.08f)))),
    )
}

/** Chapters this week · Titles · Reading, on a walnut panel. */
@Composable
fun MugiReadingStatsPanel(chapters: Int, titles: Int, readingTime: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF33200F), MugiScreenColors.ReadSurface, Color(0xFF1C110A))))
            .border(1.dp, MugiScreenColors.DarkGold, shape)
            .drawBehind {
                // brass corner studs
                val r = 2.5.dp.toPx()
                val m = 7.dp.toPx()
                listOf(
                    androidx.compose.ui.geometry.Offset(m, m),
                    androidx.compose.ui.geometry.Offset(size.width - m, m),
                    androidx.compose.ui.geometry.Offset(m, size.height - m),
                    androidx.compose.ui.geometry.Offset(size.width - m, size.height - m),
                ).forEach { drawCircle(MugiScreenColors.Gold, r, it) }
                drawRoundRect(MugiScreenColors.Gold.copy(alpha = 0.25f), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1f))
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val stats = listOf(chapters.toString() to "chapters\nthis week", titles.toString() to "titles", readingTime to "reading")
        stats.forEachIndexed { i, (value, label) ->
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = MugiScreenColors.BrightGold, fontFamily = MugiPlayfair, fontWeight = FontWeight.Bold, fontSize = 25.sp, maxLines = 1)
                Text(
                    label,
                    color = MugiScreenColors.Ivory,
                    fontFamily = MugiPlayfair,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (i < stats.lastIndex) {
                Box(Modifier.width(1.dp).height(48.dp).background(MugiScreenColors.DarkGold.copy(alpha = 0.7f)))
            }
        }
    }
}

/** More → Captain's Log and Offline Voyage, as two wooden rows. */
@Composable
fun MugiVoyageMoreCard(onLog: () -> Unit, onOffline: () -> Unit, onVault: (() -> Unit)? = null, onPrivacy: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A2412), MugiScreenColors.ReadSurface, Color(0xFF1C110A))))
            .border(1.dp, MugiScreenColors.DarkGold, RoundedCornerShape(14.dp)),
    ) {
        listOfNotNull(
            Triple("Captain's Log", "Your streaks, stats and bounties", onLog),
            Triple("Offline Voyage", "Get the next chapters ready for a trip", onOffline),
            onVault?.let { Triple("Private Library", "Books only you can open", it) },
            onPrivacy?.let { Triple("Read privacy settings", "Locks, notifications, incognito", it) },
        ).forEachIndexed { i, (title, sub, click) ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(MugiScreenColors.Gold.copy(alpha = 0.25f)))
            Row(
                Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MugiChestIcon(Modifier.size(34.dp), ship = i == 0)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color(0xFFF2D592), fontFamily = MugiPlayfair, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(sub, color = MugiScreenColors.Secondary, fontSize = 12.5.sp)
                }
                Text("›", color = MugiScreenColors.Gold, fontSize = 24.sp)
            }
        }
    }
}

/** A small treasure chest (or the sailing ship), from the approved artwork. */
@Composable
fun MugiChestIcon(modifier: Modifier = Modifier, ship: Boolean = false) {
    if (ship) {
        Image(
            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.sailing_ship_small),
            null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        return
    }
    Image(
        androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.mugi_chest_icon),
        null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
