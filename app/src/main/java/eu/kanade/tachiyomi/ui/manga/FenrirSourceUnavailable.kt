package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Fenrir: when an entry can't be loaded from its source any more (removed, renamed, site down),
 * tell the user plainly and look for the same title on their other installed sources.
 */
object FenrirSourceUnavailable {

    private const val PER_SOURCE_TIMEOUT_MS = 10_000L
    private const val MAX_PARALLEL = 5
    private const val MIN_SIMILARITY = 0.85

    private val notFoundPatterns = listOf(
        Regex("""\b(404|410)\b"""),
        Regex("""could not reach site""", RegexOption.IGNORE_CASE),
    )

    /** True when the error means "this book isn't on this source", not a generic failure. */
    fun matches(error: Throwable): Boolean {
        var e: Throwable? = error
        while (e != null) {
            if (e is HttpException && (e.code == 404 || e.code == 410)) return true
            val msg = e.message.orEmpty()
            if (notFoundPatterns.any { it.containsMatchIn(msg) }) return true
            e = e.cause
        }
        return false
    }

    /**
     * Searches the user's other enabled sources of the same kind (novel or manga) for [manga]'s
     * title. Returns the source and the matching entry (already saved locally), or null.
     */
    suspend fun findAlternative(
        manga: Manga,
        currentSourceId: Long,
        sourceManager: SourceManager = Injekt.get(),
        sourcePreferences: SourcePreferences = Injekt.get(),
        networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    ): Pair<CatalogueSource, Manga>? = coroutineScope {
        val query = manga.title.trim()
        if (query.isEmpty()) return@coroutineScope null

        val enabledLanguages = sourcePreferences.enabledLanguages.get()
        val disabledSources = sourcePreferences.disabledSources.get()
        val pinned = sourcePreferences.pinnedSources.get()

        val sources = sourceManager.getAll()
            .filterIsInstance<CatalogueSource>()
            .filter { it.id != currentSourceId && it.id != 0L }
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .filter { it.isNovelSource() == manga.isNovel }
            // Pinned sources first, so ties go to the user's favourites.
            .sortedBy { if ("${it.id}" in pinned) 0 else 1 }

        val target = normalize(query)
        val semaphore = Semaphore(MAX_PARALLEL)

        val candidates = sources.map { source ->
            async {
                semaphore.withPermit {
                    try {
                        withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                            source.getSearchManga(1, query, source.getFilterList())
                                .mangas
                                .map { it to similarity(target, normalize(it.title)) }
                                .filter { it.second >= MIN_SIMILARITY }
                                .maxByOrNull { it.second }
                                ?.let { (sManga, score) -> Triple(source, sManga, score) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logcat(LogPriority.DEBUG, e) { "Alternative search failed on ${source.name}" }
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()

        val best = candidates.maxByOrNull { it.third } ?: return@coroutineScope null
        val (source, sManga) = best
        val local = networkToLocalManga(sManga.toDomainManga(source.id, source.isNovelSource()))
        source to local
    }

    private fun normalize(title: String): String =
        title.lowercase()
            .replace(Regex("""[\p{Punct}\s]+"""), " ")
            .trim()

    /** 1.0 = identical, 0.0 = nothing in common (Levenshtein ratio). */
    internal fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            curr.copyInto(prev)
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
