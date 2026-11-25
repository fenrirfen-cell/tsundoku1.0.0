package eu.kanade.presentation.library.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.zip.ZipFile

/**
 * Import a book from a link, with safety checks: the file must really be an EPUB (an app
 * installer renamed to .epub is stopped), DRM-locked books are explained, sizes are capped, and
 * nothing in the file is ever run. A web page is scanned for the book files on it.
 */
object FenrirImport {
    const val MAX_BYTES = 1024L * 1024 * 1024 // about 1 GB per file

    sealed interface Result {
        data class Book(val uri: Uri, val name: String, val bytes: Long, val secure: Boolean, val host: String, val convertedFrom: String? = null) : Result
        data class Page(val host: String, val links: List<Pair<String, String>>) : Result // name to url
        /** [openUrl]: the site wants a human check; it can be passed in the in-app browser, then try again. */
        data class Stop(val title: String, val why: String, val openUrl: String? = null) : Result
    }

    private val client by lazy { Injekt.get<NetworkHelper>().client }

    fun hostOf(url: String) = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.").orEmpty()

    private val famous = listOf("google", "amazon", "apple", "microsoft", "paypal", "facebook", "gutenberg", "standardebooks", "github", "webnovel", "royalroad")

    /** Simple on-phone checks for links that look like tricks. Null means nothing odd was found. */
    fun linkWarning(host: String): String? {
        val h = host.lowercase()
        if (Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(h) || h.startsWith("[")) return "This link is a bare number address, not a named website"
        if (h.split('.').any { it.startsWith("xn--") }) return "This address uses look-alike letters"
        val name = h.split('.').dropLast(1).lastOrNull().orEmpty()
        val swapped = name.replace('0', 'o').replace('1', 'l').replace("rn", "m").replace('3', 'e')
        famous.forEach { f ->
            if (name != f && (swapped == f || (name.contains(f) && name != f && h.count { it == '-' } >= 1))) return "This looks like $f but isn't"
        }
        return null
    }

