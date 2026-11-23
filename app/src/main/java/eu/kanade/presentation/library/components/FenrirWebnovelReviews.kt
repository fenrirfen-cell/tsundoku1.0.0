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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import eu.kanade.tachiyomi.network.await
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Every Webnovel review for a book, with the stars each reader gave. Everything here is wrapped so
 * a change on Webnovel's side only shows a message ("couldn't load") and never crashes the app.
 */
object FenrirWebnovelReviews {
    data class Review(
        val user: String,
        val stars: Double?,
        val text: String,
        val date: String?,
        val likes: Int,
        val replies: Int,
        val spoiler: Boolean,
    )
    data class Page(val reviews: List<Review>, val total: Int?, val average: Double?, val parts: List<Pair<String, Double>>)

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun bookId(url: String): String? =
        Regex("""_(\d{10,})""").find(url)?.groupValues?.get(1) ?: Regex("""/book/(\d{6,})""").find(url)?.groupValues?.get(1)

    private fun csrf(bookUrl: String): String? = runCatching {
        val u = bookUrl.toHttpUrlOrNull() ?: return null
        client.cookieJar.loadForRequest(u).firstOrNull { it.name == "_csrfToken" }?.value
    }.getOrNull()

    /** One page of reviews (30 at a time). Returns null when Webnovel can't be reached or changed. */
    suspend fun load(bookUrl: String, page: Int): Page? = withContext(Dispatchers.IO) {
        runCatching {
            val id = bookId(bookUrl) ?: return@runCatching null
            // Opening the book page once gives us Webnovel's request token (a cookie).
            var token = csrf(bookUrl)
            if (token == null) {
                withTimeoutOrNull(12_000) {
                    runCatching { client.newCall(Request.Builder().url(bookUrl).header("User-Agent", UA).build()).await().close() }
                }
                token = csrf(bookUrl) ?: java.util.UUID.randomUUID().toString()
            }
            val url = "https://www.webnovel.com/go/pcm/bookReview/get-reviews?_csrfToken=$token&bookId=$id" +
                "&pageIndex=$page&pageSize=30&orderBy=1&novelType=0&needSummary=1&_=${System.currentTimeMillis()}"
            val text = withTimeoutOrNull(15_000) {
                client.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", UA)
                        .header("Referer", bookUrl)
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Cookie", "_csrfToken=$token")
                        .build(),
                ).await().use { if (it.isSuccessful) it.body?.string() else null }
            } ?: return@runCatching null
            val data = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonObject ?: return@runCatching null
            val list = (data["bookReviewInfos"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val body = o.s("content")?.let { org.jsoup.Jsoup.parse(it).text() }?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Review(
                    user = o.s("userName") ?: o.s("nickName") ?: "a reader",
                    stars = o.d("totalScore") ?: o.d("score"),
                    text = body,
                    date = o.s("createTimeFormat"),
                    likes = o.d("likeAmount")?.toInt() ?: 0,
                    replies = o.d("replyAmount")?.toInt() ?: 0,
                    spoiler = o.keys.any { k ->
                        k.contains("spoiler", true) && (o[k] as? JsonPrimitive)?.content.let { it == "1" || it == "true" }
                    },
                )
            }
            val stats = data["bookStatisticsInfo"] as? JsonObject
            // Webnovel's score parts (writing quality, update stability, …) when it sends them.
            val parts = stats?.entries.orEmpty().mapNotNull { (k, v) ->
                if (k == "totalScore" || !k.endsWith("Score", true)) return@mapNotNull null
                val n = (v as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it in 0.1..5.0 } ?: return@mapNotNull null
                label(k) to n
            }
            Page(
                reviews = list,
                total = stats?.d("totalReviewNum")?.toInt(),
                average = stats?.d("totalScore")?.takeIf { it in 0.1..5.0 },
                parts = parts,
            )
        }.getOrNull()
    }

    private fun label(key: String): String = when (key.lowercase()) {
        "writingscore", "writingqualityscore" -> "Writing quality"
        "stabilityscore", "updatingscore", "updatestabilityscore" -> "Update stability"
        "storyscore", "storydevelopmentscore" -> "Story development"
        "characterscore", "characterdesignscore" -> "Character design"
        "worldscore", "worldbackgroundscore" -> "World background"
        else -> key.removeSuffix("Score").removeSuffix("score")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.d(k: String): Double? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
}

@Composable
private fun Stars(value: Double, size: Int = 14) {
    Row {
        (1..5).forEach { i ->
            val c = when {
                value >= i -> Color(0xFFFFB547)
                value >= i - 0.5 -> Color(0xFFFFB547).copy(alpha = 0.55f)
                else -> Color(0xFF3A3A44)
            }
            Text("★", color = c, fontSize = size.sp)
        }
    }
}

class FenrirWebnovelReviewsScreen(private val title: String, private val bookUrl: String) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { navigator.pop() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reviews", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("$title · Webnovel", color = Color(0xFF9A9AA6), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FenrirWebnovelReviewList(bookUrl, Modifier.fillMaxSize().navigationBarsPadding())
        }
    }
}

/**
 * Webnovel's reviews as a scrolling list: overall score with stars, Webnovel's score parts, a
 * spoiler switch, star filter, then every review (more load as you scroll). [header] goes on top.
 */
