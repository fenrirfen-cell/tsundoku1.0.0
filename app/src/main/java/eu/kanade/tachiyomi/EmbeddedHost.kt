package eu.kanade.tachiyomi

import android.app.Activity

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
}
