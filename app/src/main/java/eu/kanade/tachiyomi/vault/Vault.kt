package eu.kanade.tachiyomi.vault

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.suspendCancellableCoroutine
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.resume

/**
 * MugiVerse Captain's Vault (the Private Library).
 *
 * Books moved here keep their ids, progress, downloads and categories: only a private flag is
 * kept (in the app's own preferences). While the vault is locked, private books are left out of
 * every list (library, history, updates, Continue, stats, Surprise Me, notifications). The
 * unlock lives in memory only and ends when the app goes to the background.
 *
 * This is privacy on the screen, not encryption.
 */
object Vault {
    private const val PREFS = "mugi_vault"
    private val prefs: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _privateIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Every private book (the regular library always leaves these out). */
    val privateIds: StateFlow<Set<Long>> = _privateIds.asStateFlow()

    /** Level 2, Captain's Vault: this session's own grant (memory only). */
    private val _vaultGrant = MutableStateFlow(false)

    /** Level 1, Captain's Quarters: this session's grant for the whole Read mode (memory only). */
    private val _quartersGrant = MutableStateFlow(false)

    private val _unlocked = MutableStateFlow(false)
    /** True when the private books may be shown: the vault was unlocked, or it isn't protected. */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _readLocked = MutableStateFlow(false)
    /** True while Captain's Quarters is on and not unlocked: nothing from Read may be shown. */
    val readLockedFlow: StateFlow<Boolean> = _readLocked.asStateFlow()

    /** Level 1: protect the entire Read mode (Captain's Quarters). */
    val lockEntireRead = MutableStateFlow(false)

    /** Level 2: ask before opening the Private Library (Captain's Vault). */
    val protectPrivateLibrary = MutableStateFlow(false)
    val lockOnBackground = MutableStateFlow(true)
    val hideNotifications = MutableStateFlow(true)

