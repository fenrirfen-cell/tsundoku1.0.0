package eu.kanade.presentation.browse

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimitExempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.extension.interactor.GetExtensionStores
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Checks the Read side's repositories (manga/novel extension repos and novel plugin repos) by
 * loading each one's list. A repo that doesn't load is turned off, so its extensions stop showing
 * under Available, and it's kept in a "not working" list with Turn on / Undo.
 *
 * Runs quietly when Extensions opens, at most every 12 hours; "Check again" runs it right away.
 */
object FenrirRepoHealth {
    data class Repo(val url: String, val name: String, val kind: Kind)
    enum class Kind { Extension, Plugin }

    val running = MutableStateFlow(false)
    val workingCount = MutableStateFlow(0)

    /** Repos the check turned off (still remembered after a restart). */
    val turnedOff = MutableStateFlow<List<Repo>>(emptyList())

    /** Repos turned off by the last run (for Undo). */
    val lastRun = MutableStateFlow<List<Repo>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy {
        Injekt.get<Application>().getSharedPreferences("fenrir_repo_health", android.content.Context.MODE_PRIVATE)
    }
    private val sourcePrefs by lazy { Injekt.get<SourcePreferences>() }
    private val stores by lazy { Injekt.get<GetExtensionStores>() }
    private val extensions by lazy { Injekt.get<ExtensionManager>() }
    private val plugins by lazy { Injekt.get<JsPluginManager>() }
    private val client by lazy { Injekt.get<NetworkHelper>().client.rateLimitExempt() }
    private val storeService by lazy { Injekt.get<mihon.data.extension.service.ExtensionStoreService>() }

    /** Bump to make everyone re-check once after an update (r81's check was too strict). */
    private const val CHECK_VERSION = 2
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        turnedOff.value = prefs.getStringSet("off", emptySet()).orEmpty().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 3) Repo(parts[0], parts[1], Kind.valueOf(parts[2])) else null
        }
    }

    private fun save() {
        prefs.edit().putStringSet("off", turnedOff.value.map { "${it.url}\t${it.name}\t${it.kind}" }.toSet()).apply()
    }

    /** Runs the check if it hasn't run in the last 12 hours. */
    fun checkIfDue() {
        load()
        val last = prefs.getLong("last", 0L)
        val version = prefs.getInt("version", 0)
        if (version < CHECK_VERSION || System.currentTimeMillis() - last > 12 * 60 * 60 * 1000L) checkAll()
    }

    fun checkAll() {
        load()
        if (running.value) return
        running.value = true
        scope.launch {
            try {
                val disabled = sourcePrefs.disabledExtensionRepos.get()
                val offUrls = turnedOff.value.map { it.url }.toSet()
                val storeList = runCatching { stores.get() }.getOrDefault(emptyList())
                    .filter { it.indexUrl !in disabled || it.indexUrl in offUrls }
                val storeByUrl = storeList.associateBy { it.indexUrl }
                val repos = buildList {
                    storeList.forEach { add(Repo(it.indexUrl, it.name, Kind.Extension)) }
                    plugins.repositories.value
                        .filter { it.enabled || it.url in offUrls }
                        .forEach { add(Repo(it.url, it.name, Kind.Plugin)) }
                }.distinctBy { it.url }
                val results = repos.map { repo -> async { repo to loads(repo, storeByUrl[repo.url]) } }.awaitAll()
                // Nothing loaded at all: most likely no internet, so leave every repo as it is.
                if (results.isNotEmpty() && results.none { it.second }) return@launch
                val broken = results.filter { !it.second }.map { it.first }
                val working = results.filter { it.second }.map { it.first }
                // Repos that load again come back on by themselves.
                working.filter { it.url in offUrls }.forEach { setEnabled(it, true) }
                val newlyBroken = broken.filter { it.url !in offUrls }
                newlyBroken.forEach { setEnabled(it, false) }
                turnedOff.value = broken
                lastRun.value = newlyBroken
                workingCount.value = working.size
                save()
                prefs.edit().putLong("last", System.currentTimeMillis()).putInt("version", CHECK_VERSION).apply()
                if (broken.isNotEmpty() || working.any { it.url in offUrls }) {
                    runCatching { extensions.findAvailableExtensions() }
                }
            } finally {
                running.value = false
            }
        }
    }

    /**
     * True when the repo's list loads, using the same loader as Extensions (handles JSON, gzip
     * and binary repos). Tries twice before giving up.
     */
    private suspend fun loads(repo: Repo, store: mihon.domain.extension.model.ExtensionStore?): Boolean {
        repeat(2) {
            val ok = withTimeoutOrNull(20_000) {
                when (repo.kind) {
                    Kind.Extension -> store != null &&
                        storeService.getExtensions(store).getOrNull()?.isNotEmpty() == true
                    Kind.Plugin -> runCatching {
                        client.newCall(GET(repo.url)).execute().use { response ->
                            val body = response.body?.string()?.trim().orEmpty()
                            response.isSuccessful && body.startsWith("[") && body.length > 2
                        }
                    }.getOrDefault(false)
                }
            } ?: false
            if (ok) return true
        }
        return false
    }

    private fun setEnabled(repo: Repo, enabled: Boolean) {
        when (repo.kind) {
            Kind.Extension -> sourcePrefs.disabledExtensionRepos.set(
                sourcePrefs.disabledExtensionRepos.get().let { if (enabled) it - repo.url else it + repo.url },
            )
            Kind.Plugin -> plugins.setRepositoryEnabled(repo.url, enabled)
        }
    }

    fun turnOn(repo: Repo) {
        setEnabled(repo, true)
        turnedOff.value = turnedOff.value.filterNot { it.url == repo.url }
        lastRun.value = lastRun.value.filterNot { it.url == repo.url }
        save()
        scope.launch { runCatching { extensions.findAvailableExtensions() } }
    }

    fun undo() {
        lastRun.value.forEach { setEnabled(it, true) }
        val urls = lastRun.value.map { it.url }.toSet()
        turnedOff.value = turnedOff.value.filterNot { it.url in urls }
        lastRun.value = emptyList()
        save()
        scope.launch { runCatching { extensions.findAvailableExtensions() } }
    }
}

