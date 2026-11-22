package eu.kanade.presentation.browse

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Read side "Check sources": opens each manga/novel source's Popular page. Sources that give
 * nothing back are turned off (hidden from Browse); Undo or Turn on brings them back.
 */
/**
 * Checks each installed source (extension or novel plugin) by loading its popular list.
 *
 * Runs on its own about once a day, quietly. A source that fails two checks in a row is turned
 * off and listed under "Not working" with the reason (site down, Cloudflare…), Turn on and Undo.
 * Turned-off sources keep being re-checked and come back on by themselves once they work again.
 * "Check sources" runs it right away (then one failure is enough).
 */
object FenrirReadHealth {
    enum class Status { Checking, Working, Slow, Broken }
    data class Off(val id: Long, val name: String, val reason: String, val auto: Boolean)

    val status = MutableStateFlow<Map<Long, Status>>(emptyMap())
    val running = MutableStateFlow(false)

    /** Sources turned off by the checks (kept after a restart). */
    val turnedOff = MutableStateFlow<List<Off>>(emptyList())

    /** Turned off by the last run, for Undo. */
    val lastRun = MutableStateFlow<List<Off>>(emptyList())

    /** Names that started working again in the last run. */
    val cameBack = MutableStateFlow<List<String>>(emptyList())
    val checkedCount = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { Injekt.get<SourcePreferences>() }
    private val sources by lazy { Injekt.get<SourceManager>() }
    private val store by lazy {
        Injekt.get<android.app.Application>().getSharedPreferences("fenrir_source_health", android.content.Context.MODE_PRIVATE)
    }
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        turnedOff.value = store.getStringSet("off", emptySet()).orEmpty().mapNotNull { line ->
            val p = line.split('\t')
            val id = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            Off(id, p.getOrElse(1) { "Source" }, p.getOrElse(2) { "Not loading" }, p.getOrElse(3) { "1" } == "1")
        }
    }

    private fun save() {
        store.edit().putStringSet("off", turnedOff.value.map { "${it.id}\t${it.name}\t${it.reason}\t${if (it.auto) 1 else 0}" }.toSet()).apply()
    }

    private fun strikes(id: Long) = store.getInt("strike_$id", 0)
    private fun setStrikes(id: Long, n: Int) = store.edit().putInt("strike_$id", n).apply()

    /** The daily quiet check: every enabled source plus the ones it turned off. */
    fun checkIfDue() {
        load()
        val last = store.getLong("last", 0L)
        if (System.currentTimeMillis() - last < 20 * 60 * 60 * 1000L) return
        scope.launch {
            val disabled = prefs.disabledSources.get()
            val offIds = turnedOff.value.map { it.id }.toSet()
            val list = sources.getAll()
                .filter { it is CatalogueSource && it.id != 0L }
                .filter { it.id.toString() !in disabled || it.id in offIds }
                .map { Source(id = it.id, lang = it.lang, name = it.name, supportsLatest = false, isStub = false) }
            if (list.isNotEmpty()) run(list, quiet = true)
        }
    }

    fun checkAll(list: List<Source>) {
        load()
        if (running.value || list.isEmpty()) return
        scope.launch { run(list, quiet = false) }
    }

    private suspend fun run(list: List<Source>, quiet: Boolean) {
        if (running.value) return
        running.value = true
        try {
            status.update { old -> old + list.associate { it.id to Status.Checking } }
            val permits = Semaphore(4)
            val results = coroutineScope {
                list.map { s -> async { permits.withPermit { check(s.id).let { r -> Triple(s, r.first, r.second) } } } }.awaitAll()
            }
            // Nothing loaded at all: most likely no internet, so change nothing.
            if (results.isNotEmpty() && results.none { it.second != Status.Broken }) {
                status.update { old -> old - list.map { it.id }.toSet() }
                return
            }
            status.update { old -> old + results.associate { it.first.id to it.second } }
            val offIds = turnedOff.value.associateBy { it.id }
            val newlyOff = mutableListOf<Off>()
            val back = mutableListOf<String>()
            results.forEach { (s, st, reasonText) ->
                if (st == Status.Broken) {
                    val n = strikes(s.id) + 1
                    setStrikes(s.id, n)
                    if (s.id !in offIds && (!quiet || n >= 2)) {
                        newlyOff += Off(s.id, s.name, reasonText ?: "Not loading", auto = true)
                    }
                } else {
                    setStrikes(s.id, 0)
                    val was = offIds[s.id]
                    if (was != null && was.auto) {
                        prefs.disabledSources.set(prefs.disabledSources.get() - s.id.toString())
                        back += s.name
                    }
                }
            }
            if (newlyOff.isNotEmpty()) {
                prefs.disabledSources.set(prefs.disabledSources.get() + newlyOff.map { it.id.toString() })
            }
            val backIds = results.filter { it.second != Status.Broken }.map { it.first.id }.toSet()
            turnedOff.value = turnedOff.value.filterNot { it.id in backIds && it.auto } + newlyOff
            lastRun.value = newlyOff
            cameBack.value = back
            checkedCount.value = results.size
            save()
            store.edit().putLong("last", System.currentTimeMillis()).apply()
        } finally {
            running.value = false
        }
    }

    private val reasons = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private suspend fun check(id: Long): Pair<Status, String?> {
        val source = sources.get(id) as? CatalogueSource ?: return Status.Broken to "Not installed"
        val start = System.currentTimeMillis()
        var error: Throwable? = null
        val ok = withTimeoutOrNull(20_000) {
            runCatching { source.getPopularManga(1).mangas.isNotEmpty() }
                .onFailure { error = it }
                .getOrDefault(false)
        }
        val seconds = (System.currentTimeMillis() - start) / 1000.0
        if (ok != true) {
            val reason = reasonFor(error, timedOut = ok == null)
            reasons[id] = reason
            return Status.Broken to reason
        }
        reasons.remove(id)
        return (if (seconds > 8) Status.Slow else Status.Working) to null
    }

    private fun reasonFor(e: Throwable?, timedOut: Boolean): String {
        val msg = (e?.message ?: "").lowercase()
        val code = (e as? eu.kanade.tachiyomi.network.HttpException)?.code
        return when {
            timedOut -> "Site too slow"
            "cloudflare" in msg || code == 403 || code == 503 -> "Cloudflare"
            e is java.net.UnknownHostException || e is java.net.ConnectException -> "Site down"
            code == 404 || code == 410 -> "Page gone"
            code != null && code >= 500 -> "Site down"
            code == 401 -> "Needs login"
            e == null -> "No results"
            else -> "Not loading"
        }
    }

    /** The source's website, for "Fix in browser". */
    fun siteOf(id: Long): String? {
        val s = sources.get(id) ?: return null
        return (s as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl
            ?: (s as? eu.kanade.tachiyomi.jsplugin.source.JsSource)?.baseUrl?.takeIf { it.isNotBlank() }
    }

    fun turnOn(id: Long) {
        load()
        prefs.disabledSources.set(prefs.disabledSources.get() - id.toString())
        setStrikes(id, 0)
        turnedOff.update { list -> list.filterNot { it.id == id } }
        lastRun.update { list -> list.filterNot { it.id == id } }
        status.update { it - id }
        save()
    }

    fun undo() {
        val ids = lastRun.value.map { it.id.toString() }.toSet()
        prefs.disabledSources.set(prefs.disabledSources.get() - ids)
        lastRun.value.forEach { setStrikes(it.id, 0); status.update { m -> m - it.id } }
        turnedOff.update { list -> list.filterNot { it.id.toString() in ids } }
        lastRun.value = emptyList()
        save()
    }

    fun dismiss() {
        lastRun.value = emptyList()
        cameBack.value = emptyList()
        checkedCount.value = 0
    }
}

