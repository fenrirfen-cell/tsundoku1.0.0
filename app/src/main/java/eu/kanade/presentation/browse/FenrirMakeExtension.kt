package eu.kanade.presentation.browse

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.jsplugin.model.InstalledJsPlugin
import eu.kanade.tachiyomi.jsplugin.source.JsSource
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
 * Fenrir: Browse → + → Make extension.
 *  - Simple opens Tsundoku's own Custom Sources builder (unchanged).
 *  - Code is a small editor for LNReader-format novel plugins, installed with the app's own
 *    plugin loader, so they behave like extensions from a repo.
 */

private val Bg = Color(0xFF120D0B)
private val Field = Color(0xFF1C1512)
private val Rim = Color(0xFF3A302A)
private val Muted = Color(0xFFB9AEA6)
private val Accent = Color(0xFFFF4D57)
private val Good = Color(0xFF9FD8A5)
private val Warn = Color(0xFFF2B07A)

@Composable
private fun Pill(text: String, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (!enabled) Muted else Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled && enabled) Accent else Color.Transparent)
            .border(1.dp, if (filled && enabled) Accent else Rim, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            color = Color.White,
            fontSize = 30.sp,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 12.dp),
        )
        Text(title, color = Color(0xFFE9B860), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

object FenrirExtensionTemplate {
    val blank = """
// A novel extension in LNReader's plugin format.
// 1) Change id, name and site.  2) Fill in the CSS selectors.  3) Tap Test, then Install.
const { fetchApi } = require('@libs/fetch');
const { load } = require('cheerio');

class MyNovelSite {
  constructor() {
    this.id = 'myNovelSite';
    this.name = 'My Novel Site';
    this.version = '1.0.0';
    this.site = 'https://example.com/';
    this.icon = '';
  }

  // Turns "/book/1" into "https://example.com/book/1".
  abs(path) {
    if (!path) return '';
    if (/^https?:/.test(path)) return path;
    return this.site.replace(/\/${'$'}/, '') + '/' + path.replace(/^\//, '');
  }

  // The app wants paths like "/book/1", not "https://example.com/book/1".
  relative(href) {
    if (!href) return '';
    return href.replace(/^https?:\/\/[^/]+/, '') || '/';
  }

  async page(url) {
    const res = await fetchApi(this.abs(url));
    return load(await res.text());
  }

  // A list of books (page = 1, 2, 3…).
  async popularNovels(page) {
    const ${'$'} = await this.page('popular?page=' + page);
    return ${'$'}('.book-item').toArray().map(el => ({
      name: ${'$'}(el).find('.title').text().trim(),
      path: this.relative(${'$'}(el).find('a').attr('href')),
      cover: this.abs(${'$'}(el).find('img').attr('src')),
    }));
  }

  async searchNovels(searchTerm, page) {
    const ${'$'} = await this.page('search?q=' + encodeURIComponent(searchTerm) + '&page=' + page);
    return ${'$'}('.book-item').toArray().map(el => ({
      name: ${'$'}(el).find('.title').text().trim(),
      path: this.relative(${'$'}(el).find('a').attr('href')),
      cover: this.abs(${'$'}(el).find('img').attr('src')),
    }));
  }

  // One book: its details and chapter list (first chapter first).
  async parseNovel(path) {
    const ${'$'} = await this.page(path);
    return {
      path,
      name: ${'$'}('h1').first().text().trim(),
      cover: this.abs(${'$'}('.book-cover img').attr('src')),
      author: ${'$'}('.author').first().text().trim(),
      summary: ${'$'}('.summary').text().trim(),
      chapters: ${'$'}('.chapter-list a').toArray().map((el, i) => ({
        name: ${'$'}(el).text().trim(),
        path: this.relative(${'$'}(el).attr('href')),
        chapterNumber: i + 1,
      })),
    };
  }

  // One chapter: return its HTML.
  async parseChapter(path) {
    const ${'$'} = await this.page(path);
    ${'$'}('#chapter-content script, #chapter-content .ads').remove();
    return ${'$'}('#chapter-content').html() || '';
  }
}

exports.default = new MyNovelSite();
""".trimStart()

    /** A copy of someone's plugin with its own id and name, so installing it never replaces theirs. */
    fun copyOf(installed: InstalledJsPlugin): String {
        val id = installed.plugin.id
        val name = installed.plugin.name
        var code = installed.code
        listOf("'", "\"").forEach { q ->
            code = code.replaceFirst("$q$id$q", "$q$id-mine$q")
            code = code.replaceFirst("$q$name$q", "$q$name (mine)$q")
        }
        return code
    }
}

/** Runs the code like an installed extension (a list of books, one book, one chapter) and shows real errors. */
internal suspend fun testExtension(code: String, timeoutMs: Long = 90_000): List<Pair<Boolean?, String>> {
    val manager = Injekt.get<JsPluginManager>()
    val info = manager.describeCode(code)
    val out = mutableListOf<Pair<Boolean?, String>>(null to "${info.name} · id ${info.id} · v${info.version}")
    if ("exports.default" !in code) {
        out += false to "Missing  exports.default = new YourPlugin();  at the end."
        return out
    }
    if (info.site.isBlank()) out += false to "No this.site found. Set it to the website's address."
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    fun arg(v: String) = json.encodeToString(kotlinx.serialization.json.JsonPrimitive(v))
    fun why(e: Throwable): String = (e.message ?: e.javaClass.simpleName).lines().first().take(300)
    fun obj(el: kotlinx.serialization.json.JsonElement?) = el as? kotlinx.serialization.json.JsonObject
    fun str(o: kotlinx.serialization.json.JsonObject?, key: String) =
        (o?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    val source = JsSource(InstalledJsPlugin(info, code, info.version, JsPluginManager.CUSTOM_REPO))
    try {
        withTimeout(timeoutMs) {
            // 1. List of books
            val listRaw = try {
                source.fenrirRawCall("plugin.popularNovels(1, { showLatestNovels: false, filters: plugin.filters })")
            } catch (e: Exception) {
                out += false to "popularNovels failed: ${why(e)}"
                return@withTimeout
            }
            val parsed = runCatching { json.parseToJsonElement(listRaw) }.getOrNull()
            val books = (parsed as? kotlinx.serialization.json.JsonArray)
                ?: (obj(parsed)?.get("novels") as? kotlinx.serialization.json.JsonArray)
                ?: kotlinx.serialization.json.JsonArray(emptyList())
            if (books.isEmpty()) {
                out += false to "popularNovels ran but found 0 books. The page loaded, but the selectors matched nothing, or the site builds its list with scripts."
                return@withTimeout
            }
            val first = obj(books.first())
            val name = str(first, "name")
            val path = str(first, "path")
            if (name == null || path == null) {
                out += false to "Each book needs a name and a path. The first one has name=${name ?: "missing"}, path=${path ?: "missing"}."
                return@withTimeout
            }
            out += true to "popularNovels: ${books.size} books"
            if (path.startsWith("http")) out += false to "path should be the part after the site (like /novel/123), not a full address: $path"
            out += null to "First book: $name ($path)"

            // 2. One book
            val novelRaw = try {
                source.fenrirRawCall("plugin.parseNovel(${arg(path)})")
            } catch (e: Exception) {
                out += false to "parseNovel failed: ${why(e)}"
                return@withTimeout
            }
            val novel = obj(runCatching { json.parseToJsonElement(novelRaw) }.getOrNull())
            val chapters = novel?.get("chapters") as? kotlinx.serialization.json.JsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            out += chapters.isNotEmpty() to "parseNovel: “${str(novel, "name") ?: "no name"}” · ${chapters.size} chapters"
            if (chapters.isEmpty()) {
                out += false to "No chapters found. Check the chapter list."
                return@withTimeout
            }
            val chapter = obj(chapters.first())
            val chapterPath = str(chapter, "path") ?: run {
                out += false to "The first chapter has no path."
                return@withTimeout
            }

            // 3. One chapter
            val chapterRaw = try {
                source.fenrirRawCall("plugin.parseChapter(${arg(chapterPath)})")
            } catch (e: Exception) {
                out += false to "parseChapter failed: ${why(e)}"
                return@withTimeout
            }
            val html = runCatching { (json.parseToJsonElement(chapterRaw) as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull() ?: chapterRaw
            val text = org.jsoup.Jsoup.parse(html).text()
            out += (text.length > 200) to "parseChapter (${str(chapter, "name") ?: chapterPath}): ${text.length} characters"
            if (text.isBlank()) out += false to "The chapter came back empty."
            else out += null to "“${text.take(140)}…”"
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        out += false to "Stopped after ${timeoutMs / 1000} seconds (the site may be slow or blocked)."
    } catch (e: Throwable) {
        out += false to why(e)
    } finally {
        runCatching { source.releaseRuntime() }
    }
    return out
}

/** True when the test got a list, a book with chapters and a chapter with text. */
internal fun List<Pair<Boolean?, String>>.passed(): Boolean =
    none { it.first == false } && count { it.first == true } >= 3

class FenrirMakeExtensionScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val manager = remember { Injekt.get<JsPluginManager>() }
        val installed by manager.installedPlugins.collectAsState()
        val mine = installed.filter { it.repositoryUrl == JsPluginManager.CUSTOM_REPO || it.plugin.repositoryUrl == JsPluginManager.CUSTOM_REPO }
            .sortedBy { it.plugin.name.lowercase() }
        var tab by rememberSaveable { mutableStateOf("Auto") }
        var deleting by remember { mutableStateOf<InstalledJsPlugin?>(null) }

        deleting?.let { p ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Delete ${p.plugin.name}?") },
                text = { Text("The extension is removed from the app. Books you added from it stay in your library.") },
                confirmButton = {
                    TextButton(onClick = { scope.launch { manager.uninstallPlugin(p.plugin.id) }; deleting = null }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            )
        }

        Column(Modifier.fillMaxSize().background(Bg).navigationBarsPadding()) {
            TopBar("Make extension") { navigator.pop() }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto", "Simple", "Code").forEach { t -> Pill(t, filled = tab == t) { tab = t } }
            }
            if (tab == "Auto") {
                FenrirAutoTab(
                    onOpenInCode = { code -> navigator.push(FenrirCodeEditorScreen(null, code)) },
                    onSimple = { tab = "Simple" },
                )
                return@Column
            }
            if (tab == "Simple") {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("No code needed", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Opens Tsundoku's Custom Sources builder. Type the site's address, then tap the title, chapter list and chapter text on the page to pick them. Test it, then save.",
                        color = Muted, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Works best on sites that show the text straight on the page. Sites that load chapters with scripts or block apps may need the Code tab.",
                        color = Muted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.size(16.dp))
                    Pill("Open the builder", filled = true) {
                        navigator.push(eu.kanade.tachiyomi.ui.browse.source.custom.CustomSourcesScreen())
                    }
                }
                return@Column
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Write or paste an extension in LNReader's plugin format. It installs like any other novel extension.",
                    color = Muted, fontSize = 13.5.sp, lineHeight = 19.sp,
                )
                Spacer(Modifier.size(12.dp))
                Pill("New extension", filled = true) { navigator.push(FenrirCodeEditorScreen(null)) }
                Spacer(Modifier.size(18.dp))
                Text("Your extensions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (mine.isEmpty()) {
                Text("None yet.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(mine, key = { it.plugin.id }) { p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Field).padding(12.dp)) {
                        Text(p.plugin.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.plugin.site} · v${p.installedVersion}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Edit") { navigator.push(FenrirCodeEditorScreen(p.plugin.id)) }
                            Pill("Share") {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${p.plugin.id}.js")
                                    putExtra(Intent.EXTRA_TEXT, p.code)
                                }
                                context.startActivity(Intent.createChooser(send, "Share ${p.plugin.name}"))
                            }
                            Pill("Delete") { deleting = p }
                        }
                    }
                }
                item { Spacer(Modifier.size(120.dp)) }
            }
        }
    }
}

