package eu.kanade.presentation.browse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Fenrir: Make extension → Auto, second try. When a site isn't built on a known design,
 * read its pages and guess where the book list, the chapter list and the chapter text are.
 * The guesses become a small extension (assets/fenrir/page-scan-engine.js + the rules found),
 * which is tested like any other before it's offered.
 *
 * Fenrir's own code: works on sites that put their lists and text straight in the page.
 */
object FenrirPageScan {
    class Rules(
        val list: String,
        val listPaged: String?,
        val items: String,
        val linkFilter: String,
        val title: String,
        val chapterItems: String,
        val chapterFilter: String,
        val nextWords: List<String>,
        val content: String,
        val search: String?,
    )

    private val skipSegments = setOf(
        "tag", "tags", "genre", "genres", "category", "categories", "author", "authors", "search",
        "login", "register", "signup", "sign-in", "user", "users", "account", "profile", "page",
        "privacy", "terms", "contact", "about", "dmca", "faq", "feed", "wp-login.php", "wp-admin",
    )
    private val listWords = listOf(
        "popular", "ranking", "rank", "trending", "hot", "most", "browse", "novel list", "novel-list",
        "all novels", "latest", "new", "completed", "library", "series", "stories", "novels",
    )
    private val nextTexts = listOf("Next", "Next ›", "Next »", "Next >", "›", "»", ">", "Next Page", "next")

    private fun inChrome(el: Element): Boolean =
        el.parents().any { p -> p.tagName() in setOf("nav", "header", "footer") || p.id().contains("menu", true) || p.className().contains("menu", true) }

    private fun cleanClasses(el: Element) = el.classNames()
        .filter { it.matches(Regex("[A-Za-z_][A-Za-z0-9_-]*")) && it.none(Char::isDigit) && it.length < 40 }
        .take(3)

    private fun simple(el: Element): String {
        val id = el.id()
        if (id.isNotBlank() && id.matches(Regex("[A-Za-z_][A-Za-z_-]*"))) return "#$id"
        return el.tagName() + cleanClasses(el).joinToString("") { ".$it" }
    }

    /** A selector for [el] that matches it alone on the page (or as close as three parents allow). */
    private fun unique(el: Element, doc: Document): String {
        var sel = simple(el)
        var cur: Element? = el.parent()
        repeat(3) {
            if (sel.startsWith("#") || doc.select(sel).size == 1) return sel
            val p = cur ?: return sel
            if (p.tagName() == "body" || p.tagName() == "html") return sel
            sel = simple(p) + " > " + sel
            cur = p.parent()
        }
        return sel
    }

    private fun sameHost(url: String, host: String): Boolean =
        runCatching { java.net.URI(url).host?.removePrefix("www.") == host.removePrefix("www.") }.getOrDefault(false)

    private fun segments(url: String): List<String> =
        runCatching { java.net.URI(url).path.orEmpty().split('/').filter { it.isNotBlank() } }.getOrDefault(emptyList())

    /** The nearest ancestor of [a] that repeats among its siblings (a list item / card). */
    private fun itemOf(a: Element): Element? {
        var cur: Element? = a
        repeat(6) {
            val el = cur ?: return null
            val parent = el.parent() ?: return null
            val sig = simple(el).removePrefix("#")
            val alike = parent.children().count { it.tagName() == el.tagName() && cleanClasses(it) == cleanClasses(el) }
            if (alike >= 4 && sig.isNotBlank() && !simple(el).startsWith("#")) return el
            cur = parent
        }
        return null
    }

    private class Group(val selector: String, val links: List<String>, val hasImages: Boolean, val filter: String)

