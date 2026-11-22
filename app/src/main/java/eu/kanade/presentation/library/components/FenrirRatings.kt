package eu.kanade.presentation.library.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Ratings for books: AniList first (manga, manhwa, light novels), then MyAnimeList, then
 * Webnovel for web novels. Saved for a week. No badge when no title matches closely enough.
 */
object FenrirRatings {
    data class Review(val summary: String, val score: Int, val user: String, val url: String?, val spoiler: Boolean)
    data class Rating(
        val score: Double,
        val site: String,
        val votes: Int?,
        val rank: String?,
        val url: String?,
        val tags: List<String>,
        val dist: List<Pair<Int, Int>>,
        val top: Review?,
        val low: Review?,
        val reviewCount: Int?,
    )

    val showBadges = MutableStateFlow(true)
    private val prefs by lazy {
        Injekt.get<android.app.Application>().getSharedPreferences("fenrir_ratings", Context.MODE_PRIVATE).also {
            showBadges.value = it.getBoolean("show", true)
        }
    }
    private val memory = java.util.concurrent.ConcurrentHashMap<String, Rating?>()
    private val permits = Semaphore(2)
    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json = Json { ignoreUnknownKeys = true }

    fun setShowBadges(on: Boolean) {
        prefs.edit().putBoolean("show", on).apply()
        showBadges.value = on
    }

    fun init() { prefs }

