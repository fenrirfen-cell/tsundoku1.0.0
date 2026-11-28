package eu.kanade.presentation.vault

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.library.components.MugiCollectorBook
import eu.kanade.presentation.library.components.VaultColors
import eu.kanade.presentation.library.components.bookStatusOf
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.vault.Vault
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val Serif = eu.kanade.presentation.components.MugiPlayfair

private fun android.content.Context.findFragmentActivity(): FragmentActivity? =
    generateSequence(this) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<FragmentActivity>().firstOrNull()

private fun reduceMotion(context: android.content.Context): Boolean =
    eu.kanade.tachiyomi.EmbeddedHost.reduceMotion?.invoke() == true || runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

/** Keeps private screens out of screenshots and the recent-apps preview. */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current.findFragmentActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private fun Modifier.walnut(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2A170C), VaultColors.Background, Color(0xFF0E0704))))
    var y = 0f
    var i = 0
    while (y < size.height) {
        drawLine(Color(0x12000000), Offset(0f, y), Offset(size.width, y + 5f), 2f)
        y += 18f + (i % 4) * 7f
        i++
    }
}

/** Soft warm light over a shelf (no harsh spotlights). */
fun Modifier.vaultShelfLighting(): Modifier = drawBehind {
    if (size.width > 0f && size.height > 0f) {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFFFFD397).copy(alpha = 0.13f), Color(0xFFFFB45C).copy(alpha = 0.04f), Color.Transparent),
                center = Offset(size.width / 2f, 0f),
                radius = size.width * 0.65f,
            ),
        )
    }
}

@Composable
private fun GoldButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (filled) Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFD4AF65), Color(0xFFA87832))) else SolidColor(Color(0x66140B05)))
            .border(1.dp, VaultColors.Gold, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (filled) Color(0xFF2B1A0C) else VaultColors.Ivory
        icon?.let {
            Icon(it, contentDescription = null, tint = if (filled) tint else VaultColors.Gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, fontFamily = Serif, fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp, maxLines = 1)
    }
}

/** A small brass lantern (the lantern artwork) beside vault titles. */
@Composable
private fun VaultLantern() {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.mugi_lantern),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = Modifier.width(15.dp).height(32.dp),
    )
}

@Composable
private fun TitleBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VaultColors.Gold)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            VaultLantern()
            Text(
                title,
                color = Color(0xFFF2D592),
                fontFamily = eu.kanade.presentation.components.MugiCinzel,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
            )
            VaultLantern()
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { action?.invoke() }
    }
}

/* ───────────── the cabinet (locked, opening) ───────────── */

/**
 * The cabinet from the approved artwork, laid out on its 1080 × 1280 reference canvas: back wall
 * and doors at (134, 134), padlock in a 180 × 180 box at (450, 600), the frame over the door
 * edges. [opened] releases the padlock (0–200 ms) and swings the doors out on their hinges
 * (200–700 ms); they stay visible as slim edges.
 */
@Composable
private fun VaultCabinet(
    opened: Boolean,
    reduce: Boolean,
    modifier: Modifier = Modifier,
    inside: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    front: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val lock by animateFloatAsState(if (opened) 1f else 0f, tween(if (reduce) 0 else 100), label = "padlock")
    val doors by animateFloatAsState(
        if (opened) 1f else 0f,
        tween(if (reduce) 0 else 400, delayMillis = if (opened && !reduce) 100 else 0, easing = FastOutSlowInEasing),
        label = "doors",
    )
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        fun x(v: Float) = w * (v / 1080f)
        fun y(v: Float) = h * (v / 1280f)
        val res = androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.vault_shelf_background)
        androidx.compose.foundation.Image(res, null, contentScale = androidx.compose.ui.layout.ContentScale.FillBounds, modifier = Modifier.offset(x(134f), y(134f)).size(x(836f), y(1060f)))
        Box(Modifier.offset(x(134f), y(134f)).size(x(836f), y(1060f)).androidxClip(), content = inside)
        listOf(true, false).forEach { left ->
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(if (left) eu.kanade.tachiyomi.R.drawable.vault_door_left else eu.kanade.tachiyomi.R.drawable.vault_door_right),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier
                    .offset(if (left) x(134f) else x(552f), y(134f))
                    .size(x(418f), y(1060f))
                    .graphicsLayer {
                        rotationY = (if (left) 82f else -82f) * doors
                        transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
                        cameraDistance = 24f * density
                    },
            )
        }
        if (lock < 1f) {
            val side = minOf(x(180f), y(180f))
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.vault_padlock),
                contentDescription = if (opened) null else "Locked",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .offset(x(540f) - side / 2, y(690f) - side / 2 + 26.dp * lock)
                    .size(side)
                    .graphicsLayer { alpha = 1f - lock; rotationZ = 14f * lock },
            )
        }
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.vault_cabinet_frame),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        front()
    }
}

