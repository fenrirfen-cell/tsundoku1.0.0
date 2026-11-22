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
 * Repos are no longer turned off by a check (one dead site shouldn't hide hundreds of working
 * extensions). This puts back, once, any repo the old r81/r82 check had turned off.
 */
object FenrirRepoHealth {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy {
        Injekt.get<Application>().getSharedPreferences("fenrir_repo_health", android.content.Context.MODE_PRIVATE)
    }
    private val sourcePrefs by lazy { Injekt.get<SourcePreferences>() }
    private val extensions by lazy { Injekt.get<ExtensionManager>() }
    private val plugins by lazy { Injekt.get<JsPluginManager>() }
    private var done = false

    fun restoreOnce() {
        if (done) return
        done = true
        if (prefs.getBoolean("restored_v3", false)) return
        scope.launch {
            val off = prefs.getStringSet("off", emptySet()).orEmpty().mapNotNull { line ->
                line.split('\t').takeIf { it.size == 3 }
            }
            off.forEach { (url, _, kind) ->
                if (kind == "Plugin") {
                    runCatching { plugins.setRepositoryEnabled(url, true) }
                } else {
                    sourcePrefs.disabledExtensionRepos.set(sourcePrefs.disabledExtensionRepos.get() - url)
                }
            }
            prefs.edit().remove("off").putBoolean("restored_v3", true).apply()
            if (off.isNotEmpty()) runCatching { extensions.findAvailableExtensions() }
        }
    }
}

/**
 * Top of Browse → Extensions: "Works with Tsundoku…", the Update all progress, and the sources
 * that aren't working (with the reason, Turn on and Fix in browser).
 */
@Composable
fun FenrirRepoHealthCard() {
    LaunchedEffect(Unit) {
        FenrirRepoHealth.restoreOnce()
        FenrirReadHealth.checkIfDue()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val running by FenrirReadHealth.running.collectAsState()
    val off by FenrirReadHealth.turnedOff.collectAsState()
    val back by FenrirReadHealth.cameBack.collectAsState()
    val accent = Color(0xFFFF4D57)
    val muted = Color(0xFF8E8E99)

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
            Text("✓", color = Color(0xFF4CD787), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("Works with Tsundoku extensions, plugins and repos.", color = muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
        }
        FenrirUpdateQueueCard()
        if (back.isNotEmpty()) {
            Text(
                "Working again: ${back.joinToString()}",
                color = Color(0xFF8FE3B4),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        if (off.isNotEmpty()) {
            Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("NOT WORKING · ${off.size}", color = Color(0xFFC9C9D2), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                if (!running) {
                    Text(
                        "Check again",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).clickable {
                            FenrirReadHealth.checkAll(off.map { tachiyomi.domain.source.model.Source(it.id, "", it.name, false, false) })
                        }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF17171D))
                    .border(1.dp, Color(0xFF2A2A34), RoundedCornerShape(18.dp)),
            ) {
                off.forEach { o ->
                    val cloudflare = o.reason == "Cloudflare"
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (cloudflare) "Cloudflare · tap Fix to pass it in the browser" else "${o.reason} · turned off, re-checked daily",
                                color = if (cloudflare) Color(0xFFFFB547) else Color(0xFFFF8A91),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val site = remember(o.id) { FenrirReadHealth.siteOf(o.id) }
                        if (cloudflare && site != null) {
                            Text(
                                "Fix",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(accent)
                                    .clickable {
                                        FenrirReadHealth.turnOn(o.id)
                                        context.startActivity(
                                            eu.kanade.tachiyomi.ui.webview.WebViewActivity.newIntent(context, site, o.id, o.name),
                                        )
                                    }
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                            )
                        } else {
                            Text(
                                "Turn on",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2A33))
                                    .clickable { FenrirReadHealth.turnOn(o.id) }
                                    .padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
