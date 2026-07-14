/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.Keep
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.invariantDeviceProfile
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralCustomIconShapeCreator
import app.lawnchair.ui.theme.preferenceGroupColor
import com.android.launcher3.R
import kotlinx.coroutines.launch

@Keep
enum class ShapeRoute {
    APP_SHAPE,
    FOLDER_SHAPE,
}

fun iconShapeEntries(context: Context): List<ListPreferenceEntry<IconShape>> {
    val systemShape = IconShapeManager.getSystemIconShape(context)
    return listOf(
        ListPreferenceEntry(systemShape) { stringResource(id = R.string.icon_shape_system) },
        ListPreferenceEntry(IconShape.Circle) { stringResource(id = R.string.icon_shape_circle) },
        ListPreferenceEntry(IconShape.Cylinder) { stringResource(id = R.string.icon_shape_cylinder) },
        ListPreferenceEntry(IconShape.Diamond) { stringResource(id = R.string.icon_shape_diamond) },
        ListPreferenceEntry(IconShape.Egg) { stringResource(id = R.string.icon_shape_egg) },
        ListPreferenceEntry(IconShape.Hexagon) { stringResource(id = R.string.icon_shape_hexagon) },
        ListPreferenceEntry(IconShape.Cupertino) { stringResource(id = R.string.icon_shape_cupertino) },
        ListPreferenceEntry(IconShape.Octagon) { stringResource(id = R.string.icon_shape_octagon) },
        ListPreferenceEntry(IconShape.Sammy) { stringResource(id = R.string.icon_shape_sammy) },
        ListPreferenceEntry(IconShape.RoundedSquare) { stringResource(id = R.string.icon_shape_rounded_square) },
        ListPreferenceEntry(IconShape.SharpSquare) { stringResource(id = R.string.icon_shape_sharp_square) },
        ListPreferenceEntry(IconShape.Square) { stringResource(id = R.string.icon_shape_square) },
        ListPreferenceEntry(IconShape.Squircle) { stringResource(id = R.string.icon_shape_squircle) },
        ListPreferenceEntry(IconShape.Teardrop) { stringResource(id = R.string.icon_shape_teardrop) },
        ListPreferenceEntry(IconShape.VerySunny) { stringResource(id = R.string.icon_shape_very_sunny) },
        ListPreferenceEntry(IconShape.ComplexClover) { stringResource(id = R.string.icon_shape_complex_clover) },
        ListPreferenceEntry(IconShape.FourSidedCookie) { stringResource(id = R.string.icon_shape_four_sided_cookie) },
        ListPreferenceEntry(IconShape.SevenSidedCookie) { stringResource(id = R.string.icon_shape_seven_sided_cookie) },
        ListPreferenceEntry(IconShape.Arch) { stringResource(id = R.string.icon_shape_arch) },
        ListPreferenceEntry(IconShape.Cloudy) { stringResource(id = R.string.icon_shape_cloudy) },
        ListPreferenceEntry(IconShape.Flower) { stringResource(id = R.string.icon_shape_flower) },
        ListPreferenceEntry(IconShape.Heart) { stringResource(id = R.string.icon_shape_heart) },
        ListPreferenceEntry(IconShape.Leaf) { stringResource(id = R.string.icon_shape_leaf) },
        ListPreferenceEntry(IconShape.Meow) { stringResource(id = R.string.icon_shape_meow) },
        ListPreferenceEntry(IconShape.Pebble) { stringResource(id = R.string.icon_shape_pebble) },
        ListPreferenceEntry(IconShape.RoundedHexagon) { stringResource(id = R.string.icon_shape_roundedhexagon) },
        ListPreferenceEntry(IconShape.Stretched) { stringResource(id = R.string.icon_shape_stretched) },
        ListPreferenceEntry(IconShape.Vessel) { stringResource(id = R.string.icon_shape_vessel) },
    )
}

// ---------------------------------------------------------------------------
// Top-level entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapePreference(
    modifier: Modifier = Modifier,
    currentTab: ShapeRoute = ShapeRoute.APP_SHAPE,
) {
    // Folder shape customisation is now stable — always show both tabs.
    TwoTabShapePreference(
        modifier = modifier,
        currentTab = currentTab,
    )
}

