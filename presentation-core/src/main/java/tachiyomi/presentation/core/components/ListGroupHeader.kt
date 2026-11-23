package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import tachiyomi.presentation.core.components.material.padding

/** Lets a host recolour the group headers ("Today", "Yesterday"...), e.g. gold in Fenrir Read. */
val LocalListGroupHeaderColor = androidx.compose.runtime.staticCompositionLocalOf { androidx.compose.ui.graphics.Color.Unspecified }

@Composable
fun ListGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        color = LocalListGroupHeaderColor.current.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
}
