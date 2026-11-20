package eu.kanade.presentation.components

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

/** Fenrir style: "FENRIR" on the left and a "Read ▾" pill that switches sides. */
@Composable
private fun FenrirReadHeader(modifier: Modifier, openWatch: (Activity) -> Unit) {
    val context = LocalContext.current
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "FENRIR",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp),
                    letterSpacing = androidx.compose.ui.unit.TextUnit(5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "READ",
                    color = androidx.compose.ui.graphics.Color(0xFFFF4D57),
                    fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                    letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                text = "Compatible with Tsundoku",
                color = androidx.compose.ui.graphics.Color(0xFF8E8E99),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF1C1D26))
                    .clickable(role = Role.Button) { open = true }
                    .padding(start = 18.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    EmbeddedHost.readLabel,
                    fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Switch side",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(EmbeddedHost.watchLabel) },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    onClick = { open = false; context.findActivity()?.let(openWatch) },
                )
                EmbeddedHost.openAniDrama?.let { openAniDrama ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Ani-drama") },
                        leadingIcon = { Icon(Icons.Outlined.Tv, contentDescription = null) },
                        onClick = { open = false; context.findActivity()?.let(openAniDrama) },
                    )
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(EmbeddedHost.readLabel) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false },
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
