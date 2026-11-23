package eu.kanade.presentation.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CatppuccinColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.TokyoNightColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    BaseTachiyomiTheme(
        appTheme = appTheme ?: uiPreferences.appTheme.get(),
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled.get(),
        content = content,
    )
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(appTheme, isAmoled, content)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    // Fenrir Read: match the Watch side's near-black and coral red look.
    if (eu.kanade.tachiyomi.EmbeddedHost.isFenrirStyle) {
        MaterialExpressiveTheme(colorScheme = FenrirReadColorScheme) {
            // gold section headings ("Today", "Yesterday"...) to match the gold titles
            androidx.compose.runtime.CompositionLocalProvider(
                tachiyomi.presentation.core.components.LocalListGroupHeaderColor provides androidx.compose.ui.graphics.Color(0xFFE8B95A),
                content = content,
            )
        }
        return
    }
    MaterialExpressiveTheme(
        colorScheme = remember(appTheme, isDark, isAmoled) {
            getThemeColorScheme(
                context = context,
                appTheme = appTheme,
                isDark = isDark,
                isAmoled = isAmoled,
            )
        },
        content = content,
    )
}

private fun getThemeColorScheme(
    context: Context,
    appTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(context)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isDark = isDark,
        isAmoled = isAmoled,
        overrideDarkSurfaceContainers = appTheme != AppTheme.MONET,
    )
}

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CATPPUCCIN to CatppuccinColorScheme,
    AppTheme.TOKYONIGHT to TokyoNightColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MONOCHROME to MonochromeColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
)


/** Fenrir Read colors: the library's warm dark brown with gold edges, and the Watch side's red. */
private val FenrirReadColorScheme = androidx.compose.material3.darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFF4D57),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF3A1A1E),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFB3B7),
    secondary = androidx.compose.ui.graphics.Color(0xFFFF6B73),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF3A1A1E),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFFF6B73),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFF8A91),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF3A1A1E),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFB3B7),
    background = androidx.compose.ui.graphics.Color(0xFF140E0A),
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = androidx.compose.ui.graphics.Color(0xFF140E0A),
    onSurface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2A1E16),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB9A994),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFFFF4D57),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0C0805),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF1A130E),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1F1711),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF261C15),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2E2219),
    outline = androidx.compose.ui.graphics.Color(0xFF8A6A34),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF3E2E1C),
)
