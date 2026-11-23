package eu.kanade.presentation.components

import androidx.compose.runtime.mutableStateOf

/**
 * Fenrir: what the FENRIR READ ▾ menu at the top can do. The library screen that's showing
 * (Manga or Novels) fills this in; other screens leave it empty and the menu has no arrow.
 */
object FenrirReadMenu {
    class Entry {
        var search: () -> Unit = {}
        var forYou: () -> Unit = {}
        var filter: () -> Unit = {}
        var hasFilters: Boolean = false
        var random: () -> Unit = {}
        var checkNew: () -> Unit = {}
        var more: List<Pair<String, () -> Unit>> = emptyList()
    }

    val current = mutableStateOf<Entry?>(null)

    fun register(entry: Entry) {
        current.value = entry
    }

    fun unregister(entry: Entry) {
        if (current.value === entry) current.value = null
    }
}
