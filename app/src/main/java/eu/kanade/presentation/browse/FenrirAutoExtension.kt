package eu.kanade.presentation.browse

import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Fenrir: Make extension → Auto. Type a site's link; if the site is built on a common
 * novel-site design, the extension is made from LNReader's own template for that design
 * (the same compiled code their Madara / LightNovel WP / Fictioneer / ReadNovelFull
 * extensions use), with the site's address put in. Each try is tested before it's offered.
 *
 * The option sets tried are the most common ones in LNReader's multisrc sources.json files.
 */
object FenrirAutoExtension {
    private class Design(
        val key: String,
        val title: String,
        val markers: List<String>,
        val trailingSlash: Boolean,
        val variants: List<String>,
    )

    private val designs = listOf(
        Design(
            "madara", "Madara",
            listOf("themes/madara", "wp-manga", "manga_get_chapters", "c-blog__heading"),
            trailingSlash = true,
            variants = listOf("""{"useNewChapterEndpoint":true}""", "{}"),
        ),
        Design(
            "lightnovelwp", "LightNovel WP",
            listOf("themes/lightnovel", "class=\"bsx\"", "ts-breadcrumb", "epcontent"),
            trailingSlash = true,
            variants = listOf("""{"reverseChapters":true}""", """{"reverseChapters":false}"""),
        ),
        Design(
            "fictioneer", "Fictioneer",
            listOf("themes/fictioneer", "fictioneer"),
            trailingSlash = false,
            variants = listOf("stories", "novels", "browse", "series", "collection/novels", "home/all-novels")
                .map { """{"browsePage":"$it"}""" },
        ),
        Design(
            "readnovelfull", "ReadNovelFull",
            listOf("data-novel-id", "list-truyen", "ajax-chapter-option", "novel-title"),
            trailingSlash = true,
            variants = listOf(
                """{"chapterListing":"ajax-chapter-option","latestPage":"latest-release-novel","searchPage":"search"}""",
                """{"latestPage":"novel-list/latest-release-novel","searchPage":"novel-list/search"}""",
                """{"latestPage":"sort/updates","searchPage":"search"}""",
            ),
        ),
    )

    private const val LNREADER_INDEX = "https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val templates = mutableMapOf<String, String>()

    class Made(val code: String, val name: String, val design: String, val report: List<Pair<Boolean?, String>>)

    internal suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        Injekt.get<NetworkHelper>().client.newCall(GET(url)).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} for $url")
            r.body?.string().orEmpty()
        }
    }

    /** A compiled LNReader extension built on [design]: from your repos first, else LNReader's list. */
    private suspend fun template(design: Design): String? {
        templates[design.key]?.let { return it }
        val tag = "[${design.key}]"
        val fromRepos = Injekt.get<JsPluginManager>().availablePlugins.value
            .filter { tag in it.url }
            .map { it.url }
        val urls = fromRepos.ifEmpty {
            runCatching {
                (json.parseToJsonElement(fetchText(LNREADER_INDEX)) as JsonArray)
                    .mapNotNull { (it as? JsonObject)?.get("url")?.jsonPrimitive?.content }
                    .filter { tag in it }
            }.getOrDefault(emptyList())
        }
        for (url in urls.take(3)) {
            val code = runCatching { fetchText(url) }.getOrNull() ?: continue
            if ("exports.default" in code) {
                templates[design.key] = code
                return code
            }
        }
        return null
    }

    internal fun jsString(v: String) = json.encodeToString(kotlinx.serialization.json.JsonPrimitive(v))

    private fun build(template: String, design: Design, site: String, name: String, id: String, options: String): String {
        val siteForDesign = if (design.trailingSlash) "$site/" else site
        val meta = """{"id":${jsString(id)},"sourceSite":${jsString(siteForDesign)},"sourceName":${jsString(name)},"options":$options}"""
        return buildString {
            append("// Made in Fenrir from LNReader's ${design.title} template.\n")
            append("// this.id = ${jsString(id)}\n")
            append("// this.name = ${jsString(name)}\n")
            append("// this.version = \"1.0.0\"\n")
            append("// this.site = ${jsString(site)}\n")
            append(template.trimEnd())
            append("\n;(function () {\n")
            append("  var base = exports.default;\n")
            append("  var Design = base.constructor;\n")
            append("  var meta = $meta;\n")
            // The design's code expects its filter settings (genres, sort…); keep the template's defaults.
            append("  if (base.filters) meta.filters = base.filters;\n")
            append("  exports.default = new Design(meta);\n")
            append("})();\n")
        }
    }

    /** Makes and tests an extension for [link]. [progress] gets short status lines. */
    suspend fun make(link: String, progress: (String) -> Unit): Result<Made> = runCatching {
        val raw = link.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val uri = android.net.Uri.parse(raw)
        val host = uri.host ?: error("That doesn't look like a website link.")
        val site = "${uri.scheme ?: "https"}://$host"

        progress("Opening $host…")
        val html = runCatching { fetchText("$site/") }.getOrElse {
            error("Couldn't open $host (${it.message}). If the site asks for a human check, open it once in Browse, then try again.")
        }
        val lower = html.lowercase()
        val doc = org.jsoup.Jsoup.parse(html)
        val name = (doc.selectFirst("meta[property=og:site_name]")?.attr("content")
            ?: doc.title().substringBefore(" – ").substringBefore(" - ").substringBefore(" | "))
            .trim().ifBlank { host.removePrefix("www.") }.take(40)
        val id = "fenrir" + host.removePrefix("www.").split('.', '-').filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar(Char::uppercase) }

        val matched = designs.filter { d -> d.markers.any { it in lower } }.sortedByDescending { d -> d.markers.count { it in lower } }
        val others = designs - matched.toSet()
        var lastReport: List<Pair<Boolean?, String>> = emptyList()

        suspend fun tryDesigns(list: List<Design>): Made? {
            for (design in list) {
                val template = template(design)
                if (template == null) {
                    progress("Couldn't get LNReader's ${design.title} template.")
                    continue
                }
                design.variants.forEachIndexed { i, options ->
                    progress("Trying ${design.title} (${i + 1} of ${design.variants.size})…")
                    val code = build(template, design, site, name, id, options)
                    val report = testExtension(code, timeoutMs = 45_000)
                    if (report.passed()) return Made(code, name, "LNReader's ${design.title} template", report)
                    lastReport = report
                }
            }
            return null
        }

        // 1. A known design spotted on the page: LNReader's template for it.
        if (matched.isNotEmpty()) {
            progress("Looks like ${matched.first().title}.")
            tryDesigns(matched)?.let { return@runCatching it }
        }

        // 2. Read the pages and guess where the books, chapters and text are.
        progress("No known design worked; reading the site's pages…")
        val scanProblem: String? = try {
            val rules = FenrirPageScan.scan(site, host, html, { fetchText(it) }, progress)
            val engine = Injekt.get<android.app.Application>().assets.open("fenrir/page-scan-engine.js").bufferedReader().use { it.readText() }
            val code = FenrirPageScan.build(engine, rules, id, name, site)
            progress("Testing what was found…")
            val report = testExtension(code, timeoutMs = 60_000)
            if (report.passed()) return@runCatching Made(code, name, "a scan of the site's pages", report)
            lastReport = report
            report.lastOrNull { it.first == false }?.second
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.message
        }

        // 3. Last chance: the designs that weren't spotted.
        tryDesigns(others)?.let { return@runCatching it }

        error(
            "Couldn't make a working extension for this site." +
                (scanProblem?.let { "\nPage scan: $it" } ?: "") +
                "\nTry the Simple tab instead.",
        )
    }
}