private fun Modifier.androidxClip(): Modifier = this.clipToBounds()

/** The plaque artwork with its title written on it. */
@Composable
private fun Plaque(text: String, modifier: Modifier = Modifier) {
    Box(modifier.height(64.dp).aspectRatio900(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.vault_plaque),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
        Text(text, color = Color(0xFFF2D592), fontFamily = eu.kanade.presentation.components.MugiCinzel, fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 28.dp))
    }
}

private fun Modifier.aspectRatio900(): Modifier = this.then(Modifier.aspectRatio(900f / 445f))

/** The decorated top shelf (books, globe, ship) with a small title. */
@Composable
private fun TopShelf(title: String) {
    Box(Modifier.fillMaxWidth().aspectRatioTop(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.vault_top_shelf),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xE6EAD7A8)).border(1.dp, VaultColors.Gold, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF2E180A), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = Color(0xFF2E180A), fontFamily = eu.kanade.presentation.components.MugiCinzel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

private fun Modifier.aspectRatioTop(): Modifier = this.then(Modifier.aspectRatio(1080f / 378f))

/** The locked cabinet with Unlock; used by the Private Library and by "Lock entire Read mode". */
@Composable
fun LockedCabinet(title: String = "Private Library", onUnlocked: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TopShelf(title)
        VaultCabinet(opened = false, reduce = true, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Plaque(title)
                Spacer(Modifier.height(10.dp))
                GoldButton(if (busy) "Checking…" else if (title == "Private Library") "Unlock Vault" else "Unlock", filled = true, modifier = Modifier.widthIn(min = 170.dp)) {
                    val activity = context.findFragmentActivity() ?: return@GoldButton
                    if (busy) return@GoldButton
                    busy = true
                    scope.launch {
                        error = Vault.unlock(activity)
                        busy = false
                        if (error == null) onUnlocked()
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = Color(0xFFF2B8A8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC1A0E06)).padding(8.dp),
                    )
                }
            }
        }
    }
}

/* ───────────── Private Library ───────────── */

private enum class VaultSort(val label: String) { TITLE("Title"), RECENT("Recently read"), PROGRESS("Most read") }
private enum class VaultFilter(val label: String) { ALL("All"), UNREAD("Unread"), READING("Reading"), DONE("Completed") }

class PrivateLibraryScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        SecureWindow()
        LaunchedEffect(Unit) { Vault.ensureLoaded() }
        val unlocked by Vault.unlocked.collectAsState()
        val reduce = remember { reduceMotion(context) }
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.fillMaxSize().walnut().padding(top = top)) {
            if (!unlocked) {
                Column(Modifier.fillMaxSize()) {
                    TitleBar("Private Library", onBack = { navigator.pop() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Read privacy settings", tint = VaultColors.Gold, modifier = Modifier.clip(CircleShape).clickable { navigator.push(ReadPrivacySettingsScreen()) }.padding(10.dp))
                    }
                    LockedCabinet()
                }
            } else {
                UnlockedLibrary(reduce = reduce, onBack = { navigator.pop() })
            }
        }
    }
}

