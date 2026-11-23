package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme

@Composable
fun ReaderPageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    extra: String? = null,
) {
    if (currentPage <= 0 || totalPages <= 0) return

    val text = "$currentPage / $totalPages" + (extra?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")

    val style = TextStyle(
        color = Color(235, 235, 235),
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    val strokeStyle = style.copy(
        color = Color(45, 45, 45),
        drawStyle = Stroke(width = 4f),
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = strokeStyle,
        )
        Text(
            text = text,
            style = style,
        )
    }
}

@PreviewLightDark
@Composable
private fun ReaderPageIndicatorPreview() {
    TachiyomiPreviewTheme {
        Surface {
            ReaderPageIndicator(currentPage = 10, totalPages = 69)
        }
    }
}


/** Fenrir: "Ch. 938 · 10:42 PM · 🔋 64%" for the manga page line; the clock and battery refresh every half minute. */
@Composable
fun rememberFenrirReaderInfo(chapterNumber: Double?, showChapter: Boolean, showTime: Boolean, showBattery: Boolean): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(showTime, showBattery) {
        while (showTime || showBattery) {
            kotlinx.coroutines.delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    val parts = mutableListOf<String>()
    if (showChapter && chapterNumber != null && chapterNumber >= 0) {
        val n = if (chapterNumber % 1.0 == 0.0) chapterNumber.toLong().toString() else chapterNumber.toString()
        parts += "Ch. $n"
    }
    if (showTime) {
        parts += java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(tick))
    }
    if (showBattery) {
        val level = runCatching {
            context.getSystemService(android.os.BatteryManager::class.java)
                ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()
        if (level != null && level in 0..100) parts += "🔋 $level%"
    }
    return parts.joinToString(" · ")
}