class FenrirCodeEditorScreen(private val editId: String?, private val initialCode: String? = null) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val clipboard = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val manager = remember { Injekt.get<JsPluginManager>() }
        val installed by manager.installedPlugins.collectAsState()
        val startCode = remember { initialCode ?: editId?.let { id -> manager.installedPlugins.value.find { it.plugin.id == id }?.code } ?: FenrirExtensionTemplate.blank }
        // Plugins can be large, so the text isn't put in saved state (Android limits its size).
        var code by remember { mutableStateOf(startCode) }
        var savedCode by remember { mutableStateOf(startCode) }
        var results by remember { mutableStateOf<List<Pair<Boolean?, String>>>(emptyList()) }
        var busy by remember { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf(false) }
        var leaving by remember { mutableStateOf(false) }

        if (picking) {
            val others = installed.filter { it.repositoryUrl != JsPluginManager.CUSTOM_REPO && it.plugin.repositoryUrl != JsPluginManager.CUSTOM_REPO }
                .sortedBy { it.plugin.name.lowercase() }
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text("Start from…") },
                text = {
                    if (others.isEmpty()) {
                        Text("Install a novel extension first, then copy it here as a starting point.")
                    } else {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(others, key = { it.plugin.id }) { p ->
                                Column(
                                    Modifier.fillMaxWidth().clickable {
                                        code = FenrirExtensionTemplate.copyOf(p)
                                        results = listOf(null to "Copied ${p.plugin.name}. Its id now ends in -mine, so it won't replace the original.")
                                        picking = false
                                    }.padding(vertical = 10.dp),
                                ) {
                                    Text(p.plugin.name, fontWeight = FontWeight.SemiBold)
                                    Text(p.plugin.site, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
            )
        }
        if (leaving) {
            AlertDialog(
                onDismissRequest = { leaving = false },
                title = { Text("Leave without installing?") },
                text = { Text("Changes you haven't installed will be lost.") },
                confirmButton = { TextButton(onClick = { leaving = false; navigator.pop() }) { Text("Leave") } },
                dismissButton = { TextButton(onClick = { leaving = false }) { Text("Stay") } },
            )
        }

        Column(Modifier.fillMaxSize().background(Bg).navigationBarsPadding().imePadding()) {
            TopBar(if (editId == null) "New extension" else "Edit extension") {
                if (code != savedCode) leaving = true else navigator.pop()
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill("Start from…", enabled = busy == null) { picking = true }
                Pill("Paste", enabled = busy == null) {
                    val text = clipboard.getText()?.text.orEmpty()
                    if (text.isNotBlank()) { code = text; results = listOf(null to "Pasted ${text.lines().size} lines.") }
                    else results = listOf(false to "The clipboard is empty.")
                }
                Pill("Test", enabled = busy == null) {
                    busy = "Testing…"
                    results = emptyList()
                    scope.launch {
                        results = testExtension(code)
                        busy = null
                    }
                }
                Pill("Install", filled = true, enabled = busy == null) {
                    busy = "Installing…"
                    scope.launch {
                        manager.installCustomPlugin(code)
                            .onSuccess { p ->
                                results = listOf(true to "Installed ${p.name}. Find it in Browse → Sources → Novel.")
                                savedCode = code
                                busy = null
                            }
                            .onFailure { results = listOf(false to (it.message ?: "Couldn't install it.")); busy = null }
                    }
                }
                if (busy != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                    Text(busy!!, color = Muted, fontSize = 12.sp)
                }
            }
            if (results.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Field).verticalScroll(rememberScrollState()).padding(10.dp),
                ) {
                    results.forEach { (ok, line) ->
                        Text(
                            (when (ok) { true -> "✓ "; false -> "✗ "; null -> "" }) + line,
                            color = when (ok) { true -> Good; false -> Warn; null -> Muted },
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(16.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0807)).border(1.dp, Rim, RoundedCornerShape(12.dp)),
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it },
                    textStyle = TextStyle(color = Color(0xFFE6DCD3), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun FenrirAutoTab(onOpenInCode: (String) -> Unit, onSimple: () -> Unit) {
    val scope = rememberCoroutineScope()
    val manager = remember { Injekt.get<JsPluginManager>() }
    var link by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<FenrirAutoExtension.Made?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Just type the link", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(6.dp))
        Text(
            "Fenrir checks whether the site uses a common novel-site design and, if so, makes the extension from LNReader's template for it. It's tested before you can install it.",
            color = Muted, fontSize = 13.5.sp, lineHeight = 19.sp,
        )
        Spacer(Modifier.size(12.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Field).border(1.dp, Rim, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (link.isEmpty()) Text("https://novelsite.com", color = Muted, fontSize = 14.sp)
            BasicTextField(
                value = link,
                onValueChange = { link = it; made = null; failure = null; installed = null },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Make it", filled = true, enabled = !busy && link.isNotBlank()) {
                busy = true; made = null; failure = null; installed = null
                scope.launch {
                    FenrirAutoExtension.make(link) { status = it }
                        .onSuccess { made = it }
                        .onFailure { failure = it.message ?: "Couldn't make an extension for this site." }
                    status = null
                    busy = false
                }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
            }
        }
        status?.let { Text(it, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp)) }
        failure?.let { msg ->
            Spacer(Modifier.size(12.dp))
            Text(msg, color = Warn, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.size(10.dp))
            Pill("Use Simple instead", onClick = onSimple)
        }
        made?.let { m ->
            Spacer(Modifier.size(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Field).padding(12.dp)) {
                Text("✓ ${m.name} works", color = Good, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Made from LNReader's ${m.design} template", color = Muted, fontSize = 12.5.sp)
                Spacer(Modifier.size(6.dp))
                m.report.drop(1).forEach { (ok, line) ->
                    Text(
                        (when (ok) { true -> "✓ "; false -> "✗ "; null -> "" }) + line,
                        color = when (ok) { true -> Good; false -> Warn; null -> Muted },
                        fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Install", filled = true, enabled = installed == null) {
                    scope.launch {
                        manager.installCustomPlugin(m.code)
                            .onSuccess { installed = "Installed ${it.name}. Find it in Browse → Sources → Novel." }
                            .onFailure { failure = it.message ?: "Couldn't install it." }
                    }
                }
                Pill("Open in Code") { onOpenInCode(m.code) }
            }
            installed?.let { Text(it, color = Good, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        }
        Spacer(Modifier.size(120.dp))
    }
}
