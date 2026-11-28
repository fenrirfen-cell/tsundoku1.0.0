package eu.kanade.presentation.components

import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.wrapContentSize

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.border

import android.app.Activity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.EmbeddedHost

/**
 * The Watch / Read pill shown at the top of the home screen when Tsundoku runs inside Nuvio.
 * Draws nothing in a standalone Tsundoku build.
 */
@Composable
fun EmbeddedModeSwitchBar(modifier: Modifier = Modifier) {
    val openWatch = EmbeddedHost.openWatch ?: return
    val context = LocalContext.current
    if (EmbeddedHost.isFenrirStyle) {
        FenrirReadHeader(modifier, openWatch)
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val openAniDrama = EmbeddedHost.openAniDrama
        val threeWay = openAniDrama != null
        Row(
            modifier = Modifier
                .then(if (threeWay) Modifier.fillMaxWidth().padding(horizontal = 12.dp) else Modifier)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val segmentModifier = if (threeWay) Modifier.weight(1f) else Modifier
            ModeSegment(
                label = EmbeddedHost.watchLabel,
                icon = Icons.Outlined.PlayArrow,
                selected = false,
                onClick = { context.findActivity()?.let(openWatch) },
                compact = threeWay,
                modifier = segmentModifier,
            )
            if (openAniDrama != null) {
                ModeSegment(
                    label = "Ani-drama",
                    icon = Icons.Outlined.Tv,
                    selected = false,
                    onClick = { context.findActivity()?.let(openAniDrama) },
                    compact = true,
                    modifier = segmentModifier,
                )
            }
            ModeSegment(
                label = EmbeddedHost.readLabel,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                selected = true,
                onClick = {},
                compact = threeWay,
                modifier = segmentModifier,
            )
        }
    }
}

