package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import android.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // Detect whether this preference supports "Managed by Lawnchair" (Default).
    val includeDefault = dynamicEntries.any { it.value is ColorOption.Default }

    // Page 0 = Presets (wallpaper colours), Page 1 = Custom (static grid + dialog)
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
            // ── Tab row (same animated style as IconShapePreference) ──────────
            ColorTabRow(
                selectedPage = pagerState.currentPage,
                onSelectPage = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            )

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                when (page) {
                    // Presets: wallpaper-extracted colour swatches
                    0 -> {
                        WallpaperColorGrid(
                            appliedColor = appliedColor,
                            onApplyWallpaper = {
                                selectedColor.intValue =
                                    ColorOption.WallpaperPrimary.forCustomPicker(context)
                                adapter.onChange(ColorOption.WallpaperPrimary)
                            },
                            onApplyDefault = {
                                adapter.onChange(ColorOption.Default)
                            },
                            includeDefault = includeDefault,
                        )
                    }

                    // Custom: static grid + canvas picker dialog
                    1 -> {
                        CustomColorPicker(
                            selectedColor = selectedColor.intValue,
                            onSelect = { selectedColor.intValue = it },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab row — mirrors the ShapeTabRow in IconShapePreference.kt exactly
// ---------------------------------------------------------------------------

@Composable
private fun ColorTabRow(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        stringResource(id = R.string.presets),
        stringResource(id = R.string.custom),
    )
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
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
                    .weight(1f)
                    .height(44.dp),
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun ColorOption.forCustomPicker(context: Context): Int {
    val color = colorPreferenceEntry.lightColor(context)
    if (color == 0) return Color.BLACK
    return color
}
