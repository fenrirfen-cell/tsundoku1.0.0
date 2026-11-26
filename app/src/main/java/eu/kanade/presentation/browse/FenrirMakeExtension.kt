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
    this.id = 'my-novel-site';
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

  async page(url) {
    const res = await fetchApi(this.abs(url));
    return load(await res.text());
  }

  // A list of books (page = 1, 2, 3…).
  async popularNovels(page) {
    const ${'$'} = await this.page('popular?page=' + page);
    return ${'$'}('.book-item').toArray().map(el => ({
      name: ${'$'}(el).find('.title').text().trim(),
      path: ${'$'}(el).find('a').attr('href'),
      cover: this.abs(${'$'}(el).find('img').attr('src')),
    }));
  }

  async searchNovels(searchTerm, page) {
    const ${'$'} = await this.page('search?q=' + encodeURIComponent(searchTerm) + '&page=' + page);
    return ${'$'}('.book-item').toArray().map(el => ({
      name: ${'$'}(el).find('.title').text().trim(),
      path: ${'$'}(el).find('a').attr('href'),
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
        path: ${'$'}(el).attr('href'),
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

/** Runs the code like an installed extension: a list of books, one book, one chapter. */
private suspend fun testExtension(code: String): List<Pair<Boolean?, String>> {
    val manager = Injekt.get<JsPluginManager>()
    val info = manager.describeCode(code)
    val out = mutableListOf<Pair<Boolean?, String>>(null to "${info.name} · id ${info.id} · v${info.version}")
    if ("exports.default" !in code) {
        out += false to "Missing  exports.default = new YourClass();  at the end."
        return out
    }
    if (info.site.isBlank()) out += false to "No this.site found. Set it to the website's address."
    val source = JsSource(InstalledJsPlugin(info, code, info.version, JsPluginManager.CUSTOM_REPO))
    try {
        withTimeout(90_000) {
            val list = runCatching { source.getPopularManga(1) }
                .onFailure { out += false to "popularNovels: ${it.message ?: it.javaClass.simpleName}" }
                .getOrNull()
            if (list != null) out += (list.mangas.isNotEmpty()) to "popularNovels: ${list.mangas.size} books"
            val book = list?.mangas?.firstOrNull() ?: return@withTimeout
            out += null to "First book: ${book.title}"
            runCatching { source.getMangaDetails(book) }
                .onSuccess { d -> out += true to "parseNovel: ${d.author?.ifBlank { null } ?: "no author"} · summary ${d.description?.length ?: 0} characters" }
                .onFailure { out += false to "parseNovel: ${it.message ?: it.javaClass.simpleName}" }
            val chapters = runCatching { source.getChapterList(book) }
                .onFailure { out += false to "Chapters: ${it.message ?: it.javaClass.simpleName}" }
                .getOrNull() ?: return@withTimeout
            out += chapters.isNotEmpty() to "Chapters: ${chapters.size}"
            val chapter = chapters.lastOrNull() ?: return@withTimeout
            runCatching { source.fetchPageText(Page(0, chapter.url, "")) }
                .onSuccess { html ->
                    val text = org.jsoup.Jsoup.parse(html).text()
                    out += (text.length > 200) to "parseChapter (${chapter.name}): ${text.length} characters"
                    if (text.isNotBlank()) out += null to "“${text.take(140)}…”"
                }
                .onFailure { out += false to "parseChapter: ${it.message ?: it.javaClass.simpleName}" }
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        out += false to "Stopped after 90 seconds (the site may be slow or blocked)."
    } catch (e: Throwable) {
        out += false to (e.message ?: e.javaClass.simpleName)
    } finally {
        runCatching { source.releaseRuntime() }
    }
    return out
}

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
        var tab by rememberSaveable { mutableStateOf("Simple") }
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
                listOf("Simple", "Code").forEach { t -> Pill(t, filled = tab == t) { tab = t } }
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

class FenrirCodeEditorScreen(private val editId: String?) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val clipboard = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val manager = remember { Injekt.get<JsPluginManager>() }
        val installed by manager.installedPlugins.collectAsState()
        val startCode = remember { editId?.let { id -> manager.installedPlugins.value.find { it.plugin.id == id }?.code } ?: FenrirExtensionTemplate.blank }
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
