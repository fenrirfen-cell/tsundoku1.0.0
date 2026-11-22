package eu.kanade.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel
import eu.kanade.tachiyomi.source.model.Filter as SourceFilter

private val Red = Color(0xFFFF4D57)

/** Genre names a source offers (its biggest genre group, or a genre dropdown). */
private fun genresOf(source: Source): List<String> {
    val filters = runCatching { (source as? CatalogueSource)?.getFilterList() }.getOrNull() ?: return emptyList()
    val groups = filters.filterIsInstance<SourceFilter.Group<*>>()
        .map { g -> g to g.state.filterIsInstance<SourceFilter<*>>().filter { it is SourceFilter.CheckBox || it is SourceFilter.TriState } }
        .filter { it.second.size >= 4 }
    val best = groups.firstOrNull { it.first.name.contains("genre", true) } ?: groups.maxByOrNull { it.second.size }
    if (best != null) return best.second.map { it.name }
    val select = filters.filterIsInstance<SourceFilter.Select<*>>().firstOrNull { it.name.contains("genre", true) }
    return select?.values?.map { it.toString() }?.drop(1).orEmpty()
}

/** Genres switched on in the current search. */
private fun activeGenres(listing: BrowseSourceViewModel.Listing): Set<String> {
    if (listing !is BrowseSourceViewModel.Listing.Search) return emptySet()
    val out = mutableSetOf<String>()
    listing.filters.forEach { f ->
        when (f) {
            is SourceFilter.Group<*> -> f.state.forEach { c ->
                when (c) {
                    is SourceFilter.CheckBox -> if (c.state) out += c.name
                    is SourceFilter.TriState -> if (c.state == SourceFilter.TriState.STATE_INCLUDE) out += c.name
                    else -> {}
                }
            }
            is SourceFilter.Select<*> -> if (f.name.contains("genre", true) && f.state > 0) {
                f.values.getOrNull(f.state)?.toString()?.let { out += it }
            }
            else -> {}
        }
    }
    if (!listing.query.isNullOrBlank()) out += listing.query!!
    return out
}

@Composable
fun FenrirGenreChips(
    source: Source,
    listing: BrowseSourceViewModel.Listing,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onOpenFilters: (() -> Unit)?,
) {
    val genres = remember(source.id) { genresOf(source) }
    if (genres.isEmpty() && onOpenFilters == null) return
    val active = activeGenres(listing)
    // Active ones first so the picked genre is always in view.
    val ordered = remember(genres, active) { genres.filter { it in active } + genres.filterNot { it in active } }
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenFilters != null) {
            Row(
                Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(Red)
                    .clickable(onClick = onOpenFilters)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FilterList, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (active.isEmpty()) "Filters" else "Filters · ${active.size}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        ordered.take(40).forEach { g ->
            val on = g in active
            Text(
                g,
                color = if (on) Color(0xFFFF8A91) else Color(0xFFD5D5DE),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(if (on) Red.copy(alpha = 0.18f) else Color(0xFF1C1C23))
                    .border(1.dp, if (on) Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable { if (on) onClear() else onPick(g) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
