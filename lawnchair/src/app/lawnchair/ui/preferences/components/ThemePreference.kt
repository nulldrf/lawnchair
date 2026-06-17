package app.lawnchair.ui.preferences.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
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
 * Shows an animated phone-frame mockup that reflects the currently selected
 * theme, followed by a three-segment pill control (Light / Dark / System).
 * Intended to be placed at the top of [GeneralPreferences], outside any
 * [PreferenceGroup], so the mockup has the full content width.
 */
@Composable
fun ThemePreference() {
    val adapter = preferenceManager().launcherTheme.getAdapter()

    // Optimistic local state: updated immediately on tap so the mockup animation
    // and segment highlight fire at the moment of the gesture, not after the
    // DataStore write round-trip (which can add a visible frame or two of lag).
    // The adapter write still happens so the preference is persisted; the
    // side-effect below keeps local state in sync in case it ever drifts
    // (e.g. deep-link or backup restore changing the value externally).
    var selectedTheme by remember { mutableStateOf(adapter.state.value) }

    val persistedTheme = adapter.state.value
    if (persistedTheme != selectedTheme) {
        selectedTheme = persistedTheme
    }

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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThemePreviewMockup(currentTheme = selectedTheme)
        ThemeSegmentedControl(
            options = themeOptions,
            selectedOption = selectedTheme,
            onOptionSelected = { newValue ->
                selectedTheme = newValue   // instant — drives the animation immediately
                adapter.onChange(newValue) // async — persists to DataStore
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Phone-frame preview
// ---------------------------------------------------------------------------

/**
 * An animated phone-frame mockup that smoothly transitions between the light
 * and dark colour palettes whenever [currentTheme] changes, giving the user
 * immediate visual feedback before they commit to a choice.
 */
@Composable
private fun ThemePreviewMockup(currentTheme: String) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (currentTheme) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        else -> isSystemDark
    }

    // Pull the current accents from the live theme so every surface is tinted
    // from the same palette the user has chosen.
    val primary   = MaterialTheme.colorScheme.primary
    val tertiary  = MaterialTheme.colorScheme.tertiary
    // The settings screen's own background — we derive the phone wallpaper from
    // this so the relationship is always relative, never hardcoded.
    val schemeBg  = MaterialTheme.colorScheme.background

    // All surfaces are derived exclusively from schemeBg + primary/tertiary —
    // zero residual hue from any hardcoded hex constant, so the palette
    // accurately reflects whichever accent color the user has picked.

    // Phone wallpaper: push schemeBg noticeably away from the page behind it —
    // darker in dark mode, lighter in light mode — then add a whisper of primary.
    val wallpaperBgTarget = if (isDark)
        lerp(lerp(schemeBg, Color.Black, 0.35f), primary, 0.06f)
    else
        lerp(lerp(schemeBg, Color.White, 0.55f), primary, 0.04f)

    // Card / dock base: lifted slightly above wallpaper then tinted from the accent.
    // Derived from schemeBg so it carries no foreign hue of its own.
    val cardBase = if (isDark)
        lerp(schemeBg, Color.White, 0.10f)   // a little lighter than the wallpaper
    else
        lerp(schemeBg, Color.Black, 0.04f)   // a little darker than the wallpaper

    val cardColorTarget   = lerp(cardBase, primary,  if (isDark) 0.10f else 0.05f)
    val widgetCardTarget  = lerp(cardBase, tertiary, if (isDark) 0.14f else 0.07f)

    // Subtle elements (status-bar clock, text-line placeholders): same neutral
    // base, just a bit more primary tint so they're distinct but not glaring.
    val subtleColorTarget = lerp(cardBase, primary, if (isDark) 0.22f else 0.15f)

    // Border: thin tinted ring
    val borderColorTarget = if (isDark) primary.copy(alpha = 0.28f)
                            else        primary.copy(alpha = 0.18f)

    // Accent icon squares — pure primary / tertiary with alpha, no grey base at all.
    val accentStrongTarget = primary.copy(alpha  = if (isDark) 0.85f else 0.80f)
    val accentMidTarget    = primary.copy(alpha  = if (isDark) 0.45f else 0.40f)
    val widgetAccentTarget = tertiary.copy(alpha = if (isDark) 0.85f else 0.80f)

    val spec = tween<Color>(durationMillis = 400)
    val wallpaperBg   by animateColorAsState(wallpaperBgTarget,  spec, "mock_bg")
    val cardColor     by animateColorAsState(cardColorTarget,    spec, "mock_card")
    val widgetCard    by animateColorAsState(widgetCardTarget,   spec, "mock_widget_card")
    val subtleColor   by animateColorAsState(subtleColorTarget,  spec, "mock_subtle")
    val borderColor   by animateColorAsState(borderColorTarget,  spec, "mock_border")
    val accentStrong  by animateColorAsState(accentStrongTarget, spec, "mock_acc_strong")
    val accentMid     by animateColorAsState(accentMidTarget,    spec, "mock_acc_mid")
    val widgetAccent  by animateColorAsState(widgetAccentTarget, spec, "mock_widget_acc")

    val phoneShape = RoundedCornerShape(32.dp)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Phone shell — thin border, no shadow
        Box(
            modifier = Modifier
                .width(224.dp)
                .height(308.dp)
                .clip(phoneShape)
                .background(wallpaperBg)
                .border(width = 1.dp, color = borderColor, shape = phoneShape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            ) {
                // ── Status bar ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Clock placeholder — tinted from subtleColor
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(subtleColor),
                    )
                    // System icon dots — same tint family
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(subtleColor),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Large widget card ────────────────────────────────────────
                // Uses tertiary (accent3) so it reads as a visually distinct zone
                // from the primary-tinted shortcut cards and dock below it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(widgetCard),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        // Accent stripe — tertiary tint to match the card zone
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(widgetAccent),
                        )
                        // Body text placeholders — subtle tint
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(subtleColor),
                        )
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(subtleColor),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Two smaller shortcut cards ───────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // First card: strong accent icon; second: mid accent icon
                    listOf(accentStrong, accentMid).forEach { iconTint ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardColor),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(iconTint),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── Dock ─────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(cardColor)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // First icon: strong accent; rest: subtle tint
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (index == 0) accentStrong else subtleColor),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Segmented control (generalised from TwoTabPreferenceLayout to support N tabs)
// ---------------------------------------------------------------------------

