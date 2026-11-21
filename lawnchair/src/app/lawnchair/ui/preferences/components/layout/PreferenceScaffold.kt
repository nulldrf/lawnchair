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

package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.SettingsWallpaperBlurHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
internal class PreferenceTopBarState(
    val scrollBehavior: TopAppBarScrollBehavior,
) {
    var topBarBottomInRoot by mutableFloatStateOf(0f)
}

internal val LocalPreferenceTopBarState = compositionLocalOf<PreferenceTopBarState?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScaffold(
    label: String,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier,
    expandedLabel: String = label,
    onExpandedTitleClick: (() -> Unit)? = null,
    backArrowVisible: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
    content: @Composable (PaddingValues) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = if (isExpandedScreen) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val topBarState = remember(scrollBehavior) { PreferenceTopBarState(scrollBehavior) }

    // ── Settings background blur (pure Compose, matching PreferenceSearchScaffold) ──
    val prefs = preferenceManager()
    val blurEnabled = prefs.settingsBlurBackground.getAdapter().state.value
    val blurIntensity = prefs.settingsBlurIntensity.getAdapter().state.value.toInt()

    val configuration = LocalConfiguration.current
    val screenBounds = remember(configuration) { SettingsWallpaperBlurHelper.screenBounds(context) }
    val screenWidth = screenBounds.width()
    val screenHeight = screenBounds.height()

    val blurredBitmap by produceState(
        initialValue = SettingsWallpaperBlurHelper.getCachedBitmap(blurEnabled, blurIntensity, screenWidth, screenHeight),
        blurEnabled, blurIntensity, screenWidth, screenHeight,
    ) {
        value = if (blurEnabled) {
            withContext(Dispatchers.IO) {
                SettingsWallpaperBlurHelper.getBlurredBitmap(context, blurIntensity)
            }
        } else {
            SettingsWallpaperBlurHelper.clearCache()
            null
        }
    }

    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
    val scrimColor = Color(ColorUtils.setAlphaComponent(surfaceArgb, (0.72f * 255).toInt()))
    val bitmap = blurredBitmap
    val showBlur = blurEnabled && bitmap != null && !bitmap.isRecycled

    Box(modifier = modifier.fillMaxSize()) {
        if (showBlur) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor),
            )
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = if (showBlur) Color.Transparent else MaterialTheme.colorScheme.surface,
            topBar = {
                TopBar(
                    backArrowVisible = backArrowVisible,
                    label = label,
                    expandedLabel = expandedLabel,
                    onExpandedTitleClick = onExpandedTitleClick,
                    isExpandedScreen = isExpandedScreen,
                    containerColor = if (showBlur) Color.Transparent else MaterialTheme.colorScheme.surface,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        topBarState.topBarBottomInRoot = coords.positionInRoot().y + coords.size.height
                    },
                )
            },
            bottomBar = bottomBar,
        ) {
            CompositionLocalProvider(LocalPreferenceTopBarState provides topBarState) {
                content(it)
            }
        }
    }
}
