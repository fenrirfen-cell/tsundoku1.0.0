package eu.kanade.presentation.library.components

import androidx.compose.foundation.BorderStroke
import uy.kohesive.injekt.api.get
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fenrir: how the Read-side library looks. Chosen from FENRIR READ ▾ → Library look. */
object FenrirLibraryLook {
    enum class Look(val label: String, val blurb: String) {
        Bookshelf("Bookshelf", "Wooden bookcase, ornate tomes, decorations"),
        Luxury("Luxury Collection", "Black-and-gold glass cabinet, collector's editions"),
        Regular("Regular", "Plain dark grid, no furniture"),
    }

    private const val KEY = "fenrir_library_look"
    private fun prefs() = runCatching {
        uy.kohesive.injekt.Injekt.get<android.app.Application>().getSharedPreferences("fenrir_ui", android.content.Context.MODE_PRIVATE)
    }.getOrNull()

    val current = mutableStateOf(
        prefs()?.getString(KEY, null)?.let { s -> Look.entries.firstOrNull { it.name == s } } ?: Look.Bookshelf,
    )

    fun set(look: Look) {
        current.value = look
        prefs()?.edit()?.putString(KEY, look.name)?.apply()
    }

    /** Shown from the FENRIR READ menu. */
    val pickerOpen = mutableStateOf(false)
}

/** Sizes the Luxury look shares between the header, the page background and the shelves. */
internal data class LuxGeom(val wide: Boolean, val tower: Dp, val frame: Dp) {
    /** Inner side padding of the collection (towers + frame + a little air). */
    val side: Dp get() = tower + frame + 4.dp
}

internal val LocalLuxGeom = compositionLocalOf { LuxGeom(false, 0.dp, 6.dp) }

@Composable
internal fun FenrirLibraryLookPicker() {
    if (!FenrirLibraryLook.pickerOpen.value) return
    val now = FenrirLibraryLook.current.value
    AlertDialog(
        onDismissRequest = { FenrirLibraryLook.pickerOpen.value = false },
        title = { Text("Library look", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FenrirLibraryLook.Look.entries.forEach { look ->
                    val on = look == now
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (on) Color(0x33FF4D57) else Color(0x14FFFFFF),
                        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) Color(0xFFFF4D57) else Color(0x33FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                FenrirLibraryLook.set(look)
                                FenrirLibraryLook.pickerOpen.value = false
                            },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(look.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(look.blurb, color = Color(0xFFB5B5C0), fontSize = 12.sp)
                            }
                            if (on) {
                                Spacer(Modifier.width(8.dp))
                                Text("✓", color = Color.White, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).padding(4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { FenrirLibraryLook.pickerOpen.value = false }) { Text("Close") }
        },
    )
}
