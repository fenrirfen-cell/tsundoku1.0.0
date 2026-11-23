package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.kanade.tachiyomi.source.Source

class AllGlobalSearchViewModel(
    initialQuery: String,
    initialExtensionFilter: String?,
) : SearchViewModel(State(searchQuery = initialQuery)) {

    companion object {
        val INITIAL_QUERY_KEY = CreationExtras.Key<String>()
        val INITIAL_EXTENSION_FILTER_KEY = CreationExtras.Key<String?>()

        val Factory = viewModelFactory {
            initializer {
                AllGlobalSearchViewModel(
                    initialQuery = get(INITIAL_QUERY_KEY)!!,
                    initialExtensionFilter = get(INITIAL_EXTENSION_FILTER_KEY),
                )
            }
        }
    }

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<Source> {
        val filter = state.value.sourceFilter
        return super.getEnabledSources()
            .filterIsInstance<eu.kanade.tachiyomi.source.CatalogueSource>()
            .filter {
                when (filter) {
                    SourceFilter.All -> true
                    SourceFilter.PinnedOnly -> "${it.id}" in pinnedSources
                    is SourceFilter.Group -> "${it.id}" in sourceGroups.getOrElse(filter.name) { emptySet() }
                }
            }
    }
}
