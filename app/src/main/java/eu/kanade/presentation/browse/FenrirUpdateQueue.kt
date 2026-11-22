package eu.kanade.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Update all, one at a time: "Updating 3 of 7 · MangaBat", Stop, and Retry for the ones that
 * failed. Running them all at once used to pile up behind Android's installer and look frozen.
 */
object FenrirUpdateQueue {
    enum class State { Waiting, Working, Done, Failed }
    data class Item(val key: String, val name: String, val state: State)

    val items = MutableStateFlow<List<Item>>(emptyList())
    val running = MutableStateFlow(false)

    private var job: Job? = null
    private val work = HashMap<String, suspend () -> Boolean>()
    // Its own scope, so leaving the screen doesn't stop the queue.
    private val appScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Queues [jobs] (key, name, work) and runs them one by one. Already-queued keys are skipped. */
    fun run(jobs: List<Triple<String, String, suspend () -> Boolean>>) {
        val known = items.value.filter { it.state == State.Waiting || it.state == State.Working }.map { it.key }.toSet()
        val fresh = jobs.filter { it.first !in known }
        if (fresh.isEmpty()) return
        // Finished entries from an earlier run are cleared when a new run starts.
        if (!running.value) items.value = items.value.filter { it.state == State.Failed && fresh.none { f -> f.first == it.key } }
        fresh.forEach { (key, _, w) -> work[key] = w }
        items.update { old -> old.filterNot { o -> fresh.any { it.first == o.key } } + fresh.map { Item(it.first, it.second, State.Waiting) } }
        start()
    }

    private fun start() {
        if (running.value) return
        running.value = true
        job = appScope.launch {
            try {
                while (true) {
                    val next = items.value.firstOrNull { it.state == State.Waiting } ?: break
                    set(next.key, State.Working)
                    val ok = runCatching { work[next.key]?.invoke() ?: false }.getOrDefault(false)
                    set(next.key, if (ok) State.Done else State.Failed)
                }
            } finally {
                running.value = false
                // Anything still marked Working after a Stop goes back to Failed so it can be retried.
                items.update { list -> list.map { if (it.state == State.Working || it.state == State.Waiting) it.copy(state = State.Failed) else it } }
            }
        }
    }

    private fun set(key: String, state: State) {
        items.update { list -> list.map { if (it.key == key) it.copy(state = state) else it } }
    }

    fun stop() {
        job?.cancel()
    }

    fun retry(key: String) {
        if (work[key] == null) return
        set(key, State.Waiting)
        start()
    }

    fun dismiss() {
        if (running.value) return
        items.value = emptyList()
        work.clear()
    }
}

@Composable
fun FenrirUpdateQueueCard() {
    val items by FenrirUpdateQueue.items.collectAsState()
    val running by FenrirUpdateQueue.running.collectAsState()
    if (items.isEmpty()) return
    val accent = Color(0xFFFF4D57)
    val done = items.count { it.state == FenrirUpdateQueue.State.Done }
    val failed = items.filter { it.state == FenrirUpdateQueue.State.Failed }
    val current = items.firstOrNull { it.state == FenrirUpdateQueue.State.Working }
    val position = done + failed.size + if (current != null) 1 else 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF17171D))
            .border(1.dp, Color(0xFF2A2A34), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        running -> "Updating $position of ${items.size}"
                        failed.isEmpty() -> "All ${items.size} updated"
                        else -> "$done updated · ${failed.size} failed"
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (running) "${current?.name ?: "…"} · one at a time so nothing gets stuck" else "Tap Retry on any that failed",
                    color = Color(0xFF9A9AA6),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (running) "Stop" else "✕",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF26262E))
                    .clickable { if (running) FenrirUpdateQueue.stop() else FenrirUpdateQueue.dismiss() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        if (running) {
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF26262E))) {
                Box(
                    Modifier
                        .fillMaxWidth(((position - 1).coerceAtLeast(0) + 0.5f) / items.size.coerceAtLeast(1))
                        .height(6.dp)
                        .background(accent),
                )
            }
        }
        failed.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(item.name, color = Color(0xFFD0D0D8), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    "Retry",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent)
                        .clickable { FenrirUpdateQueue.retry(item.key) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        if (running) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = accent)
                Spacer(Modifier.width(6.dp))
                Text("$done done · ${items.count { it.state == FenrirUpdateQueue.State.Waiting }} waiting", color = Color(0xFF8E8E99), fontSize = 11.5.sp)
            }
        }
    }
}
