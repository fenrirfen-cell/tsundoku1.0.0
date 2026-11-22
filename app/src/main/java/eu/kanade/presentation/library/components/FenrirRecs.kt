package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A suggested title from one of the user's sources. */
data class FenrirRec(val sourceId: Long, val manga: SManga, val tag: String? = null)

data class FenrirRecRow(val title: String, val accent: String, val items: List<FenrirRec>)

/** Finding suggestions in the user's own sources (search by author / title, popular lists). */
object FenrirRecs {
    private val sources by lazy { Injekt.get<SourceManager>() }
    private val networkToLocal by lazy { Injekt.get<NetworkToLocalManga>() }

    suspend fun search(sourceId: Long, query: String): List<SManga> {
        val source = sources.get(sourceId) as? CatalogueSource ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return withTimeoutOrNull(20_000) {
            runCatching { source.getSearchManga(1, query, FilterList()).mangas }.getOrDefault(emptyList())
        }.orEmpty()
    }

    suspend fun popular(sourceId: Long): List<SManga> {
        val source = sources.get(sourceId) as? CatalogueSource ?: return emptyList()
        return withTimeoutOrNull(20_000) {
            runCatching { source.getPopularManga(1).mangas }.getOrDefault(emptyList())
        }.orEmpty()
    }

    fun sourceName(sourceId: Long): String = sources.get(sourceId)?.name ?: "your source"

    /** Saves the suggestion as a local entry (not in the library) and returns its id to open it. */
    suspend fun open(rec: FenrirRec): Long = withContext(Dispatchers.IO) {
        val isNovel = sources.get(rec.sourceId)?.isNovelSource ?: false
        networkToLocal(rec.manga.toDomainManga(rec.sourceId, isNovel)).id
    }