// ---------------------------------------------------------------------------
// Two-tab variant (app shape + folder shape)
// Inlined instead of using TwoTabPreferenceLayout so the mockup can sit
// above the tab row and react to whichever tab is active.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TwoTabShapePreference(
    modifier: Modifier = Modifier,
    currentTab: ShapeRoute = ShapeRoute.APP_SHAPE,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val entries = remember { iconShapeEntries(context) }
    val iconShapeAdapter = prefs2.iconShape.getAdapter()
    val folderShapeAdapter = prefs2.folderShape.getAdapter()
    val customIconShape = prefs2.customIconShape.asState()
    val customFolderShape = prefs2.customFolderShape.asState()

    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { 2 },
    )
    val scope = rememberCoroutineScope()

    // The active adapter drives the mockup — switches when the user swipes tabs.
    val activeAdapter = if (pagerState.currentPage == 0) iconShapeAdapter else folderShapeAdapter

    PreferenceLayout(
        label = stringResource(id = R.string.icon_shape_label),
        modifier = modifier,
        backArrowVisible = !LocalIsExpandedScreen.current,
        isExpandedScreen = true,
    ) {
        // ── Mockup ───────────────────────────────────────────────────────────
        ShapeMockup(shapeAdapter = activeAdapter)

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tab row ──────────────────────────────────────────────────────────
        ShapeTabRow(
            selectedPage = pagerState.currentPage,
            onSelectPage = { scope.launch { pagerState.scrollToPage(it) } },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Pager ────────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
        ) { page ->
            Column {
                when (page) {
                    0 -> ShapeGridContent(
                        entries = entries,
                        shapeAdapter = iconShapeAdapter,
                        customIconShape = customIconShape.value,
                        currentTab = ShapeRoute.APP_SHAPE,
                    )
                    1 -> ShapeGridContent(
                        entries = entries,
                        shapeAdapter = folderShapeAdapter,
                        customIconShape = customFolderShape.value,
                        currentTab = ShapeRoute.FOLDER_SHAPE,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Phone-frame mockup
// ---------------------------------------------------------------------------

/**
 * Live phone-frame preview that re-renders whenever [shapeAdapter] changes,
 * using the same gap-free pattern established in IconPackPreferences:
 * width-only constraint + DummyLauncherBox internal aspectRatio owns the height.
 */
@Composable
private fun ShapeMockup(
    shapeAdapter: PreferenceAdapter<IconShape>,
) {
    val primary = MaterialTheme.colorScheme.primary
    val phoneShape = RoundedCornerShape(28.dp)
    val borderColor = primary.copy(alpha = 0.25f)
    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val widthFraction = if (isLandscape) 0.45f else 0.65f
    val idp = invariantDeviceProfile()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth(widthFraction)) {
            WithWallpaper(displayWallpaperButton = false) { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = borderColor, shape = phoneShape)
                        .clip(phoneShape),
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // key() forces DummyLauncherLayout to recompose when the
                    // shape changes so the live preview updates immediately.
                    key(shapeAdapter.state.value) {
                        DummyLauncherLayout(
                            idp = idp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab row (same outlined/filled style as IconPackPreferences)
// ---------------------------------------------------------------------------

@Composable
private fun ShapeTabRow(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
) {
    val tabs = listOf(
        stringResource(id = R.string.app_icon_shape_label),
        stringResource(id = R.string.folder_shape_label),
    )
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = selectedPage == index
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) primary else Color.Transparent,
                animationSpec = tween(250), label = "tab_bg_$index",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) onPrimary else onSurface,
                animationSpec = tween(250), label = "tab_fg_$index",
            )
            val cornerRadius by animateDpAsState(
                targetValue = if (isSelected) 50.dp else 16.dp,
                animationSpec = tween(250), label = "tab_corner_$index",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) Color.Transparent else outline,
                animationSpec = tween(250), label = "tab_border_$index",
            )
            Surface(
                onClick = { onSelectPage(index) },
                modifier = Modifier.weight(1f).height(44.dp),
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
// Shape grid content (grid + custom shape button at bottom)
// ---------------------------------------------------------------------------

@Composable
private fun ShapeGridContent(
    entries: List<ListPreferenceEntry<IconShape>>,
    shapeAdapter: PreferenceAdapter<IconShape>,
    customIconShape: IconShape?,
    currentTab: ShapeRoute = ShapeRoute.APP_SHAPE,
) {
    val navController = LocalNavController.current

    PreferenceGroup(heading = stringResource(id = R.string.presets)) {
        ShapeGrid(
            entries = entries,
            shapeAdapter = shapeAdapter,
        )
    }

    // Custom shape option — shown after the presets grid.
    // If a custom shape exists it appears as a selectable card before the
    // create/edit button, matching the visual language of the presets grid.
    if (customIconShape != null) {
        PreferenceGroup(heading = stringResource(id = R.string.custom)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShapeCard(
                    iconShape = customIconShape,
                    label = stringResource(id = R.string.custom),
                    selected = IconShape.isCustomShape(shapeAdapter.state.value),
                    modifier = Modifier.size(80.dp),
                    onClick = { shapeAdapter.onChange(customIconShape) },
                )
            }
            ModifyCustomIconShapePreference(
                customIconShape = customIconShape,
                currentTab = currentTab,
            )
        }
    } else {
        PreferenceGroup(heading = stringResource(id = R.string.custom)) {
            ModifyCustomIconShapePreference(
                customIconShape = null,
                currentTab = currentTab,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shape grid row
// ---------------------------------------------------------------------------

@Composable
private fun ShapeGrid(
    entries: List<ListPreferenceEntry<IconShape>>,
    shapeAdapter: PreferenceAdapter<IconShape>,
) {
    val lazyListState = rememberLazyListState()
    val selectedShape = shapeAdapter.state.value

    // Scroll to selected shape on first composition.
    LaunchedEffect(selectedShape) {
        val index = entries.indexOfFirst { it.value == selectedShape }
        if (index != -1) lazyListState.scrollToItem(index)
    }

    LazyRow(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        items(entries, key = { it.value.key }) { entry ->
            ShapeCard(
                iconShape = entry.value,
                label = entry.label(),
                selected = entry.value == selectedShape,
                modifier = Modifier.size(72.dp),
                onClick = { shapeAdapter.onChange(entry.value) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Individual shape card
// ---------------------------------------------------------------------------

/**
 * A square card showing only the shape preview canvas. No label is shown
 * unless the user long-presses, which triggers a [PlainTooltip] with the
 * shape name — keeping the grid clean while remaining discoverable.
 *
 * Selected state: [MaterialTheme.colorScheme.primaryContainer] background.
 * Unselected state: [preferenceGroupColor] background.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ShapeCard(
    iconShape: IconShape,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        state = tooltipState,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                preferenceGroupColor()
            },
            modifier = modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { scope.launch { tooltipState.show() } },
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Shape canvas — fills 60% of the card area
                IconShapePreview(
                    iconShape = iconShape,
                    modifier = Modifier.fillMaxSize(0.6f),
                )

            }
        }
    }
}

// ---------------------------------------------------------------------------
// Create / Edit custom icon shape button (unchanged logic, kept at bottom)
// ---------------------------------------------------------------------------

@Composable
private fun ModifyCustomIconShapePreference(
    customIconShape: IconShape?,
    currentTab: ShapeRoute,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    // Pass the active tab so the creator screen opens in the right context.
    val route = GeneralCustomIconShapeCreator(selectedId = currentTab)

    val created = customIconShape != null

    val text = stringResource(
        when (currentTab) {
            ShapeRoute.APP_SHAPE -> if (created) R.string.custom_icon_shape_edit else R.string.custom_icon_shape_create
            ShapeRoute.FOLDER_SHAPE -> if (created) R.string.custom_folder_shape_edit else R.string.custom_folder_shape_create
        },
    )

    val icon = if (created) Icons.Rounded.Edit else Icons.Rounded.Add

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { navController.navigate(route = route) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.secondary,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium,
            ) {
                Text(text = text)
            }
            Spacer(modifier = Modifier.requiredWidth(12.dp))
            Icon(
                imageVector = icon,
                tint = MaterialTheme.colorScheme.secondary,
                contentDescription = null,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// IconShapePreview canvas (unchanged — reused by grid cards and GeneralPreferences)
// ---------------------------------------------------------------------------

@Composable
fun IconShapePreview(
    iconShape: IconShape,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
) {
    // getMaskPath() on SystemBased delegates to nearestShape.getMaskPath(), which
    // approximates the system shape from a short list. On Samsung, the nearest
    // match is Circle — making the System card indistinguishable from Circle in
    // the preview even though actual icons use the real squircle path.
    //
    // Fix: for SystemBased, bypass getMaskPath() and read the raw iconMask from
    // AdaptiveIconDrawable directly. Android normalises this path to [0,100]×[0,100]
    // (MASK_SIZE = 100 in AOSP), so it slots into our scaling pipeline unchanged.
    // For all other shapes, getMaskPath() is correct as documented.
    val basePath = remember(iconShape) {
        if (iconShape is IconShape.SystemBased) {
            AdaptiveIconDrawable(null, null).iconMask
        } else {
            iconShape.getMaskPath()
        }
    }

    Canvas(modifier = modifier.requiredSize(48.dp)) {
        // Step 1: scale from the 100×100 viewport to the actual canvas size.
        val scaleX = size.width / 100f
        val scaleY = size.height / 100f
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY)

        val scaledPath = AndroidPath()
        basePath.transform(matrix, scaledPath)

        // Step 2: center the scaled path within the canvas.
        // Shapes like Hexagon, Teardrop etc. don't fill the full [0,100]
        // viewport symmetrically, so after scaling their bounding box sits
        // off-center. computeBounds() gives us the actual bounds so we can
        // translate to the geometric center of the canvas.
        val bounds = android.graphics.RectF()
        scaledPath.computeBounds(bounds, true)
        val dx = (size.width - bounds.width()) / 2f - bounds.left
        val dy = (size.height - bounds.height()) / 2f - bounds.top
        scaledPath.offset(dx, dy)

        val composePath = scaledPath.asComposePath()
        drawPath(path = composePath, color = fillColor)
        drawPath(path = composePath, color = strokeColor, style = Stroke(width = 4f))
    }
}
