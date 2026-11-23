package app.lawnchair.ui.preferences.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.layout.ConnectedButtonGroup
import app.lawnchair.ui.theme.getColorScheme
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * Kept here (same package as the original) so that [app.lawnchair.ui.theme.Theme]
 * can continue to import ThemeChoice from this file without any changes.
 */
object ThemeChoice {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"
}

/**
 * Full redesign of the theme preference.
 *
 * Shows a compact dual-surface phone preview (Launcher home + Settings UI)
 * that reflects the currently selected theme and dynamic accent palette,
 * followed by a three-segment pill control (Light / Dark / System).
 */
@Composable
fun ThemePreference(
    modifier: Modifier = Modifier,
) {
    val adapter = preferenceManager2().launcherTheme.getAdapter()
    // CachedPreferencesDataStore updates in-memory state synchronously on write,
    // so adapter.state.value reflects taps on the very next frame with zero lag.
    val selectedTheme = adapter.state.value

    // Build the option list, conditionally adding System on supported API levels.
    // Mirrors the original filter logic from ThemeChoice / themeEntries.
    // Explicit type avoids a Kotlin inference failure when ThemeChoice constants
    // are resolved at the call site inside a buildList lambda.
    val themeOptions = buildList<Pair<String, String>> {
        add(ThemeChoice.LIGHT to stringResource(id = R.string.theme_light))
        add(ThemeChoice.DARK to stringResource(id = R.string.theme_dark))
        if (Utilities.ATLEAST_O_MR1) {
            val systemLabel = if (Utilities.ATLEAST_P) {
                stringResource(id = R.string.theme_system_default)
            } else {
                stringResource(id = R.string.theme_follow_wallpaper)
            }
            add(ThemeChoice.SYSTEM to systemLabel)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThemePreviewMockup(currentTheme = selectedTheme)
        ThemeSegmentedControl(
            options = themeOptions,
            selectedOption = selectedTheme,
            onOptionSelect = { newValue ->
                if (newValue != selectedTheme) {
                    adapter.onChange(newValue)
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Dual-surface phone preview (Launcher home + Settings UI, icon-only)
// ---------------------------------------------------------------------------

private data class MockupPalette(
    val pageBg: Color,
    val wallTop: Color,
    val wallBottom: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val subtle: Color,
    val border: Color,
)

@Composable
private fun rememberAnimatedMockupPalette(isDark: Boolean): MockupPalette {
    val scheme = getColorScheme(darkTheme = isDark)

    val targetPageBg = scheme.surface
    val targetWallTop = lerp(
        scheme.surfaceContainerLow,
        scheme.primaryContainer,
        if (isDark) 0.22f else 0.36f,
    )
    val targetWallBottom = lerp(
        scheme.surfaceContainer,
        scheme.tertiaryContainer,
        if (isDark) 0.24f else 0.32f,
    )
    val targetSurface = scheme.surfaceContainer
    val targetSurfaceHigh = scheme.surfaceContainerHighest
    val targetPrimary = scheme.primary
    val targetOnPrimary = scheme.onPrimary
    val targetPrimaryContainer = scheme.primaryContainer
    val targetOnPrimaryContainer = scheme.onPrimaryContainer
    val targetSecondaryContainer = scheme.secondaryContainer
    val targetOnSecondaryContainer = scheme.onSecondaryContainer
    val targetTertiaryContainer = scheme.tertiaryContainer
    val targetOnTertiaryContainer = scheme.onTertiaryContainer
    val targetSubtle = scheme.onSurfaceVariant.copy(alpha = if (isDark) 0.32f else 0.26f)
    val targetBorder = scheme.outlineVariant

    val spec = tween<Color>(durationMillis = 250)
    return MockupPalette(
        pageBg = animateColorAsState(targetPageBg, spec, "m_page").value,
        wallTop = animateColorAsState(targetWallTop, spec, "m_wt").value,
        wallBottom = animateColorAsState(targetWallBottom, spec, "m_wb").value,
        surface = animateColorAsState(targetSurface, spec, "m_surf").value,
        surfaceHigh = animateColorAsState(targetSurfaceHigh, spec, "m_surf_hi").value,
        primary = animateColorAsState(targetPrimary, spec, "m_pri").value,
        onPrimary = animateColorAsState(targetOnPrimary, spec, "m_on_pri").value,
        primaryContainer = animateColorAsState(targetPrimaryContainer, spec, "m_pri_c").value,
        onPrimaryContainer = animateColorAsState(targetOnPrimaryContainer, spec, "m_on_pri_c").value,
        secondaryContainer = animateColorAsState(targetSecondaryContainer, spec, "m_sec_c").value,
        onSecondaryContainer = animateColorAsState(targetOnSecondaryContainer, spec, "m_on_sec_c").value,
        tertiaryContainer = animateColorAsState(targetTertiaryContainer, spec, "m_ter_c").value,
        onTertiaryContainer = animateColorAsState(targetOnTertiaryContainer, spec, "m_on_ter_c").value,
        subtle = animateColorAsState(targetSubtle, spec, "m_subtle").value,
        border = animateColorAsState(targetBorder, spec, "m_border").value,
    )
}

@Composable
private fun ThemePreviewMockup(currentTheme: String) {
    // In System mode, show Light on the left tile and Dark on the right tile
    // so both adaptive palettes are visible side-by-side without any text labels.
    val leftDark = currentTheme == ThemeChoice.DARK
    val rightDark = currentTheme == ThemeChoice.DARK || currentTheme == ThemeChoice.SYSTEM

    val leftPalette = rememberAnimatedMockupPalette(isDark = leftDark)
    val rightPalette = rememberAnimatedMockupPalette(isDark = rightDark)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeMiniPhoneTile(
                palette = leftPalette,
                modifier = Modifier.weight(1f),
            )
            SettingsMiniPhoneTile(
                palette = rightPalette,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeMiniPhoneTile(
    palette: MockupPalette,
    modifier: Modifier = Modifier,
) {
    val frameShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(218.dp)
            .clip(frameShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.wallTop, palette.wallBottom),
                ),
            )
            .border(width = 1.dp, color = palette.border, shape = frameShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
        ) {
            MiniStatusBar(subtle = palette.subtle)

            Spacer(modifier = Modifier.height(10.dp))

            // Icon-only At a Glance accent strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(14.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(palette.onPrimaryContainer.copy(alpha = 0.65f)),
                    )
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(palette.subtle),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Widget tile + 2x2 themed icons & mini folder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tertiary widget block (pure icon + bar)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.tertiaryContainer)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = palette.onTertiaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.TopStart),
                    )
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(palette.onTertiaryContainer.copy(alpha = 0.45f))
                            .align(Alignment.BottomStart),
                    )
                }

                // 2x2 icons + folder bubble
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniAppIcon(
                            containerColor = palette.primaryContainer,
                            glyphColor = palette.primary,
                            isCircleGlyph = true,
                            modifier = Modifier.weight(1f),
                        )
                        MiniAppIcon(
                            containerColor = palette.secondaryContainer,
                            glyphColor = palette.onSecondaryContainer.copy(alpha = 0.65f),
                            isCircleGlyph = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniAppIcon(
                            containerColor = palette.tertiaryContainer,
                            glyphColor = palette.onTertiaryContainer.copy(alpha = 0.65f),
                            isCircleGlyph = true,
                            modifier = Modifier.weight(1f),
                        )
                        // Mini 2x2 folder
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.surface)
                                .border(0.75.dp, palette.border, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.primary))
                                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.45f)))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.onTertiaryContainer.copy(alpha = 0.6f)))
                                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.7f)))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Dock icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MiniAppIcon(
                    containerColor = palette.primary,
                    glyphColor = palette.onPrimary,
                    isCircleGlyph = true,
                    modifier = Modifier.weight(1f),
                )
                MiniAppIcon(
                    containerColor = palette.primaryContainer,
                    glyphColor = palette.primary,
                    isCircleGlyph = false,
                    modifier = Modifier.weight(1f),
                )
                MiniAppIcon(
                    containerColor = palette.secondaryContainer,
                    glyphColor = palette.onSecondaryContainer.copy(alpha = 0.7f),
                    isCircleGlyph = true,
                    modifier = Modifier.weight(1f),
                )
                MiniAppIcon(
                    containerColor = palette.tertiaryContainer,
                    glyphColor = palette.onTertiaryContainer.copy(alpha = 0.7f),
                    isCircleGlyph = false,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Pill QSB search bar (icon-only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(13.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(palette.subtle),
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.2.dp, palette.subtle, RoundedCornerShape(2.dp)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            MiniGesturePill(subtle = palette.subtle)
        }
    }
}

