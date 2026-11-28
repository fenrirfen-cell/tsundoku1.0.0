package tachiyomi.presentation.core.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** MugiVerse: lets the app show its compass instead of the generic spinner. */
object LoadingScreenStyle {
    /** True when the themed indicator should be used. */
    var enabled: (() -> Boolean)? = null

    /** The themed indicator (the compass artwork). */
    var indicator: (@Composable () -> Unit)? = null
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val themed = LoadingScreenStyle.indicator
        if (themed != null && LoadingScreenStyle.enabled?.invoke() == true) {
            themed()
        } else {
            CircularProgressIndicator()
        }
    }
}
