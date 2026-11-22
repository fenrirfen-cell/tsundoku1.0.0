package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(manga: Manga, downloadManager: DownloadManager): Chapter? {
    return applyFilters(manga, downloadManager).let { chapters ->
        val ordered = if (manga.sortDescending()) chapters.reversed() else chapters
        FenrirContinue.pick(manga.id, ordered)
    }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
    return applyFilters(manga).let { chapters ->
        val ordered = (if (manga.sortDescending()) chapters.reversed() else chapters).map { it.chapter }
        FenrirContinue.pick(manga.id, ordered)
    }
}

/**
 * Fenrir: Continue follows your reading history instead of the first chapter that isn't marked
 * read — one chapter closed a few lines early no longer sends you far back.
 *
 * The chapter read most recently: that one if it isn't finished, otherwise the next unread one
 * after it. Falls back to the first unread chapter when there's no history.
 */
object FenrirContinue {
    private val lastRead = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    /** Remembers the most recently read chapter of [mangaId] (from History). */
    suspend fun refresh(mangaId: Long) {
        val history = runCatching {
            uy.kohesive.injekt.Injekt.get<tachiyomi.domain.history.repository.HistoryRepository>()
                .getHistoryByMangaId(mangaId)
        }.getOrNull() ?: return
        val latest = history.filter { it.readAt != null }.maxByOrNull { it.readAt!!.time } ?: return
        lastRead[mangaId] = latest.chapterId
    }

    /** [ordered] = chapters in reading order (first chapter first). */
    fun pick(mangaId: Long, ordered: List<Chapter>): Chapter? {
        val firstUnread = ordered.find { !it.read }
        val id = lastRead[mangaId] ?: return firstUnread
        val idx = ordered.indexOfFirst { it.id == id }
        if (idx < 0) return firstUnread
        val last = ordered[idx]
        if (!last.read) return last
        return ordered.drop(idx + 1).firstOrNull { !it.read } ?: firstUnread
    }

    /** Unread chapters before the one Continue opens (for the "still unread" chip). */
    fun skippedBefore(mangaId: Long, ordered: List<Chapter>): List<Chapter> {
        val target = pick(mangaId, ordered) ?: return emptyList()
        val idx = ordered.indexOfFirst { it.id == target.id }
        if (idx <= 0) return emptyList()
        return ordered.take(idx).filter { !it.read }
    }
}