    /** Repeated items on [doc] whose links look like [kind] (books or chapters). */
    private fun groups(doc: Document, host: String, accept: (String) -> Boolean): List<Group> {
        val byItemSig = LinkedHashMap<String, MutableList<Pair<Element, String>>>()
        doc.select("a[href]").forEach { a ->
            val href = a.absUrl("href")
            if (href.isBlank() || !sameHost(href, host) || inChrome(a)) return@forEach
            val segs = segments(href)
            if (segs.isEmpty() || segs.first().lowercase() in skipSegments) return@forEach
            if (!accept(href)) return@forEach
            val item = itemOf(a) ?: return@forEach
            val parent = item.parent() ?: return@forEach
            val key = unique(parent, doc) + " > " + simple(item)
            byItemSig.getOrPut(key) { mutableListOf() } += item to href
        }
        return byItemSig.mapNotNull { (sel, pairs) ->
            // One link per item (the first that passed).
            val firstPerItem = pairs.distinctBy { it.first }.map { it.second }.distinct()
            if (firstPerItem.size < 2) return@mapNotNull null
            if (doc.select(sel).isEmpty()) return@mapNotNull null
            val firstSegs = firstPerItem.map { segments(it).firstOrNull().orEmpty() }
            val common = firstSegs.groupingBy { it }.eachCount().maxByOrNull { it.value }
            val filter = if (common != null && common.value >= firstPerItem.size * 0.8 && firstPerItem.all { segments(it).size >= 2 }) {
                "/${common.key}/"
            } else {
                ""
            }
            Group(sel, firstPerItem, pairs.any { (item, _) -> item.selectFirst("img") != null }, filter)
        }
    }

    private fun bookGroup(doc: Document, host: String): Group? =
        groups(doc, host) { href -> !Regex("(?i)chapter|/ch-?\\d|/c\\d").containsMatchIn(href) }
            .filter { it.links.size >= 5 }
            .maxByOrNull { it.links.size + (if (it.hasImages) 10 else 0) + (if (it.filter.isNotEmpty()) 5 else 0) }

    private fun pagedUrl(doc: Document, host: String, listUrl: String): String? {
        val links = doc.select("a[href]").map { it.absUrl("href") }.filter { it.isNotBlank() && sameHost(it, host) }
        val two = links.firstOrNull { Regex("([?&](page|p|pg)=2(&|$))|(/page/2/?($|\\?))").containsMatchIn(it) } ?: return null
        return Regex("([?&](?:page|p|pg)=)2(&|$)").find(two)?.let { two.replaceRange(it.range, "${it.groupValues[1]}{page}${it.groupValues[2]}") }
            ?: Regex("/page/2(/?)").find(two)?.let { two.replaceRange(it.range, "/page/{page}${it.groupValues[1]}") }
    }

    private fun searchUrl(home: Document, site: String, host: String): String? {
        home.select("form").forEach { form ->
            val input = form.selectFirst("input[type=search], input[name=s], input[name=q], input[name=keyword], input[name=search], input[name=query], input[name=searchkey], input[name=keyw]")
                ?: return@forEach
            if (form.attr("method").equals("post", true)) return@forEach
            val name = input.attr("name").ifBlank { return@forEach }
            val action = form.absUrl("action").ifBlank { "$site/" }
            if (!sameHost(action, host)) return@forEach
            val extra = form.select("input[type=hidden][name]").joinToString("") { "&${it.attr("name")}=${java.net.URLEncoder.encode(it.attr("value"), "UTF-8")}" }
            return action + (if ('?' in action) "&" else "?") + "$name={term}" + extra
        }
        return null
    }

    private fun contentSelector(doc: Document): String? {
        val candidates = doc.select("div, article, section, main").filter { el ->
            !inChrome(el) && el.children().count { it.tagName() == "p" } >= 3
        }
        val best = candidates.maxByOrNull { el -> el.children().filter { it.tagName() == "p" }.sumOf { it.text().length } }
            ?: doc.select("div, article").filter { !inChrome(it) }.maxByOrNull { it.ownText().length }?.takeIf { it.ownText().length > 500 }
            ?: return null
        return unique(best, doc)
    }