@Composable
private fun UnlockedLibrary(reduce: Boolean, onBack: () -> Unit) {
    val navigator = LocalNavigator.currentOrThrow
    val libraryFlow = remember { Injekt.get<GetLibraryManga>().subscribe() }
    val library by libraryFlow.collectAsState(initial = null)
    val privateIds by Vault.privateIds.collectAsState()
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(0) } // 0 all, 1 manga, 2 novels
    var sort by remember { mutableStateOf(VaultSort.TITLE) }
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var sortOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    // the doors: they were just opened, so swing them out
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    val shelfAlpha by animateFloatAsState(if (opened) 1f else 0f, tween(if (reduce) 0 else 150, delayMillis = if (reduce) 0 else 500), label = "shelf")

    val books = remember(library, privateIds, query, kind, sort, filter) {
        library.orEmpty()
            .filter { it.manga.id in privateIds }
            .filter { kind == 0 || (kind == 2) == it.manga.isNovel }
            .filter { query.isBlank() || it.manga.title.contains(query.trim(), ignoreCase = true) }
            .filter {
                val st = bookStatusOf(it.readCount, it.totalChapters, it.manga.status)
                when (filter) {
                    VaultFilter.ALL -> true
                    VaultFilter.UNREAD -> st == eu.kanade.presentation.library.components.BookReadingStatus.UNREAD
                    VaultFilter.READING -> st == eu.kanade.presentation.library.components.BookReadingStatus.READING
                    VaultFilter.DONE -> st == eu.kanade.presentation.library.components.BookReadingStatus.COMPLETED
                }
            }
            .let { list ->
                when (sort) {
                    VaultSort.TITLE -> list.sortedBy { it.manga.title.lowercase() }
                    VaultSort.RECENT -> list.sortedByDescending { it.lastRead }
                    VaultSort.PROGRESS -> list.sortedByDescending { it.readCount }
                }
            }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize()) {
        TitleBar("Private Library", onBack = onBack) {
            Icon(Icons.Filled.Settings, contentDescription = "Read privacy settings", tint = VaultColors.Gold, modifier = Modifier.clip(CircleShape).clickable { navigator.push(ReadPrivacySettingsScreen()) }.padding(10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            GoldButton("Lock again", Icons.Filled.Lock) { Vault.lock() }
            GoldButton("Add Books", Icons.Filled.Add) { navigator.push(VaultAddBooksScreen()) }
        }
        // search · sort · filter
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, VaultColors.Gold, RoundedCornerShape(22.dp)).background(Color(0x66140B05)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = VaultColors.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search books…", color = Color(0xFFB8A88C), fontFamily = Serif, fontSize = 15.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = VaultColors.Ivory, fontSize = 15.sp), cursorBrush = SolidColor(VaultColors.Gold), modifier = Modifier.fillMaxWidth())
            }
            Box {
                Text("☰ ${sort.label} ▾", color = VaultColors.Ivory, fontFamily = Serif, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { sortOpen = true }.padding(horizontal = 8.dp, vertical = 12.dp))
                DropdownMenu(sortOpen, { sortOpen = false }, containerColor = Color(0xFF2A1A0E)) {
                    VaultSort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label, color = VaultColors.Ivory) }, onClick = { sort = s; sortOpen = false }) }
                }
            }
            Box {
                Text("▼ ${filter.label}", color = VaultColors.Ivory, fontFamily = Serif, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { filterOpen = true }.padding(horizontal = 8.dp, vertical = 12.dp))
                DropdownMenu(filterOpen, { filterOpen = false }, containerColor = Color(0xFF2A1A0E)) {
                    VaultFilter.entries.forEach { f -> DropdownMenuItem(text = { Text(f.label, color = VaultColors.Ivory) }, onClick = { filter = f; filterOpen = false }) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, VaultColors.Gold.copy(alpha = 0.8f), RoundedCornerShape(18.dp))) {
                listOf("All", "Manga", "Novels").forEachIndexed { i, l ->
                    val on = i == kind
                    Text(
                        l,
                        color = if (on) Color(0xFF2B1A0C) else VaultColors.Ivory,
                        fontFamily = Serif,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFA87832))) else SolidColor(Color.Transparent))
                            .clickable { kind = i }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${books.size} ${if (books.size == 1) "book" else "books"}", color = VaultColors.Ivory, fontFamily = Serif, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        // the cabinet: doors swung open at the sides, shelves inside
        VaultCabinet(opened = opened, reduce = reduce, modifier = Modifier.fillMaxWidth().weight(1f), inside = {
            BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = shelfAlpha }) {
                val columns = (maxWidth.value / 112f).toInt().coerceIn(2, 8).let { if (maxWidth < 360.dp) 3.coerceAtMost(it.coerceAtLeast(3)) else it }
                if (library == null) {
                    Text("Opening…", color = VaultColors.Ivory, modifier = Modifier.align(Alignment.Center))
                } else if (books.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xB31A0E06)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (privateIds.isEmpty()) "The cabinet is empty" else "No books match", color = Color(0xFFF2D592), fontFamily = Serif, fontSize = 19.sp)
                        Text(
                            if (privateIds.isEmpty()) "Use Add Books to move books here. They keep their progress and downloads." else "Try another search or filter.",
                            color = Color(0xFFE0D0B0),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    val rows = remember(books, columns) { books.chunked(columns) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
                        items(rows, key = { row -> row.joinToString("|") { it.manga.id.toString() } }) { row ->
                            Column(Modifier.fillMaxWidth().vaultShelfLighting()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                                    row.forEach { lm -> VaultBookCell(lm, Modifier.weight(1f)) { navigator.push(MangaScreen(lm.manga.id)) } }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                // the shelf plank from the artwork, repeated for every row
                                androidx.compose.foundation.Image(
                                    androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.vault_shelf_row),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxWidth().height(22.dp).offset(y = (-8).dp),
                                )
                            }
                        }
                    }
                }
            }
        })
        Spacer(Modifier.height(bottom + 8.dp))
    }
}

