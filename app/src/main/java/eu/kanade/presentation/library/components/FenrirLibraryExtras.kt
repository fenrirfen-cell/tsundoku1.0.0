package eu.kanade.presentation.library.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val Red = Color(0xFFFF4D57)
private val Muted = Color(0xFF9A9AA6)
private val CardBg = Color(0xFF17171D)
private val Rim = Color(0xFF2A2A34)

private const val LOCAL_MANGA_SOURCE = 0L
private const val LOCAL_NOVEL_SOURCE = 1L

@Composable
private fun Header(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Seg(items: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(CircleShape).background(CardBg).border(1.dp, Rim, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { t ->
            Box(
                Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (t == selected) Red else Color.Transparent).clickable { onPick(t) },
                contentAlignment = Alignment.Center,
            ) { Text(t, color = if (t == selected) Color.White else Color(0xFFA9A9B4), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color(0xFFFF8A91) else Color(0xFFD5D5DE),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(if (on) Red.copy(alpha = 0.18f) else Color(0xFF1C1C23))
            .border(1.dp, if (on) Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/* ------------------------------------------------------------------ My Books */

/** Pinned first in Browse → Sources: everything you imported. */
@Composable
fun FenrirMyBooksCard() {
    val navigator = LocalNavigator.current
    val books by produceState(emptyList<LibraryManga>()) { value = myBooks() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(0f to Red.copy(alpha = 0.22f), 0.65f to CardBg))
            .border(1.dp, Red.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable { navigator?.push(FenrirMyBooksScreen()) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Red, Color(0xFF7A1E26)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("My Books", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("${books.size} imported · offline", color = Color(0xFFC9C9D2), fontSize = 12.sp)
        }
        books.take(3).forEach { b ->
            MangaCover.Book(data = b.manga.asMangaCover(), modifier = Modifier.padding(start = 4.dp).width(26.dp).clip(RoundedCornerShape(5.dp)))
        }
    }
}

private suspend fun myBooks(): List<LibraryManga> = withContext(Dispatchers.IO) {
    runCatching { Injekt.get<GetLibraryManga>().await() }.getOrDefault(emptyList())
        .filter { it.manga.source == LOCAL_MANGA_SOURCE || it.manga.source == LOCAL_NOVEL_SOURCE }
        .distinctBy { it.manga.id }
}

class FenrirMyBooksScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val books by produceState<List<LibraryManga>?>(null) { value = myBooks() }
        var tab by remember { mutableStateOf("Series") }
        var genre by remember { mutableStateOf<String?>(null) }
        var settings by remember { mutableStateOf(false) }
        val all = books.orEmpty()
        val genres = remember(all) { all.flatMap { it.manga.genre.orEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(20) }
        val shown = all.filter { genre == null || genre in it.manga.genre.orEmpty() }
        // Series: volumes of the same book grouped under one cover.
        val groups: List<Pair<String, List<LibraryManga>>> = when (tab) {
            "Series" -> shown.groupBy { FenrirRecs.titleStem(it.manga.title).ifBlank { it.manga.title } }.entries.map { (_, v) -> v.first().manga.title to v.sortedBy { it.manga.title } }
            "Authors" -> shown.groupBy { it.manga.author?.ifBlank { null } ?: "Unknown author" }.entries.sortedBy { it.key }.map { it.key to it.value }
            else -> shown.flatMap { m -> m.manga.genre.orEmpty().ifEmpty { listOf("Other") }.map { it to m } }.groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }.map { it.key to it.value }
        }
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Header("My Books", "Your imports · ${all.size} books · offline") { navigator.pop() } }
                Text(
                    if (settings) "Done" else "Settings",
                    color = Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.statusBarsPadding().padding(end = 16.dp, top = 8.dp).clickable { settings = !settings },
                )
            }
            if (settings) {
                FenrirMyBooksSettings()
                return@Column
            }
            Seg(listOf("Series", "Authors", "Genres"), tab) { tab = it }
            if (genres.isNotEmpty() && tab != "Genres") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("All", genre == null) { genre = null }
                    genres.forEach { g -> Chip(g, genre == g) { genre = if (genre == g) null else g } }
                }
            }
            when {
                books == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                all.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No imported books yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Use Import a book in Library ⋮ or Browse +.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEach { (name, list) ->
                        if (tab != "Series") {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("$name · ${list.size}", color = Color(0xFFC9C9D2), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                            }
                            items(list, key = { "${name}_${it.manga.id}" }) { b -> BookTile(b, null) { navigator.push(eu.kanade.tachiyomi.ui.manga.MangaScreen(b.manga.id)) } }
                        } else {
                            item(key = "s_$name") {
                                BookTile(list.first(), if (list.size > 1) "${list.size} volumes" else null) {
                                    navigator.push(eu.kanade.tachiyomi.ui.manga.MangaScreen(list.first().manga.id))
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BookTile(b: LibraryManga, volumes: String?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            MangaCover.Book(data = b.manga.asMangaCover(), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
            FenrirRatingBadge(b.manga.title, b.manga.isNovel, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(b.manga.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(volumes ?: b.manga.author ?: "", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FenrirMyBooksSettings() {
    val context = LocalContext.current
    val running by FenrirWifiShare.running.collectAsState()
    val address by FenrirWifiShare.address.collectAsState()
    Column(Modifier.padding(16.dp)) {
        Text("SHARE", color = Color(0xFF8E8E99), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).border(1.dp, Rim, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Share on Wi-Fi", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Other devices at home can browse and download your imported books", color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = running,
                onCheckedChange = { if (it) FenrirWifiShare.start(context) else FenrirWifiShare.stop() },
                colors = SwitchDefaults.colors(checkedTrackColor = Red),
            )
        }
        if (running && address != null) {
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).padding(14.dp)) {
                Text("Open this in a reading app on the same Wi-Fi:", color = Color(0xFFC9C9D2), fontSize = 13.sp)
                Text(address!!, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text("Password (PIN): ${FenrirWifiShare.pin}", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Only your home Wi-Fi · stops when you turn it off or leave the app", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text("ADD BOOKS BY FOLDER", color = Color(0xFF8E8E99), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        Text(
            "EPUBs you put in Fenrir Read's “localnovels” folder (and comics in “local”) show up here after you pull down to refresh the Local source.",
            color = Muted,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/* ------------------------------------------------------------------ Wi-Fi sharing (OPDS) */

/** A small catalog server on your home Wi-Fi so other reading apps can download your imports. */
object FenrirWifiShare {
    val running = MutableStateFlow(false)
    val address = MutableStateFlow<String?>(null)
    val pin: String = (1000..9999).random().toString()
    private var job: Job? = null
    private var server: java.net.ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun localIp(): String? = java.net.NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<java.net.Inet4Address>()
        .map { it.hostAddress }
        .firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(it) }

    private fun books(): List<com.hippo.unifile.UniFile> {
        val dirs = listOfNotNull(
            runCatching { Injekt.get<StorageManager>().getLocalNovelSourceDirectory() }.getOrNull(),
        )
        val out = mutableListOf<com.hippo.unifile.UniFile>()
        fun walk(d: com.hippo.unifile.UniFile, depth: Int) {
            if (depth > 3) return
            d.listFiles()?.forEach { f ->
                if (f.isDirectory) walk(f, depth + 1) else if (f.name?.endsWith(".epub", true) == true) out += f
            }
        }
        dirs.forEach { walk(it, 0) }
        return out
    }

    fun start(context: Context) {
        if (running.value) return
        val ip = localIp() ?: run { address.value = null; return }
        job = scope.launch {
            val ss = runCatching { java.net.ServerSocket(8080) }.getOrElse { runCatching { java.net.ServerSocket(0) }.getOrNull() } ?: return@launch
            server = ss
            address.value = "http://$ip:${ss.localPort}/opds"
            running.value = true
            try {
                while (isActive) {
                    val socket = runCatching { ss.accept() }.getOrNull() ?: break
                    scope.launch { runCatching { handle(socket) }; runCatching { socket.close() } }
                }
            } finally {
                running.value = false
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        job?.cancel()
        running.value = false
    }

    private fun handle(socket: java.net.Socket) {
        socket.soTimeout = 15_000
        val input = socket.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.toList()
        val path = requestLine.split(' ').getOrNull(1) ?: "/"
        val auth = headers.firstOrNull { it.startsWith("Authorization:", true) }?.substringAfter(":")?.trim()
        val pinOk = path.contains("pin=$pin") || (auth != null && auth.startsWith("Basic ", true) &&
            runCatching { String(android.util.Base64.decode(auth.substring(6), android.util.Base64.DEFAULT)).substringAfter(':') == pin }.getOrDefault(false))
        val out = socket.getOutputStream()
        fun send(code: String, type: String, body: ByteArray, extra: String = "") {
            out.write("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n$extra\r\n".toByteArray())
            out.write(body); out.flush()
        }
        if (!pinOk) {
            send("401 Unauthorized", "text/plain", "Enter the PIN shown in Fenrir Read as the password.".toByteArray(), "WWW-Authenticate: Basic realm=\"Fenrir Read\"\r\n")
            return
        }
        val list = books()
        val clean = path.substringBefore('?')
        if (clean.startsWith("/file/")) {
            val i = clean.removePrefix("/file/").substringBefore('/').toIntOrNull()
            val f = i?.let { list.getOrNull(it) } ?: return send("404 Not Found", "text/plain", "Not found".toByteArray())
            val len = f.length()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/epub+zip\r\nContent-Length: $len\r\nContent-Disposition: attachment; filename=\"${f.name}\"\r\nConnection: close\r\n\r\n".toByteArray())
            f.openInputStream().use { it.copyTo(out) }
            out.flush()
            return
        }
        fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val entries = list.mapIndexed { i, f ->
            val title = f.name.orEmpty().removeSuffix(".epub")
            """<entry><title>${x(title)}</title><id>fenrir:$i</id><updated>2026-01-01T00:00:00Z</updated><link rel="http://opds-spec.org/acquisition" href="/file/$i/${x(f.name.orEmpty())}?pin=$pin" type="application/epub+zip"/></entry>"""
        }.joinToString("")
        val feed = """<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog"><id>fenrir-read</id><title>Fenrir Read · My Books</title><updated>2026-01-01T00:00:00Z</updated>$entries</feed>"""
        send("200 OK", "application/atom+xml;profile=opds-catalog;kind=acquisition", feed.toByteArray())
    }
}

/* ------------------------------------------------------------------ Catalogs (OPDS) */

object FenrirCatalogs {
    data class Catalog(val name: String, val url: String, val note: String)
    data class Entry(val title: String, val author: String?, val cover: String?, val download: String?, val nav: String?)
    data class Feed(val title: String, val entries: List<Entry>, val next: String?, val search: String?)

    val suggested = listOf(
        Catalog("Project Gutenberg", "https://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads", "70,000+ free books"),
        Catalog("Standard Ebooks", "https://standardebooks.org/feeds/opds", "Polished free classics (may ask for a login)"),
    )
    private val prefs by lazy { Injekt.get<android.app.Application>().getSharedPreferences("fenrir_catalogs", Context.MODE_PRIVATE) }
    val saved = MutableStateFlow<List<Catalog>>(emptyList())

    fun load() {
        saved.value = prefs.getStringSet("list", emptySet()).orEmpty().mapNotNull { line ->
            line.split('\t').takeIf { it.size >= 2 }?.let { Catalog(it[0], it[1], it.getOrElse(2) { "" }) }
        }.sortedBy { it.name }
    }

    fun add(c: Catalog) {
        val next = (saved.value.filterNot { it.url == c.url } + c)
        prefs.edit().putStringSet("list", next.map { "${it.name}\t${it.url}\t${it.note}" }.toSet()).apply()
        saved.value = next.sortedBy { it.name }
    }

    fun remove(url: String) {
        val next = saved.value.filterNot { it.url == url }
        prefs.edit().putStringSet("list", next.map { "${it.name}\t${it.url}\t${it.note}" }.toSet()).apply()
        saved.value = next
    }

    suspend fun fetch(url: String): Result<Feed> = withContext(Dispatchers.IO) {
        runCatching {
            val client = Injekt.get<NetworkHelper>().client
            client.newCall(GET(url)).execute().use { r ->
                if (r.code == 401 || r.code == 403) error("This catalog needs a login, which Fenrir Read doesn't support yet.")
                if (!r.isSuccessful) error("The catalog answered with an error (${r.code}).")
                parse(url, r.body?.string().orEmpty())
            }
        }
    }

    private fun parse(base: String, xml: String): Feed {
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(java.io.StringReader(xml))
        fun abs(h: String?) = h?.let { runCatching { java.net.URI(base).resolve(it).toString() }.getOrDefault(it) }
        val entries = mutableListOf<Entry>()
        var feedTitle = ""; var next: String? = null; var search: String? = null
        var inEntry = false; var inAuthor = false
        var title = ""; var author: String? = null; var cover: String? = null; var download: String? = null; var nav: String? = null
        var text = StringBuilder()
        var ev = parser.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (ev) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (parser.name) {
                        "entry" -> { inEntry = true; title = ""; author = null; cover = null; download = null; nav = null }
                        "author" -> inAuthor = true
                        "link" -> {
                            val rel = parser.getAttributeValue(null, "rel").orEmpty()
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            val href = abs(parser.getAttributeValue(null, "href"))
                            if (inEntry) {
                                when {
                                    rel.contains("acquisition") && type.contains("epub") && download == null -> download = href
                                    rel.contains("acquisition") && download == null && (type.contains("pdf") || type.contains("cbz") || type.contains("mobi")) -> download = href
                                    rel.contains("thumbnail") -> cover = href
                                    rel.contains("image") && cover == null -> cover = href
                                    type.contains("atom+xml") && nav == null -> nav = href
                                }
                            } else {
                                if (rel == "next") next = href
                                if (rel == "search" && type.contains("atom")) search = href
                            }
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> text.append(parser.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> if (inEntry) title = text.toString().trim() else if (feedTitle.isEmpty()) feedTitle = text.toString().trim()
                    "name" -> if (inAuthor && author == null) author = text.toString().trim()
                    "author" -> inAuthor = false
                    "entry" -> { inEntry = false; if (title.isNotBlank() && (download != null || nav != null)) entries += Entry(title, author, cover, download, nav) }
                }
            }
            ev = parser.next()
        }
        // Project Gutenberg's search link is OpenSearch-style; build a simple one when missing.
        if (search == null && base.contains("gutenberg.org")) search = "https://www.gutenberg.org/ebooks/search.opds/?query={searchTerms}"
        return Feed(feedTitle, entries, next, search)
    }
}

/** Catalog list for Browse → Sources: suggested + your own, with Add a catalog. */
@Composable
fun FenrirCatalogsCard() {
    val navigator = LocalNavigator.current
    LaunchedEffect(Unit) { FenrirCatalogs.load() }
    val saved by FenrirCatalogs.saved.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    val all = (FenrirCatalogs.suggested + saved).distinctBy { it.url }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("CATALOGS", color = Color(0xFFC9C9D2), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (adding) "Cancel" else "+ Add a catalog", color = Red, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { adding = !adding })
        }
        if (adding) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("Paste a catalog (OPDS) link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                "Add",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clip(CircleShape).background(Red).clickable {
                    val u = link.trim()
                    if (u.startsWith("http")) {
                        FenrirCatalogs.add(FenrirCatalogs.Catalog(FenrirImport.hostOf(u).ifBlank { "Catalog" }, u, "Your catalog"))
                        link = ""; adding = false
                    }
                }.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).border(1.dp, Rim, RoundedCornerShape(18.dp))) {
            all.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { navigator?.push(FenrirCatalogScreen(c.name, c.url)) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2F7A62)), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.note, color = Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (c in saved) {
                        Text("Remove", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { FenrirCatalogs.remove(c.url) }.padding(6.dp))
                    }
                }
            }
        }
    }
}

class FenrirCatalogScreen(private val name: String, private val url: String) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var current by remember { mutableStateOf(url) }
        var query by remember { mutableStateOf("") }
        var feed by remember { mutableStateOf<FenrirCatalogs.Feed?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        val extra = remember { mutableStateListOf<FenrirCatalogs.Entry>() }
        var next by remember { mutableStateOf<String?>(null) }
        val downloading = remember { mutableStateListOf<String>() }
        var message by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(current) {
            feed = null; error = null; extra.clear()
            FenrirCatalogs.fetch(current).onSuccess { feed = it; next = it.next }.onFailure { error = it.message ?: "Couldn't load the catalog." }
        }
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
            Header(name, feed?.title?.takeIf { it.isNotBlank() && it != name } ?: "Catalog") { navigator.pop() }
            feed?.search?.let { tpl ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search this catalog") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Search",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Red).clickable {
                            if (query.isNotBlank()) current = tpl.replace("{searchTerms}", java.net.URLEncoder.encode(query.trim(), "UTF-8"))
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            message?.let { Text(it, color = Color(0xFFFFB547), fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
            val f = feed
            when {
                error != null -> Text(error!!, color = Color(0xFFFF8A91), fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                f == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                else -> {
                    val list = f.entries + extra
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list.filter { it.download == null && it.nav != null }, span = { GridItemSpan(maxLineSpan) }) { e ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable { current = e.nav!! }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(e.title, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("›", color = Muted, fontSize = 18.sp)
                            }
                        }
                        items(list.filter { it.download != null }) { e ->
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF22232C))) {
                                    if (e.cover != null) AsyncImage(model = e.cover, contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Text(e.title.take(2).uppercase(), color = Color(0xFF55555F), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                                    Box(
                                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color(0xD10C0C10))
                                            .clickable(enabled = e.download !in downloading) {
                                                val dl = e.download!!
                                                downloading += dl
                                                scope.launch {
                                                    when (val r = FenrirImport.check(context, dl)) {
                                                        is FenrirImport.Result.Book -> navigator.push(eu.kanade.tachiyomi.ui.library.ImportEpubScreen(initialUriStrings = listOf(r.uri.toString())))
                                                        is FenrirImport.Result.Stop -> message = "${e.title}: ${r.title}"
                                                        else -> message = "${e.title}: couldn't download"
                                                    }
                                                    downloading -= dl
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (e.download in downloading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Red)
                                        else Icon(Icons.Outlined.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(e.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                e.author?.let { Text(it, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                        if (next != null) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Load more",
                                    color = Red,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp).clickable {
                                        val n = next ?: return@clickable
                                        next = null
                                        scope.launch { FenrirCatalogs.fetch(n).onSuccess { extra.addAll(it.entries); next = it.next } }
                                    },
                                )
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Storage */

class FenrirStorageScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var refresh by remember { mutableStateOf(0) }
        val sizes by produceState<Map<String, Long>?>(null, refresh) {
            value = withContext(Dispatchers.IO) {
                val sm = Injekt.get<StorageManager>()
                fun size(f: com.hippo.unifile.UniFile?, depth: Int = 0): Long =
                    if (f == null || depth > 6) 0L else if (!f.isDirectory) f.length() else f.listFiles()?.sumOf { size(it, depth + 1) } ?: 0L
                mapOf(
                    "Downloads" to size(sm.getDownloadsDirectory()),
                    "Imported" to size(sm.getLocalNovelSourceDirectory()) + size(sm.getLocalSourceDirectory()),
                    "Temporary" to (java.io.File(context.cacheDir, "fenrir_imports").walkTopDown().filter { it.isFile }.sumOf { it.length() }),
                )
            }
        }
        val finished by produceState<List<LibraryManga>>(emptyList(), refresh) {
            value = withContext(Dispatchers.IO) {
                runCatching { Injekt.get<GetLibraryManga>().await() }.getOrDefault(emptyList())
                    .filter { it.totalChapters > 0 && it.readCount >= it.totalChapters && it.manga.source != LOCAL_NOVEL_SOURCE && it.manga.source != LOCAL_MANGA_SOURCE }
            }
        }
        var cleaning by remember { mutableStateOf(false) }
        fun mb(b: Long) = if (b >= 1L shl 30) String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0) else "${b / 1048576} MB"
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
            Header("Storage & backup", null) { navigator.pop() }
            val s = sizes
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).border(1.dp, Rim, RoundedCornerShape(20.dp)).padding(14.dp)) {
                if (s == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Red)
                        Spacer(Modifier.width(8.dp)); Text("Measuring…", color = Muted, fontSize = 13.sp)
                    }
                } else {
                    val total = s.values.sum().coerceAtLeast(1)
                    val free = runCatching { android.os.StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
                    Row { Text("Fenrir Read uses ${mb(s.values.sum())}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${mb(free)} free", color = Muted, fontSize = 13.sp) }
                    val colors = listOf(Red, Color(0xFF8FB4FF), Color(0xFFFFB547))
                    Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFF26262E))) {
                        s.values.forEachIndexed { i, v -> if (v > 0) Box(Modifier.weight(v.toFloat() / total).height(10.dp).background(colors[i % 3])) }
                        val rest = 0.0001f; Box(Modifier.weight(rest))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        s.entries.forEachIndexed { i, (k, v) -> Text("● $k ${mb(v)}", color = if (i == 0) Red else colors[i % 3], fontSize = 11.5.sp) }
                    }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Finished books with downloads: ${finished.size}", color = Color(0xFFC9C9D2), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (cleaning) "Cleaning…" else "Clean up",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(Red).clickable(enabled = !cleaning && (finished.isNotEmpty() || (s["Temporary"] ?: 0) > 0)) {
                                cleaning = true
                                scope.launch(Dispatchers.IO) {
                                    val dm = Injekt.get<eu.kanade.tachiyomi.data.download.DownloadManager>()
                                    val sources = Injekt.get<tachiyomi.domain.source.service.SourceManager>()
                                    finished.forEach { m -> runCatching { dm.deleteManga(m.manga, sources.getOrStub(m.manga.source)) } }
                                    runCatching { java.io.File(context.cacheDir, "fenrir_imports").deleteRecursively() }
                                    cleaning = false; refresh++
                                }
                            }.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Text("WHERE BOOKS GO · BACKUP", color = Color(0xFF8E8E99), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 26.dp, top = 6.dp))
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).border(1.dp, Rim, RoundedCornerShape(20.dp))) {
                listOf(
                    "Storage location" to "Phone, SD card or any folder (even one a cloud app syncs)",
                    "Backup & restore" to "Back up now, restore, and automatic backups (daily or weekly)",
                ).forEach { (t, d) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { navigator.push(eu.kanade.tachiyomi.ui.setting.SettingsScreen(eu.kanade.tachiyomi.ui.setting.SettingsScreen.Destination.DataAndStorage)) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(d, color = Muted, fontSize = 12.sp)
                        }
                        Text("›", color = Muted, fontSize = 20.sp)
                    }
                }
            }
            Text(
                "Tip: pick a folder a cloud app like Google Drive syncs as the backup location, and your backups end up in your cloud.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 26.dp),
            )
        }
    }
}
