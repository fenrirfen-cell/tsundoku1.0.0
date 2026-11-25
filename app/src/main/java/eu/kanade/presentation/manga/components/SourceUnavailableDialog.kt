package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.domain.manga.model.Manga

/**
 * Fenrir: shown instead of the raw plugin error when a book can't be found on its source any more.
 */
@Composable
fun SourceUnavailableDialog(
    sourceName: String,
    errorText: String,
    searching: Boolean,
    alternative: Manga?,
    alternativeSourceName: String?,
    onSwitch: (Manga) -> Unit,
    onOpenAlternative: (Manga) -> Unit,
    onSearchOtherSources: () -> Unit,
    onOpenInWebView: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Not available on $sourceName") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Sorry, this book can't be found on $sourceName anymore. It may have been removed or moved.")

                when {
                    searching -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Looking for it on your other sources…")
                    }
                    alternative != null -> Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenAlternative(alternative) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "Found on ${alternativeSourceName.orEmpty()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(alternative.title, fontWeight = FontWeight.Bold)
                            Text(
                                "Tap to preview, or Switch to move your progress there.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    else -> Text(
                        "Couldn't find it automatically on your other sources. Try searching them yourself.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Column {
                    TextButton(onClick = onSearchOtherSources) { Text("Search other sources") }
                    TextButton(onClick = onOpenInWebView) { Text("Open in WebView") }
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) "Hide error details" else "Show error details")
                    }
                }

                if (showDetails) {
                    SelectionContainer {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (alternative != null) {
                TextButton(onClick = { onSwitch(alternative) }) { Text("Switch") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Close") }
        },
    )
}