@Composable
private fun VaultBookCell(lm: LibraryManga, modifier: Modifier, onClick: () -> Unit) {
    val m = lm.manga
    val cover = remember(m.id, m.coverLastModified, m.thumbnailUrl) { MangaCover(m.id, m.source, m.favorite, m.thumbnailUrl, m.coverLastModified) }
    Column(modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        MugiCollectorBook(
            cover = cover,
            status = bookStatusOf(lm.readCount, lm.totalChapters, m.status),
            readCount = lm.readCount,
            totalChapters = lm.totalChapters,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(m.title, color = VaultColors.Ivory, fontFamily = Serif, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

/* ───────────── Add Books ───────────── */

class VaultAddBooksScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        SecureWindow()
        val unlocked by Vault.unlocked.collectAsState()
        // protection can only be changed while the cabinet is open
        LaunchedEffect(unlocked) { if (!unlocked) navigator.pop() }
        val libraryFlow = remember { Injekt.get<GetLibraryManga>().subscribe() }
        val library by libraryFlow.collectAsState(initial = emptyList())
        val categoriesFlow = remember { Injekt.get<GetCategories>().subscribe() }
        val categories by categoriesFlow.collectAsState(initial = emptyList())
        val privateIds by Vault.privateIds.collectAsState()
        var query by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(setOf<Long>()) }
        var message by remember { mutableStateOf<String?>(null) }
        val list = remember(library, query) {
            library.filter { query.isBlank() || it.manga.title.contains(query.trim(), true) }.sortedBy { it.manga.title.lowercase() }
        }
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(Modifier.fillMaxSize().walnut().padding(top = top)) {
            TitleBar("Add Books", onBack = { navigator.pop() })
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, VaultColors.Gold, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = VaultColors.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search your library…", color = Color(0xFFB8A88C), fontSize = 15.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = VaultColors.Ivory, fontSize = 15.sp), cursorBrush = SolidColor(VaultColors.Gold), modifier = Modifier.fillMaxWidth())
                }
            }
            if (categories.isNotEmpty()) {
                Text("Select a collection", color = Color(0xFFB8A88C), fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp, top = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { !it.isSystemCategory }.forEach { c ->
                        Text(
                            c.name,
                            color = VaultColors.Ivory,
                            fontFamily = Serif,
                            fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, VaultColors.Gold.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).clickable {
                                selected = selected + library.filter { c.id in it.categories }.map { it.manga.id }
                            }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(list, key = { it.manga.id }) { lm ->
                    val id = lm.manga.id
                    val isPrivate = id in privateIds
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (id in selected) selected - id else selected + id }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = id in selected,
                            onCheckedChange = { selected = if (it) selected + id else selected - id },
                            colors = CheckboxDefaults.colors(checkedColor = VaultColors.Gold, uncheckedColor = VaultColors.Gold.copy(alpha = 0.7f), checkmarkColor = Color(0xFF2B1A0C)),
                        )
                        eu.kanade.presentation.manga.components.MangaCover.Book(
                            data = MangaCover(id, lm.manga.source, lm.manga.favorite, lm.manga.thumbnailUrl, lm.manga.coverLastModified),
                            modifier = Modifier.width(40.dp).border(1.dp, VaultColors.Gold, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lm.manga.title, color = VaultColors.Ivory, fontFamily = Serif, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (lm.manga.isNovel) "Novel" else "Manga") + if (isPrivate) " · in the Private Library" else "",
                                color = if (isPrivate) VaultColors.Gold else Color(0xFFB8A88C),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            message?.let { Text(it, color = VaultColors.Gold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val toAdd = selected.filter { it !in privateIds }
                val toRemove = selected.filter { it in privateIds }
                GoldButton("Move to Private Library (${toAdd.size})", filled = true, modifier = Modifier.weight(1f)) {
                    if (toAdd.isEmpty()) return@GoldButton
                    Vault.add(toAdd)
                    message = "Moved ${toAdd.size} ${if (toAdd.size == 1) "book" else "books"}."
                    selected = emptySet()
                }
                GoldButton("Move back (${toRemove.size})", modifier = Modifier.weight(1f)) {
                    if (toRemove.isEmpty()) return@GoldButton
                    val activity = context.findFragmentActivity() ?: return@GoldButton
                    scope.launch {
                        // taking protection off always asks again
                        val err = Vault.authenticate(activity, "Remove from Private Library")
                        if (err == null) {
                            Vault.remove(toRemove)
                            message = "Moved ${toRemove.size} back to the regular library."
                            selected = emptySet()
                        } else {
                            message = err
                        }
                    }
                }
            }
        }
    }
}

/* ───────────── Read privacy settings ───────────── */

class ReadPrivacySettingsScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { Vault.ensureLoaded() }
        val lockAll by Vault.lockEntireRead.collectAsState()
        val protectVault by Vault.protectPrivateLibrary.collectAsState()
        val lockBg by Vault.lockOnBackground.collectAsState()
        val hideNotes by Vault.hideNotifications.collectAsState()
        val collector by Vault.collectorBooks.collectAsState()
        val basePrefs = remember { Injekt.get<BasePreferences>() }
        val incognito by basePrefs.incognitoMode.changes().collectAsState(initial = basePrefs.incognitoMode.get())
        var message by remember { mutableStateOf<String?>(null) }
        /** Turning a protection off asks for the screen lock first. */
        fun guarded(turningOff: Boolean, apply: () -> Unit) {
            if (!turningOff) { apply(); return }
            val activity = context.findFragmentActivity() ?: return
            scope.launch {
                val err = Vault.authenticate(activity, "Change privacy settings")
                if (err == null) apply() else message = err
            }
        }
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(Modifier.fillMaxSize().walnut().padding(top = top).verticalScroll(rememberScrollState())) {
            TitleBar("Read Privacy Settings", onBack = { navigator.pop() })
            Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, VaultColors.Gold, Color.Transparent))))
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x99241309)).border(1.dp, VaultColors.Gold, RoundedCornerShape(14.dp)),
            ) {
                Row(Modifier.fillMaxWidth().clickable { navigator.push(PrivateLibraryScreen()) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = VaultColors.Gold, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Private Library", color = Color(0xFFF2D592), fontFamily = eu.kanade.presentation.components.MugiCinzel, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Lock selected books and collections", color = Color(0xFFCBB892), fontSize = 13.sp)
                    }
                    Text("›", color = VaultColors.Gold, fontSize = 24.sp)
                }
                Divider()
                SettingRow("Captain's Quarters", "Lock all of Read: ask to unlock every time it opens", lockAll) { on -> guarded(!on) { Vault.setLockEntireRead(on) } }
                Divider()
                SettingRow("Captain's Vault", "Ask to unlock the Private Library (unlocking Read doesn't open it)", protectVault) { on -> guarded(!on) { Vault.setProtectPrivateLibrary(on) } }
                Divider()
                SettingRow("Lock when leaving app", "Lock Read and the cabinet whenever MugiVerse goes to the background", lockBg) { on -> guarded(!on) { Vault.setLockOnBackground(on) } }
                Divider()
                SettingRow("Hide private notifications", "No private titles in notifications (no Read titles at all while Read is locked)", hideNotes) { on -> guarded(!on) { Vault.setHideNotifications(on) } }
            }
            if (lockAll) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GoldButton("Lock Read now", Icons.Filled.Lock) { Vault.lockRead() }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x99241309)).border(1.dp, VaultColors.Gold, RoundedCornerShape(14.dp))) {
                SettingRow("Incognito Reading", "Do not record new reading history (old history stays)", incognito) { basePrefs.incognitoMode.set(it) }
                Divider()
                SettingRow("Collector's edition books", "Slim leather books with bookmarks on the shelves (off: the ornate tomes)", collector) { Vault.setCollectorBooks(it) }
            }
            message?.let { Text(it, color = Color(0xFFF2B8A8), fontSize = 13.sp, modifier = Modifier.padding(18.dp)) }
            Text(
                "This hides books on your screen; it doesn't encrypt them. Your fingerprint and PIN stay with Android.",
                color = Color(0xFFB8A88C),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(VaultColors.Gold.copy(alpha = 0.25f)))

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VaultColors.Ivory, fontFamily = Serif, fontSize = 17.sp)
            Text(subtitle, color = Color(0xFFB8A88C), fontSize = 12.5.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = VaultColors.Ivory, checkedTrackColor = VaultColors.Gold, uncheckedThumbColor = Color(0xFFB8A88C), uncheckedTrackColor = Color(0xFF3A2A1C)),
        )
    }
}

