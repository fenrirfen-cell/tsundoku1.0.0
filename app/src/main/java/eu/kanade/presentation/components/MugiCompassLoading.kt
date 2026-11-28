package eu.kanade.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.EmbeddedHost
import eu.kanade.tachiyomi.R

/** The compass artwork swinging gently: MugiVerse Read's loading indicator (still when motion is reduced). */
@Composable
fun MugiCompassSpinner(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember {
        EmbeddedHost.reduceMotion?.invoke() == true || runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val swing = if (still) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "compassLoading")
        val a by t.animateFloat(-24f, 24f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "compassSwing")
        a
    }
    Image(
        painterResource(R.drawable.mugi_compass),
        contentDescription = "Loading",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).graphicsLayer { rotationZ = swing },
    )
}

/** Compass with "Loading your voyage…" (used by every Read loading screen in MugiVerse). */
@Composable
fun MugiCompassLoading() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MugiCompassSpinner(110.dp)
        Text(
            "Loading your voyage…",
            color = Color(0xFFF2E6CC),
            fontFamily = MugiPlayfair,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
        )
    }
}
