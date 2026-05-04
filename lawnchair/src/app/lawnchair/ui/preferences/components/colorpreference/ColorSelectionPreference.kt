package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import android.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.pickers.CustomColorPicker
import app.lawnchair.ui.preferences.components.colorpreference.pickers.WallpaperColorGrid
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorSelection(
    label: String,
    preference: Preference<ColorOption, String, *>,
    modifier: Modifier = Modifier,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>> = dynamicColors,
    staticEntries: List<ColorPreferenceEntry<ColorOption>> = staticColors,
) {
    val adapter = preference.getAdapter()
    val appliedColor = adapter.state.value
    val context = LocalContext.current

    val selectedColor = remember { mutableIntStateOf(appliedColor.forCustomPicker(context)) }
    val selectedColorApplied = remember {
        derivedStateOf {
            appliedColor is ColorOption.CustomColor &&
                appliedColor.color == selectedColor.intValue
        }
    }

    // Whether this preference supports "Managed by Lawnchair" (Default).
    val includeDefault = dynamicEntries.any { it.value is ColorOption.Default }

    // Page 0 = Presets (system + wallpaper colours)
    // Page 1 = Custom (static grid + canvas picker dialog)
    val defaultTabIndex = if (
        appliedColor is ColorOption.WallpaperPrimary ||
        appliedColor is ColorOption.Default ||
        appliedColor is ColorOption.SystemAccent ||
        dynamicEntries.any { it.value == appliedColor }
    ) 0 else 1

    val pagerState = rememberPagerState(
        initialPage = defaultTabIndex,
        pageCount = { 2 },
    )
    val scope = rememberCoroutineScope()

    // Track which specific custom color was last selected on page 1 so the
    // indicator dot shows the right color before the user hits Apply.
    var pendingCustomColor by remember { mutableStateOf<Int?>(null) }

    PreferenceLayout(
        label = label,
        modifier = modifier,
        bottomBar = {
            if (pagerState.currentPage == 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    Button(
                        enabled = !selectedColorApplied.value,
                        onClick = {
                            adapter.onChange(
                                newValue = ColorOption.CustomColor(selectedColor.intValue),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(text = stringResource(id = R.string.action_apply))
                    }
                    BottomSpacer()
                }
            } else {
                BottomSpacer()
            }
        },
    ) {
        Column {
            // ── Selection indicator ──────────────────────────────────────────
            SelectionIndicator(
                appliedColor = appliedColor,
                pendingCustomColor = pendingCustomColor,
                currentPage = pagerState.currentPage,
                includeDefault = includeDefault,
                context = context,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ── Tab row — exact ShapeTabRow pattern from IconShapePreference ─
            ColorTabRow(
                selectedPage = pagerState.currentPage,
                onSelectPage = { scope.launch { pagerState.animateScrollToPage(it) } },
            )

            Spacer(modifier = Modifier.height(4.dp))

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                when (page) {
                    0 -> WallpaperColorGrid(
                        appliedColor = appliedColor,
                        onApplyOption = { option ->
                            selectedColor.intValue = option.forCustomPicker(context)
                            adapter.onChange(newValue = option)
                        },
                        includeDefault = includeDefault,
                    )
                    1 -> CustomColorPicker(
                        selectedColor = selectedColor.intValue,
                        onSelect = { newColor ->
                            selectedColor.intValue = newColor
                            pendingCustomColor = newColor
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Selection indicator banner
// ---------------------------------------------------------------------------

/**
 * Displays a tinted container with:
 *  - A filled circle showing the currently active colour.
 *  - A bold label ("Presets" or "Custom") matching the active page.
 *  - A secondary description label ("System", "Wallpaper",
 *    "Managed by Lawnchair", or "Custom").
 *
 * Mirrors the top-of-card indicator from Repainter.
 */
@Composable
private fun SelectionIndicator(
    appliedColor: ColorOption,
    pendingCustomColor: Int?,
    currentPage: Int,
    includeDefault: Boolean,
    context: Context,
    modifier: Modifier = Modifier,
) {
    // Resolve the colour to display in the dot.
    val dotColorInt = when {
        currentPage == 1 && pendingCustomColor != null -> pendingCustomColor
        appliedColor is ColorOption.CustomColor -> appliedColor.color
        else -> appliedColor.colorPreferenceEntry.lightColor(context)
    }
    val dotColor = if (dotColorInt != 0) ComposeColor(dotColorInt)
        else MaterialTheme.colorScheme.primary

    // Container tint: very light wash of the dot colour.
    val containerTint = dotColor.copy(alpha = 0.12f)

    // Big label matches the active tab.
    val bigLabel = if (currentPage == 0) {
        stringResource(id = R.string.presets)
    } else {
        stringResource(id = R.string.custom)
    }

    // Small description reflects the actual applied preference.
    val smallLabel = when (appliedColor) {
        is ColorOption.SystemAccent -> stringResource(id = R.string.system)
        is ColorOption.WallpaperPrimary -> stringResource(id = R.string.wallpaper)
        is ColorOption.Default -> stringResource(id = R.string.managed_by_lawnchair)
        is ColorOption.CustomColor -> stringResource(id = R.string.custom)
        else -> stringResource(id = R.string.custom)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerTint)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Colour dot
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                // Labels
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = bigLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = smallLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab row — exact ShapeTabRow from IconShapePreference.kt
// Centered and compact: constrained width so it doesn't stretch full-screen.
// ---------------------------------------------------------------------------

@Composable
private fun ColorTabRow(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
) {
    val tabs = listOf(
        stringResource(id = R.string.presets),
        stringResource(id = R.string.custom),
    )
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 32.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = selectedPage == index

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) primary else ComposeColor.Transparent,
                    animationSpec = tween(250),
                    label = "tab_bg_$index",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) onPrimary else onSurface,
                    animationSpec = tween(250),
                    label = "tab_fg_$index",
                )
                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 50.dp else 16.dp,
                    animationSpec = tween(250),
                    label = "tab_corner_$index",
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) ComposeColor.Transparent else outline,
                    animationSpec = tween(250),
                    label = "tab_border_$index",
                )

                Surface(
                    onClick = { onSelectPage(index) },
                    modifier = Modifier
                        .width(140.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(cornerRadius),
                    color = containerColor,
                    contentColor = contentColor,
                    border = BorderStroke(1.dp, borderColor),
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
}

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

private fun ColorOption.forCustomPicker(context: Context): Int {
    val color = colorPreferenceEntry.lightColor(context)
    if (color == 0) return Color.BLACK
    return color
}