/* ───────────── Captain's Quarters (level 1: the whole of Read) ───────────── */

/**
 * The arched cabin door from the approved artwork, laid out on the frame's 525 × 724 canvas. The
 * door sits under the arch (its edges hidden by the frame) and swings open on its left hinges;
 * warm light and [libraryPreview] show through the doorway once [unlocked].
 */
@Composable
fun QuartersDoorAnimation(
    unlocked: Boolean,
    reduce: Boolean,
    modifier: Modifier = Modifier,
    onDoor: @Composable androidx.compose.foundation.layout.BoxScope.(doorWidth: androidx.compose.ui.unit.Dp) -> Unit = {},
    libraryPreview: @Composable () -> Unit,
) {
    // 0–150 ms the lock lets go, 150–800 ms the door swings, light from 300 ms
    val rotation by animateFloatAsState(
        targetValue = if (unlocked) -85f else 0f,
        animationSpec = tween(durationMillis = if (reduce) 0 else 650, delayMillis = if (unlocked && !reduce) 150 else 0, easing = FastOutSlowInEasing),
        label = "Entrance door",
    )
    val lightAlpha by animateFloatAsState(
        targetValue = if (unlocked) 0.45f else 0f,
        animationSpec = tween(durationMillis = if (reduce) 0 else 500, delayMillis = if (unlocked && !reduce) 300 else 0),
        label = "Cabin light",
    )
    BoxWithConstraints(modifier.aspectRatio(525f / 724f, matchHeightConstraintsFirst = true)) {
        val w = maxWidth
        val h = maxHeight
        fun x(v: Float) = w * (v / 525f)
        fun y(v: Float) = h * (v / 724f)
        // the doorway (inside the arch)
        Box(
            Modifier
                .offset(x(60f), y(48f))
                .size(x(405f), y(672f))
                .clip(RoundedCornerShape(topStart = x(202f), topEnd = x(202f)))
                .background(Color(0xFF170D08)),
        ) {
            if (unlocked) libraryPreview()
        }
        if (unlocked) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.quarters_light),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = lightAlpha },
            )
        }
        // the door, hinged on its left edge
        Box(
            Modifier
                .offset(x(62f), y(52f))
                .size(x(401f), y(664f))
                .graphicsLayer {
                    rotationY = rotation
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = 24f * density
                },
        ) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.quarters_door),
                contentDescription = "Captain's Quarters door",
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            // the brass plaque's title, as real text (plaque at 30.8–70.5 % across, 63.2–71.1 % down)
            val dw = x(401f)
            val dh = y(664f)
            Box(
                Modifier
                    .offset(dw * 0.318f, dh * 0.634f)
                    .size(dw * 0.377f, dh * 0.075f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Captain's Quarters",
                    color = Color(0xFF3A210E),
                    fontFamily = Serif,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontSize = (dw.value * 0.377f / 11.5f).sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            onDoor(dw)
        }
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.quarters_frame),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** What shows through the doorway while it opens: the cabin shelves only, never real titles. */
@Composable
private fun QuartersPreview() {
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.vault_shelf_background),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().vaultShelfLighting().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x00000000), Color(0x55000000)))))
    }
}