@Composable
private fun ModeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.secondaryContainer) else Modifier)
            .semantics { this.selected = selected }
            .clickable(enabled = !selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Fenrir style, matching the Watch side: FENRIR READ (bigger, on one baseline) centred on the
 * glass "📖 Read ▾" pill at the right, and the same glass Watch / Ani-drama / Read menu. When a new
 * version is out, a round download button sits before the pill.
 */
@Composable
private fun FenrirReadHeader(modifier: Modifier, openWatch: (Activity) -> Unit) {
    if (EmbeddedHost.isMugi) {
        MugiReadHeader(modifier)
        return
    }
    val topLook = eu.kanade.presentation.library.components.FenrirLibraryLook.current.value
    val context = LocalContext.current
    val accent = eu.kanade.presentation.reader.FenrirAccent
    val white = androidx.compose.ui.graphics.Color.White
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Fenrir: dark wood behind the crown, all the way up behind the status bar.
            .then(
                when (topLook) {
                    eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Bookshelf -> eu.kanade.presentation.library.components.fenrirCrownModifier()
                    eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Luxury -> Modifier.background(androidx.compose.ui.graphics.Color(0xFF0E0C0A))
                    // Regular: a soft red glow at the top, fading down into the page
                    else -> Modifier.background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF3A161B), androidx.compose.ui.graphics.Color(0xFF2C1317))))
                },
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 2.dp),
    ) {
        // Fenrir: the top follows the library look (carved crown / gold-and-black / plain)
        val ratio = when (topLook) { eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Bookshelf -> CrownRatio; eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Luxury -> 1440f / 236f; else -> 1440f / 170f }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            val cw = maxWidth
            val ch = maxHeight
            val slots: List<FloatArray> = when (topLook) {
                eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Bookshelf -> listOf(floatArrayOf(0.1028f, 0.3902f, 0.2561f, 0.337f), floatArrayOf(0.641f, 0.3902f, 0.2561f, 0.337f))
                eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Luxury -> listOf(floatArrayOf(0.015f, 0.28f, 0.355f, 0.46f), floatArrayOf(0.63f, 0.28f, 0.355f, 0.46f))
                else -> listOf(floatArrayOf(0.03f, 0.2f, 0.42f, 0.58f), floatArrayOf(0.665f, 0.2f, 0.305f, 0.58f))
            }
            when (topLook) {
                eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Bookshelf -> androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.fenrir_crown),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Luxury -> {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.lux_marble), null,
                        Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    slots.forEachIndexed { i, r ->
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(if (i == 0) eu.kanade.tachiyomi.R.drawable.lux_pill_l else eu.kanade.tachiyomi.R.drawable.lux_pill_r), null,
                            Modifier.offset(cw * r[0], ch * r[1]).size(cw * r[2], ch * r[3]),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                        )
                    }
                    val ringW = cw * 0.25f
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.lux_ring), null,
                        Modifier.align(Alignment.TopCenter).size(ringW, ringW * (166f / 257f)),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                    )
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.lux_wolf), null,
                        Modifier.align(Alignment.TopCenter).offset(y = ringW * 0.07f).size(ringW * 0.62f),
                    )
                }
                else -> Unit
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalTopStyle provides topLook.ordinal) {
            // the two button spots (left: FENRIR READ ▾, right: Read ▾)
            Box(Modifier.offset(cw * slots[0][0], ch * slots[0][1] + (if (topLook == eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Bookshelf) 3.dp else 0.dp)).size(cw * slots[0][2], ch * slots[0][3])) {
                FenrirReadWordmarkMenu()
            }
            // Fenrir (Regular): a round Search button next to Read; the rest is under FENRIR READ ▾
            if (topLook == eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Regular) {
                val d = ch * 0.58f
                Box(
                    Modifier
                        .offset(cw * 0.665f - d - 8.dp, ch * 0.2f)
                        .size(d)
                        .fenrirReaderGlassPill()
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(role = Role.Button) { eu.kanade.presentation.components.FenrirReadMenu.current.value?.search?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search your library", tint = white, modifier = Modifier.size(21.dp))
                }
            }
            Box(Modifier.offset(cw * slots[1][0], ch * slots[1][1] + (if (topLook == eu.kanade.presentation.library.components.FenrirLibraryLook.Look.Bookshelf) 3.dp else 0.dp)).size(cw * slots[1][2], ch * slots[1][3])) {
            val appUpdate by eu.kanade.tachiyomi.EmbeddedHost.appUpdate.collectAsState()
            Box(Modifier.fillMaxSize()) {
                FenrirCarvedPanel(onClick = { open = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFFE2343F),
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    FenrirCarvedText(EmbeddedHost.readLabel, 15.dp)
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Switch side",
                        tint = if (LocalTopStyle.current == 0) FenrirGoldText else androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // Fenrir: red dot when a new app version is out (Update is at the top of the menu).
                if (appUpdate != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 6.dp)
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accent),
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    modifier = Modifier.width(224.dp).padding(horizontal = 6.dp),
                    shape = RoundedCornerShape(26.dp),
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C22).copy(alpha = 0.96f),
                    tonalElevation = 0.dp,
                    shadowElevation = 18.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        Brush.verticalGradient(listOf(white.copy(alpha = 0.32f), white.copy(alpha = 0.05f), white.copy(alpha = 0.12f))),
                    ),
                ) {
                    appUpdate?.let { u ->
                        FenrirUpdateMenuItem(u) { open = false }
                        androidx.compose.material3.HorizontalDivider(
                            color = white.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    FenrirModeItem(EmbeddedHost.watchLabel, Icons.Outlined.PlayArrow, false) {
                        open = false
                        context.findActivity()?.let(openWatch)
                    }
                    EmbeddedHost.openAniDrama?.let { openAniDrama ->
                        FenrirModeItem("Ani-drama", Icons.Outlined.Tv, false) {
                            open = false
                            context.findActivity()?.let(openAniDrama)
                        }
                    }
                    FenrirModeItem(EmbeddedHost.readLabel, Icons.AutoMirrored.Outlined.MenuBook, true) { open = false }
                }
            }
            }
            }
        }
    }
}

/** 0 = carved crown, 1 = luxury gold-and-black, 2 = plain glass (follows the library look). */
private val LocalTopStyle = androidx.compose.runtime.compositionLocalOf { 0 }

/** The crown picture's shape (width / height). */
private const val CrownRatio = 1440f / 300f
private val FenrirGoldText = androidx.compose.ui.graphics.Color(0xFFF2C96A)

/**
 * Fenrir: a button carved into one of the crown's wood panels. The wood behind the words is a
 * little darker; pressing it makes the panel glow gold for a moment.
 */
