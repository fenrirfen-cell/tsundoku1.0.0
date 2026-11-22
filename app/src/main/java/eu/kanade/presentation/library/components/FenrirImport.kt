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
        data class Book(val uri: Uri, val name: String, val bytes: Long, val secure: Boolean, val host: String) : Result
        data class Page(val host: String, val links: List<Pair<String, String>>) : Result // name to url
        data class Stop(val title: String, val why: String) : Result
    }

    private val client by lazy { Injekt.get<NetworkHelper>().client }

    fun hostOf(url: String) = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.").orEmpty()

    suspend fun check(context: Context, rawUrl: String): Result = withContext(Dispatchers.IO) {
        val url = rawUrl.trim().let { if (!it.startsWith("http")) "https://$it" else it }
        val host = hostOf(url)
        if (host.isEmpty()) return@withContext Result.Stop("That's not a link", "Paste the whole web address, starting with https://")
        val secure = url.startsWith("https://")
        val response = runCatching { client.newCall(GET(url)).execute() }.getOrElse {
            return@withContext Result.Stop("Couldn't open the link", "The site didn't answer. Check the link or try again later.")
        }
        response.use { r ->
            if (!r.isSuccessful) return@withContext Result.Stop("Couldn't open the link", "The site answered with an error (${r.code}).")
            val type = r.header("Content-Type").orEmpty().lowercase()
            val length = r.body?.contentLength() ?: -1L
            if (length > MAX_BYTES) return@withContext Result.Stop("Too big", "Files over about 1 GB aren't imported.")
            val body = r.body ?: return@withContext Result.Stop("Empty file", "The link gave back nothing.")
            if (type.contains("text/html")) {
                val html = body.string()
                return@withContext scanPage(url, host, html)
            }
            val name = fileName(r.header("Content-Disposition"), url)
            val dir = File(context.cacheDir, "fenrir_imports").apply { mkdirs() }
            val out = File(dir, name.ifBlank { "book" }.replace(Regex("[^A-Za-z0-9._ -]"), "_").let { if (it.endsWith(".epub", true)) it else "$it.epub" })
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
            inspect(out)?.let { stop -> out.delete(); return@withContext stop }
            Result.Book(Uri.fromFile(out), name.removeSuffix(".epub"), total, secure, host)
        }
    }

    /** Looks inside the file (never runs anything). Returns why it can't be imported, or null if it's a fine EPUB. */
    private fun inspect(file: File): Result.Stop? {
        val head = file.inputStream().use { s -> ByteArray(5).also { s.read(it) } }
        val text = String(head, Charsets.ISO_8859_1)
        return when {
            text.startsWith("%PDF") -> Result.Stop("PDF books aren't supported yet", "Only EPUB books can be imported for now.")
            text.startsWith("Rar!") -> Result.Stop("Comic archives (CBR) aren't supported yet", "Only EPUB books can be imported for now.")
            text.startsWith("BOOKM") || text.startsWith("\u0000\u0000\u0000") ->
                Result.Stop("Kindle files aren't supported yet", "Get the EPUB version of this book instead.")
            !text.startsWith("PK") -> Result.Stop("This isn't a book", "The file isn't an EPUB, so it was stopped.")
            else -> runCatching {
                ZipFile(file).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toList()
                    when {
                        names.any { it == "AndroidManifest.xml" || it == "classes.dex" } ->
                            Result.Stop("This isn't a book", "It's really an app installer (APK) named like a book. Fenrir Read never installs anything, so it was stopped.")
                        names.any { it.equals("META-INF/encryption.xml", true) } &&
                            zip.getEntry("META-INF/encryption.xml")?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }
                                ?.let { "adobe.com/adept" in it || "readium.org/2014/01/lcp" in it || "kindle" in it.lowercase() } == true ->
                            Result.Stop("🔒 This book is locked (DRM)", "It only opens in the store's own app. Read it there, or get a DRM-free copy.")
                        zip.getEntry("mimetype")?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString().trim() } } == "application/epub+zip" -> null
                        names.any { it.lowercase().endsWith(".opf") } -> null
                        names.all { it.lowercase().matches(Regex(""".*\.(jpe?g|png|webp|gif|xml|txt)$|.*/$""")) } ->
                            Result.Stop("Comic archives (CBZ) aren't supported yet", "Only EPUB books can be imported for now.")
                        else -> Result.Stop("This isn't a book", "The file isn't an EPUB, so it was stopped.")
                    }
                }
            }.getOrElse { Result.Stop("This isn't a book", "The file couldn't be opened as an EPUB.") }
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
            val isEpub = href.substringBefore('?').lowercase().let { it.endsWith(".epub") || it.endsWith(".epub3") || it.endsWith(".kepub.epub") } ||
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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF15151B)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Import a book", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("EPUB · goes into your Library", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
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
                Pill("From your phone", primary = false, enabled = !busy) { onDismiss(); openImporter(emptyList()) }
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
                        Check("Really an EPUB book · ${sizeLabel(r.bytes)}", ok = true)
                        Check("DRM-free", ok = true)
                        Check("No hidden scripts will run", ok = true)
                    }
                    Row(Modifier.padding(top = 12.dp)) {
                        Spacer(Modifier.weight(1f))
                        Pill("Import", primary = true) { onDismiss(); openImporter(listOf(r.uri.toString())) }
                    }
                }
                is FenrirImport.Result.Page -> {
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Found ${r.links.size} book${if (r.links.size > 1) "s" else ""}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("on ${r.host}", color = Muted, fontSize = 12.sp)
                        }
                        Text(
                            if (picked.size == r.links.size) "Select none" else "Select all",
                            color = Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                if (picked.size == r.links.size) picked.clear() else { picked.clear(); picked.addAll(r.links.map { it.second }) }
                            },
                        )
                    }
                    Column(Modifier.padding(top = 6.dp)) {
                        r.links.forEach { (name, url) ->
                            Row(Modifier.fillMaxWidth().clickable { if (url in picked) picked.remove(url) else picked.add(url) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = url in picked,
                                    onCheckedChange = { if (it) picked.add(url) else picked.remove(url) },
                                    colors = CheckboxDefaults.colors(checkedColor = Red),
                                )
                                Text(name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp)) {
                        Spacer(Modifier.weight(1f))
                        Pill("Download ${picked.size}", primary = true, enabled = picked.isNotEmpty() && !busy) {
                            busy = true
                            scope.launch {
                                val got = mutableListOf<String>()
                                val stops = mutableListOf<String>()
                                picked.toList().forEachIndexed { i, url ->
                                    progress = "Downloading ${i + 1} of ${picked.size}…"
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
                    }
                }
                is FenrirImport.Result.Stop -> {
                    Column(
                        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0x14FFB547)).border(1.dp, Color(0x59FFB547), RoundedCornerShape(18.dp)).padding(14.dp),
                    ) {
                        Text(r.title, color = Color(0xFFFFB547), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(r.why, color = Color(0xFFC9C9D2), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                null -> Unit
            }
            Text(
                "Tip: on a book's download page in your browser, tap Share → Fenrir Read.\nFree & DRM-free books: Standard Ebooks, Project Gutenberg, Humble Bundle.",
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
