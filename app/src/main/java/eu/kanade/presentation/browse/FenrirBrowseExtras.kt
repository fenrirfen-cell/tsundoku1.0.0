package eu.kanade.presentation.browse

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.browse.components.SourceTypeBadge
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.border
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.domain.extension.interactor.AddExtensionStore
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// ---------------------------------------------------------------------------- pin order + use counts

/** The user's order for pinned sources, and how often each source is opened ("Most used first"). */
object FenrirPinPrefs {
    private const val PREFS = "fenrir_browse"
    private const val KEY_ORDER = "pin_order"
    private const val KEY_USES = "source_uses"

    private val prefs by lazy {
        Injekt.get<Application>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _order = MutableStateFlow<List<Long>>(emptyList())
    val order: StateFlow<List<Long>> = _order.asStateFlow()
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        _order.value = prefs.getString(KEY_ORDER, null).orEmpty()
            .split(',').mapNotNull { it.trim().toLongOrNull() }
    }

    fun orderFlow(): StateFlow<List<Long>> { load(); return order }

    fun setOrder(ids: List<Long>) {
        load()
        _order.value = ids
        prefs.edit().putString(KEY_ORDER, ids.joinToString(",")).apply()
    }

    private fun uses(): Map<Long, Int> = prefs.getString(KEY_USES, null).orEmpty()
        .split(',').mapNotNull { part ->
            val (id, n) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (id.toLongOrNull() ?: return@mapNotNull null) to (n.toIntOrNull() ?: 0)
        }.toMap()

    fun usesOf(id: Long): Int = uses()[id] ?: 0

