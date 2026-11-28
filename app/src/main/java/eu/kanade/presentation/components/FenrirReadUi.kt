package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
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
    val Muted = Color(0xFFA89A88)
    val Card = Color(0xFF1E1610)
    val CardBorder = Color(0xFF3E2E1C)
}

fun Modifier.fenrirReadGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Color(0xEB2A1F17))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.55f to Color.Transparent))
    // Fenrir: a thin gold rim, like the library's gold trim
    .border(1.dp, Brush.verticalGradient(listOf(Color(0xCCF0CD7A), Color(0x88B07A2C))), shape)

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


/** Small "Novel | Manga" toggle under a switch. */
@Composable
fun FenrirKindToggle(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    badges: List<Int?> = emptyList(),
) {
    Row(
        modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .border(1.dp, if (on) Color.White.copy(alpha = 0.22f) else Color(0xFF2C2C36), CircleShape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = if (on) Color.White else Color(0xFF9A9AA6), fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                badges.getOrNull(i)?.takeIf { it > 0 }?.let { n ->
                    Spacer(Modifier.width(6.dp))
                    // Fenrir: badge grows with the font size and keeps the number centred.
                    Box(
                        Modifier.clip(CircleShape).background(FenrirReadUi.Accent)
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$n",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Whether the Read side's bars are showing (scrolling down hides them, up brings them back). */
object FenrirBars {
    val shown = kotlinx.coroutines.flow.MutableStateFlow(true)
}

/** Round glass icon button (title rows: search, filter, ⋮…). */
@Composable
fun FenrirGlassIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    content: @Composable () -> Unit,
) {
    // MugiVerse: gold-outlined dark circles with gold icons, 44dp (48dp touch with the padding)
    val mugi = eu.kanade.tachiyomi.EmbeddedHost.isMugi
    Box(
        modifier = Modifier
            .padding(horizontal = if (mugi) 2.dp else 3.dp)
            .size(if (mugi) 44.dp else size)
            .then(
                if (mugi) {
                    Modifier
                        .clip(CircleShape)
                        .background(Color(0xCC1E150E))
                        .border(1.dp, Color(0xFFD9AE62), CircleShape)
                } else {
                    Modifier.fenrirReadGlass(CircleShape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (mugi) Color(0xFFD9AE62) else Color(0xFFE4E4EA),
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { content() }
        }
    }
}

/**
 * B: while scrolling down, a small floating glass pill: "Light novel | 🔍 ≡".
 * Tapping the name brings the full title row back.
 */
@Composable
fun FenrirScrollPill(
    label: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    actions: List<Pair<ImageVector, () -> Unit>> = emptyList(),
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 },
    ) {
        // Fade behind the status bar so the clock stays readable; the pill sits below the camera.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF140E0A),
                        0.35f to Color(0xF20B0B0F),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.Companion.statusBars)
                .padding(top = 16.dp)
                .height(44.dp)
                .androidxShadowPill()
                .fenrirReadGlass(RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 160.dp)
                    .clickable { FenrirBars.shown.value = true }
                    .padding(end = 6.dp),
            )
            actions.forEach { (icon, onClick) ->
                Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.15f)))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFFE4E4EA), modifier = Modifier.size(18.dp))
                }
            }
        }
        }
    }
}

private fun Modifier.androidxShadowPill(): Modifier =
    this.then(Modifier.shadow(12.dp, RoundedCornerShape(22.dp)))

/**
 * Fenrir: message bars ("Updating library"…) on the screens with the glass island. Material's bar
 * is white in this theme and sat right behind the island (a white strip); this one sits above the
 * island while it shows, in the dark Fenrir look.
 */
@Composable
fun FenrirIslandSnackbarHost(hostState: androidx.compose.material3.SnackbarHostState) {
    val islandShown by FenrirBars.shown.collectAsState()
    val lift by androidx.compose.animation.core.animateDpAsState(if (islandShown) 92.dp else 0.dp, label = "snackbarLift")
    androidx.compose.material3.SnackbarHost(
        hostState = hostState,
        modifier = Modifier.padding(bottom = lift),
    ) { data ->
        androidx.compose.material3.Snackbar(
            snackbarData = data,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E24),
            contentColor = androidx.compose.ui.graphics.Color.White,
            actionColor = androidx.compose.ui.graphics.Color(0xFFFF6B73),
            dismissActionContentColor = androidx.compose.ui.graphics.Color.White,
        )
    }
}