    private fun key(title: String, novel: Boolean) = (if (novel) "n:" else "m:") + norm(title)
    fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }

    private fun matches(query: String, candidates: List<String>): Boolean {
        val q = norm(query)
        if (q.length < 2) return false
        return candidates.map { norm(it) }.filter { it.isNotEmpty() }.any { c ->
            c == q || ((c.contains(q) || q.contains(c)) && minOf(c.length, q.length).toDouble() / maxOf(c.length, q.length) >= 0.75)
        }
    }

    suspend fun get(title: String, novel: Boolean): Rating? {
        val k = key(title, novel)
        if (memory.containsKey(k)) return memory[k]
        prefs.getString(k, null)?.let { raw ->
            val parts = raw.split('\u0001', limit = 2)
            val at = parts[0].toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - at < 7 * 24 * 3600_000L) {
                val r = parts.getOrNull(1)?.let { decode(it) }
                memory[k] = r
                return r
            }
        }
        val r = permits.withPermit {
            withContext(Dispatchers.IO) {
                runCatching { aniList(title, novel) }.getOrNull()
                    ?: runCatching { jikan(title) }.getOrNull()
                    ?: if (novel) runCatching { webnovel(title) }.getOrNull() else null
            }
        }
        memory[k] = r
        prefs.edit().putString(k, "${System.currentTimeMillis()}\u0001${r?.let { encode(it) }.orEmpty()}").apply()
        return r
    }

    // ---- AniList ----
    private suspend fun aniList(title: String, novel: Boolean): Rating? {
        val q = """
            query(${'$'}s: String) { Page(perPage: 6) { media(search: ${'$'}s, type: MANGA) {
              format averageScore siteUrl synonyms title { romaji english native }
              rankings { rank allTime type context }
              tags { name rank isMediaSpoiler }
              stats { scoreDistribution { score amount } }
              top: reviews(sort: [RATING_DESC], perPage: 1) { pageInfo { total } nodes { summary score siteUrl body user { name } } }
              low: reviews(sort: [SCORE], perPage: 1) { nodes { summary score siteUrl body user { name } } }
            } } }
        """.trimIndent()
        val body = buildJsonObject {
            put("query", q)
            put("variables", buildJsonObject { put("s", title.take(120)) })
        }.toString()
        val text = withTimeoutOrNull(15_000) {
            client.newCall(
                Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json")
                    .build(),
            ).execute().use { it.body?.string() }
        } ?: return null
        val list = (json.parseToJsonElement(text).obj("data")?.obj("Page")?.get("media") as? JsonArray) ?: return null
        val media = list.mapNotNull { it as? JsonObject }
            .filter { m ->
                val t = m.obj("title")
                val names = listOfNotNull(t?.str("romaji"), t?.str("english"), t?.str("native")) +
                    ((m["synonyms"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList())
                matches(title, names)
            }
            .sortedByDescending { m -> if ((m.str("format") == "NOVEL") == novel) 1 else 0 }
            .firstOrNull { it.int("averageScore") != null } ?: return null
        val score = (media.int("averageScore") ?: return null) / 10.0
        val buckets = (media.obj("stats")?.get("scoreDistribution") as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { o -> (o.int("score") ?: return@mapNotNull null) to (o.int("amount") ?: 0) }.orEmpty()
        val votes = buckets.sumOf { it.second }.takeIf { it > 0 }
        val stars = (1..5).map { s -> s to buckets.filter { ((it.first + 19) / 20) == s }.sumOf { it.second } }
        val dist = if (votes != null) stars.reversed().map { (s, n) -> s to (n * 100 / votes) } else emptyList()
        val rank = (media["rankings"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("type") == "RATED" && it.bool("allTime") == true }
            ?.let { r ->
                val fmt = when (media.str("format")) {
                    "NOVEL" -> "light novel"
                    "MANGA" -> "manga"
                    "ONE_SHOT" -> "one-shot"
                    else -> "title"
                }
                "#${r.int("rank")} top-rated $fmt"
            }
        val tags = (media["tags"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.filter { it.bool("isMediaSpoiler") != true }
            ?.sortedByDescending { it.int("rank") ?: 0 }
            ?.take(4)?.mapNotNull { it.str("name") }.orEmpty()
        fun review(o: JsonObject?): Review? {
            val n = (o?.get("nodes") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            val summary = n.str("summary")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return Review(
                summary = summary,
                score = n.int("score") ?: 0,
                user = n.obj("user")?.str("name") ?: "a reader",
                url = n.str("siteUrl"),
                spoiler = n.str("body")?.contains("~!") == true,
            )
        }
        val top = review(media.obj("top"))
        val low = review(media.obj("low"))?.takeIf { it.summary != top?.summary }
        val count = media.obj("top")?.obj("pageInfo")?.int("total")
        return Rating(score, "AniList", votes, rank, media.str("siteUrl"), tags, dist, top, low, count)
    }

    // ---- MyAnimeList (Jikan) ----
    private suspend fun jikan(title: String): Rating? {
        val url = "https://api.jikan.moe/v4/manga?limit=5&q=" + java.net.URLEncoder.encode(title.take(100), "UTF-8")
        val text = withTimeoutOrNull(15_000) {
            client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
        } ?: return null
        val data = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonArray ?: return null
        val m = data.mapNotNull { it as? JsonObject }.firstOrNull { o ->
            val names = listOfNotNull(o.str("title"), o.str("title_english"), o.str("title_japanese")) +
                ((o["title_synonyms"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList())
            o.dbl("score") != null && matches(title, names)
        } ?: return null
        return Rating(
            score = m.dbl("score") ?: return null,
            site = "MyAnimeList",
            votes = m.int("scored_by"),
            rank = m.int("rank")?.let { "#$it on MyAnimeList" },
            url = m.str("url"),
            tags = (m["genres"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("name") }?.take(4).orEmpty(),
            dist = emptyList(),
            top = null,
            low = null,
            reviewCount = null,
        )
    }

    // ---- Webnovel (public search page; can stop working if the site changes) ----
    private suspend fun webnovel(title: String): Rating? {
        val url = "https://www.webnovel.com/search?keywords=" + java.net.URLEncoder.encode(title.take(100), "UTF-8")
        val html = withTimeoutOrNull(15_000) {
            client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute().use { it.body?.string() }
        } ?: return null
        // Each result carries the book name and a score out of 5 close together.
        val re = Regex("""(?s)"bookName"\s*:\s*"([^"]{1,200})".{0,1500}?"(?:totalScore|score)"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)""")
        val hit = re.findAll(html).firstOrNull { matches(title, listOf(it.groupValues[1])) } ?: return null
        val five = hit.groupValues[2].toDoubleOrNull()?.takeIf { it in 0.1..5.0 } ?: return null
        return Rating(five * 2, "Webnovel", null, null, url, emptyList(), emptyList(), null, null, null)
    }

    // ---- tiny JSON helpers ----
    private fun JsonElement.obj(k: String): JsonObject? = (this as? JsonObject)?.get(k) as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    // ---- cache encoding (one line per rating) ----
    private fun esc(s: String?) = (s ?: "").replace('\u0002', ' ').replace('\u0003', ' ')
    private fun encode(r: Rating): String = listOf(
        r.score.toString(), r.site, r.votes?.toString().orEmpty(), esc(r.rank), esc(r.url),
        r.tags.joinToString("\u0003") { esc(it) },
        r.dist.joinToString("\u0003") { "${it.first}:${it.second}" },
        r.top?.let { "${esc(it.summary)}\u0003${it.score}\u0003${esc(it.user)}\u0003${esc(it.url)}\u0003${it.spoiler}" }.orEmpty(),
        r.low?.let { "${esc(it.summary)}\u0003${it.score}\u0003${esc(it.user)}\u0003${esc(it.url)}\u0003${it.spoiler}" }.orEmpty(),
        r.reviewCount?.toString().orEmpty(),
    ).joinToString("\u0002")

    private fun decode(s: String): Rating? {
        if (s.isBlank()) return null
        val p = s.split('\u0002')
        fun rev(x: String?): Review? {
            val q = x?.takeIf { it.isNotBlank() }?.split('\u0003') ?: return null
            return Review(q[0], q.getOrNull(1)?.toIntOrNull() ?: 0, q.getOrElse(2) { "" }, q.getOrNull(3)?.ifBlank { null }, q.getOrNull(4) == "true")
        }
        return Rating(
            score = p.getOrNull(0)?.toDoubleOrNull() ?: return null,
            site = p.getOrElse(1) { "" },
            votes = p.getOrNull(2)?.toIntOrNull(),
            rank = p.getOrNull(3)?.ifBlank { null },
            url = p.getOrNull(4)?.ifBlank { null },
            tags = p.getOrNull(5)?.split('\u0003')?.filter { it.isNotBlank() }.orEmpty(),
            dist = p.getOrNull(6)?.split('\u0003')?.mapNotNull { d -> d.split(':').takeIf { it.size == 2 }?.let { (a, b) -> (a.toIntOrNull() ?: 0) to (b.toIntOrNull() ?: 0) } }.orEmpty(),
            top = rev(p.getOrNull(7)),
            low = rev(p.getOrNull(8)),
            reviewCount = p.getOrNull(9)?.toIntOrNull(),
        )
    }
}

fun fenrirRatingColor(score: Double) = when {
    score >= 8.0 -> Color(0xFF4CD787)
    score >= 6.5 -> Color(0xFFFFB547)
    else -> Color(0xFFFF8A91)
}

private fun one(d: Double) = String.format(java.util.Locale.US, "%.1f", d)

/** ★ 8.7 badge for a cover; tap for why it's rated that way. Nothing shows without a sure match. */
@Composable
fun FenrirRatingBadge(title: String, novel: Boolean, modifier: Modifier = Modifier) {
    FenrirRatings.init()
    val show by FenrirRatings.showBadges.collectAsState()
    if (!show) return
    val rating by produceState<FenrirRatings.Rating?>(initialValue = null, title, novel) {
        value = FenrirRatings.get(title, novel)
    }
    val r = rating ?: return
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(Color(0xD10C0C10))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable { open = true }
            .padding(start = 6.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = fenrirRatingColor(r.score), fontSize = 11.sp)
        Spacer(Modifier.width(3.dp))
        Text(one(r.score), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
    }
    if (open) FenrirRatingSheet(title, r) { open = false }
}

/** The "why it's rated that way" card: score, bars, tags, reviews with a spoiler switch. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FenrirRatingSheet(title: String, r: FenrirRatings.Rating, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    var spoilers by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF15151B)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(fenrirRatingColor(r.score).copy(alpha = 0.14f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("★", color = fenrirRatingColor(r.score), fontSize = 15.sp)
                    Text(one(r.score), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        r.site + (r.votes?.let { " · ${"%,d".format(it)} ratings" } ?: ""),
                        color = Color(0xFFB8B8C2),
                        fontSize = 12.5.sp,
                    )
                    when {
                        r.rank != null -> Text(r.rank, color = Color(0xFFFF4D57), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        (r.votes ?: 0) in 1..99 -> Text("Few ratings so far", color = Color(0xFFFFB547), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (r.dist.isNotEmpty()) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.dist.forEach { (stars, pct) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$stars", color = Color(0xFF9A9AA6), fontSize = 11.5.sp, modifier = Modifier.width(14.dp))
                            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color(0xFF26262E))) {
                                Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).background(if (stars >= 4) Color(0xFFFF4D57) else Color(0xFF55555F)))
                            }
                            Text("$pct%", color = Color(0xFF9A9AA6), fontSize = 11.5.sp, modifier = Modifier.padding(start = 8.dp).width(34.dp))
                        }
                    }
                }
            }
            if (r.tags.isNotEmpty()) {
                Text("WHAT PEOPLE SAY", color = Color(0xFF8E8E99), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.tags.forEach { t ->
                        Text(
                            "👍 $t",
                            color = Color(0xFF8FE3B4),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(CircleShape).background(Color(0x1F4CD787)).border(1.dp, Color(0x594CD787), CircleShape)
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (r.top != null || r.low != null) {
                Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("REVIEWS", color = Color(0xFF8E8E99), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Text("Spoilers", color = Color(0xFFC9C9D2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = spoilers,
                        onCheckedChange = { spoilers = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF4D57)),
                    )
                }
                r.top?.let { ReviewCard("MOST LIKED REVIEW", Color(0xFF4CD787), it, spoilers) { u -> uri.openUri(u) } }
                r.low?.let { ReviewCard("LOWEST-RATED REVIEW", Color(0xFFFF8A91), it, spoilers) { u -> uri.openUri(u) } }
                if (r.url != null) {
                    Text(
                        "See all${r.reviewCount?.let { " $it" } ?: ""} reviews on ${r.site} ›",
                        color = Color(0xFFFF4D57),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp).clickable { uri.openUri(r.url + if (r.site == "AniList") "/reviews" else "") },
                    )
                }
            } else if (r.url != null) {
                Text(
                    "Reviews on ${r.site} ›",
                    color = Color(0xFFFF4D57),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp).clickable { uri.openUri(r.url) },
                )
            }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show rating badges on covers", color = Color(0xFF9A9AA6), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                val show by FenrirRatings.showBadges.collectAsState()
                Switch(checked = show, onCheckedChange = { FenrirRatings.setShowBadges(it) })
            }
        }
    }
}

@Composable
private fun ReviewCard(kind: String, color: Color, rv: FenrirRatings.Review, spoilers: Boolean, open: (String) -> Unit) {
    var reveal by remember(rv) { mutableStateOf(false) }
    val hide = rv.spoiler && !spoilers && !reveal
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1B22))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
            if (rv.spoiler) {
                Text(
                    "SPOILER",
                    color = Color(0xFFFFB547),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0x24FFB547)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("★ ${rv.score / 10}", color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.padding(top = 6.dp)) {
            Text(
                "“${rv.summary}”",
                color = Color(0xFFE4E4EA),
                fontSize = 13.5.sp,
                modifier = if (hide) Modifier.blur(6.dp) else Modifier,
            )
            if (hide) {
                Text(
                    "Tap to show spoiler",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF26262E))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { reveal = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text("— ${rv.user}", color = Color(0xFF8E8E99), fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            if (rv.url != null) {
                Text("Read full review ↗", color = Color(0xFFFF4D57), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { open(rv.url) })
            }
        }
    }
}
