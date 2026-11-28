package eu.kanade.presentation.components

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.R

/** Cinzel: major headings and achievement titles. */
val MugiCinzel = FontFamily(
    Font(R.font.cinzel, FontWeight.Normal),
    Font(R.font.cinzel_bold, FontWeight.Bold),
)

/** Playfair Display: section headings, book titles and labels (italic for dates and subtitles). */
val MugiPlayfair = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display_bold, FontWeight.Bold),
    Font(R.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.playfair_display_italic_bold, FontWeight.Bold, FontStyle.Italic),
)
