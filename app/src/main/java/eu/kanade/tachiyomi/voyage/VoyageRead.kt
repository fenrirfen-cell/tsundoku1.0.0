package eu.kanade.tachiyomi.voyage

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MugiVerse voyage features (Continue, Captain's Log, Bounties, Offline Voyage, Surprise Me) read
 * the Read side through here: plain data from the real history, library and download queue.
 * Nothing leaves the phone.
 */
object VoyageRead {
    data class HistoryRow(val mangaId: Long, val chapterId: Long, val readAt: Long, val durationMs: Long, val chapterRead: Boolean)

    data class Book(
        val mangaId: Long,
        val title: String,
        val coverUrl: String?,
        val isNovel: Boolean,
        val favorite: Boolean,
        val totalChapters: Long,
        val readCount: Long,
        val lastRead: Long,
        val genres: List<String>,
        val language: String?,
        val downloadCount: Long,
    )

    data class Continue(
        val mangaId: Long,
        val chapterId: Long,
        val title: String,
        val coverUrl: String?,
        val isNovel: Boolean,
        val chapterNumber: Double,
        val readAt: Long,
        val totalChapters: Long,
        val readCount: Long,
    )

    data class Snapshot(val history: List<HistoryRow>, val books: List<Book>, val continueReading: List<Continue>, val downloadedChapters: Int)

    enum class DlState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    data class Dl(val chapterId: Long, val mangaId: Long, val title: String, val chapterName: String, val state: DlState, val progress: Int)

    suspend fun snapshot(): Snapshot {
        // MugiVerse Private Library: locked private books never leave the Read side
        val hidden = eu.kanade.tachiyomi.vault.Vault.hiddenIds()
        // Captain's Quarters: while Read is locked nothing from it reaches the Watch side
        val readLocked = eu.kanade.tachiyomi.vault.Vault.readLocked()
        val history = if (readLocked) emptyList() else Injekt.get<GetHistory>().subscribe("", Long.MAX_VALUE).first().filter { it.mangaId !in hidden }
        val library = if (readLocked) emptyList() else runCatching { Injekt.get<GetLibraryManga>().await() }.getOrDefault(emptyList()).filter { it.manga.id !in hidden }
        val sources = Injekt.get<SourceManager>()
        val books = library.map { lm ->
            val m = lm.manga
            Book(
                mangaId = m.id,
                title = m.title,
                coverUrl = m.thumbnailUrl,
                isNovel = m.isNovel,
                favorite = m.favorite,
                totalChapters = lm.totalChapters,
                readCount = lm.readCount,
                lastRead = lm.lastRead,
                genres = m.genre.orEmpty().map { it.trim() }.filter { it.isNotEmpty() },
                language = runCatching { sources.get(m.source)?.lang }.getOrNull(),
                downloadCount = lm.downloadCount,
            )
        }
        val byId = books.associateBy { it.mangaId }
        val rows = history.map { HistoryRow(it.mangaId, it.chapterId, it.readAt?.time ?: 0L, it.readDuration, it.chapterRead) }
        val latest = history.filter { (it.readAt?.time ?: 0L) > 0 }.groupBy { it.mangaId }.mapNotNull { (_, list) -> list.maxByOrNull { it.readAt?.time ?: 0L } }
        val cont = latest
            .sortedByDescending { it.readAt?.time ?: 0L }
            .mapNotNull { h ->
                val b = byId[h.mangaId]
                val total = b?.totalChapters ?: 0L
                val read = b?.readCount ?: 0L
                if (total > 0 && read >= total) return@mapNotNull null // finished
                Continue(
                    mangaId = h.mangaId,
                    chapterId = h.chapterId,
                    title = h.title,
                    coverUrl = b?.coverUrl ?: h.coverData.url,
                    isNovel = h.isNovel,
                    chapterNumber = h.chapterNumber,
                    readAt = h.readAt?.time ?: 0L,
                    totalChapters = total,
                    readCount = read,
                )
            }
            .take(30)
        val downloaded = runCatching { Injekt.get<DownloadManager>().getDownloadCount() }.getOrDefault(0)
        return Snapshot(rows, books, cont, downloaded)
    }

    /** The chapter to open: the same one if it isn't finished, otherwise the next unread one. */
    suspend fun resumeChapterId(mangaId: Long, chapterId: Long): Long {
        val next = runCatching { Injekt.get<GetNextChapters>().await(mangaId, chapterId, onlyUnread = true) }.getOrDefault(emptyList())
        return next.firstOrNull()?.id ?: chapterId
    }

    /** Queues the next [count] unread chapters (already downloaded or queued ones are skipped). */
    suspend fun downloadNext(mangaId: Long, count: Int): Int {
        val manager = Injekt.get<DownloadManager>()
        val manga = Injekt.get<GetManga>().await(mangaId) ?: return 0
        val queued = manager.queueState.value.map { it.chapterId }.toSet()
        val chapters = Injekt.get<GetNextChapters>().await(mangaId, onlyUnread = true)
            .filter { it.id !in queued }
            .take(count)
        if (chapters.isEmpty()) return 0
        val before = manager.queueState.value.size
        manager.downloadChapters(manga, chapters)
        delay(400)
        val added = manager.queueState.value.size - before
        return if (added > 0) added else chapters.size.coerceAtMost(count)
    }

    fun downloads(): Flow<List<Dl>> {
        val manager = Injekt.get<DownloadManager>()
        return manager.queueState.map { list ->
            val running = manager.isRunning
            val hidden = eu.kanade.tachiyomi.vault.Vault.hiddenIds()
            val readLocked = eu.kanade.tachiyomi.vault.Vault.readLocked()
            list.filter { !readLocked && it.mangaId !in hidden }.map { d ->
                val state = when (d.status) {
                    Download.State.DOWNLOADING -> DlState.DOWNLOADING
                    Download.State.DOWNLOADED -> DlState.COMPLETED
                    Download.State.ERROR -> DlState.FAILED
                    else -> if (running) DlState.QUEUED else DlState.PAUSED
                }
                Dl(d.chapterId, d.mangaId, d.mangaTitle, d.chapterName, state, d.progress)
            }
        }
    }

    /** MugiVerse "Download over Wi-Fi only" (the Read side's own setting, shared with Watch). */
    fun wifiOnly(): Boolean = runCatching {
        Injekt.get<tachiyomi.domain.download.service.DownloadPreferences>().downloadOnlyOverWifi.get()
    }.getOrDefault(true)

    fun setWifiOnly(on: Boolean) {
        runCatching { Injekt.get<tachiyomi.domain.download.service.DownloadPreferences>().downloadOnlyOverWifi.set(on) }
    }

    fun pause() = Injekt.get<DownloadManager>().pauseDownloads()

    fun resume() = Injekt.get<DownloadManager>().startDownloads()

    fun cancel(chapterId: Long) {
        val manager = Injekt.get<DownloadManager>()
        manager.getQueuedDownloadOrNull(chapterId)?.let { manager.cancelQueuedDownloads(listOf(it)) }
    }
}
