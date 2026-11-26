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

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
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

    private fun jsString(v: String) = json.encodeToString(kotlinx.serialization.json.JsonPrimitive(v))

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

        val ordered = designs.sortedByDescending { d -> d.markers.count { it in lower } }
        val matched = ordered.filter { d -> d.markers.any { it in lower } }
        if (matched.isEmpty()) progress("No known design spotted; trying each one…")
        else progress("Looks like ${matched.first().title}.")

        var lastReport: List<Pair<Boolean?, String>> = emptyList()
        for (design in ordered) {
            val template = template(design) ?: run {
                progress("Couldn't get LNReader's ${design.title} template.")
                null
            } ?: continue
            design.variants.forEachIndexed { i, options ->
                progress("Trying ${design.title} (${i + 1} of ${design.variants.size})…")
                val code = build(template, design, site, name, id, options)
                val report = testExtension(code, timeoutMs = 45_000)
                if (report.passed()) return@runCatching Made(code, name, design.title, report)
                lastReport = report
            }
        }
        val reason = lastReport.lastOrNull { it.first == false }?.second
        error(
            "This site doesn't match a known design, or it blocks apps." +
                (reason?.let { "\nLast try: $it" } ?: "") +
                "\nTry the Simple tab instead.",
        )
    }
}
