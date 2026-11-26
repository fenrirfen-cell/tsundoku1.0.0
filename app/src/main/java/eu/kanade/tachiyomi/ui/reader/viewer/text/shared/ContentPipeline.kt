package eu.kanade.tachiyomi.ui.reader.viewer.text.shared

import androidx.annotation.WorkerThread
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.TextSplitter

/**
 * Order (kept stable — changing it changes user-visible output for some chapters):
 * stripChapterTitle → normalize → regex replacements → forceLowercase → autoSplit →
 * translate → sanitizeForRender.
 */
class ContentPipeline(private val preferences: ReaderPreferences) {

    suspend fun process(
        raw: String,
        config: ContentConfig,
        translator: (suspend (String) -> String)? = null,
    ): ProcessedContent = finalize(preTranslate(raw, config), config, translator)

    @WorkerThread
    fun preTranslate(raw: String, config: ContentConfig): PreTranslated {
        var content = raw
        val plainTextMode = HtmlUtils.isPlainTextChapter(config.chapterUrl)

        if (config.hideTitle) {
            content = HtmlUtils.stripChapterTitle(content, config.chapterName)
        }

        content = if (plainTextMode) {
            HtmlUtils.normalizePlainTextContent(content)
        } else {
            HtmlUtils.normalizeContentForHtml(content, config.chapterUrl)
        }

        content = RegexReplacementsProcessor.apply(content, preferences)

        if (config.forceLowercase) content = content.lowercase()

        if (preferences.novelAutoSplitText.get()) {
            content = TextSplitter.splitText(content, preferences.novelAutoSplitWordCount.get(), isHtml = !plainTextMode)
        }

        return PreTranslated(content, plainTextMode)
    }

    suspend fun finalize(
        pre: PreTranslated,
        config: ContentConfig,
        translator: (suspend (String) -> String)? = null,
    ): ProcessedContent {
        var content = pre.text
        if (translator != null) content = translator(content)

        if (!pre.isPlainText) {
            content = HtmlUtils.sanitizeForRender(
                content,
                target = config.target,
                keepEmbeddedCss = config.keepEmbeddedCss,
                keepEmbeddedJs = config.keepEmbeddedJs,
                blockMedia = config.blockMedia,
            )
            if (preferences.novelRemoveExtraSpacing.get()) content = removeExtraSpacing(content)
        }

        return ProcessedContent(
            text = content,
            isPlainText = pre.isPlainText,
            chapterUrl = config.chapterUrl,
        )
    }

    /**
     * Fenrir: the spacing sites add between paragraphs, as LNReader's "Remove extra paragraph
     * spacing" does: line breaks right before/after a paragraph or at its start/end
     * (`</p><br><br><p>`), runs of more than two line breaks, and paragraphs with nothing visible
     * (spaces, &nbsp;, zero-width marks) go. Each gap is then just the paragraph spacing setting.
     */
    private fun removeExtraSpacing(html: String): String = runCatching {
        // a whole document (kept styles in <head>) stays whole; otherwise it's a body fragment
        val wholeDocument = Regex("<(html|head)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(html)
        val doc = if (wholeDocument) org.jsoup.Jsoup.parse(html) else org.jsoup.Jsoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(false)
        val body = doc.body()
        val blockTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "ul", "ol", "li", "hr", "table", "section", "article")
        val invisible = Regex("[\\s\\u00a0\\u00ad\\u200b-\\u200f\\u2028-\\u202f\\u205f-\\u2064\\u3000\\ufeff]+")
        fun org.jsoup.nodes.Node.isBlankText() =
            this is org.jsoup.nodes.TextNode && this.wholeText.replace(invisible, "").isEmpty()
        fun org.jsoup.nodes.Node?.isBlock() =
            this is org.jsoup.nodes.Element && this.tagName().lowercase() in blockTags
        fun org.jsoup.nodes.Node.prevMeaningful(): org.jsoup.nodes.Node? {
            var n = previousSibling()
            while (n != null && n.isBlankText()) n = n.previousSibling()
            return n
        }
        fun org.jsoup.nodes.Node.nextMeaningful(): org.jsoup.nodes.Node? {
            var n = nextSibling()
            while (n != null && n.isBlankText()) n = n.nextSibling()
            return n
        }
        // empty paragraphs first (nothing visible, no picture/media inside)
        body.select("p, div").reversed().forEach { el ->
            if (el.select("img, svg, video, audio, iframe, table, hr").isEmpty() &&
                el.select("p, div, h1, h2, h3, h4, h5, h6").isEmpty() &&
                el.text().replace(invisible, "").isEmpty()
            ) {
                el.remove()
            }
        }
        // line breaks next to a paragraph, at a block's start or end, or more than two in a row
        var changed = true
        while (changed) {
            changed = false
            for (br in body.select("br")) {
                val prev = br.prevMeaningful()
                val next = br.nextMeaningful()
                val parent = br.parent()
                val nearBlock = prev.isBlock() || next.isBlock()
                val atEdge = parent != body && parent.isBlock() && (prev == null || next == null)
                val third = prev is org.jsoup.nodes.Element && prev.tagName() == "br" &&
                    prev.prevMeaningful().let { it is org.jsoup.nodes.Element && it.tagName() == "br" }
                if (nearBlock || atEdge || third) {
                    br.remove()
                    changed = true
                }
            }
        }
        if (wholeDocument) doc.outerHtml() else body.html()
    }.getOrDefault(html)

    data class PreTranslated(
        val text: String,
        val isPlainText: Boolean,
    )
}