/** Card at the top of Browse → Sources: "Check sources", then the results with Undo / Turn on. */
@Composable
fun FenrirCheckSourcesCard(sources: List<Source>) {
    LaunchedEffect(Unit) { FenrirReadHealth.checkIfDue() }
    val running by FenrirReadHealth.running.collectAsState()
    val off by FenrirReadHealth.lastRun.collectAsState()
    val checked by FenrirReadHealth.checkedCount.collectAsState()
    val accent = Color(0xFFFF4D57)
    if (checked == 0 && !running) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sources are checked daily; broken ones turn off by themselves.", color = Color(0xFF8E8E99), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                "Check now",
                color = accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { FenrirReadHealth.checkAll(sources) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(0f to accent.copy(alpha = 0.18f), 0.6f to Color(0xFF15151B)))
            .border(1.dp, Color(0xFF2A2A34), RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                Spacer(Modifier.width(10.dp))
                Text("Checking sources…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Column(Modifier.weight(1f)) {
                    Text("Checked $checked sources", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (off.isEmpty()) "All working" else "${off.size} not working, turned off",
                        color = Color(0xFFB8B8C2),
                        fontSize = 12.sp,
                    )
                }
                if (off.isNotEmpty()) {
                    Text(
                        "Undo",
                        color = accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).clickable { FenrirReadHealth.undo() }.padding(6.dp),
                    )
                }
                Text(
                    "✕",
                    color = Color(0xFF8E8E99),
                    fontSize = 13.sp,
                    modifier = Modifier.clip(CircleShape).clickable { FenrirReadHealth.dismiss() }.padding(6.dp),
                )
            }
        }
        off.forEach { o ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("${o.name} · ${o.reason}", color = Color(0xFFD0D0D8), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    "Turn on",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A33))
                        .clickable { FenrirReadHealth.turnOn(o.id) }
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Small health dot for a source icon (null when not checked). */
@Composable
fun fenrirReadHealthColor(id: Long): Color? {
    val map by FenrirReadHealth.status.collectAsState()
    return when (map[id]) {
        FenrirReadHealth.Status.Working -> Color(0xFF4CD787)
        FenrirReadHealth.Status.Slow -> Color(0xFFFFB547)
        FenrirReadHealth.Status.Broken -> Color(0xFFFF4D57)
        FenrirReadHealth.Status.Checking -> Color(0xFF6E6E78)
        null -> null
    }
}