    fun recordUse(id: Long) {
        val map = uses().toMutableMap()
        map[id] = (map[id] ?: 0) + 1
        prefs.edit().putString(KEY_USES, map.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    /** Pinned sources in the saved order; ones not in it yet go at the end, A→Z. */
    fun sorted(sources: List<Source>, order: List<Long>): List<Source> {
        val index = order.withIndex().associate { it.value to it.index }
        return sources.sortedWith(compareBy<Source>({ index[it.id] ?: Int.MAX_VALUE }, { it.name.lowercase() }))
    }
}

private val CardBg = Color(0xFF17171D)
private val CardBorderless = Color(0xFF1F1F27)
private val MutedText = Color(0xFF8E8E99)

// ---------------------------------------------------------------------------- continue browsing

@Composable
fun FenrirContinueBrowsingCard(source: Source, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) { SourceIcon(source = source) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MutedText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Continue browsing", color = MutedText, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                SourceTypeBadge(source = source)
            }
        }
        Text(
            "Open",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

// ---------------------------------------------------------------------------- pinned grid

/** Compact pinned cards, three per row. Tap = Popular; the small chip = Latest. */
@Composable
fun FenrirPinnedGrid(
    sources: List<Source>,
    onPopular: (Source) -> Unit,
    onLatest: (Source) -> Unit,
    onLongClick: (Source) -> Unit,
    onEditOrder: () -> Unit,
    onAddPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pinned", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("Edit order", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onEditOrder).padding(horizontal = 6.dp, vertical = 4.dp))
            Text("·", color = MutedText, fontSize = 13.sp)
            Text("+ Pin", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onAddPin).padding(horizontal = 6.dp, vertical = 4.dp))
        }
        sources.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { source ->
                    PinnedCard(source, onPopular, onLatest, onLongClick, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PinnedCard(
    source: Source,
    onPopular: (Source) -> Unit,
    onLatest: (Source) -> Unit,
    onLongClick: (Source) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .combinedClickable(onClick = { onPopular(source) }, onLongClick = { onLongClick(source) })
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))) { SourceIcon(source = source) }
            // Dot: health after "Check sources" (green / yellow / red), otherwise the pinned dot.
            val health = fenrirReadHealthColor(source.id)
            Box(
                Modifier.align(Alignment.TopEnd).padding(1.dp).size(9.dp).clip(CircleShape).background(health ?: MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(source.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            SourceTypeBadge(source = source)
            if (source.supportsLatest) {
                Text(
                    "Latest",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardBorderless)
                        .clickable { onLatest(source) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------- pin order screen

@Composable
fun FenrirPinOrderDialog(pinned: List<Source>, onDismiss: () -> Unit) {
    val list = remember { mutableStateListOf<Source>().apply { addAll(pinned) } }
    fun move(from: Int, to: Int) {
        if (to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin order") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    SmallChip("Sort A→Z") {
                        val sorted = list.sortedBy { it.name.lowercase() }
                        list.clear(); list.addAll(sorted)
                    }
                    SmallChip("Most used first") {
                        val sorted = list.sortedByDescending { FenrirPinPrefs.usesOf(it.id) }
                        list.clear(); list.addAll(sorted)
                    }
                }
                // Hold and drag the ≡ handle to move a source; the arrows still work too.
                var dragId by remember { mutableStateOf<Long?>(null) }
                var dragY by remember { mutableStateOf(0f) }
                var rowPx by remember { mutableStateOf(1f) }
                LazyColumn(Modifier.heightIn(max = 420.dp), userScrollEnabled = dragId == null) {
                    itemsIndexed(list, key = { _, s -> s.id }) { index, source ->
                        val dragging = dragId == source.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragY else 0f; scaleX = if (dragging) 1.03f else 1f; scaleY = scaleX }
                                .onSizeChanged { rowPx = it.height.toFloat().coerceAtLeast(1f) }
                                .padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (dragging) Color(0xFF2A2A34) else CardBg).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to move",
                                tint = if (dragging) Color.White else MutedText,
                                modifier = Modifier
                                    .size(28.dp)
                                    .pointerInput(source.id) {
                                        detectDragGestures(
                                            onDragStart = { dragId = source.id; dragY = 0f },
                                            onDragEnd = { dragId = null; dragY = 0f },
                                            onDragCancel = { dragId = null; dragY = 0f },
                                        ) { change, amount ->
                                            change.consume()
                                            dragY += amount.y
                                            val at = list.indexOfFirst { it.id == source.id }
                                            if (dragY > rowPx / 2 && at < list.lastIndex) { move(at, at + 1); dragY -= rowPx }
                                            else if (dragY < -rowPx / 2 && at > 0) { move(at, at - 1); dragY += rowPx }
                                        }
                                    }
                                    .padding(4.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))) { SourceIcon(source = source) }
                            Spacer(Modifier.width(8.dp))
                            Text(source.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = if (index > 0) Color.White else MutedText.copy(alpha = 0.4f),
                                modifier = Modifier.clip(CircleShape).clickable(enabled = index > 0) { move(index, index - 1) }.padding(6.dp).size(20.dp),
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = if (index < list.lastIndex) Color.White else MutedText.copy(alpha = 0.4f),
                                modifier = Modifier.clip(CircleShape).clickable(enabled = index < list.lastIndex) { move(index, index + 1) }.padding(6.dp).size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                FenrirPinPrefs.setOrder(list.map { it.id })
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier.clip(CircleShape).background(CardBorderless).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** "+ Pin": pick sources to pin. */
@Composable
fun FenrirAddPinDialog(unpinned: List<Source>, onPin: (Source) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin a source") },
        text = {
            if (unpinned.isEmpty()) {
                Text("Everything is pinned already.")
            } else {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    itemsIndexed(unpinned, key = { _, s -> s.id }) { _, source ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPin(source) }.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))) { SourceIcon(source = source) }
                            Spacer(Modifier.width(10.dp))
                            Text(source.visualName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.PushPin, contentDescription = "Pin", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ---------------------------------------------------------------------------- + Add (paste a link)

private enum class ReadRepoKind(val label: String) { Manga("Manga"), NovelApk("Novel ext."), NovelJs("Novel plugins") }

private fun guessReadKind(link: String, novelSide: Boolean): ReadRepoKind {
    val l = link.lowercase()
    return when {
        l.contains("plugins.min.json") || l.contains("lnreader") -> ReadRepoKind.NovelJs
        l.contains("novel") -> ReadRepoKind.NovelApk
        novelSide && !l.contains("index.min.json") -> ReadRepoKind.NovelJs
        else -> ReadRepoKind.Manga
    }
}

/** Keeps installs going after the sheet closes. */
private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

@Composable
fun FenrirReadAddSheet(novelSide: Boolean, onDismiss: () -> Unit) {
    val extensionManager = remember { Injekt.get<ExtensionManager>() }
    val jsPlugins = remember { Injekt.get<JsPluginManager>() }
    val addStore = remember { Injekt.get<AddExtensionStore>() }
    val scope = rememberCoroutineScope()

    var link by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(if (novelSide) ReadRepoKind.NovelJs else ReadRepoKind.Manga) }
    var picked by remember { mutableStateOf(false) }
    var addedUrl by remember { mutableStateOf<String?>(null) }
    var addedKind by remember { mutableStateOf<ReadRepoKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val installing = remember { mutableStateListOf<String>() }
    // Fenrir: the repo is tested before it's added.
    var verdict by remember { mutableStateOf<FenrirRepoVerdict?>(null) }
    // Fenrir: Browse + also offers Import a book.
    var importOpen by remember { mutableStateOf(false) }
    val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current
    if (importOpen) {
        eu.kanade.presentation.library.components.FenrirImportSheet(
            onDismiss = { importOpen = false; onDismiss() },
            openImporter = { uris -> navigator?.push(eu.kanade.tachiyomi.ui.library.ImportEpubScreen(initialUriStrings = uris)) },
        )
        return
    }

    val available by extensionManager.availableExtensionsFlow.collectAsState()
    val installedExt by extensionManager.installedExtensionsFlow.collectAsState()
    val jsAvailable by jsPlugins.availablePlugins.collectAsState()
    val jsInstalled by jsPlugins.installedPlugins.collectAsState()
    val jsLoading by jsPlugins.isLoading.collectAsState()

    fun add(force: Boolean = false) {
        val url = link.trim()
        if (url.isEmpty()) return
        message = null
        if (!force) {
            busy = true
            scope.launch {
                val v = fenrirVerifyRepo(url, jsPlugins)
                busy = false
                verdict = v
                if (v is FenrirRepoVerdict.Works) {
                    if (v.isPlugins && kind != ReadRepoKind.NovelJs) kind = ReadRepoKind.NovelJs
                    if (!v.isPlugins && kind == ReadRepoKind.NovelJs) kind = guessReadKind(url, false).takeIf { it != ReadRepoKind.NovelJs } ?: ReadRepoKind.Manga
                    add(force = true)
                }
            }
            return
        }
        when (kind) {
            ReadRepoKind.NovelJs -> {
                jsPlugins.addRepository(url.substringAfter("://").substringBefore("/").ifBlank { "Repository" }, url)
                addedUrl = url; addedKind = kind
            }
            else -> {
                busy = true
                scope.launch {
                    addStore(url, isNovel = kind == ReadRepoKind.NovelApk)
                        .onSuccess {
                            extensionManager.findAvailableExtensions()
                            addedUrl = url; addedKind = kind
                        }
                        .onFailure { message = it.message ?: "Couldn't add that repository." }
                    busy = false
                }
            }
        }
        link = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add sources") },
        text = {
            Column {
                Text(
                    "Paste a repository link. Its extensions show here to install.",
                    color = MutedText,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        if (!picked && it.isNotBlank()) kind = guessReadKind(it, novelSide)
                    },
                    placeholder = { Text("Paste a link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReadRepoKind.entries.forEach { k ->
                        val on = k == kind
                        Text(
                            k.label,
                            fontSize = 12.sp,
                            color = if (on) Color.White else MutedText,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else CardBorderless)
                                .clickable { kind = k; picked = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                verdict?.let { v ->
                    FenrirRepoVerdictCard(
                        verdict = v,
                        onAddAnyway = { verdict = null; add(force = true) },
                        onCancel = { verdict = null },
                    )
                }
                if (busy || (addedKind == ReadRepoKind.NovelJs && jsLoading)) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading…", fontSize = 13.sp, color = MutedText)
                    }
                }
                val url = addedUrl
                when (addedKind) {
                    ReadRepoKind.NovelJs -> {
                        val installedIds = jsInstalled.map { it.plugin.id }.toSet()
                        val list = jsAvailable.filter { it.repositoryUrl == url && it.id !in installedIds }
                        FoundList(
                            names = list.map { it.name to it.lang },
                            installing = installing,
                            onInstall = { i ->
                                val p = list[i]
                                installing += p.id
                                installScope.launch {
                                    if (FenrirInstallCheck.confirmIfDown(p.name, p.site)) jsPlugins.installPlugin(p, url!!)
                                    installing -= p.id
                                }
                            },
                            onInstallAll = {
                                list.forEach { p ->
                                    installing += p.id
                                    installScope.launch { jsPlugins.installPlugin(p, url!!); installing -= p.id }
                                }
                            },
                            ids = list.map { it.id },
                        )
                    }
                    ReadRepoKind.Manga, ReadRepoKind.NovelApk -> {
                        val installedPkgs = installedExt.map { it.pkgName }.toSet()
                        val base = url?.substringBeforeLast("/")
                        val list = available.filter { ext ->
                            ext.pkgName !in installedPkgs &&
                                (ext.store.indexUrl == url || (base != null && ext.store.indexUrl.startsWith(base)))
                        }
                        FoundList(
                            names = list.map { it.name to it.lang },
                            installing = installing,
                            onInstall = { i ->
                                val e = list[i]
                                installing += e.pkgName
                                installScope.launch {
                                    runCatching { extensionManager.installExtension(e).collect { } }
                                    installing -= e.pkgName
                                }
                            },
                            onInstallAll = {
                                list.forEach { e ->
                                    installing += e.pkgName
                                    installScope.launch {
                                        runCatching { extensionManager.installExtension(e).collect { } }
                                        installing -= e.pkgName
                                    }
                                }
                            },
                            ids = list.map { it.pkgName },
                        )
                    }
                    null -> Unit
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { importOpen = true }) { Text("Import a book") }
                TextButton(onClick = onDismiss) { Text("Close") }
                TextButton(onClick = { verdict = null; add() }, enabled = link.isNotBlank() && !busy) { Text("Add") }
            }
        },
    )
}

@Composable
private fun FoundList(
    names: List<Pair<String, String>>,
    ids: List<String>,
    installing: List<String>,
    onInstall: (Int) -> Unit,
    onInstallAll: () -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (names.isEmpty()) "Nothing new to install from it yet." else "${names.size} to install",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (names.size > 1) TextButton(onClick = onInstallAll) { Text("Install all") }
        }
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
            itemsIndexed(names, key = { i, _ -> ids[i] }) { i, (name, lang) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        if (lang.isNotBlank()) Text(lang.uppercase(), color = MutedText, fontSize = 11.sp)
                    }
                    if (ids[i] in installing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { onInstall(i) }) { Text("Install") }
                    }
                }
            }
        }
    }
}


/** What the test of a pasted repo link found. */
sealed interface FenrirRepoVerdict {
    data class Works(val count: Int?, val isPlugins: Boolean) : FenrirRepoVerdict
    data object Anime : FenrirRepoVerdict
    data object DidntLoad : FenrirRepoVerdict
    data class Duplicate(val name: String) : FenrirRepoVerdict
}

/** Loads the repo before adding it: does it load, is it for Read, is it already added? */
suspend fun fenrirVerifyRepo(url: String, jsPlugins: JsPluginManager): FenrirRepoVerdict = withContext(Dispatchers.IO) {
    fun norm(u: String) = u.trim().trimEnd('/').substringAfter("://").lowercase()
    val known = runCatching { Injekt.get<mihon.domain.extension.interactor.GetExtensionStores>().get() }.getOrDefault(emptyList())
    known.firstOrNull { norm(it.indexUrl) == norm(url) || norm(it.indexUrl).substringBeforeLast("/") == norm(url).substringBeforeLast("/") && norm(url).endsWith("index.min.json") }
        ?.let { return@withContext FenrirRepoVerdict.Duplicate(it.name) }
    jsPlugins.repositories.value.firstOrNull { norm(it.url) == norm(url) }
        ?.let { return@withContext FenrirRepoVerdict.Duplicate(it.name) }
    val client = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().client
    val body = kotlinx.coroutines.withTimeoutOrNull(15_000) {
        runCatching {
            client.newCall(eu.kanade.tachiyomi.network.GET(url.trim())).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        }.getOrNull()
    } ?: return@withContext FenrirRepoVerdict.DidntLoad
    val t = body.trim()
    val lower = t.take(200_000).lowercase()
    if ("animeextension" in lower || "aniyomi" in lower && "\"pkg\"" in lower) return@withContext FenrirRepoVerdict.Anime
    when {
        t.startsWith("[") -> {
            val items = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(t) as? kotlinx.serialization.json.JsonArray }.getOrNull()
                ?: return@withContext FenrirRepoVerdict.DidntLoad
            if (items.isEmpty()) return@withContext FenrirRepoVerdict.DidntLoad
            val plugins = "\"apk\"" !in lower && (".js\"" in lower || "\"site\"" in lower)
            FenrirRepoVerdict.Works(items.size, isPlugins = plugins)
        }
        t.isEmpty() -> FenrirRepoVerdict.DidntLoad
        t.startsWith("<") -> FenrirRepoVerdict.DidntLoad // a web page, not a repo
        else -> FenrirRepoVerdict.Works(null, isPlugins = false)
    }
}

@Composable
private fun FenrirRepoVerdictCard(verdict: FenrirRepoVerdict, onAddAnyway: () -> Unit, onCancel: () -> Unit) {
    val (color, title, body) = when (verdict) {
        is FenrirRepoVerdict.Works -> Triple(
            Color(0xFF4CD787),
            "✓ Works with Read",
            verdict.count?.let { "$it ${if (verdict.isPlugins) "novel plugins" else "extensions"} · adding it" } ?: "Adding it",
        )
        FenrirRepoVerdict.Anime -> Triple(
            Color(0xFFFFB547),
            "⚠ Not compatible",
            "This is an Aniyomi (anime) repo. Its extensions won't work in Read — add it on the Watch side under Settings → Sources.",
        )
        FenrirRepoVerdict.DidntLoad -> Triple(
            Color(0xFFFFB547),
            "⚠ Didn't load",
            "The link may be wrong, or the site is down right now.",
        )
        is FenrirRepoVerdict.Duplicate -> Triple(
            Color(0xFF8FB4FF),
            "You already have this repo",
            "It's listed as “${verdict.name}”.",
        )
    }
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(body, color = Color(0xFFC9C9D2), fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
        if (verdict == FenrirRepoVerdict.Anime || verdict == FenrirRepoVerdict.DidntLoad) {
            Row(Modifier.padding(top = 6.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onAddAnyway) { Text("Add anyway") }
            }
        }
    }
}
