package eu.kanade.tachiyomi.util.storage

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * File provider authority for the app this code is actually running in.
 * BuildConfig.APPLICATION_ID is Tsundoku's own id, which is wrong when embedded in Fenrir Read
 * (and for its debug/fast builds), so use the real package name instead.
 */
object FileProviderAuthority {
    val value: String
        get() = Injekt.get<Application>().packageName + ".provider"
}