@Composable
private fun SettingsMiniPhoneTile(
    palette: MockupPalette,
    modifier: Modifier = Modifier,
) {
    val frameShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(218.dp)
            .clip(frameShape)
            .background(palette.pageBg)
            .border(width = 1.dp, color = palette.border, shape = frameShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
        ) {
            MiniStatusBar(subtle = palette.subtle)

            Spacer(modifier = Modifier.height(10.dp))

            // Pill search bar (icon + bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceHigh)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = palette.subtle.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(palette.subtle),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mini M3E MainSwitch pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(palette.primaryContainer)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(palette.onPrimaryContainer.copy(alpha = 0.75f)),
                )
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(13.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                        .padding(2.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(palette.onPrimary),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Segmented settings cards with circular icon badges (icon-only)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MiniSettingsRow(
                    surfaceColor = palette.surface,
                    badgeContainer = palette.primaryContainer,
                    badgeContent = palette.onPrimaryContainer,
                    icon = Icons.Rounded.Tune,
                    barColor = palette.subtle,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                )
                MiniSettingsRow(
                    surfaceColor = palette.surface,
                    badgeContainer = palette.tertiaryContainer,
                    badgeContent = palette.onTertiaryContainer,
                    icon = Icons.Rounded.Home,
                    barColor = palette.subtle,
                    shape = RoundedCornerShape(4.dp),
                )
                MiniSettingsRow(
                    surfaceColor = palette.surface,
                    badgeContainer = palette.secondaryContainer,
                    badgeContent = palette.onSecondaryContainer,
                    icon = Icons.Rounded.Palette,
                    barColor = palette.subtle,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            MiniGesturePill(subtle = palette.subtle)
        }
    }
}

@Composable
private fun MiniStatusBar(subtle: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(subtle),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(subtle))
            Box(Modifier.width(9.dp).height(4.dp).clip(RoundedCornerShape(1.5.dp)).background(subtle))
        }
    }
}

