package eu.kanade.tachiyomi

import android.app.Activity
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Hooks for when Tsundoku runs inside another app (the Nuvio "Read" mode).
 *
 * Everything here is null in a standalone Tsundoku build, so nothing changes there.
 * A host app sets these once from its Application.onCreate.
 */
object EmbeddedHost {

    /**
     * Switches from Tsundoku back to the host app's main screen. When set, the home screen
     * shows the Watch / Read switch at the top.
     */
    @Volatile
    var openWatch: ((Activity) -> Unit)? = null

    /**
     * Opens the host app's Ani-drama browser. When set, the switch shows three options:
     * Watch / Ani-drama / Read.
     */
    @Volatile
    var openAniDrama: ((Activity) -> Unit)? = null

    /** Label for the host side of the switch. */
    @Volatile
    var watchLabel: String = "Watch"

    /** Label for the Tsundoku side of the switch. */
    @Volatile
    var readLabel: String = "Read"

    /**
     * Fenrir style (Zangetsu-like look) from the host: when it returns true, the Read side uses
     * the Fenrir colors, the wordmark + mode pill header and the pill bottom bar.
     */
    @Volatile
    var fenrirStyle: (() -> Boolean)? = null

    val isFenrirStyle: Boolean get() = openWatch != null && fenrirStyle?.invoke() == true

    /** Installed manga extensions (for the host's Settings → Sources). */
    fun mangaExtensionCount(): Int = runCatching {
        Injekt.get<eu.kanade.tachiyomi.extension.ExtensionManager>().installedExtensionsFlow.value.size
    }.getOrDefault(0)

    /** Installed novel (JS) plugins (for the host's Settings → Sources). */
    fun novelPluginCount(): Int = runCatching {
        Injekt.get<eu.kanade.tachiyomi.jsplugin.JsPluginManager>().installedPlugins.value.size
    }.getOrDefault(0)
}
