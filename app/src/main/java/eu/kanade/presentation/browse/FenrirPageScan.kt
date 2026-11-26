package eu.kanade.presentation.browse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
        /** When the chapter list is loaded by the site's script: that address and how to fill it in. */
        val chapterApi: ChapterApi? = null,
        /** A link on the book page to its full chapter list, and how that list's pages are numbered. */
        val toc: String? = null,
        val tocPaged: String? = null,
    )

    /** [url] has {0}, {1}… filled from [vars] (each a small JS object: {seg:n}, {sel,attr} or {re}). */
    class ChapterApi(val url: String, val vars: List<String>)

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

    // Site menus, headers and footers (not lists that merely have "menu" somewhere in a class name).
    private val chromeWords = setOf("menu", "navbar", "nav", "navigation", "header", "footer", "breadcrumb", "breadcrumbs", "sidebar")
    private fun inChrome(el: Element): Boolean =
        el.parents().any { p ->
            p.tagName() in setOf("nav", "header", "footer") ||
                p.attr("role") == "navigation" ||
                p.id().lowercase() in chromeWords ||
                p.classNames().any { it.lowercase() in chromeWords }
        }

    /** What the scan saw, for the error message when it can't finish. */
    private val notes = mutableListOf<String>()

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
            // Siblings of the same kind: same tag, and sharing a class (some items carry extra ones like "active").
            val mine = cleanClasses(el).toSet()
            val alike = parent.children().count { sib ->
                sib.tagName() == el.tagName() && (mine.isEmpty() && cleanClasses(sib).isEmpty() || cleanClasses(sib).any { it in mine })
            }
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
            // Item selector: tag + its first class, so items with extra classes still match.
            val key = unique(parent, doc) + " > " + item.tagName() + (cleanClasses(item).firstOrNull()?.let { ".$it" } ?: "")
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

    private fun bookGroups(doc: Document, host: String): List<Group> =
        groups(doc, host) { href -> !Regex("(?i)chapter|/ch-?\\d|/c\\d").containsMatchIn(href) }
            .filter { it.links.size >= 5 }
            .sortedByDescending { it.links.size + (if (it.hasImages) 10 else 0) + (if (it.filter.isNotEmpty()) 5 else 0) }
            .take(3)

    private class ChapterFind(
        val bookUrl: String,
        val bookDoc: Document,
        val chapters: Group,
        val toc: String? = null,
        val tocPaged: String? = null,
        val api: ChapterApi? = null,
    )

    /** Looks for the chapter list of [bookUrl]: on the page, on its own page, or (if [watch]) from the page's script. */
    private suspend fun chaptersOf(
        bookUrl: String,
        otherBook: String?,
        site: String,
        host: String,
        watch: Boolean,
        get: suspend (String) -> String,
        progress: (String) -> Unit,
    ): ChapterFind? {
        notes += "book page ${bookUrl.removePrefix(site)}"
        val bookHtml = runCatching { get(bookUrl) }.getOrElse { notes += "couldn't open it"; return null }
        val bookDoc = Jsoup.parse(bookHtml, bookUrl)
        val inPage = chapterGroup(bookDoc, host, bookUrl)
        notes += "${bookDoc.select("a[href]").size} links, ${inPage?.links?.size ?: 0} chapters there"
        if ((inPage?.links?.size ?: 0) >= 20) return ChapterFind(bookUrl, bookDoc, inPage!!)
        // The full list may be on its own page ("More chapters", "Table of contents"…).
        findTocPage(bookDoc, bookUrl, host, get)?.let { (selector, paged, group) ->
            progress("Found the full chapter list on its own page.")
            return ChapterFind(bookUrl, bookDoc, group, toc = selector, tocPaged = paged)
        }
        if (inPage != null && inPage.links.size >= 5) return ChapterFind(bookUrl, bookDoc, inPage)
        if (!watch) return null
        progress("Chapters may load with a script; watching the book page…")
        findChapterApi(bookUrl, bookHtml, host, otherBook, get)?.let { (api, group) ->
            return ChapterFind(bookUrl, bookDoc, group, api = api)
        }
        return inPage?.let { ChapterFind(bookUrl, bookDoc, it) }
    }

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

        // Every list of links that could be books, best first (a list of genres can look like books too).
        val lists = mutableListOf<Triple<String, Document, Group>>()
        for (url in candidates.distinct()) {
            val doc = if (url == "$site/") home else runCatching { Jsoup.parse(get(url), url) }.getOrNull() ?: continue
            bookGroups(doc, host).forEach { lists += Triple(url, doc, it) }
        }
        if (lists.isEmpty()) error("Couldn't find a list of books on this site. It may build its pages with scripts.")
        val ranked = lists.sortedByDescending { (url, doc, group) ->
            group.links.size + (if (pagedUrl(doc, host, url) != null) 20 else 0) + (if (url != "$site/") 3 else 0) +
                (if (group.hasImages) 10 else 0)
        }.distinctBy { it.third.links.first() }.take(6)

        // 2. A book page: its chapter list. Try each list until one leads to chapters.
        progress("Reading a book page…")
        notes.clear()
        var found: Pair<Triple<String, Document, Group>, ChapterFind>? = null
        for (watch in listOf(false, true)) {
            for (entry in if (watch) ranked.take(2) else ranked) {
                val books = entry.third
                val find = chaptersOf(books.links.first(), books.links.getOrNull(1), site, host, watch, get, progress)
                if (find != null) { found = entry to find; break }
            }
            if (found != null) break
        }
        val (listEntry, find) = found ?: error("Found lists of links, but none led to a chapter list. (Saw: ${notes.takeLast(9).joinToString("; ")})")
        val (listUrl, listDoc, books) = listEntry
        progress("Found ${books.links.size} books on ${listUrl.removePrefix(site).ifBlank { "/" }}.")
        val bookDoc = find.bookDoc
        val chapters = find.chapters
        val chapterApi = find.api
        val toc = find.toc
        val tocPaged = find.tocPaged
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
            chapterApi = chapterApi,
            toc = toc,
            tocPaged = tocPaged,
        )
    }

    /* ---------------------------------------------- chapter list on its own page */

    private val tocText = Regex("(?i)^(more chapters?|view more|view all|see all|show all|all chapters|chapter list|chapters list|table of contents|contents|catalog(ue)?|toc|full list|index)\\b")
    private val tocHref = Regex("(?i)(catalog|chapters|chapter-list|chapterlist|toc|contents|/index)")

    /** Finds a link from the book page to its full chapter list. Returns (link selector, paging, chapters). */
    private suspend fun findTocPage(bookDoc: Document, bookUrl: String, host: String, get: suspend (String) -> String): Triple<String, String?, Group>? {
        val candidates = bookDoc.select("a[href]").filter { a ->
            val href = a.absUrl("href")
            href.isNotBlank() && sameHost(href, host) && href.substringBefore('#') != bookUrl.substringBefore('#') &&
                (tocText.containsMatchIn(a.text().trim()) || tocHref.containsMatchIn(java.net.URI(href).path.orEmpty()))
        }.map { it.absUrl("href").substringBefore('#') }.distinct().take(4)
        if (candidates.isEmpty()) notes += "no chapter-list link"
        for (url in candidates) {
            val doc = runCatching { Jsoup.parse(get(url), url) }.getOrNull()
            if (doc == null) { notes += "couldn't open ${java.net.URI(url).path}"; continue }
            val found = chapterGroup(doc, host, bookUrl)
            notes += "${java.net.URI(url).path}: ${doc.select("a[href]").size} links, ${found?.links?.size ?: 0} chapters"
            val group = found?.takeIf { it.links.size >= 5 } ?: continue
            val first = segments(url).firstOrNull() ?: continue
            val selector = "a[href*='/$first/']"
            if (bookDoc.select(selector).none { it.absUrl("href").substringBefore('#') == url }) continue
            val paged = when {
                Regex("/1/?$").containsMatchIn(java.net.URI(url).path.orEmpty()) && java.net.URI(url).query == null -> "path"
                Regex("[?&](page|p|pg)=1(&|$)").containsMatchIn(url) -> "query"
                else -> null
            }
            return Triple(selector, paged, group)
        }
        return null
    }

    /* ---------------------------------------------- chapter lists loaded by a script */

    private val staticFile = Regex("(?i)\\.(js|css|png|jpe?g|gif|webp|svg|ico|woff2?|ttf|otf|mp4|mp3|m3u8|map)(\\?|$)")
    private val trackers = listOf("google", "doubleclick", "facebook", "analytics", "cloudflareinsights", "gtag", "adservice", "disqus", "pixel")

    /** Same as the engine's toHtml: chapter lists sent as JSON become links. */
    fun toHtml(text: String): String {
        val t = text.trim()
        if (!t.startsWith("{") && !t.startsWith("[")) return t
        val data = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(t) }.getOrNull() ?: return t
        val parts = mutableListOf<String>()
        fun walk(v: kotlinx.serialization.json.JsonElement) {
            when (v) {
                is kotlinx.serialization.json.JsonPrimitive -> if (v.isString && "<a" in v.content) parts += v.content
                is kotlinx.serialization.json.JsonArray -> v.forEach { walk(it) }
                is kotlinx.serialization.json.JsonObject -> {
                    fun str(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
                        (v[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
                    }
                    val url = str("url", "link", "href", "path")
                    val name = str("title", "name", "chapter_title", "chapterName", "chapter_name")
                    if (url != null && name != null && (url.startsWith("/") || url.startsWith("http"))) {
                        parts += "<a href=\"${url.replace("\"", "&quot;")}\">${name.replace("<", "&lt;")}</a>"
                    } else {
                        v.values.forEach { walk(it) }
                    }
                }
            }
        }
        walk(data)
        return "<div class=\"fenrir-chapters\">" + parts.joinToString("\n") + "</div>"
    }

    /** Opens [url] in a hidden browser and lists the addresses its scripts ask for. */
    private suspend fun watchRequests(url: String): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        val context = Injekt.get<android.app.Application>()
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val web = android.webkit.WebView(context)
        try {
            web.setDefaultSettingsSafely()
            web.settings.blockNetworkImage = true
            web.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(view: android.webkit.WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                    if (request.method.equals("GET", true) && !request.isForMainFrame) seen += request.url.toString()
                    return null
                }
            }
            web.loadUrl(url)
            kotlinx.coroutines.delay(7_000)
            // Some pages only load chapters after scrolling or opening a Chapters tab.
            web.evaluateJavascript(
                """
                window.scrollTo(0, document.body.scrollHeight);
                Array.from(document.querySelectorAll('button, [role=tab], a[href^="#"], li, span'))
                  .filter(function (e) { return /^(chapters?|table of contents|toc|chapter list)$/i.test((e.textContent || '').trim()); })
                  .slice(0, 3).forEach(function (e) { try { e.click(); } catch (x) {} });
                """.trimIndent(),
                null,
            )
            kotlinx.coroutines.delay(4_000)
            seen.toList()
        } finally {
            web.stopLoading()
            web.destroy()
        }
    }

    private fun android.webkit.WebView.setDefaultSettingsSafely() {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        runCatching {
            val ua = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().defaultUserAgentProvider()
            if (ua.isNotBlank()) settings.userAgentString = ua
        }
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }

    private suspend fun fetchXhr(url: String, referer: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val headers = okhttp3.Headers.Builder().add("X-Requested-With", "XMLHttpRequest").add("Referer", referer).build()
        Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().client
            .newCall(eu.kanade.tachiyomi.network.GET(url, headers)).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
    }

    private fun chapterGroup(doc: Document, host: String, bookUrl: String): Group? {
        val bookPath = segments(bookUrl)
        return groups(doc, host) { href ->
            href.substringBefore('#') != bookUrl.substringBefore('#') &&
                (Regex("(?i)chapter|chap|/ch-?\\d|/c\\d|episode|/\\d+/?$").containsMatchIn(href) ||
                    (bookPath.isNotEmpty() && segments(href).take(bookPath.size) == bookPath && segments(href).size > bookPath.size))
        }.maxByOrNull { it.links.size }
    }

    /**
     * Turns the address the script asked for into a pattern: parts that come from the book's link
     * or from the book page become {0}, {1}…; returns null if an id can't be traced back.
     */
    private fun generalize(apiUrl: String, bookUrl: String, bookHtml: String, bookDoc: Document): ChapterApi? {
        val bookSegs = segments(bookUrl)
        val uri = runCatching { java.net.URI(apiUrl) }.getOrNull() ?: return null
        val tokens = (uri.path.orEmpty().split('/') + uri.query.orEmpty().split('&').map { it.substringAfter('=', "") })
            .map { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .filter { it.length >= 3 }
            .distinct()
            .sortedByDescending { it.length }
        // Only the part after the host is rewritten (a book word like "novel" may also be in the host).
        val head = "${uri.scheme}://${uri.rawAuthority}"
        var url = apiUrl.removePrefix(head)
        val vars = mutableListOf<String>()
        val json = kotlinx.serialization.json.Json
        fun js(v: String) = json.encodeToString(kotlinx.serialization.json.JsonPrimitive(v))
        for (token in tokens) {
            val idLike = token.any(Char::isDigit) || token in bookSegs
            if (!idLike) continue
            val seg = bookSegs.indexOf(token)
            val variable = when {
                seg >= 0 -> "{ seg: $seg }"
                else -> {
                    val holder = bookDoc.getAllElements().firstOrNull { el -> el.attributes().any { it.value == token } }
                    if (holder != null) {
                        val attr = holder.attributes().first { it.value == token }.key
                        "{ sel: ${js(unique(holder, bookDoc))}, attr: ${js(attr)} }"
                    } else {
                        val m = Regex("([A-Za-z_][A-Za-z0-9_-]{1,40})[\"']?\\s*[:=]\\s*[\"']?" + Regex.escape(token) + "\\b").find(bookHtml)
                            ?: continue // not traceable: keep it as is (the second-book check catches mistakes)
                        val name = m.groupValues[1] // letters, digits, _ and - only
                        "{ re: ${js(name + "[\"']?\\s*[:=]\\s*[\"']?([\\w-]+)")} }"
                    }
                }
            }
            val index = vars.size
            vars += variable
            url = url.replace(java.net.URLEncoder.encode(token, "UTF-8"), "{$index}").replace(token, "{$index}")
        }
        if (vars.isEmpty()) return null // the same address for every book: not a per-book list
        return ChapterApi(head + url, vars)
    }

    /** Fills a [ChapterApi] for another book, the same way the engine does. */
    private fun fill(api: ChapterApi, bookUrl: String, bookHtml: String, bookDoc: Document): String? {
        val segs = segments(bookUrl)
        var url = api.url
        api.vars.forEachIndexed { i, v ->
            val value = when {
                v.startsWith("{ seg:") -> segs.getOrNull(Regex("\\d+").find(v)!!.value.toInt())
                v.startsWith("{ sel:") -> {
                    val parts = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(v).map { kotlinx.serialization.json.Json.decodeFromString<String>(it.value) }.toList()
                    bookDoc.selectFirst(parts[0])?.attr(parts[1])
                }
                else -> {
                    val pattern = kotlinx.serialization.json.Json.decodeFromString<String>(Regex("\"(.*)\"").find(v)!!.value)
                    Regex(pattern).find(bookHtml)?.groupValues?.getOrNull(1)
                }
            } ?: return null
            url = url.replace("{$i}", java.net.URLEncoder.encode(value, "UTF-8"))
        }
        return url
    }

    private suspend fun findChapterApi(
        bookUrl: String,
        bookHtml: String,
        host: String,
        otherBookUrl: String?,
        get: suspend (String) -> String,
    ): Pair<ChapterApi, Group>? {
        val base = host.removePrefix("www.").split('.').takeLast(2).joinToString(".")
        val asked = watchRequests(bookUrl)
            .map { it.substringBefore('#') }
            .filter { u ->
                val h = runCatching { java.net.URI(u).host.orEmpty() }.getOrDefault("")
                h.endsWith(base) && !staticFile.containsMatchIn(u) && trackers.none { it in u.lowercase() } && u != bookUrl
            }
            .distinct()
            .take(15)
        val bookDoc = Jsoup.parse(bookHtml, bookUrl)
        for (u in asked) {
            val body = runCatching { fetchXhr(u, bookUrl) }.getOrNull() ?: continue
            val frag = Jsoup.parse(toHtml(body), u)
            val group = chapterGroup(frag, host, bookUrl)?.takeIf { it.links.size >= 2 } ?: continue
            val api = generalize(u, bookUrl, bookHtml, bookDoc) ?: continue
            // Make sure the pattern also works for a second book.
            if (otherBookUrl != null) {
                val otherHtml = runCatching { get(otherBookUrl) }.getOrNull() ?: continue
                val otherUrl = fill(api, otherBookUrl, otherHtml, Jsoup.parse(otherHtml, otherBookUrl)) ?: continue
                val otherBody = runCatching { fetchXhr(otherUrl, otherBookUrl) }.getOrNull() ?: continue
                if (chapterGroup(Jsoup.parse(toHtml(otherBody), otherUrl), host, otherBookUrl) == null) continue
            }
            return api to group
        }
        return null
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
            append("  toc: ${opt(rules.toc)},\n")
            append("  tocPaged: ${opt(rules.tocPaged)},\n")
            rules.chapterApi?.let { api ->
                append("  chapterApi: { url: ${j.jsString(api.url)}, vars: [${api.vars.joinToString(", ")}] },\n")
            }
            append("};\n")
            append(engine)
        }
    }
}