@Composable
private fun FenrirCarvedPanel(onClick: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glow by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, label = "panelGlow")
    val style = LocalTopStyle.current
    val shape = if (style == 0) RoundedCornerShape(8.dp) else RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(if (style == 2) Modifier.fenrirReaderGlassPill() else Modifier)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (style == 0) 0.3f else 0f))
            .background(
                Brush.radialGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xFFFFC060).copy(alpha = 0.45f * glow), androidx.compose.ui.graphics.Color.Transparent),
                ),
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * Gold letters set into the wood: a thin dark carved edge, polished gold (light at the top,
 * deeper gold at the bottom) and a soft warm glow. Sized in dp so it always fits the panel.
 */
@Composable
private fun FenrirCarvedText(
    text: String,
    size: androidx.compose.ui.unit.Dp,
    red: Boolean = false,
    letterSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fs = with(density) { size.toSp() }
    val ls = with(density) { letterSpacing.toSp() }
    val colors = if (red) {
        listOf(androidx.compose.ui.graphics.Color(0xFFFF7070), androidx.compose.ui.graphics.Color(0xFFD01E2C))
    } else {
        listOf(androidx.compose.ui.graphics.Color(0xFFFFE89A), androidx.compose.ui.graphics.Color(0xFFDE9624))
    }
    val glow = if (red) androidx.compose.ui.graphics.Color(0xCCFF3040) else androidx.compose.ui.graphics.Color(0xCCFFB040)
    if (LocalTopStyle.current != 0) {
        // Luxury / Regular: plain white letters, READ in the red branding
        Text(
            text, modifier = modifier, maxLines = 1, softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = fs, letterSpacing = ls, fontWeight = FontWeight.Bold,
                color = if (red) androidx.compose.ui.graphics.Color(0xFFE53340) else androidx.compose.ui.graphics.Color.White,
            ),
        )
        return
    }
    Box(modifier) {
        Text(
            text,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = fs,
                letterSpacing = ls,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF1C0C02),
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = with(density) { 2.5.dp.toPx() }),
            ),
        )
        Text(
            text,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = fs,
                letterSpacing = ls,
                fontWeight = FontWeight.Bold,
                brush = Brush.verticalGradient(colors),
                shadow = androidx.compose.ui.graphics.Shadow(glow, androidx.compose.ui.geometry.Offset.Zero, with(density) { 7.dp.toPx() }),
            ),
        )
    }
}

@Composable
private fun FenrirModeItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val accent = eu.kanade.presentation.reader.FenrirAccent
    val white = androidx.compose.ui.graphics.Color.White
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, color = white, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (selected) accent else white) },
        trailingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = accent) }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** Same glass as the Watch side's buttons: see-through tint, top sheen, light rim, soft shadow. */
private fun Modifier.fenrirReaderGlassPill(): Modifier = this
    .shadow(12.dp, CircleShape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
    .clip(CircleShape)
    .background(androidx.compose.ui.graphics.Color(0xFF26262E).copy(alpha = 0.92f))
    .background(Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), 0.55f to androidx.compose.ui.graphics.Color.Transparent))
    .border(
        1.dp,
        Brush.linearGradient(
            0f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
            0.4f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
            1f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
        ),
        CircleShape,
    )

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


/** Top of the Watch / Read menu when a new version is out: Update, then Install. */
@Composable
private fun FenrirUpdateMenuItem(u: eu.kanade.tachiyomi.EmbeddedHost.AppUpdateInfo, onDone: () -> Unit) {
    val accent = eu.kanade.presentation.reader.FenrirAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable(enabled = !u.downloading) {
                eu.kanade.tachiyomi.EmbeddedHost.startAppUpdate?.invoke()
                if (u.ready) onDone()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Download, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                when {
                    u.error != null -> "Update failed · tap to retry"
                    u.downloading -> "Downloading ${((u.progress ?: 0f) * 100).toInt().coerceIn(0, 100)}%"
                    u.ready -> "Install update"
                    else -> "Update to ${u.version.removePrefix("fenrir-").substringAfterLast("-")}"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 1,
            )
            Text(
                if (u.ready) "Ready to install" else "New version ready",
                fontSize = 11.sp,
                color = androidx.compose.ui.graphics.Color(0xFFB8B8C2),
                maxLines = 1,
            )
        }
        if (!u.downloading) {
            Text(
                "✕",
                fontSize = 13.sp,
                color = androidx.compose.ui.graphics.Color(0xFF8E8E99),
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { eu.kanade.tachiyomi.EmbeddedHost.dismissAppUpdate?.invoke(); onDone() }
                    .padding(6.dp),
            )
        }
    }
}

