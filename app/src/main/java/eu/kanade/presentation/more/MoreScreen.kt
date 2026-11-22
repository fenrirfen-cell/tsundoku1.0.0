package eu.kanade.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.FenrirCardGroup
import eu.kanade.presentation.components.FenrirGroupDivider
import eu.kanade.presentation.components.FenrirGroupRow
import eu.kanade.presentation.components.FenrirGroupSwitchRow
import eu.kanade.presentation.components.FenrirReadUi
import eu.kanade.presentation.components.FenrirSectionLabel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickSettings: () -> Unit,
    onClickSupport: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold { contentPadding ->
        val downloadQueueState = downloadQueueStateProvider()
        val pending = when (downloadQueueState) {
            DownloadQueueState.Stopped -> 0
            is DownloadQueueState.Paused -> downloadQueueState.pending
            is DownloadQueueState.Downloading -> downloadQueueState.pending
        }
        ScrollbarLazyColumn(contentPadding = contentPadding) {
            // Fenrir: "Your reading" card with quick buttons.
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                0f to FenrirReadUi.Accent.copy(alpha = 0.28f),
                                0.65f to FenrirReadUi.Card,
                            ),
                        )
                        .border(1.dp, Color(0xFF2A2A34), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(FenrirReadUi.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("F", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your reading", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Library, downloads and backups", color = Color(0xFFB8B8C2), fontSize = 12.sp)
                        }
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_settings),
                            tint = Color(0xFFD0D0D8),
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onClickSettings).padding(6.dp).size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickButton(stringResource(MR.strings.label_stats), onClickStats, Modifier.weight(1f))
                        QuickButton(
                            if (pending > 0) "Downloads · $pending" else "Downloads",
                            onClickDownloadQueue,
                            Modifier.weight(1f),
                        )
                        QuickButton("Backup", onClickDataAndStorage, Modifier.weight(1f))
                    }
                }
            }

            item { FenrirSectionLabel("Library") }
            item {
                FenrirCardGroup {
                    FenrirGroupRow(
                        title = stringResource(MR.strings.categories),
                        icon = Icons.AutoMirrored.Outlined.Label,
                        onClick = onClickCategories,
                    )
                    FenrirGroupDivider()
                    FenrirGroupSwitchRow(
                        title = stringResource(MR.strings.label_downloaded_only),
                        icon = Icons.Outlined.CloudOff,
                        checked = downloadedOnly,
                        onCheckedChange = onDownloadedOnlyChange,
                    )
                    FenrirGroupDivider()
                    FenrirGroupSwitchRow(
                        title = stringResource(MR.strings.pref_incognito_mode),
                        icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                        checked = incognitoMode,
                        onCheckedChange = onIncognitoModeChange,
                    )
                }
            }

            item { FenrirSectionLabel("App") }
            item {
                FenrirCardGroup {
                    FenrirGroupRow(
                        title = stringResource(MR.strings.label_settings),
                        subtitle = "Reader, library, tracking",
                        icon = Icons.Outlined.Settings,
                        onClick = onClickSettings,
                    )
                    FenrirGroupDivider()
                    FenrirGroupRow(
                        title = stringResource(MR.strings.label_data_storage),
                        icon = Icons.Outlined.Storage,
                        onClick = onClickDataAndStorage,
                    )
                    FenrirGroupDivider()
                    FenrirGroupRow(
                        title = stringResource(MR.strings.pref_category_about),
                        icon = Icons.Outlined.Info,
                        onClick = onClickAbout,
                    )
                    FenrirGroupDivider()
                    FenrirGroupRow(
                        title = stringResource(MR.strings.label_help),
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = { uriHandler.openUri(Constants.URL_HELP) },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun QuickButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
