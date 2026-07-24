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
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.colorpreference.pickers.CustomColorPicker
import app.lawnchair.ui.preferences.components.colorpreference.pickers.WallpaperColorGrid
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.theme.isSelectedThemeDark
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
    useSimpleSwatches: Boolean = false,
) {
    val adapter = preference.getAdapter()
    val appliedColor = adapter.state.value
    val context = LocalContext.current
    val navController = LocalNavController.current
    val selectedColor = remember { mutableIntStateOf(appliedColor.forCustomPicker(context)) }
    val selectedColorApplied = remember {
        derivedStateOf {
            appliedColor is ColorOption.CustomColor &&
                appliedColor.color == selectedColor.intValue
        }
    }

    val includeDefault = dynamicEntries.any { it.value is ColorOption.Default }

    // Open Custom tab only when a manually-picked CustomColor is active.
    // WallpaperDerived, SystemAccent, WallpaperPrimary, and Default all live
    // on the Presets tab.
    val defaultTabIndex = if (appliedColor is ColorOption.CustomColor) 1 else 0

    val pagerState = rememberPagerState(
        initialPage = defaultTabIndex,
        pageCount = { 2 },
    )
    val scope = rememberCoroutineScope()

    var pendingCustomColor by remember { mutableStateOf<Int?>(null) }

    PreferenceLayout(
        label = label,
        modifier = modifier,
        bottomBar = {
            if (pagerState.currentPage == 0) {
                BottomSpacer()
                return@PreferenceLayout
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Button(
                    enabled = !selectedColorApplied.value,
                    onClick = {
                        adapter.onChange(newValue = ColorOption.CustomColor(selectedColor.intValue))
                        navController.popBackStack()
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
        },
    ) {
        Column {
            Spacer(modifier = Modifier.height(8.dp))

            SelectionIndicator(
                appliedColor = appliedColor,
                pendingCustomColor = pendingCustomColor,
                currentPage = pagerState.currentPage,
                context = context,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            ColorTabRow(
                selectedPage = pagerState.currentPage,
                onSelectPage = { scope.launch { pagerState.animateScrollToPage(it) } },
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                        simple = useSimpleSwatches,
                    )
                    1 -> CustomColorPicker(
                        selectedColor = selectedColor.intValue,
                        onSelect = { newColor ->
                            selectedColor.intValue = newColor
                            pendingCustomColor = newColor
                        },
                        simple = useSimpleSwatches,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Selection indicator banner
// ---------------------------------------------------------------------------

@Composable
private fun SelectionIndicator(
    appliedColor: ColorOption,
    pendingCustomColor: Int?,
    currentPage: Int,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val isDark = isSelectedThemeDark

    val dotColorInt = when {
        currentPage == 1 && pendingCustomColor != null -> pendingCustomColor
        appliedColor is ColorOption.CustomColor -> appliedColor.color
        appliedColor is ColorOption.WallpaperDerived -> appliedColor.color
        else -> appliedColor.colorPreferenceEntry.lightColor(context)
    }
    val dotColor = if (dotColorInt != 0) ComposeColor(dotColorInt)
        else MaterialTheme.colorScheme.primary

    // Tint alpha is stronger on dark backgrounds so the wash registers.
    val containerTint = dotColor.copy(alpha = if (isDark) 0.28f else 0.14f)

    val bigLabel = if (currentPage == 0) {
        stringResource(id = R.string.presets)
    } else {
        stringResource(id = R.string.custom)
    }

    // WallpaperDerived shows "Wallpaper" — this is the fix for the label bug.
    val smallLabel = when (appliedColor) {
        is ColorOption.SystemAccent -> stringResource(id = R.string.system)
        is ColorOption.WallpaperPrimary -> stringResource(id = R.string.wallpaper)
        is ColorOption.WallpaperDerived -> stringResource(id = R.string.wallpaper)
        is ColorOption.Default -> stringResource(id = R.string.managed_by_lawnchair)
        is ColorOption.CustomColor -> stringResource(id = R.string.custom)
        else -> stringResource(id = R.string.custom)
    }

    // Use surfaceContainer so the base is always a visible step above the
    // background in both light and dark mode without touching surfaceContainerHigh.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerTint)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = bigLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = smallLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab row
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