@Composable
private fun MiniAppIcon(
    containerColor: Color,
    glyphColor: Color,
    isCircleGlyph: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(if (isCircleGlyph) CircleShape else RoundedCornerShape(3.dp))
                .background(glyphColor),
        )
    }
}

@Composable
private fun MiniSettingsRow(
    surfaceColor: Color,
    badgeContainer: Color,
    badgeContent: Color,
    icon: ImageVector,
    barColor: Color,
    shape: RoundedCornerShape,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColor)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(badgeContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeContent,
                modifier = Modifier.size(11.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(barColor.copy(alpha = 0.85f)),
            )
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(barColor.copy(alpha = 0.45f)),
            )
        }
    }
}

@Composable
private fun MiniGesturePill(subtle: Color) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(subtle),
        )
    }
}

// ---------------------------------------------------------------------------
// Theme selector (Material 3 Expressive connected button group)
// ---------------------------------------------------------------------------

/**
 * Connected button group selector for theme options.
 */
@Composable
fun ThemeSegmentedControl(
    options: List<Pair<String, String>>,
    selectedOption: String,
    onOptionSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOfFirst { it.first == selectedOption }.coerceAtLeast(0)
    ConnectedButtonGroup(
        options = options.map { it.second },
        selectedIndex = selectedIndex,
        onSelect = { index -> onOptionSelect(options[index].first) },
        modifier = modifier,
    )
}
