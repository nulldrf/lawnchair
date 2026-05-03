package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import android.graphics.Color
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    // Page 0 = Presets (wallpaper colors), Page 1 = Custom (static grid + canvas dialog)
    val defaultTabIndex = when {
        dynamicEntries.any { it.value == appliedColor } -> 0
        staticEntries.any { it.value == appliedColor } -> 0
        appliedColor is ColorOption.WallpaperPrimary -> 0
        else -> 1
    }

    val pagerState = rememberPagerState(
        initialPage = defaultTabIndex,
        pageCount = { 2 },
    )
    val scope = rememberCoroutineScope()

    PreferenceLayout(
        label = label,
        modifier = modifier,
        bottomBar = {
            // Apply button only shown on the Custom page when a new color
            // has been selected but not yet committed to the preference.
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
            // Centered M3 segmented button row
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
            ) {
                SegmentedButton(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(text = stringResource(id = R.string.presets)) },
                )
                SegmentedButton(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(text = stringResource(id = R.string.custom)) },
                )
            }

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                when (page) {
                    // Presets: wallpaper-extracted color swatches only
                    0 -> {
                        WallpaperColorGrid(
                            onSwatchClick = { option ->
                                selectedColor.intValue = option.forCustomPicker(context)
                                adapter.onChange(newValue = option)
                            },
                            isSwatchSelected = { it == appliedColor },
                        )
                    }

                    // Custom: static color grid + canvas picker dialog
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

private fun ColorOption.forCustomPicker(context: Context): Int {
    val color = colorPreferenceEntry.lightColor(context)
    if (color == 0) return Color.BLACK
    return color
}