    suspend fun check(context: Context, rawUrl: String): Result = withContext(Dispatchers.IO) {
        val url = rawUrl.trim().let { if (!it.startsWith("http")) "https://$it" else it }
        val host = hostOf(url)
        if (host.isEmpty()) return@withContext Result.Stop("That's not a link", "Paste the whole web address, starting with https://")
        var secure = url.startsWith("https://")
        // Fenrir: a plain http:// link is blocked by Android; the same address over https usually works
        val firstTry = runCatching { client.newCall(GET(url)).execute() }
        val response = firstTry.getOrNull() ?: run {
            val error = firstTry.exceptionOrNull()
            if (!secure && url.startsWith("http://")) {
                runCatching { client.newCall(GET("https://" + url.removePrefix("http://"))).execute() }.getOrNull()
                    ?.also { secure = true }
            } else {
                null
            }
                ?: return@withContext Result.Stop(
                    "Couldn't open the link",
                    whyFailed(error, url),
                    openUrl = url.takeIf { isHumanCheck(error) },
                )
        }
        response.use { r ->
            if (!r.isSuccessful) {
                // Cloudflare-style "are you human" page: passable in the in-app browser
                val challenge = (r.code == 403 || r.code == 503 || r.code == 429) &&
                    (r.header("cf-mitigated") != null || r.header("Server").orEmpty().contains("cloudflare", true))
                return@withContext if (challenge) {
                    Result.Stop("Couldn't open the link", HUMAN_CHECK, openUrl = r.request.url.toString())
                } else {
                    Result.Stop("Couldn't open the link", "The site answered with an error (${r.code}).")
                }
            }
            val type = r.header("Content-Type").orEmpty().lowercase()
            val length = r.body?.contentLength() ?: -1L
            if (length > MAX_BYTES) return@withContext Result.Stop("Too big", "Files over about 1 GB aren't imported.")
            val body = r.body ?: return@withContext Result.Stop("Empty file", "The link gave back nothing.")
            if (type.contains("text/html")) {
                val html = body.string()
                // the address that answered (https after a retry, or after redirects), so the books' links resolve right
                return@withContext scanPage(r.request.url.toString(), host, html)
            }
            val name = fileName(r.header("Content-Disposition"), url)
            val dir = File(context.cacheDir, "fenrir_imports").apply { mkdirs() }
            val out = File(dir, "${System.currentTimeMillis()}-" + name.ifBlank { "book" }.replace(Regex("[^A-Za-z0-9._ -]"), "_"))
            var total = 0L
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            o.close(); out.delete()
                            return@withContext Result.Stop("Too big", "Files over about 1 GB aren't imported.")
                        }
                        o.write(buf, 0, n)
                    }
                }
            }
            process(out, name, secure, host)
        }
    }

    /**
     * Looks inside the file (never runs anything): EPUBs go straight through, comic archives, PDFs
     * and DRM-free Kindle files are converted to EPUB, app installers and locked books are stopped.
     */
    fun process(file: File, rawName: String, secure: Boolean, host: String): Result {
        val head = file.inputStream().use { s -> ByteArray(68).also { s.read(it) } }
        val text = String(head, Charsets.ISO_8859_1)
        val base = rawName.substringBeforeLast('.').ifBlank { "Book" }
        val dir = file.parentFile ?: file
        fun converted(kind: String, block: (File) -> String?): Result = runCatching {
            val out = File(dir, "${base.replace(Regex("[^A-Za-z0-9._ -]"), "_")}-${System.currentTimeMillis()}.epub")
            val title = block(out) ?: base
            file.delete()
            Result.Book(Uri.fromFile(out), title, out.length(), secure, host, convertedFrom = kind)
        }.getOrElse { e ->
            if (e is FenrirConvert.Locked) Result.Stop("🔒 This book is locked (DRM)", "It only opens in the store's own app. Read it there, or get a DRM-free copy.")
            else Result.Stop("Couldn't convert this $kind", e.message ?: "The file may be damaged.")
        }
        return when {
            text.startsWith("%PDF") -> converted("PDF") { FenrirConvert.pdfToEpub(file, base, it); base }
            text.startsWith("Rar!") || text.startsWith("7z") -> converted("comic archive") { FenrirConvert.archiveToEpub(file, base, it); base }
            text.length >= 68 && text.substring(60, 68).let { it == "BOOKMOBI" || it == "TEXtREAd" } ->
                converted("Kindle file") { FenrirConvert.mobiToEpub(file, it) }
            !text.startsWith("PK") -> { file.delete(); Result.Stop("This isn't a book", "It isn't an EPUB, PDF, comic archive or Kindle file, so it was stopped.") }
            else -> {
                val kind = runCatching {
                    ZipFile(file).use { zip ->
                        val names = zip.entries().asSequence().map { it.name }.toList()
                        when {
                            names.any { it == "AndroidManifest.xml" || it == "classes.dex" } -> "apk"
                            names.any { it.equals("META-INF/encryption.xml", true) } &&
                                zip.getEntry("META-INF/encryption.xml")?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }
                                    ?.let { "adobe.com/adept" in it || "readium.org/2014/01/lcp" in it } == true -> "drm"
                            zip.getEntry("mimetype")?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString().trim() } } == "application/epub+zip" -> "epub"
                            names.any { it.lowercase().endsWith(".opf") } -> "epub"
                            names.any { it.lowercase().matches(Regex(""".*\.(jpe?g|png|webp|gif|avif)$""")) } -> "cbz"
                            else -> "other"
                        }
                    }
                }.getOrDefault("broken")
                when (kind) {
                    "apk" -> { file.delete(); Result.Stop("This isn't a book", "It's really an app installer (APK) named like a book. Fenrir Read never installs anything, so it was stopped.") }
                    "drm" -> { file.delete(); Result.Stop("🔒 This book is locked (DRM)", "It only opens in the store's own app. Read it there, or get a DRM-free copy.") }
                    "epub" -> {
                        val epub = if (file.name.endsWith(".epub", true)) file else File(dir, "$base.epub").also { file.renameTo(it) }
                        Result.Book(Uri.fromFile(epub), base, epub.length(), secure, host)
                    }
                    "cbz" -> converted("comic archive") { FenrirConvert.archiveToEpub(file, base, it); base }
                    else -> { file.delete(); Result.Stop("This isn't a book", "The file couldn't be opened as a book.") }
                }
            }
        }
    }

    /** A file picked on the phone: copied here, then checked and converted like a download. */
    suspend fun fromPhone(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "book"
        val dir = File(context.cacheDir, "fenrir_imports").apply { mkdirs() }
        val out = File(dir, "${System.currentTimeMillis()}-" + name.replace(Regex("[^A-Za-z0-9._ -]"), "_"))
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        }.getOrNull()
        if (copied == null) return@withContext Result.Stop("Couldn't open the file", "The file couldn't be read.")
        if (out.length() > MAX_BYTES) { out.delete(); return@withContext Result.Stop("Too big", "Files over about 1 GB aren't imported.") }
        process(out, name, secure = true, host = "your phone")
    }

    private const val HUMAN_CHECK = "The site asks for a human check. Tap Open site, pass the check there, then come back and tap Check link."

    private fun isHumanCheck(e: Throwable?): Boolean {
        val text = generateSequence(e) { it.cause }.joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }.lowercase()
        return "cloudflare" in text || "challenge" in text
    }

    /** Fenrir: what went wrong when the link didn't open, in plain words. */
    private fun whyFailed(e: Throwable?, url: String): String {
        val text = generateSequence(e) { it.cause }.joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }.lowercase()
        return when {
            "cleartext" in text -> "This site only works over plain http, which Android blocks to keep you safe, and it has no https version."
            "unknownhost" in text -> "That website wasn't found. Check the address (or your internet)."
            "timeout" in text || "timed out" in text -> "The site took too long to answer. Try again later."
            "ssl" in text || "certificate" in text || "handshake" in text -> "The site's secure connection didn't work (its certificate isn't valid)."
            "toomanyfollowups" in text || "too many follow-up" in text -> "The link keeps redirecting and never reaches a file."
            "cloudflare" in text || "challenge" in text -> HUMAN_CHECK
            else -> "The site didn't answer. Check the link or try again later." +
                (e?.javaClass?.simpleName?.let { " ($it)" } ?: "")
        }
    }

    private fun fileName(disposition: String?, url: String): String {
        Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)""").find(disposition.orEmpty())?.groupValues?.get(1)?.let { return Uri.decode(it) }
        return Uri.decode(url.substringBefore('?').substringAfterLast('/'))
    }

    /** Every EPUB link on the page (plus "download" buttons that point at one). */
    private fun scanPage(pageUrl: String, host: String, html: String): Result {
        val base = runCatching { java.net.URI(pageUrl) }.getOrNull()
        val re = Regex("""<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val links = re.findAll(html).mapNotNull { m ->
            val href = m.groupValues[1].replace("&amp;", "&")
            val label = m.groupValues[2].replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            val isEpub = href.substringBefore('?').lowercase().let { h -> listOf(".epub", ".epub3", ".pdf", ".cbz", ".cbr", ".mobi", ".azw3", ".azw").any { h.endsWith(it) } } ||
                (href.lowercase().contains("epub") && (label.lowercase().contains("download") || label.lowercase().contains("epub")))
            if (!isEpub) return@mapNotNull null
            val abs = runCatching { base?.resolve(href)?.toString() }.getOrNull() ?: href
            val name = label.takeIf { it.length in 2..120 && !it.equals("download", true) && !it.equals("epub", true) }
                ?: Uri.decode(abs.substringBefore('?').substringAfterLast('/')).removeSuffix(".epub")
            name to abs
        }.distinctBy { it.second }.take(200).toList()
        if (links.isEmpty()) return Result.Stop("No books on this page", "Couldn't find any EPUB downloads on $host.")
        return Result.Page(host, links)
    }
}

