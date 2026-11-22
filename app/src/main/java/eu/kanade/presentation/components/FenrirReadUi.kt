package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fenrir Read look on the Read side (matches the Watch side). */
object FenrirReadUi {
    val Accent = Color(0xFFFF4D57)
    val Muted = Color(0xFF9A9AA6)
    val Card = Color(0xFF15151B)
    val CardBorder = Color(0xFF24242C)
}

fun Modifier.fenrirReadGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Color(0xEB26262E))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.55f to Color.Transparent))
    .border(1.dp, Color.White.copy(alpha = 0.28f), shape)

/**
 * A glass switch with a lit-up red choice (Updates: New chapters / Calendar; Browse tabs).
 * With many choices it scrolls sideways instead of squeezing them.
 */
@Composable
fun FenrirSegmentSwitch(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    badges: List<Int?> = emptyList(),
) {
    val scroll = labels.size > 3
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(44.dp)
            .fenrirReadGlass(RoundedCornerShape(22.dp))
            .then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                modifier = Modifier
                    .then(if (scroll) Modifier else Modifier.weight(1f))
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (on) FenrirReadUi.Accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                icons.getOrNull(i)?.let {
                    Icon(it, contentDescription = null, tint = if (on) Color.White else Color(0xFFC9C9D2), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    color = if (on) Color.White else Color(0xFFC9C9D2),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                badges.getOrNull(i)?.takeIf { it > 0 }?.let { n ->
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "$n",
                        color = if (on) Color.White else FenrirReadUi.Accent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Small uppercase heading above a group ("TODAY · SAT 21", "LIBRARY"). */
@Composable
fun FenrirSectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            color = Color(0xFF8E8E99),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                action,
                color = FenrirReadUi.Accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** A rounded card holding rows (More screen groups). */
@Composable
fun FenrirCardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(FenrirReadUi.Card)
            .border(1.dp, FenrirReadUi.CardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
fun FenrirGroupDivider() {
    HorizontalDivider(color = FenrirReadUi.CardBorder)
}

@Composable
fun FenrirGroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(FenrirReadUi.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = FenrirReadUi.Accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color(0xFF8E8E99), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = Color(0xFF6E6E78), fontSize = 20.sp)
    }
}

@Composable
fun FenrirGroupSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(FenrirReadUi.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = FenrirReadUi.Accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.5.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color(0xFF8E8E99), fontSize = 12.sp, maxLines = 2)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = FenrirReadUi.Accent, checkedThumbColor = Color.White),
        )
    }
}