/**
 * Top of Browse → Extensions: "Works with Tsundoku…", the repo check result, and the repos that
 * aren't working (hidden behind "👁 N not working").
 */
@Composable
fun FenrirRepoHealthCard() {
    LaunchedEffect(Unit) { FenrirRepoHealth.checkIfDue() }
    val running by FenrirRepoHealth.running.collectAsState()
    val off by FenrirRepoHealth.turnedOff.collectAsState()
    val lastRun by FenrirRepoHealth.lastRun.collectAsState()
    val working by FenrirRepoHealth.workingCount.collectAsState()
    var showOff by remember { mutableStateOf(false) }
    val accent = Color(0xFFFF4D57)
    val muted = Color(0xFF8E8E99)

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("✓", color = Color(0xFF4CD787), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                "Works with Tsundoku extensions, plugins and repos.",
                color = muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (running) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
            } else {
                Text(
                    "Check repos",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).clickable { FenrirRepoHealth.checkAll() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (lastRun.isNotEmpty() && !running) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF17171D))
                    .border(1.dp, Color(0xFF2A2A34), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Repos checked · $working working", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${lastRun.size} wasn't loading, so it's turned off and hidden",
                        color = Color(0xFFB8B8C2),
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "Undo",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { FenrirRepoHealth.undo() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        if (off.isNotEmpty()) {
            Row(
                Modifier.padding(top = 6.dp, start = 4.dp).clip(CircleShape).clickable { showOff = !showOff }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "👁 ${off.size} not working",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (showOff) "Hide" else "Tap to show", color = muted, fontSize = 12.sp)
            }
            if (showOff) {
                off.forEach { repo ->
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(repo.name, color = Color(0xFFD0D0D8), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(repo.url.substringAfter("://"), color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "Turn on",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A33))
                                .clickable { FenrirRepoHealth.turnOn(repo) }
                                .padding(horizontal = 11.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