/**
 * Captain's Quarters: covers all of Read while it's locked (MainActivity shows nothing else
 * underneath). Unlock Read asks Android's own fingerprint / screen-lock prompt first; only after
 * it succeeds does the door open. Unlocking here never opens Captain's Vault.
 */
@Composable
fun CaptainsQuartersGate() {
    remember { Vault.ensureLoaded() }
    val locked by Vault.readLockedFlow.collectAsState()
    var visible by remember { mutableStateOf(locked) }
    var opening by remember { mutableStateOf(false) }
    LaunchedEffect(locked) {
        if (locked) {
            opening = false
            visible = true
        }
    }
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduce = remember { reduceMotion(context) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val gateAlpha by animateFloatAsState(if (opening && !locked) 0f else 1f, tween(if (reduce) 0 else 150, delayMillis = if (reduce) 0 else 800), label = "gate")
    LaunchedEffect(opening, locked) {
        if (opening && !locked) {
            kotlinx.coroutines.delay(if (reduce) 0L else 960L)
            visible = false
            opening = false
        }
    }
    SecureWindow()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = gateAlpha }
            .background(Color(0xFF19120E))
            .walnut()
            // nothing underneath can be touched while the door is shut
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { }
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // the MugiVerse logo with its gold READ ribbon (no library content here)
            Box(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp).height(60.dp)) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.mugi_logo),
                    contentDescription = "MugiVerse",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.width(168.dp).height(46.dp),
                )
                Text(
                    "READ",
                    color = Color(0xFF2E180A),
                    fontFamily = Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 38.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFF3C75F), Color(0xFFB98222))))
                        .padding(horizontal = 16.dp, vertical = 1.dp),
                )
                Icon(Icons.Filled.Lock, contentDescription = "Read is locked", tint = VaultColors.Gold, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(24.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                QuartersDoorAnimation(
                    unlocked = opening && !locked,
                    reduce = reduce,
                    modifier = Modifier.widthIn(max = 520.dp),
                    onDoor = { _ ->
                        if (!opening) {
                            Column(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                GoldButton(if (busy) "Checking…" else "Unlock Read", filled = true, modifier = Modifier.widthIn(min = 170.dp)) {
                                    val activity = context.findFragmentActivity() ?: return@GoldButton
                                    if (busy || opening) return@GoldButton
                                    busy = true
                                    error = null
                                    scope.launch {
                                        // authentication first; the door only opens after it succeeds
                                        val err = Vault.unlockRead(activity)
                                        busy = false
                                        if (err == null && !Vault.readLocked()) opening = true else error = err
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "‹ Back to Watch",
                                    color = Color(0xFFF2D592),
                                    fontFamily = Serif,
                                    fontSize = 15.sp,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClickLabel = "Back to Watch") {
                                            val activity = context.findFragmentActivity() ?: return@clickable
                                            eu.kanade.tachiyomi.EmbeddedHost.openWatch?.invoke(activity)
                                        }
                                        .background(Color(0x99170D08))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    },
                    libraryPreview = { QuartersPreview() },
                )
            }
            error?.let {
                Text(
                    it,
                    color = Color(0xFFF2B8A8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC1A0E06)).padding(8.dp),
                )
            }
        }
    }
}
