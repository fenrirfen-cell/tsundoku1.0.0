package eu.kanade.presentation.webview

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Fenrir: ad blocking for the Browse browser in Import a book. Stops pop-up / new-tab ads,
 * jumps to another site that the user didn't tap for, and requests to well-known ad networks.
 * A switch in the browser's menu turns it off for sites that need their ads to work.
 */
object FenrirAdBlock {
    private const val PREFS = "fenrir_browser"
    private const val KEY = "block_ads"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /** Ad, pop-under and tracking networks (any subdomain of these). */
    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "adnxs.com", "adsrvr.org", "advertising.com", "amazon-adsystem.com", "criteo.com", "criteo.net",
        "taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "media.net", "pubmatic.com",
        "rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com", "teads.tv",
        "propellerads.com", "propellerclick.com", "popads.net", "popcash.net", "popmyads.com",
        "adsterra.com", "adsterratech.com", "highperformanceformat.com", "profitablecpmrate.com",
        "profitablegatecpm.com", "effectivegatecpm.com", "effectiveratecpm.com", "exoclick.com",
        "exosrv.com", "exdynsrv.com", "juicyads.com", "trafficjunky.net", "hilltopads.net",
        "adcash.com", "clickadu.com", "clickaine.com", "onclickads.net", "onclickalgo.com",
        "onclickperformance.com", "a-ads.com", "bitmedia.io", "coinzilla.com", "ad-maven.com",
        "admaven.com", "admavenx.com", "monetag.com", "galaksion.com", "richads.com",
        "pushnami.com", "pushwhy.com", "notix.io", "evadav.com", "rollerads.com", "dtscout.com",
        "yllix.com", "ylx-1.com", "ylx-2.com", "ylx-3.com", "ylx-4.com", "adskeeper.com",
        "adf.ly", "shorte.st", "linkvertise.com", "ouo.io", "adshrink.it", "exe.io",
        "scorecardresearch.com", "quantserve.com", "moatads.com", "zedo.com", "33across.com",
        "sharethrough.com", "yieldmo.com", "undertone.com", "infolinks.com", "adroll.com",
        "bidvertiser.com", "valueimpression.com", "vidoomy.com", "ezoic.net", "ezodn.com",
        "mediavine.com", "adthrive.com", "venatusmedia.com", "playwire.com", "snigelweb.com",
    )

    fun isAdHost(host: String?): Boolean {
        val h = host?.lowercase()?.removePrefix("www.") ?: return false
        return AD_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    /** "the site" part of a host (example.co.uk, example.com), to tell another site from this one. */
    private fun siteOf(host: String?): String {
        val parts = host?.lowercase()?.split('.')?.filter { it.isNotEmpty() }.orEmpty()
        if (parts.size <= 2) return parts.joinToString(".")
        val second = parts[parts.size - 2]
        val keep = if (second.length <= 3 && parts.last().length == 2) 3 else 2 // co.uk, com.au…
        return parts.takeLast(keep).joinToString(".")
    }

    fun sameSite(a: String?, b: String?): Boolean {
        val ha = a?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        val hb = b?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        if (ha == null || hb == null) return true
        return siteOf(ha) == siteOf(hb)
    }

    private val BOOK_EXTENSIONS = listOf(".epub", ".pdf", ".cbz", ".cbr", ".mobi", ".azw3", ".azw", ".zip", ".rar", ".7z")

    /** A tapped new-tab link worth following: same site, or a download (book file or "download" link). */
    fun looksWanted(target: String, pageUrl: String?): Boolean {
        val host = runCatching { Uri.parse(target).host }.getOrNull()
        if (!target.startsWith("http") || isAdHost(host)) return false
        if (sameSite(target, pageUrl)) return true
        val path = target.substringBefore('?').lowercase()
        return BOOK_EXTENSIONS.any { path.endsWith(it) } || "download" in target.lowercase()
    }

    /** An empty answer for a blocked request. */
    fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
