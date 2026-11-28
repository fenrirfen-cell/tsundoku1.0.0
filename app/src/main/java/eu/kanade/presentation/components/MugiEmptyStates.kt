package eu.kanade.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R

private val Gold = Color(0xFFD4AF65)
private val Ivory = Color(0xFFF2E6CC)

/** Thin gold lines with the ship's wheel in the middle. */
@Composable
private fun WheelDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.width(70.dp).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Gold))))
        Image(painterResource(R.drawable.mugi_ship_wheel), null, contentScale = ContentScale.Fit, modifier = Modifier.padding(horizontal = 8.dp).size(24.dp))
        Box(Modifier.width(70.dp).height(1.dp).background(Brush.horizontalGradient(listOf(Gold, Color.Transparent))))
    }
}

/**
 * MugiVerse Read's themed empty state: the top shelf artwork, a gold italic title, the wheel
 * divider, a short message and (optionally) a gold action button.
 */
@Composable
fun MugiReadEmpty(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    showBookTile: Boolean = false,
    /** False inside a list that already scrolls. */
    scrollable: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    Column(
        (if (scrollable) modifier.fillMaxSize().verticalScroll(rememberScrollState()) else modifier.fillMaxWidth())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painterResource(R.drawable.vault_top_shelf),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().aspectRatio(1080f / 378f),
        )
        Text(title, color = Color(0xFFF2D592), fontFamily = MugiPlayfair, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center)
        WheelDivider()
        Text(message, color = Ivory.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        if (showBookTile && onAction != null) {
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .size(width = 124.dp, height = 116.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF4A2A14), Color(0xFF2A170C))))
                    .border(1.5.dp, Gold, RoundedCornerShape(6.dp))
                    .clickable(onClickLabel = actionLabel, onClick = onAction),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = Gold, modifier = Modifier.size(40.dp))
                Text("Add a Book", color = Ivory, fontFamily = MugiPlayfair, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .widthIn(min = 200.dp)
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFD4AF65), Color(0xFFA87832))))
                    .border(1.dp, Gold, RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+  $actionLabel", color = Color(0xFF2B1A0C), fontFamily = MugiPlayfair, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}
