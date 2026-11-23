package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PRed = Color(0xFFFF4D57)
private val PMuted = Color(0xFF9A9AA6)

/**
 * The book's Reviews section: one chip per site that has the book. Novels open on Webnovel's full
 * review list (stars per reader, score parts, star filter, spoilers); manga on AniList's breakdown.
 * Nothing found → says so, with Try again and search by another title. Never throws.
 */
@Composable
fun FenrirReviewsPanel(title: String, novel: Boolean, modifier: Modifier = Modifier, bottomPad: Dp = 110.dp) {
    val uri = LocalUriHandler.current
    var query by remember(title) { mutableStateOf(title) }
    var attempt by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var rating by remember { mutableStateOf<FenrirRatings.Rating?>(null) }
    LaunchedEffect(query, attempt) {
        loading = true
        rating = runCatching { FenrirRatings.get(query, novel) }.getOrNull()
        loading = false
    }
    val r = rating
    if (loading) {
        Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PRed) }
        return
    }
    if (r == null) {
        var other by remember { mutableStateOf("") }
        Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💬", fontSize = 36.sp)
            Text("No reviews found", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
            Text(
                if (novel) {
                    "Couldn't find “$query” on Webnovel, AniList, MyAnimeList, Novel Updates or Royal Road."
                } else {
                    "Couldn't find “$query” on AniList, MyAnimeList or Anime-Planet."
                },
                color = PMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Try again",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp).clip(CircleShape).background(PRed)
                    .clickable { FenrirRatings.forget(query, novel); attempt++ }.padding(horizontal = 18.dp, vertical = 10.dp),
            )
            Text("Or search by another title (English, Japanese, …)", color = PMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 18.dp))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = other, onValueChange = { other = it }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Search",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { if (other.isNotBlank()) query = other.trim() }.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        return
    }
    val sites = remember(r) { (listOf(r.site) + r.also.map { it.site }).distinct() }
    var site by remember(r) { mutableStateOf(if (novel && "Webnovel" in sites) "Webnovel" else r.site) }
    val chips: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sites.forEach { s ->
                val on = s == site
                val score = if (s == r.site) r.score else r.also.firstOrNull { it.site == s }?.score
                Text(
                    s + (if (!on && score != null) " " + String.format(java.util.Locale.US, "%.1f", score) else ""),
                    color = if (on) Color.White else Color(0xFFD5D5DE),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (on) PRed else Color(0xFF1C1C23))
                        .border(1.dp, if (on) Color.Transparent else Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable { site = s }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
    val wnUrl = if (site == "Webnovel") (if (r.site == "Webnovel") r.url else r.also.firstOrNull { it.site == "Webnovel" }?.url) else null
    when {
        wnUrl != null && FenrirWebnovelReviews.bookId(wnUrl) != null ->
            FenrirWebnovelReviewList(wnUrl, modifier.fillMaxSize(), bottomPad, header = chips)
        site == r.site -> LazyColumn(modifier.fillMaxSize()) {
            item { chips() }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { FenrirRatingDetails(title, r, null) }
            }
            item { Spacer(Modifier.height(bottomPad)) }
        }
        else -> {
            val o = r.also.firstOrNull { it.site == site }
            LazyColumn(modifier.fillMaxSize()) {
                item { chips() }
                item {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF15151B)).padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            o?.score?.let {
                                Text(String.format(java.util.Locale.US, "%.1f", it), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(site, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    (o?.label ?: "") + (o?.votes?.let { " · ${"%,d".format(it)} ratings" } ?: ""),
                                    color = PMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        Text(
                            "Scores are out of 10 (sites that rate out of 5 are doubled). This site's written reviews open on its page.",
                            color = PMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        o?.url?.let { u ->
                            Text(
                                "Read reviews on $site ›",
                                color = PRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp).clickable { runCatching { uri.openUri(u) } },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(bottomPad)) }
            }
        }
    }
}