    /** "Lantern Archivist: Vol. 2 (Web Novel)" -> "lantern archivist". */
    fun titleStem(title: String): String = title.lowercase()
        .replace(Regex("""[(\[{].*?[)\]}]"""), " ")
        .substringBefore(":")
        .replace(Regex("""\b(vol(ume)?|season|part|book|arc)\.?\s*\d+.*$"""), " ")
        .replace(Regex("""\b\d+\s*$"""), " ")
        .replace(Regex("""^(the|a|an)\s+"""), "")
        .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun sameTitle(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** Book page: sequels / side stories (same title), more from the author, popular on the source. */
    suspend fun forBook(manga: Manga): List<FenrirRecRow> = kotlinx.coroutines.coroutineScope {
        val stem = titleStem(manga.title)
        val author = manga.author?.split(",", "/", "&")?.firstOrNull()?.trim().orEmpty()
        val related = async {
            if (stem.length < 3) emptyList() else search(manga.source, stem)
                .filter { !sameTitle(it.title, manga.title) && titleStem(it.title).contains(stem) }
                .map { FenrirRec(manga.source, it, tagFor(it.title)) }
        }
        val byAuthor = async {
            if (author.length < 3) emptyList() else search(manga.source, author)
                .filter { !sameTitle(it.title, manga.title) }
                .map { FenrirRec(manga.source, it) }
        }
        val popular = async { popular(manga.source).filter { !sameTitle(it.title, manga.title) }.map { FenrirRec(manga.source, it) } }
        val rel = related.await()
        val relUrls = rel.map { it.manga.url }.toSet()
        listOfNotNull(
            FenrirRecRow("Sequels, side stories & more", "", rel.take(15)).takeIf { it.items.isNotEmpty() },
            FenrirRecRow("More from ", author, byAuthor.await().filter { it.manga.url !in relUrls }.take(15)).takeIf { it.items.isNotEmpty() },
            FenrirRecRow("Popular on ", sourceName(manga.source), popular.await().take(15)).takeIf { it.items.isNotEmpty() },
        )
    }

    private fun tagFor(title: String): String? {
        val t = title.lowercase()
        return when {
            Regex("""\b(vol(ume)?|season|part|book)\.?\s*\d""").containsMatchIn(t) -> "SEQUEL"
            "side stor" in t || "extra" in t || "after stor" in t || "gaiden" in t -> "EXTRA"
            "manga" in t || "comic" in t || "manhwa" in t -> "COMIC"
            "novel" in t -> "NOVEL"
            else -> "RELATED"
        }
    }
}

/** Library ✨ "For you": picks from the user's own sources, based on what they read. */
object FenrirReadForYou {
    val open = MutableStateFlow(false)
    val rows = MutableStateFlow<List<FenrirRecRow>>(emptyList())
    val loading = MutableStateFlow(false)
    private var loadedAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun show() {
        open.value = true
        if (loading.value) return
        if (rows.value.isNotEmpty() && System.currentTimeMillis() - loadedAt < 30 * 60 * 1000L) return
        loading.value = true
        scope.launch {
            try {
                val library = runCatching { Injekt.get<MangaRepository>().getLibraryManga() }.getOrDefault(emptyList())
                val owned = library.map { it.manga.source to it.manga.url }.toSet()
                fun fresh(list: List<FenrirRec>) = list.filter { (it.sourceId to it.manga.url) !in owned }.distinctBy { it.manga.url }
                val recent = library.filter { it.lastRead > 0 }.sortedByDescending { it.lastRead }
                val authorRows = recent
                    .mapNotNull { lm -> lm.manga.author?.split(",", "/", "&")?.firstOrNull()?.trim()?.takeIf { it.length >= 3 }?.let { lm to it } }
                    .distinctBy { it.second.lowercase() }
                    .take(3)
                    .map { (lm, author) ->
                        async {
                            val found = fresh(FenrirRecs.search(lm.manga.source, author).map { FenrirRec(lm.manga.source, it) })
                                .filterNot { r -> library.any { it.manga.title.equals(r.manga.title, true) } }
                            FenrirRecRow("More from ", author, found.take(15)).takeIf { found.isNotEmpty() }
                        }
                    }
                val topSources = library.groupingBy { it.manga.source }.eachCount().entries
                    .sortedByDescending { it.value }.take(2).map { it.key }
                val popularRows = topSources.map { id ->
                    async {
                        val found = fresh(FenrirRecs.popular(id).map { FenrirRec(id, it) })
                        FenrirRecRow("Popular on ", FenrirRecs.sourceName(id), found.take(15)).takeIf { found.isNotEmpty() }
                    }
                }
                rows.value = (authorRows + popularRows).awaitAll().filterNotNull()
                loadedAt = System.currentTimeMillis()
            } finally {
                loading.value = false
            }
        }
    }
}

private val RecRed = Color(0xFFFF4D57)

@Composable
fun FenrirRecRowView(row: FenrirRecRow, onOpen: (FenrirRec) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.padding(horizontal = 18.dp)) {
            Text(row.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (row.accent.isNotBlank()) {
                Text(row.accent, color = RecRed, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(row.items, key = { it.sourceId.toString() + it.manga.url }) { rec ->
                Column(Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(rec) }) {
                    Box {
                        MangaCover.Book(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            data = tachiyomi.domain.manga.model.MangaCover(
                                mangaId = -1L,
                                sourceId = rec.sourceId,
                                isMangaFavorite = false,
                                url = rec.manga.thumbnail_url,
                                lastModified = 0L,
                            ),
                        )
                        rec.tag?.let { tag ->
                            Text(
                                tag,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(RecRed).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        rec.manga.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

/** The Library ✨ For you panel. [onOpenManga] opens a book page by id. */
@Composable
fun FenrirReadForYouHost(onOpenManga: (Long) -> Unit) {
    val open by FenrirReadForYou.open.collectAsState()
    if (!open) return
    val rows by FenrirReadForYou.rows.collectAsState()
    val loading by FenrirReadForYou.loading.collectAsState()
    val scope = rememberCoroutineScope()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Dialog(onDismissRequest = { FenrirReadForYou.open.value = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { FenrirReadForYou.open.value = false },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF15151B))
                    .clickable(enabled = false) {}
                    .padding(top = 12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color(0xFF3A3A44)))
                Row(Modifier.padding(start = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = RecRed, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("For you", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    "Picked from your sources, based on what you read",
                    color = Color(0xFF9A9AA6),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 6.dp),
                )
                when {
                    loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RecRed)
                    }
                    rows.isEmpty() -> Text(
                        "Nothing to suggest yet. Add and read a few books and check back.",
                        color = Color(0xFFB8B8C2),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
                        items(rows, key = { it.title + it.accent }) { row ->
                            FenrirRecRowView(row) { rec ->
                                scope.launch {
                                    val id = runCatching { FenrirRecs.open(rec) }.getOrNull() ?: return@launch
                                    FenrirReadForYou.open.value = false
                                    onOpenManga(id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Book page: folded "You may like" card; opening it looks for sequels, same author, popular. */
@Composable
fun FenrirYouMayLike(manga: Manga, onOpenManga: (Long) -> Unit) {
    var expanded by remember(manga.id) { mutableStateOf(false) }
    var rows by remember(manga.id) { mutableStateOf<List<FenrirRecRow>?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF15151B))
            .border(1.dp, Color(0xFF24242C), RoundedCornerShape(18.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                    if (expanded && rows == null) {
                        scope.launch { rows = runCatching { FenrirRecs.forBook(manga) }.getOrDefault(emptyList()) }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = RecRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("You may like", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text("Sequels, side stories, same author", color = Color(0xFF9A9AA6), fontSize = 11.5.sp)
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFFC9C9D2),
            )
        }
        if (expanded) {
            val list = rows
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = RecRed)
                }
                list.isEmpty() -> Text(
                    "Couldn't find anything related on this source.",
                    color = Color(0xFFB8B8C2),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
                else -> Column(Modifier.padding(bottom = 8.dp)) {
                    list.forEach { row ->
                        FenrirRecRowView(row) { rec ->
                            scope.launch {
                                val id = runCatching { FenrirRecs.open(rec) }.getOrNull() ?: return@launch
                                onOpenManga(id)
                            }
                        }
                    }
                }
            }
        }
    }
}
