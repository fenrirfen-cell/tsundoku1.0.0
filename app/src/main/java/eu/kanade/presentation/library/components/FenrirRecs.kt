package eu.kanade.presentation.library.components

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
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
        return withTimeoutOrNull(10_000) {
            runCatching { source.getSearchManga(1, query, FilterList()).mangas }.getOrDefault(emptyList())
        }.orEmpty()
    }

    suspend fun popular(sourceId: Long): List<SManga> {
        val source = sources.get(sourceId) as? CatalogueSource ?: return emptyList()
        return withTimeoutOrNull(10_000) {
            runCatching { source.getPopularManga(1).mangas }.getOrDefault(emptyList())
        }.orEmpty()
    }

    /** A suggestion that isn't on this source: tapping it searches all your sources for it. */
    const val SEARCH_EVERYWHERE = -2L

    /**
     * Books readers of [title] also liked (from AniList), found on [sourceId] when it has them.
     * Ones the source doesn't have stay in the row and open a search across your sources.
     */
    suspend fun similarIn(sourceId: Long, title: String, novel: Boolean, skip: (String) -> Boolean = { false }): List<FenrirRec> =
        kotlinx.coroutines.coroutineScope {
            val gate = kotlinx.coroutines.sync.Semaphore(3)
            FenrirRatings.similar(title, novel)
                .filterNot { s -> s.names.any { skip(it) } || s.names.any { sameTitle(it, title) } }
                .take(10)
                .map { s ->
                    async {
                        val hit = gate.withPermitCompat {
                            s.names.firstNotNullOfOrNull { name ->
                                search(sourceId, name).firstOrNull { FenrirRatings.matches(it.title, s.names) }
                            }
                        }
                        if (hit != null) {
                            FenrirRec(sourceId, hit, null)
                        } else {
                            FenrirRec(
                                SEARCH_EVERYWHERE,
                                SManga.create().apply {
                                    this.title = s.names.first()
                                    url = "fenrir-search:" + s.names.first()
                                    thumbnail_url = s.cover
                                },
                                "FIND IT",
                            )
                        }
                    }
                }
                .awaitAll()
                .distinctBy { it.sourceId.toString() + it.manga.url }
                .sortedBy { if (it.sourceId == SEARCH_EVERYWHERE) 1 else 0 }
        }

    private suspend fun <T> kotlinx.coroutines.sync.Semaphore.withPermitCompat(block: suspend () -> T): T {
        acquire()
        try { return block() } finally { release() }
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

    /** Same book (also when one title is cut short with "…" or differs only in punctuation). */
    private fun sameTitle(a: String, b: String): Boolean {
        fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
        val cutA = a.trim().endsWith("…") || a.trim().endsWith("...")
        val cutB = b.trim().endsWith("…") || b.trim().endsWith("...")
        val na = norm(a.trim().removeSuffix("…").removeSuffix("..."))
        val nb = norm(b.trim().removeSuffix("…").removeSuffix("..."))
        if (na.isEmpty() || nb.isEmpty()) return false
        return na == nb || (cutA && nb.startsWith(na)) || (cutB && na.startsWith(nb))
    }

    /** Book page: sequels / side stories (same title), more from the author, popular on the source. */
    suspend fun forBook(manga: Manga): List<FenrirRecRow> = kotlinx.coroutines.coroutineScope {
        val stem = titleStem(manga.title)
        val author = manga.author?.split(",", "/", "&")?.firstOrNull()?.trim().orEmpty()
        val related = async {
            if (stem.length < 3) emptyList() else search(manga.source, stem)
                .filter { !sameTitle(it.title, manga.title) && it.url != manga.url && titleStem(it.title).contains(stem) }
                .map { FenrirRec(manga.source, it, tagFor(it.title)) }
        }
        val byAuthor = async {
            if (author.length < 3) emptyList() else search(manga.source, author)
                .filter { !sameTitle(it.title, manga.title) && it.url != manga.url }
                .map { FenrirRec(manga.source, it) }
        }
        val similar = async {
            runCatching { similarIn(manga.source, manga.title, manga.isNovel) }.getOrDefault(emptyList())
        }
        val popular = async { popular(manga.source).filter { !sameTitle(it.title, manga.title) && it.url != manga.url }.map { FenrirRec(manga.source, it) } }
        val rel = related.await()
        val relUrls = rel.map { it.manga.url }.toSet()
        listOfNotNull(
            FenrirRecRow("Readers also liked", "", similar.await().filter { it.manga.url !in relUrls }).takeIf { it.items.isNotEmpty() },
            FenrirRecRow("Sequels, side stories & more", "", rel.take(15)).takeIf { it.items.isNotEmpty() },
            FenrirRecRow("More from ", author, byAuthor.await().filter { it.manga.url !in relUrls }.take(15)).takeIf { it.items.isNotEmpty() },
            FenrirRecRow("Popular on ", sourceName(manga.source), popular.await().take(15)).takeIf { it.items.isNotEmpty() },
        )
    }

    /**
     * Same rows as [forBook], but each row shows as soon as it's ready (instead of waiting for the
     * slowest). "Readers also liked" shows right away from AniList, then fills in which ones your
     * source has. Results are kept for the session, and loading starts when the book page opens.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.flow.MutableStateFlow<RecState>>()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    data class RecState(val rows: List<FenrirRecRow>, val loading: Boolean)

    fun rowsFor(manga: Manga): kotlinx.coroutines.flow.StateFlow<RecState> {
        cache[manga.id]?.let { return it }
        val flow = kotlinx.coroutines.flow.MutableStateFlow(RecState(emptyList(), true))
        cache.putIfAbsent(manga.id, flow)?.let { return it }
        scope.launch { runCatching { load(manga, flow) }; flow.value = flow.value.copy(loading = false) }
        return flow
    }

    private suspend fun load(manga: Manga, out: kotlinx.coroutines.flow.MutableStateFlow<RecState>) = kotlinx.coroutines.coroutineScope {
        val stem = titleStem(manga.title)
        val author = manga.author?.split(",", "/", "&")?.firstOrNull()?.trim().orEmpty()
        val slots = arrayOfNulls<FenrirRecRow>(4)
        val lock = Any()
        fun put(i: Int, row: FenrirRecRow?) {
            synchronized(lock) {
                slots[i] = row?.takeIf { it.items.isNotEmpty() }
                out.value = out.value.copy(rows = slots.filterNotNull())
            }
        }
        val related = async {
            val list = if (stem.length < 3) emptyList() else search(manga.source, stem)
                .filter { !sameTitle(it.title, manga.title) && it.url != manga.url && titleStem(it.title).contains(stem) }
                .map { FenrirRec(manga.source, it, tagFor(it.title)) }
            put(1, FenrirRecRow("Sequels, side stories & more", "", list.take(15)))
            list
        }
        launch {
            val similar = runCatching {
                withTimeoutOrNull(15_000) { FenrirRatings.similar(manga.title, manga.isNovel) }.orEmpty()
            }.getOrDefault(emptyList())
                .filterNot { s -> s.names.any { sameTitle(it, manga.title) } }
                .take(10)
            if (similar.isEmpty()) return@launch
            // Show them straight away (tap = find it in your sources) …
            val items = similar.map { s ->
                FenrirRec(
                    SEARCH_EVERYWHERE,
                    SManga.create().apply {
                        this.title = s.names.first()
                        url = "fenrir-search:" + s.names.first()
                        thumbnail_url = s.cover
                    },
                    "FIND IT",
                )
            }.toMutableList()
            put(0, FenrirRecRow("Readers also liked", "", items.toList()))
            // … then swap in the ones this source has, a few at a time.
            val gate = kotlinx.coroutines.sync.Semaphore(4)
            similar.mapIndexed { i, s ->
                launch {
                    val hit = gate.withPermitCompat {
                        withTimeoutOrNull(12_000) {
                            s.names.take(2).firstNotNullOfOrNull { name ->
                                search(manga.source, name).firstOrNull { FenrirRatings.matches(it.title, s.names) }
                            }
                        }
                    }
                    if (hit != null) {
                        synchronized(items) { items[i] = FenrirRec(manga.source, hit, null) }
                        put(0, FenrirRecRow("Readers also liked", "", synchronized(items) { items.sortedBy { if (it.sourceId == SEARCH_EVERYWHERE) 1 else 0 } }))
                    }
                }
            }
        }
        launch {
            val list = if (author.length < 3) emptyList() else search(manga.source, author)
                .filter { !sameTitle(it.title, manga.title) && it.url != manga.url }
                .map { FenrirRec(manga.source, it) }
            val relUrls = runCatching { related.await().map { it.manga.url }.toSet() }.getOrDefault(emptySet())
            put(2, FenrirRecRow("More from ", author, list.filter { it.manga.url !in relUrls }.take(15)))
        }
        launch {
            val list = popular(manga.source).filter { !sameTitle(it.title, manga.title) && it.url != manga.url }.map { FenrirRec(manga.source, it) }
            put(3, FenrirRecRow("Popular on ", sourceName(manga.source), list.take(15)))
        }
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

private fun titleKey(t: String) = FenrirRecs.titleStem(t)

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
                // "Because you read …": what readers of your recent books also liked.
                val ownedTitles = library.map { it.manga.title }
                val becauseRows = recent.distinctBy { titleKey(it.manga.title) }.take(3).map { lm ->
                    async {
                        val found = runCatching {
                            FenrirRecs.similarIn(lm.manga.source, lm.manga.title, lm.manga.isNovel) { t -> FenrirRatings.matches(t, ownedTitles) }
                        }.getOrDefault(emptyList()).let { list -> list.filter { it.sourceId == FenrirRecs.SEARCH_EVERYWHERE || (it.sourceId to it.manga.url) !in owned } }
                        FenrirRecRow("Because you read ", lm.manga.title, found).takeIf { found.isNotEmpty() }
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
                rows.value = (becauseRows + authorRows + popularRows).awaitAll().filterNotNull()
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
    val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Dialog(onDismissRequest = { FenrirReadForYou.open.value = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { FenrirReadForYou.open.value = false },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Fenrir: swipe the top down to close, up for full screen.
            var dragY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            var full by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (full) 0.95f else 0.8f)
                    .offset { androidx.compose.ui.unit.IntOffset(0, dragY.coerceAtLeast(0f).toInt()) }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF15151B))
                    .clickable(enabled = false) {}
                    .padding(top = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                when {
                                    dragY > 260f -> FenrirReadForYou.open.value = false
                                    dragY > 90f && full -> full = false
                                    dragY < -90f -> full = true
                                }
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                            onVerticalDrag = { _, dy -> dragY += dy },
                        )
                    },
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
                }
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
                                if (rec.sourceId == FenrirRecs.SEARCH_EVERYWHERE) {
                                    FenrirReadForYou.open.value = false
                                    navigator?.push(fenrirFindEverywhere(rec))
                                    return@FenrirRecRowView
                                }
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
    // starts looking as soon as the book page opens, so it's ready when you unfold it
    val state by remember(manga.id) { FenrirRecs.rowsFor(manga) }.collectAsState()
    val scope = rememberCoroutineScope()
    val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current
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
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = RecRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("You may like", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text("Similar books, sequels, same author", color = Color(0xFF9A9AA6), fontSize = 11.5.sp)
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFFC9C9D2),
            )
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                state.rows.forEach { row ->
                    FenrirRecRowView(row) { rec ->
                        if (rec.sourceId == FenrirRecs.SEARCH_EVERYWHERE) {
                            navigator?.push(fenrirFindEverywhere(rec, manga.isNovel))
                            return@FenrirRecRowView
                        }
                        scope.launch {
                            val id = runCatching { FenrirRecs.open(rec) }.getOrNull() ?: return@launch
                            onOpenManga(id)
                        }
                    }
                }
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = RecRed)
                    }
                    state.rows.isEmpty() -> Text(
                        "Couldn't find similar books for this one.",
                        color = Color(0xFFB8B8C2),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/** Opens a search across your sources for a suggestion the current source doesn't have. */
private fun fenrirFindEverywhere(rec: FenrirRec, novel: Boolean? = null): cafe.adriel.voyager.core.screen.Screen {
    val kind = when (novel) {
        true -> eu.kanade.presentation.browse.FenrirSearchKind.Novels
        false -> eu.kanade.presentation.browse.FenrirSearchKind.Manga
        null -> eu.kanade.presentation.browse.FenrirSearchKind.Both
    }
    return eu.kanade.presentation.browse.FenrirSearchKind.screen(rec.manga.title, kind)
}