/**
 * Fenrir: FENRIR READ ▾ as a glass pill. On the library it opens a glass menu with the library's
 * tools (search, For you, filter & sort, a random book, checking for new chapters, more).
 */
@Composable
private fun FenrirReadWordmarkMenu(label: (@Composable () -> Unit)? = null) {
    val accent = eu.kanade.presentation.reader.FenrirAccent
    val white = androidx.compose.ui.graphics.Color.White
    val entry = FenrirReadMenu.current.value
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var more by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // MugiVerse: the logo is the button for the same library menu
        if (label != null) Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = entry != null, onClickLabel = "Library menu") { more = false; open = true },
        ) { label() } else
        FenrirCarvedPanel(onClick = if (entry != null) ({ more = false; open = true }) else null) {
            Row(verticalAlignment = Alignment.Bottom) {
                FenrirCarvedText("FENRIR", 14.5.dp, letterSpacing = 1.dp, modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(4.dp))
                FenrirCarvedText("READ", 9.dp, red = true, letterSpacing = 1.dp, modifier = Modifier.alignByBaseline())
            }
            if (entry != null) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Library menu",
                    tint = if (LocalTopStyle.current == 0) FenrirGoldText else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (entry != null) {
            androidx.compose.material3.DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.width(248.dp).padding(horizontal = 6.dp),
                shape = RoundedCornerShape(26.dp),
                containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C22).copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    Brush.verticalGradient(listOf(white.copy(alpha = 0.32f), white.copy(alpha = 0.05f), white.copy(alpha = 0.12f))),
                ),
            ) {
                fun run(action: () -> Unit) {
                    open = false
                    action()
                }
                if (!more) {
                    if (LocalTopStyle.current != 2) FenrirMenuRow("Search your library", Icons.Outlined.Search) { run(entry.search) }
                    FenrirMenuRow("For you", Icons.Filled.AutoAwesome, tint = accent) { run(entry.forYou) }
                    FenrirMenuRow("Filter & sort", Icons.Outlined.FilterList, tint = if (entry.hasFilters) accent else white) { run(entry.filter) }
                    androidx.compose.material3.HorizontalDivider(
                        color = white.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    FenrirMenuRow("Open a random book", Icons.Outlined.Casino) { run(entry.random) }
                    FenrirMenuRow("Check for new chapters", Icons.Outlined.Refresh) { run(entry.checkNew) }
                    FenrirMenuRow("Library look", Icons.Outlined.GridView) { run { eu.kanade.presentation.library.components.FenrirLibraryLook.pickerOpen.value = true } }
                    if (entry.more.isNotEmpty()) {
                        FenrirMenuRow("More options", Icons.Outlined.MoreVert) { more = true }
                    }
                } else {
                    FenrirMenuRow("Back", Icons.AutoMirrored.Outlined.ArrowBack, tint = accent) { more = false }
                    androidx.compose.material3.HorizontalDivider(
                        color = white.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    entry.more.forEach { (label, action) ->
                        FenrirMenuRow(label, null) { run(action) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FenrirMenuRow(
    label: String,
    icon: ImageVector?,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    onClick: () -> Unit,
) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 1.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(18.dp)),
    )
}


/**
 * MugiVerse on Read: the captain's cabin behind the MugiVerse logo with a gold READ ribbon (tap
 * it for the library menu: looks, filters, import…), then Search and the bell (app updates).
 */
@Composable
private fun MugiReadHeader(modifier: Modifier) {
    val gold = androidx.compose.ui.graphics.Color(0xFFD9B26A)
    val appUpdate by eu.kanade.tachiyomi.EmbeddedHost.appUpdate.collectAsState()
    var bell by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .mugiCabin(header = true)
            // a carved beam along the bottom edge, with a soft shadow falling on the shelves below,
            // so the books slide under the ceiling instead of against a hard cut
            .drawWithContent {
                drawContent()
                val h = size.height
                drawRect(
                    Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF3A2414), androidx.compose.ui.graphics.Color(0xFF1C110A)), startY = h - 6.dp.toPx(), endY = h),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, h - 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx()),
                )
                drawRect(
                    androidx.compose.ui.graphics.Color(0xFFB98A42).copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, h - 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                )
                drawRect(
                    Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), androidx.compose.ui.graphics.Color.Transparent), startY = h, endY = h + 22.dp.toPx()),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, h),
                    size = androidx.compose.ui.geometry.Size(size.width, 22.dp.toPx()),
                )
            },
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), androidx.compose.ui.graphics.Color.Transparent)),
            ),
        )
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(66.dp)
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                FenrirReadWordmarkMenu(label = {
                    Box(Modifier.width(176.dp).height(60.dp)) {
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(eu.kanade.tachiyomi.R.drawable.mugi_logo),
                            contentDescription = "MugiVerse",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.width(168.dp).height(46.dp),
                        )
                        Text(
                            "READ ▾",
                            color = androidx.compose.ui.graphics.Color(0xFF2E180A),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = 38.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFF3C75F), androidx.compose.ui.graphics.Color(0xFFB98222))))
                                .padding(horizontal = 16.dp, vertical = 1.dp),
                        )
                    }
                })
            }
            // Surprise Me: the little treasure chest next to the bell
            if (eu.kanade.tachiyomi.EmbeddedHost.openVoyage != null) {
                val voyageCtx = LocalContext.current
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Surprise me") { eu.kanade.tachiyomi.EmbeddedHost.voyage(voyageCtx, "SURPRISE") },
                    contentAlignment = Alignment.Center,
                ) {
                    MugiChestIcon(Modifier.size(30.dp))
                }
            }
            Box {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Notifications") { bell = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = gold, modifier = Modifier.size(25.dp))
                    if (appUpdate != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 9.dp, end = 9.dp)
                                .size(9.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(eu.kanade.presentation.reader.FenrirAccent),
                        )
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = bell,
                    onDismissRequest = { bell = false },
                    modifier = Modifier.width(240.dp).padding(horizontal = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1E140C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFC9954D)),
                ) {
                    val u = appUpdate
                    if (u != null) {
                        FenrirUpdateMenuItem(u) { bell = false }
                    } else {
                        Text(
                            "You're all caught up",
                            color = androidx.compose.ui.graphics.Color(0xFFB9B3A8),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            MugiReadMore.open?.let { openMore ->
                val tint = if (MugiReadMore.selected) gold else androidx.compose.ui.graphics.Color(0xFFF0E4CC)
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .size(width = 56.dp, height = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClickLabel = "More") { openMore() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text("More", color = tint, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        MugiLogoTip(Modifier.align(Alignment.BottomStart).offset(x = 14.dp, y = 30.dp))
    }
}


/**
 * MugiVerse: "▲ Tap the logo for Search, Filter & sort, For you…", the first few times Read opens
 * (tap it to hide it for good). Nothing to see after that; the ▾ on READ stays as the cue.
 */
@Composable
private fun MugiLogoTip(modifier: Modifier) {
    val context = LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("fenrir_ui", android.content.Context.MODE_PRIVATE) }
    var show by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (prefs.getInt("mugi_logo_tip_seen", 0) >= 4) return@LaunchedEffect
        // after the opening animation, and only for a few seconds
        kotlinx.coroutines.delay(3500)
        prefs.edit().putInt("mugi_logo_tip_seen", prefs.getInt("mugi_logo_tip_seen", 0) + 1).apply()
        show = true
        kotlinx.coroutines.delay(6000)
        show = false
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = show,
        modifier = modifier.wrapContentSize(unbounded = true),
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
    ) {
        Text(
            "▲ Tap the logo for Search, Filter & sort, For you…",
            color = androidx.compose.ui.graphics.Color(0xFF2E180A),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFE2BA72))
                .clickable { prefs.edit().putInt("mugi_logo_tip_seen", 99).apply(); show = false }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
