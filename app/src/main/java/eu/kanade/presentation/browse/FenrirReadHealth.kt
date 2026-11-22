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
object FenrirReadHealth {
    enum class Status { Checking, Working, Slow, Broken }

    val status = MutableStateFlow<Map<Long, Status>>(emptyMap())
    val running = MutableStateFlow(false)

    /** Sources the last check turned off (id to name). */
    val turnedOff = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val checkedCount = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { Injekt.get<SourcePreferences>() }
    private val sources by lazy { Injekt.get<SourceManager>() }

    fun checkAll(list: List<Source>) {
        if (running.value || list.isEmpty()) return
        running.value = true
        scope.launch {
            try {
                status.update { old -> old + list.associate { it.id to Status.Checking } }
                val permits = Semaphore(4)
                val results = list.map { s ->
                    async { permits.withPermit { s to check(s.id) } }
                }.awaitAll()
                status.update { old -> old + results.associate { it.first.id to it.second } }
                val broken = results.filter { it.second == Status.Broken }.map { it.first.id to it.first.name }
                if (broken.isNotEmpty()) {
                    prefs.disabledSources.set(prefs.disabledSources.get() + broken.map { it.first.toString() })
                }
                turnedOff.value = broken
                checkedCount.value = results.size
            } finally {
                running.value = false
            }
        }
    }

    private suspend fun check(id: Long): Status {
        val source = sources.get(id) as? CatalogueSource ?: return Status.Broken
        val start = System.currentTimeMillis()
        val ok = withTimeoutOrNull(20_000) {
            runCatching { source.getPopularManga(1).mangas.isNotEmpty() }.getOrDefault(false)
        } ?: false
        val seconds = (System.currentTimeMillis() - start) / 1000.0
        return when {
            !ok -> Status.Broken
            seconds > 8 -> Status.Slow
            else -> Status.Working
        }
    }

    fun turnOn(id: Long) {
        prefs.disabledSources.set(prefs.disabledSources.get() - id.toString())
        turnedOff.update { list -> list.filterNot { it.first == id } }
        status.update { it - id }
    }

    fun undo() {
        val ids = turnedOff.value.map { it.first.toString() }.toSet()
        prefs.disabledSources.set(prefs.disabledSources.get() - ids)
        turnedOff.value.forEach { (id, _) -> status.update { it - id } }
        turnedOff.value = emptyList()
    }

    fun dismiss() {
        turnedOff.value = emptyList()
        checkedCount.value = 0
    }
}

/** Card at the top of Browse → Sources: "Check sources", then the results with Undo / Turn on. */
@Composable
fun FenrirCheckSourcesCard(sources: List<Source>) {
    val running by FenrirReadHealth.running.collectAsState()
    val off by FenrirReadHealth.turnedOff.collectAsState()
    val checked by FenrirReadHealth.checkedCount.collectAsState()
    val accent = Color(0xFFFF4D57)
    if (checked == 0 && !running) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Some sources stop working over time.", color = Color(0xFF8E8E99), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                "Check sources",
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
                Text("Checking ${sources.size} sources…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Column(Modifier.weight(1f)) {
                    Text("Checked $checked sources", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (off.isEmpty()) "All working" else "${checked - off.size} working · ${off.size} not working, turned off",
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
        off.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(name, color = Color(0xFFD0D0D8), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    "Turn on",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A33))
                        .clickable { FenrirReadHealth.turnOn(id) }
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                )
            }
        }
        if (!running) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Check again",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF22222A))
                        .clickable { FenrirReadHealth.checkAll(sources) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
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