private val Red = Color(0xFFFF4D57)
private val Muted = Color(0xFF9A9AA6)

private fun sizeLabel(bytes: Long) = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> "${(bytes / 1024).coerceAtLeast(1)} KB"
}

/**
 * Import a book: paste a link (checked first), scan a page for books, or pick from your phone.
 * [openImporter] opens the EPUB importer with the downloaded files (empty = pick from phone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenrirImportSheet(onDismiss: () -> Unit, openImporter: (List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FenrirImport.Result?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<String?>(null) }
    // From your phone: any book file; non-EPUBs are converted first.
    val phonePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val got = mutableListOf<String>(); val stops = mutableListOf<String>()
            uris.forEachIndexed { i, u ->
                progress = "Checking ${i + 1} of ${uris.size}…"
                when (val one = FenrirImport.fromPhone(context, u)) {
                    is FenrirImport.Result.Book -> got += one.uri.toString()
                    is FenrirImport.Result.Stop -> stops += one.title
                    else -> {}
                }
            }
            busy = false; progress = null
            if (got.isNotEmpty()) { onDismiss(); openImporter(got) } else {
                result = FenrirImport.Result.Stop("None could be imported", stops.distinct().joinToString())
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF15151B)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Import a book", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("EPUB · PDF · CBZ · CBR · MOBI · AZW3 · goes into your Library", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it; result = null },
                placeholder = { Text("Paste a link to a book or a page") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Check link", primary = true, enabled = link.isNotBlank() && !busy) {
                    busy = true; result = null; picked.clear()
                    scope.launch {
                        result = FenrirImport.check(context, link)
                        (result as? FenrirImport.Result.Page)?.let { p -> picked.addAll(p.links.map { it.second }) }
                        busy = false
                    }
                }
                Pill("From your phone", primary = false, enabled = !busy) { phonePicker.launch(arrayOf("*/*")) }
            }
            if (busy) {
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Red)
                    Spacer(Modifier.width(8.dp))
                    Text(progress ?: "Checking…", color = Muted, fontSize = 13.sp)
                }
            }
            when (val r = result) {
                is FenrirImport.Result.Book -> {
                    Column(
                        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0x144CD787)).border(1.dp, Color(0x594CD787), RoundedCornerShape(18.dp)).padding(14.dp),
                    ) {
                        Text(r.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Check(if (r.secure) "Secure link (https) · ${r.host}" else "From ${r.host}", ok = r.secure, bad = "Not a secure link (http) · ${r.host}")
                        FenrirImport.linkWarning(r.host)?.let { Check(it, ok = false, bad = it) }
                        Check(
                            r.convertedFrom?.let { "Converted from $it → EPUB · ${sizeLabel(r.bytes)}" } ?: "Really an EPUB book · ${sizeLabel(r.bytes)}",
                            ok = true,
                        )
                        Check("DRM-free", ok = true)
                        Check("No hidden scripts will run", ok = true)
                    }
                    Row(Modifier.padding(top = 12.dp)) {
                        Spacer(Modifier.weight(1f))
                        Pill("Import", primary = true) { onDismiss(); openImporter(listOf(r.uri.toString())) }
                    }
                }
                is FenrirImport.Result.Page -> {
                    // Search the found books; if nothing here matches, look on the site itself.
                    var find by remember(r) { mutableStateOf("") }
                    val shown = r.links.filter { (name, url) ->
                        find.isBlank() || name.contains(find.trim(), true) || url.contains(find.trim().replace(' ', '-'), true)
                    }
                    fun downloadAll(urls: List<String>) {
                        busy = true
                        scope.launch {
                            val got = mutableListOf<String>()
                            val stops = mutableListOf<String>()
                            urls.forEachIndexed { i, url ->
                                progress = "Downloading ${i + 1} of ${urls.size}…"
                                when (val one = FenrirImport.check(context, url)) {
                                    is FenrirImport.Result.Book -> got += one.uri.toString()
                                    is FenrirImport.Result.Stop -> stops += one.title
                                    else -> stops += "Not a book"
                                }
                            }
                            busy = false; progress = null
                            if (got.isNotEmpty()) { onDismiss(); openImporter(got) } else {
                                result = FenrirImport.Result.Stop("None could be imported", stops.distinct().joinToString())
                            }
                        }
                    }
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Found ${r.links.size} book${if (r.links.size > 1) "s" else ""}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("on ${r.host}", color = Muted, fontSize = 12.sp)
                        }
                        Text(
                            if (shown.isNotEmpty() && shown.all { it.second in picked }) "Select none" else "Select all",
                            color = Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                if (shown.all { it.second in picked }) picked.removeAll(shown.map { it.second }.toSet())
                                else picked.addAll(shown.map { it.second }.filterNot { it in picked })
                            },
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = find,
                        onValueChange = { find = it },
                        placeholder = { Text("🔍  Search these books") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    if (shown.isEmpty() && find.isNotBlank()) {
                        Text(
                            "Nothing here matches “${find.trim()}”.",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Pill("Search ${r.host} for “${find.trim()}”", primary = false, enabled = !busy) {
                            busy = true
                            val q = java.net.URLEncoder.encode(find.trim(), "UTF-8")
                            scope.launch {
                                // Common search addresses; the first that lists books wins.
                                val tries = listOf("search?q=$q", "search?query=$q", "?s=$q", "search?keywords=$q", "search/$q")
                                var found: FenrirImport.Result? = null
                                for (t in tries) {
                                    progress = "Searching ${r.host}…"
                                    val res = FenrirImport.check(context, "https://${r.host}/$t")
                                    if (res is FenrirImport.Result.Page && res.links.isNotEmpty() || res is FenrirImport.Result.Book) { found = res; break }
                                }
                                busy = false; progress = null
                                result = found ?: FenrirImport.Result.Stop("No results", "${r.host} didn't list any books for “${find.trim()}”. Try the site's own search in your browser and paste that page here.")
                                picked.clear()
                            }
                        }
                    }
                    Column(Modifier.padding(top = 6.dp)) {
                        shown.forEach { (name, url) ->
                            Row(Modifier.fillMaxWidth().clickable { if (url in picked) picked.remove(url) else picked.add(url) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = url in picked,
                                    onCheckedChange = { if (it) picked.add(url) else picked.remove(url) },
                                    colors = CheckboxDefaults.colors(checkedColor = Red),
                                )
                                Text(name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                // Download just this one.
                                Text(
                                    "⤓",
                                    color = Red,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(CircleShape).clickable(enabled = !busy) { downloadAll(listOf(url)) }.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Pill(
                        if (picked.isEmpty()) "Tick books or tap ⤓ to download" else "⤓ Download ${picked.size} book${if (picked.size > 1) "s" else ""}",
                        primary = true,
                        enabled = picked.isNotEmpty() && !busy,
                    ) { downloadAll(picked.toList()) }
                }
                is FenrirImport.Result.Stop -> {
                    Column(
                        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0x14FFB547)).border(1.dp, Color(0x59FFB547), RoundedCornerShape(18.dp)).padding(14.dp),
                    ) {
                        Text(r.title, color = Color(0xFFFFB547), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(r.why, color = Color(0xFFC9C9D2), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        r.openUrl?.let { url ->
                            Row(Modifier.padding(top = 10.dp)) {
                                // in-app browser: same cookies and browser name as the downloader, so
                                // the passed check counts for "Check link" afterwards
                                Pill("Open site", primary = false) {
                                    runCatching {
                                        context.startActivity(
                                            eu.kanade.tachiyomi.ui.webview.WebViewActivity.newIntent(context, url, title = FenrirImport.hostOf(url)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                null -> Unit
            }
            Text(
                "Tip: on a book's download page in your browser, tap Share → Fenrir Vault.\nFree & DRM-free books: Standard Ebooks, Project Gutenberg, Humble Bundle.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun Check(text: String, ok: Boolean, bad: String = text) {
    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "!", color = if (ok) Color(0xFF4CD787) else Color(0xFFFFB547), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(if (ok) text else bad, color = Color(0xFFD5F5E3), fontSize = 13.sp)
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (primary) (if (enabled) Red else Red.copy(alpha = 0.4f)) else Color(0xFF26262E))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