/**
 * A pill-style segmented control that works with any number of options.
 *
 * The selected segment animates to the primary colour with fully-rounded
 * corners, while unselected segments use [MaterialTheme.colorScheme.surfaceVariant]
 * and softer corners — identical to the animation style used in
 * [TwoTabPreferenceLayout], but not coupled to a HorizontalPager.
 *
 * @param options       Ordered list of (value, display label) pairs.
 * @param selectedOption The value that is currently selected.
 * @param onOptionSelected Called with the new value when the user taps a segment.
 */
@Composable
fun ThemeSegmentedControl(
    options: List<Pair<String, String>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary         = MaterialTheme.colorScheme.primary
    val onPrimary       = MaterialTheme.colorScheme.onPrimary
    val surfaceVariant  = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = selectedOption == value

            // Animate container/content colours and corner radius independently
            // so each segment transitions smoothly without affecting its neighbours.
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) primary else surfaceVariant,
                animationSpec = tween(durationMillis = 300),
                label = "segment_container_$value",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) onPrimary else onSurfaceVariant,
                animationSpec = tween(durationMillis = 300),
                label = "segment_content_$value",
            )
            // Active pill gets fully-rounded ends; inactive gets a modest rounding.
            val cornerRadius by animateDpAsState(
                targetValue = if (isSelected) 50.dp else 16.dp,
                animationSpec = tween(durationMillis = 300),
                label = "segment_corner_$value",
            )

            Surface(
                onClick = { onOptionSelected(value) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(cornerRadius),
                color = containerColor,
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
