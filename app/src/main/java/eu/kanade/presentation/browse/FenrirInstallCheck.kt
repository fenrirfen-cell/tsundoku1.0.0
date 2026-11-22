package eu.kanade.presentation.browse

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Before installing a novel plugin: quickly try its website. If it doesn't answer, ask
 * "may not work · Install anyway?" (Cloudflare-style blocks count as "can't tell", so no warning).
 */
object FenrirInstallCheck {
    data class Ask(val name: String, val reason: String, val answer: CompletableDeferred<Boolean>)

    val pending = MutableStateFlow<Ask?>(null)

    private val client by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** Returns true to go ahead with the install. */
    suspend fun confirmIfDown(name: String, site: String?): Boolean {
        val reason = probe(site) ?: return true
        val ask = Ask(name, reason, CompletableDeferred())
        pending.value = ask
        return try {
            ask.answer.await()
        } finally {
            if (pending.value === ask) pending.value = null
        }
    }

    private suspend fun probe(site: String?): String? = withContext(Dispatchers.IO) {
        val url = site?.trim()?.takeIf { it.startsWith("http") } ?: return@withContext null
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                when (r.code) {
                    404, 410 -> "Its website says the page is gone (${r.code})"
                    500, 502, 504 -> "Its website is having problems (${r.code})"
                    else -> null
                }
            }
        } catch (e: Exception) {
            "Its website didn't answer"
        }
    }
}

/** Shows the warning while an install is waiting for an answer. Put once on the Read side. */
@Composable
fun FenrirInstallCheckDialog() {
    val ask by FenrirInstallCheck.pending.collectAsState()
    val a = ask ?: return
    AlertDialog(
        onDismissRequest = { a.answer.complete(false) },
        containerColor = Color(0xFF1A1A21),
        title = { Text("${a.name} may not work", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "${a.reason}. You can still install it, but it probably won't load anything.",
                color = Color(0xFFB8B8C2),
            )
        },
        confirmButton = {
            TextButton(onClick = { a.answer.complete(true) }) { Text("Install anyway", color = Color(0xFFFF6B73)) }
        },
        dismissButton = {
            TextButton(onClick = { a.answer.complete(false) }) { Text("Cancel", color = Color.White) }
        },
    )
}
