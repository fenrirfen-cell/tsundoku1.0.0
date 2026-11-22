package eu.kanade.presentation.components

import androidx.compose.material.icons.outlined.Check

import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.draw.shadow

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.border

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.EmbeddedHost

/**
 * The Watch / Read pill shown at the top of the home screen when Tsundoku runs inside Nuvio.
 * Draws nothing in a standalone Tsundoku build.
 */
@Composable
fun EmbeddedModeSwitchBar(modifier: Modifier = Modifier) {
    val openWatch = EmbeddedHost.openWatch ?: return
    val context = LocalContext.current
    if (EmbeddedHost.isFenrirStyle) {
        FenrirReadHeader(modifier, openWatch)
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val openAniDrama = EmbeddedHost.openAniDrama
        val threeWay = openAniDrama != null
        Row(
            modifier = Modifier
                .then(if (threeWay) Modifier.fillMaxWidth().padding(horizontal = 12.dp) else Modifier)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val segmentModifier = if (threeWay) Modifier.weight(1f) else Modifier
            ModeSegment(
                label = EmbeddedHost.watchLabel,
                icon = Icons.Outlined.PlayArrow,
                selected = false,
                onClick = { context.findActivity()?.let(openWatch) },
                compact = threeWay,
                modifier = segmentModifier,
            )
            if (openAniDrama != null) {
                ModeSegment(
                    label = "Ani-drama",
                    icon = Icons.Outlined.Tv,
                    selected = false,
                    onClick = { context.findActivity()?.let(openAniDrama) },
                    compact = true,
                    modifier = segmentModifier,
                )
            }
            ModeSegment(
                label = EmbeddedHost.readLabel,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                selected = true,
                onClick = {},
                compact = threeWay,
                modifier = segmentModifier,
            )
        }
    }
}

@Composable
private fun ModeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.secondaryContainer) else Modifier)
            .semantics { this.selected = selected }
            .clickable(enabled = !selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Fenrir style, matching the Watch side: FENRIR READ (bigger, on one baseline) centred on the
 * glass "📖 Read ▾" pill at the right, with "Compatible with Tsundoku" as a small subtitle, and
 * the same glass Watch / Ani-drama / Read menu.
 */
@Composable
private fun FenrirReadHeader(modifier: Modifier, openWatch: (Activity) -> Unit) {
    val context = LocalContext.current
    val accent = eu.kanade.presentation.reader.FenrirAccent
    val white = androidx.compose.ui.graphics.Color.White
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FENRIR",
                    color = white,
                    fontSize = 25.sp,
                    letterSpacing = 5.75.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "READ",
                    color = accent,
                    fontSize = 15.sp,
                    letterSpacing = 4.6.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Box {
                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .fenrirReaderGlassPill()
                        .clickable(role = Role.Button) { open = true }
                        .padding(start = 12.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(EmbeddedHost.readLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = white, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Switch side",
                        tint = white,
                        modifier = Modifier.size(20.dp),
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    modifier = Modifier.width(224.dp).padding(horizontal = 6.dp),
                    shape = RoundedCornerShape(26.dp),
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C22).copy(alpha = 0.96f),
                    tonalElevation = 0.dp,
                    shadowElevation = 18.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        Brush.verticalGradient(listOf(white.copy(alpha = 0.32f), white.copy(alpha = 0.05f), white.copy(alpha = 0.12f))),
                    ),
                ) {
                    FenrirModeItem(EmbeddedHost.watchLabel, Icons.Outlined.PlayArrow, false) {
                        open = false
                        context.findActivity()?.let(openWatch)
                    }
                    EmbeddedHost.openAniDrama?.let { openAniDrama ->
                        FenrirModeItem("Ani-drama", Icons.Outlined.Tv, false) {
                            open = false
                            context.findActivity()?.let(openAniDrama)
                        }
                    }
                    FenrirModeItem(EmbeddedHost.readLabel, Icons.AutoMirrored.Outlined.MenuBook, true) { open = false }
                }
            }
        }
        Text(
            text = "Compatible with Tsundoku",
            color = androidx.compose.ui.graphics.Color(0xFF8E8E99),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun FenrirModeItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val accent = eu.kanade.presentation.reader.FenrirAccent
    val white = androidx.compose.ui.graphics.Color.White
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, color = white, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (selected) accent else white) },
        trailingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = accent) }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** Same glass as the Watch side's buttons: see-through tint, top sheen, light rim, soft shadow. */
private fun Modifier.fenrirReaderGlassPill(): Modifier = this
    .shadow(12.dp, CircleShape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
    .clip(CircleShape)
    .background(androidx.compose.ui.graphics.Color(0xFF26262E).copy(alpha = 0.92f))
    .background(Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), 0.55f to androidx.compose.ui.graphics.Color.Transparent))
    .border(
        1.dp,
        Brush.linearGradient(
            0f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
            0.4f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
            1f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
        ),
        CircleShape,
    )

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
