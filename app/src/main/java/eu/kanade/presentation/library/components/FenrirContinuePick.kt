package eu.kanade.presentation.library.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Fenrir: the book for "Continue reading". It comes from your reading history (updated the moment
 * you read), not from the library list, which is only refreshed now and then and also skips books
 * hidden by filters or categories. That's why some manga and webtoons never showed up, or an old
 * book stayed there. Picks the most recently read book in your library that still has unread
 * chapters, with fresh chapter counts. [kinds]: which of novel (true) / manga (false) this library
 * shows, or null for both. Falls back to [fallback] if history gives nothing.
 */
@Composable
internal fun rememberFenrirContinueBook(fallback: LibraryManga?, kinds: Set<Boolean>?): LibraryManga? {
    val history = remember { Injekt.get<HistoryRepository>().getHistoryGrouped("", 40) }
    val recent by history.collectAsState(initial = null)
    val libraryFlow = remember { Injekt.get<GetLibraryManga>().subscribe() }
    val library by libraryFlow.collectAsState(initial = emptyList())
    val picked by produceState<LibraryManga?>(null, recent, library, kinds) {
        val list = recent ?: return@produceState
        value = runCatching {
            withContext(Dispatchers.IO) {
                val byId = library.associateBy { it.manga.id }
                val getChapters = Injekt.get<GetChaptersByMangaId>()
                var checked = 0
                val seen = HashSet<Long>()
                for (h in list.sortedByDescending { it.readAt?.time ?: 0L }) {
                    if (!seen.add(h.mangaId)) continue
                    val book = byId[h.mangaId] ?: continue
                    if (kinds != null && book.manga.isNovel !in kinds) continue
                    if (++checked > 12) break
                    val chapters = runCatching { getChapters.await(h.mangaId, applyScanlatorFilter = true) }.getOrNull()
                        ?: continue
                    val total = chapters.size.toLong()
                    val read = chapters.count { it.read }.toLong()
                    if (total - read <= 0) continue
                    return@withContext book.copy(
                        totalChapters = total,
                        readCount = read,
                        lastRead = maxOf(book.lastRead, h.readAt?.time ?: 0L),
                    )
                }
                null
            }
        }.getOrNull()
    }
    return picked ?: fallback
}