    /** Collector's-edition books on the shelves (off = the ornate tomes, as before). */
    val collectorBooks = MutableStateFlow(true)

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            _privateIds.value = prefs.getStringSet("private_ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
            lockEntireRead.value = prefs.getBoolean("lock_entire_read", false)
            lockOnBackground.value = prefs.getBoolean("lock_on_background", true)
            hideNotifications.value = prefs.getBoolean("hide_notifications", true)
            collectorBooks.value = prefs.getBoolean("collector_books", true)
            // Before the two levels existed the vault always asked: keep that for anyone with private books.
            protectPrivateLibrary.value = if (prefs.contains("protect_private_library")) {
                prefs.getBoolean("protect_private_library", false)
            } else {
                _privateIds.value.isNotEmpty()
            }
        }
        refresh()
    }

    /** Recomputes the derived states from the settings and the two in-memory grants. */
    /** Set by the host app: told whenever Read becomes locked or unlocked (to drop or reload reading elsewhere). */
    var onReadLockChanged: ((locked: Boolean) -> Unit)? = null

    private fun refresh() {
        val quartersLocked = lockEntireRead.value && !_quartersGrant.value
        val changed = _readLocked.value != quartersLocked
        _readLocked.value = quartersLocked
        if (changed) runCatching { onReadLockChanged?.invoke(quartersLocked) }
        _unlocked.value = !quartersLocked && (_vaultGrant.value || !protectPrivateLibrary.value)
    }

    /** True while the whole Read mode must stay closed (checked before anything from Read is shown). */
    fun readLocked(): Boolean {
        ensureLoaded()
        return _readLocked.value
    }

    /** Ids to hide right now (all private books while the vault is locked, none while it's open). */
    fun hiddenIds(): Set<Long> {
        ensureLoaded()
        return if (_unlocked.value) emptySet() else _privateIds.value
    }

    /** Emits the ids to hide whenever the private list or the lock changes. */
    val hiddenFlow by lazy {
        ensureLoaded()
        combine(_privateIds, _unlocked) { ids, open -> if (open) emptySet() else ids }
    }

    fun isPrivate(mangaId: Long): Boolean {
        ensureLoaded()
        return mangaId in _privateIds.value
    }

    fun isHidden(mangaId: Long): Boolean = mangaId in hiddenIds()

    fun add(ids: Collection<Long>) {
        ensureLoaded()
        save(_privateIds.value + ids)
    }

    fun remove(ids: Collection<Long>) {
        ensureLoaded()
        save(_privateIds.value - ids.toSet())
    }

    private fun save(ids: Set<Long>) {
        _privateIds.value = ids
        prefs.edit().putStringSet("private_ids", ids.map { it.toString() }.toSet()).apply()
    }

    fun setLockEntireRead(on: Boolean) {
        lockEntireRead.value = on
        prefs.edit().putBoolean("lock_entire_read", on).apply()
        // switched on from inside Read: you're already in, so this session stays open
        if (on) _quartersGrant.value = true
        refresh()
    }

    fun setProtectPrivateLibrary(on: Boolean) {
        protectPrivateLibrary.value = on
        prefs.edit().putBoolean("protect_private_library", on).apply()
        if (on) _vaultGrant.value = false
        refresh()
    }
    fun setLockOnBackground(on: Boolean) { lockOnBackground.value = on; prefs.edit().putBoolean("lock_on_background", on).apply() }
    fun setHideNotifications(on: Boolean) { hideNotifications.value = on; prefs.edit().putBoolean("hide_notifications", on).apply() }

    fun setCollectorBooks(on: Boolean) { collectorBooks.value = on; prefs.edit().putBoolean("collector_books", on).apply() }

    /** Locks Captain's Vault only (Read stays open). */
    fun lock() {
        _vaultGrant.value = false
        refresh()
    }

    /** Locks Captain's Quarters, and with it Captain's Vault. */
    fun lockRead() {
        _quartersGrant.value = false
        _vaultGrant.value = false
        refresh()
    }

    /** Called when the whole app goes to the background. */
    fun onAppBackground() {
        ensureLoaded()
        if (lockOnBackground.value) lockRead()
    }

    /** Whether notifications should leave out titles because Read itself is locked. */
    fun hideReadInNotifications(): Boolean {
        ensureLoaded()
        return lockEntireRead.value && hideNotifications.value
    }

    @Volatile private var authenticating = false

    /**
     * Shows the phone's own fingerprint / PIN / pattern prompt. No screen lock set up means no
     * unlock (it never lets you through without one). Returns null on success, else a message.
     */
    suspend fun authenticate(activity: FragmentActivity, title: String = "Unlock Private Library"): String? {
        if (!activity.isAuthenticationSupported()) {
            return "Set up a screen lock (fingerprint, PIN or pattern) in your phone's settings first."
        }
        if (authenticating) return "Already asking"
        authenticating = true
        return try {
            suspendCancellableCoroutine { cont ->
                activity.startAuthentication(
                    title,
                    "Confirm it's you",
                    callback = object : AuthenticatorUtil.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            activity: FragmentActivity?,
                            result: androidx.biometric.BiometricPrompt.AuthenticationResult,
                        ) {
                            super.onAuthenticationSucceeded(activity, result)
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onAuthenticationError(
                            activity: FragmentActivity?,
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            super.onAuthenticationError(activity, errorCode, errString)
                            if (cont.isActive) cont.resume(errString.toString().ifBlank { "Not unlocked" })
                        }
                    },
                )
            }
        } finally {
            authenticating = false
        }
    }

    /** For the Watch side, which only knows it has an Activity. */
    suspend fun authenticateFrom(activity: android.app.Activity, title: String): String? {
        val fa = activity as? FragmentActivity ?: return "Unlocking isn't available on this screen."
        return authenticate(fa, title)
    }

    /** Level 2: unlocks Captain's Vault only (never the Quarters). */
    suspend fun unlock(activity: FragmentActivity): String? {
        ensureLoaded()
        if (_readLocked.value) return "Unlock Read first."
        val error = authenticate(activity, "Unlock Private Library")
        if (error == null) {
            _vaultGrant.value = true
            refresh()
        }
        return error
    }

    /** Level 1: unlocks Captain's Quarters only (the Private Library stays locked). */
    suspend fun unlockRead(activity: FragmentActivity): String? {
        ensureLoaded()
        val error = authenticate(activity, "Unlock Read")
        if (error == null) {
            _quartersGrant.value = true
            refresh()
        }
        return error
    }
}
