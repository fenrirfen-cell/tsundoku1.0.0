package eu.kanade.presentation.library.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Fenrir: the first page of the chapter you're on, for the book-opening animation. Manga: the
 * chapter's first image. Novels: the start of the chapter's text. It goes through the reader's
 * own loader, so it works the same for online, downloaded and local books, manga and novels.
 * Everything is wrapped: if it can't load in time the animation just shows a plain page.
 */
internal object FenrirFirstPage {
    sealed interface Content {
        val chapter: String
        class Image(override val chapter: String, val bitmap: ImageBitmap) : Content
        class Text(override val chapter: String, val text: String) : Content
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = object : LinkedHashMap<Long, Deferred<Content?>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Deferred<Content?>>?) = size > 6
    }

    /** Starts (or reuses) loading the first page of the chapter you're on for [manga]. */
    fun prefetch(manga: Manga): Deferred<Content?> = synchronized(cache) {
        cache[manga.id]?.let { return it }
        scope.async {
            val got = runCatching { withTimeoutOrNull(20_000) { load(manga) } }.getOrNull()
            // didn't work this time: let the next try start fresh
            if (got == null) synchronized(cache) { cache.remove(manga.id) }
            got
        }.also { cache[manga.id] = it }
    }

    /** Forget it once you've read on, so next time it shows the new chapter. */
    fun forget(mangaId: Long) = synchronized(cache) { cache.remove(mangaId) }

    private suspend fun load(manga: Manga): Content? {
        val chapters = Injekt.get<GetChaptersByMangaId>().await(manga.id, applyScanlatorFilter = true)
        val downloads = Injekt.get<DownloadManager>()
        // The chapter you're on: the next unread one (or the first, if you've read them all).
        val chapter = chapters.getNextUnread(manga, downloads)
            ?: chapters.minByOrNull { it.chapterNumber }
            ?: return null
        val source = Injekt.get<SourceManager>().getOrStub(manga.source)
        val loader = ChapterLoader(
            Injekt.get<android.app.Application>(),
            downloads,
            Injekt.get<DownloadProvider>(),
            manga,
            source,
        )
        val rc = ReaderChapter(chapter)
        try {
            loader.loadChapter(rc)
            val page = rc.pages?.firstOrNull() ?: return null
            val pageLoader = rc.pageLoader ?: return null
            val job = scope.launch { runCatching { pageLoader.loadPage(page) } }
            try {
                withTimeoutOrNull(15_000) {
                    page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
                }
            } finally {
                job.cancel()
            }
            val name = chapter.name.ifBlank { "Chapter ${chapter.chapterNumber}" }
            page.text?.takeIf { it.isNotBlank() }?.let { html ->
                val text = org.jsoup.Jsoup.parse(html).text().trim()
                if (text.isNotEmpty()) return Content.Text(name, text.take(1400))
            }
            val stream = page.stream ?: return null
            val bmp = decode(stream) ?: return null
            return Content.Image(name, bmp.asImageBitmap())
        } finally {
            runCatching { rc.pageLoader?.recycle() }
        }
    }

    /** Decodes small (about phone width) and keeps the top of very tall webtoon strips. */
    private fun decode(open: () -> java.io.InputStream): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
        val bmp = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return@runCatching null
        val maxH = (bmp.width * 1.6f).toInt()
        if (bmp.height > maxH) Bitmap.createBitmap(bmp, 0, 0, bmp.width, maxH) else bmp
    }.getOrNull()
}
