package eu.kanade.tachiyomi.data.notification

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/**
 * Fenrir Read: says new chapters out loud after a library update ("2 new chapters: Solo Leveling
 * chapter 201, and Omniscient Reader chapter 245"). Off by default; stays quiet when the phone is
 * on silent, vibrate or Do Not Disturb.
 */
object SpokenChapterAlerts {

    fun enabledPreference(): Preference<Boolean> =
        Injekt.get<PreferenceStore>().getBoolean("fenrir_speak_new_chapters", false)

    fun announce(context: Context, updates: List<Pair<Manga, Array<Chapter>>>, hideTitles: Boolean) {
        if (updates.isEmpty() || !enabledPreference().get() || !soundAllowed(context)) return
        speak(context, sentence(updates, hideTitles))
    }

    fun test(context: Context) {
        speak(context, "2 new chapters: Solo Leveling chapter 201, and Omniscient Reader chapter 245.")
    }

    private fun sentence(updates: List<Pair<Manga, Array<Chapter>>>, hideTitles: Boolean): String {
        val total = updates.sumOf { it.second.size }
        val countPart = if (total == 1) "New chapter" else "$total new chapters"
        if (hideTitles) return "$countPart in your library."
        val parts = updates.take(3).map { (manga, chapters) ->
            val latest = chapters.maxByOrNull { it.chapterNumber }
            val number = latest?.chapterNumber?.takeIf { it >= 0 }?.let { n ->
                if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
            }
            if (number != null) "${manga.title} chapter $number" else manga.title
        }
        val list = when (parts.size) {
            1 -> parts[0]
            2 -> "${parts[0]}, and ${parts[1]}"
            else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
        }
        val more = if (updates.size > 3) ", and ${updates.size - 3} more" else ""
        return "$countPart: $list$more."
    }

    private fun soundAllowed(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager != null && manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return false
        return true
    }

    private fun speak(context: Context, text: String) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                tts.shutdown()
                return@TextToSpeech
            }
            runCatching { tts.language = Locale.getDefault() }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = tts.shutdown()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = tts.shutdown()
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fenrir-chapters")
        }
    }
}