    /**
     * Reads the site and returns the rules, or an error that says which part couldn't be found.
     * [get] fetches a page's HTML.
     */
    suspend fun scan(site: String, host: String, homeHtml: String, get: suspend (String) -> String, progress: (String) -> Unit): Rules {
        val home = Jsoup.parse(homeHtml, "$site/")

        // 1. Find a page that lists books: the home page or a list page linked from it.
        progress("Looking for a list of books…")
        val candidates = mutableListOf("$site/")
        home.select("a[href]").map { it to it.text().trim().lowercase() }
            .filter { (a, text) ->
                val href = a.absUrl("href")
                sameHost(href, host) && listWords.any { w -> w in text || w.replace(' ', '-') in href.lowercase() }
            }
            .sortedBy { (a, text) -> listWords.indexOfFirst { w -> w in text || w.replace(' ', '-') in a.absUrl("href").lowercase() } }
            .map { it.first.absUrl("href").substringBefore('#') }
            .distinct().take(4).forEach { candidates += it }

        var best: Triple<String, Document, Group>? = null
        var bestScore = -1
        for (url in candidates.distinct()) {
            val doc = if (url == "$site/") home else runCatching { Jsoup.parse(get(url), url) }.getOrNull() ?: continue
            val group = bookGroup(doc, host) ?: continue
            val score = group.links.size + (if (pagedUrl(doc, host, url) != null) 20 else 0) + (if (url != "$site/") 3 else 0)
            if (score > bestScore) { bestScore = score; best = Triple(url, doc, group) }
        }
        val (listUrl, listDoc, books) = best ?: error("Couldn't find a list of books on this site. It may build its pages with scripts.")
        progress("Found ${books.links.size} books on ${listUrl.removePrefix(site).ifBlank { "/" }}.")

        // 2. A book page: its chapter list.
        progress("Reading a book page…")
        val bookUrl = books.links.first()
        val bookDoc = Jsoup.parse(get(bookUrl), bookUrl)
        val bookPath = segments(bookUrl)
        val chapterGroups = groups(bookDoc, host) { href ->
            href.substringBefore('#') != bookUrl.substringBefore('#') &&
                (Regex("(?i)chapter|chap|/ch-?\\d|/c\\d|episode|/\\d+/?$").containsMatchIn(href) ||
                    (bookPath.isNotEmpty() && segments(href).take(bookPath.size) == bookPath && segments(href).size > bookPath.size))
        }
        val chapters = chapterGroups.maxByOrNull { it.links.size }
            ?: error("Found books, but no chapter list on the book page. The site may load chapters with scripts.")
        val chapterFilter = when {
            chapters.links.count { "chapter" in it.lowercase() } >= chapters.links.size * 0.8 -> "chapter"
            else -> ""
        }
        val nextWords = if (bookDoc.select("a[rel=next]").isNotEmpty() || bookDoc.select("a").any { it.text().trim() in nextTexts }) nextTexts else emptyList()
        progress("Found ${chapters.links.size} chapters on the first page.")

        // 3. A chapter page: where the text is.
        progress("Reading a chapter…")
        val chapterUrl = chapters.links.first()
        val chapterDoc = Jsoup.parse(get(chapterUrl), chapterUrl)
        val content = contentSelector(chapterDoc) ?: error("Found chapters, but couldn't find the chapter text. The site may load it with scripts.")

        return Rules(
            list = listUrl,
            listPaged = pagedUrl(listDoc, host, listUrl),
            items = books.selector,
            linkFilter = books.filter,
            title = if (bookDoc.selectFirst("h1") != null) "h1" else "title",
            chapterItems = chapters.selector,
            chapterFilter = chapterFilter,
            nextWords = nextWords,
            content = content,
            search = searchUrl(home, site, host),
        )
    }

    /** The extension's code: header lines the app reads, the rules, then the shared engine. */
    fun build(engine: String, rules: Rules, id: String, name: String, site: String): String {
        val j = FenrirAutoExtension
        fun opt(v: String?) = v?.let { j.jsString(it) } ?: "null"
        return buildString {
            append("// this.id = ${j.jsString(id)}\n")
            append("// this.name = ${j.jsString(name)}\n")
            append("// this.version = \"1.0.0\"\n")
            append("// this.site = ${j.jsString(site)}\n")
            append("const RULES = {\n")
            append("  id: ${j.jsString(id)},\n")
            append("  name: ${j.jsString(name)},\n")
            append("  site: ${j.jsString(site)},\n")
            append("  list: ${j.jsString(rules.list)},\n")
            append("  listPaged: ${opt(rules.listPaged)},\n")
            append("  items: ${j.jsString(rules.items)},\n")
            append("  linkFilter: ${j.jsString(rules.linkFilter)},\n")
            append("  title: ${j.jsString(rules.title)},\n")
            append("  chapterItems: ${j.jsString(rules.chapterItems)},\n")
            append("  chapterFilter: ${j.jsString(rules.chapterFilter)},\n")
            append("  nextWords: ${if (rules.nextWords.isEmpty()) "null" else rules.nextWords.joinToString(", ", "[", "]") { j.jsString(it) }},\n")
            append("  content: ${j.jsString(rules.content)},\n")
            append("  search: ${opt(rules.search)},\n")
            append("};\n")
            append(engine)
        }
    }
}