@Composable
fun FenrirWebnovelReviewList(
    bookUrl: String,
    modifier: Modifier = Modifier,
    bottomPad: androidx.compose.ui.unit.Dp = 20.dp,
    header: (@Composable () -> Unit)? = null,
) {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val reviews = remember(bookUrl) { mutableStateListOf<FenrirWebnovelReviews.Review>() }
    var head by remember(bookUrl) { mutableStateOf<FenrirWebnovelReviews.Page?>(null) }
    var page by remember(bookUrl) { mutableStateOf(1) }
    var loading by remember(bookUrl) { mutableStateOf(false) }
    var ended by remember(bookUrl) { mutableStateOf(false) }
    var failed by remember(bookUrl) { mutableStateOf(false) }
    var spoilers by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) } // 0 = all, 1..5 = that many stars
    val listState = rememberLazyListState()

    fun more() {
        if (loading || ended) return
        loading = true
        scope.launch {
            val p = FenrirWebnovelReviews.load(bookUrl, page)
            if (p == null) {
                if (reviews.isEmpty()) failed = true else ended = true
            } else {
                if (head == null) head = p
                val seen = reviews.map { it.user + it.text.take(40) }.toHashSet()
                val fresh = p.reviews.filter { (it.user + it.text.take(40)) !in seen }
                reviews.addAll(fresh)
                page++
                if (fresh.isEmpty() || p.reviews.size < 30) ended = true
            }
            loading = false
        }
    }
    LaunchedEffect(bookUrl) { more() }
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd) { if (nearEnd && head != null) more() }

    val shown = reviews.filter { r -> filter == 0 || (r.stars != null && kotlin.math.round(r.stars).toInt() == filter) }
    LazyColumn(state = listState, modifier = modifier) {
        if (header != null) item(key = "head") { header() }
        when {
            failed -> item(key = "failed") {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load reviews from Webnovel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("The site may be busy or asking for a check. Try again later.", color = Color(0xFF9A9AA6), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Try again",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(Color(0xFFFF4D57)).clickable { failed = false; ended = false; more() }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                        Text(
                            "Open on Webnovel",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable { runCatching { uri.openUri(bookUrl) } }.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            head == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFFF4D57)) }
            }
            else -> {
                item(key = "summary") {
                    val h = head!!
                    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF15151B)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val avg = h.average ?: reviews.mapNotNull { it.stars }.takeIf { it.isNotEmpty() }?.average()
                            if (avg != null) {
                                Text(String.format(java.util.Locale.US, "%.1f", avg), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Stars(avg, 16)
                                    Text("${"%,d".format(h.total ?: reviews.size)} reviews · Webnovel", color = Color(0xFF9A9AA6), fontSize = 12.sp)
                                }
                            } else {
                                Text("${h.total ?: reviews.size} reviews", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                            Text("Spoilers", color = Color(0xFFC9C9D2), fontSize = 12.5.sp)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = spoilers, onCheckedChange = { spoilers = it }, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF4D57)))
                        }
                        h.parts.forEach { (name, v) ->
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = Color(0xFFC9C9D2), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                Stars(v, 12)
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 5, 4, 3, 2, 1).forEach { n ->
                            val on = filter == n
                            Text(
                                if (n == 0) "All" else "$n ★",
                                color = if (on) Color.White else Color(0xFFC9C9D2),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(CircleShape).background(if (on) Color(0xFFFF4D57) else Color(0xFF1C1C23))
                                    .clickable { filter = n }.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                itemsIndexed(shown, key = { i, r -> "$i-${r.user}-${r.text.hashCode()}" }) { _, r ->
                    ReviewRow(r, spoilers)
                }
                item(key = "foot") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFFFF4D57))
                            ended && reviews.isEmpty() -> Text("No reviews yet.", color = Color(0xFF9A9AA6), fontSize = 13.sp)
                            ended -> Text("That's all of them.", color = Color(0xFF6E6E78), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item(key = "pad") { Spacer(Modifier.height(bottomPad)) }
    }
}

@Composable
private fun ReviewRow(r: FenrirWebnovelReviews.Review, spoilers: Boolean) {
    var reveal by remember(r) { mutableStateOf(false) }
    var expanded by remember(r) { mutableStateOf(false) }
    val hide = r.spoiler && !spoilers && !reveal
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF15151B)).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .clickable { if (hide) reveal = true else expanded = !expanded }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.user, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (r.spoiler) {
                Text(
                    "SPOILER",
                    color = Color(0xFFFFB547),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x24FFB547)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            r.stars?.let { Stars(it, 13) }
        }
        Box(Modifier.padding(top = 6.dp)) {
            Text(
                r.text,
                color = Color(0xFFE4E4EA),
                fontSize = 13.5.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = if (hide) Modifier.blur(6.dp) else Modifier,
            )
            if (hide) {
                Text(
                    "Tap to show spoiler",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(Color(0xFF26262E)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Text(listOfNotNull(r.date, "👍 ${r.likes}", if (r.replies > 0) "💬 ${r.replies}" else null).joinToString(" · "), color = Color(0xFF8E8E99), fontSize = 11.5.sp)
        }
    }
}
