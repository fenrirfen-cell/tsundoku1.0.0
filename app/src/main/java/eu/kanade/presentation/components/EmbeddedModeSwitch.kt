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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
