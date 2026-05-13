/*
 * Copyright 2021, Lawnchair
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.SettingsWallpaperBlurHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents the layout of all Preference screens.
 * Uses a combination of [PreferenceScaffold] and [PreferenceColumn] to represent the layout.
 *
 * When the user has enabled the settings background blur, every preference screen shows the
 * blurred wallpaper behind its content. The blur bitmap is cached in
 * [SettingsWallpaperBlurHelper] so navigation between screens is instant with no flash.
 *
 * @param label the text shown in the *collapsed* toolbar and used as the default
 *   expanded title (e.g. "Settings")
 * @param expandedLabel the text shown in the *expanded* toolbar when scrolled to the top.
 * @param onExpandedTitleClick optional click handler for the expanded title area.
 * @param backArrowVisible whether to show the back arrow or not
 * @param verticalArrangement the vertical arrangement of the layout's children
 * @param horizontalAlignment the horizontal alignment of the layout's children
 * @param scrollState the [ScrollState] to use to allow vertical overflow
 * @param actions what content to show at the top-right of the layout
 * @param bottomBar what content to show at the bottom of the layout
 * @param content the actual content
 * @see [PreferenceLayoutLazyColumn]
 */
@Composable
fun PreferenceLayout(
    label: String,
    expandedLabel: String = label,
    onExpandedTitleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backArrowVisible: Boolean = true,
    isExpandedScreen: Boolean = LocalIsExpandedScreen.current,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState? = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsBlurContainer(modifier = modifier) { blurModifier, containerColor ->
        PreferenceScaffold(
            modifier = blurModifier,
            backArrowVisible = backArrowVisible,
            label = label,
            expandedLabel = expandedLabel,
            onExpandedTitleClick = onExpandedTitleClick,
            isExpandedScreen = isExpandedScreen,
            containerColor = containerColor,
            actions = actions,
            bottomBar = bottomBar,
        ) {
            PreferenceColumn(
                contentPadding = it,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                scrollState = scrollState,
                content = content,
            )
        }
    }
}

/**
 * Represents the layout of all Preference screens.
 * This composable only composes and lays out the currently visible items.
 * Uses a combination of [PreferenceScaffold] and [PreferenceLazyColumn] to represent the layout.
 *
 * @param label the text to be displayed at the top of the screen
 * @param modifier the [Modifier] to apply at [PreferenceLazyColumn]
 * @param enabled whether the layout allows user input or not
 * @param backArrowVisible whether to show the back arrow or not
 * @param state the state object to be used to control or observe the list's state
 * @param actions what content to show at the top-right of the layout
 * @param content the actual content
 * @see [PreferenceLayout]
 */
@Composable
fun PreferenceLayoutLazyColumn(
    label: String,
    modifier: Modifier = Modifier,
    isExpandedScreen: Boolean = LocalIsExpandedScreen.current,
    enabled: Boolean = true,
    backArrowVisible: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    SettingsBlurContainer { blurModifier, containerColor ->
        PreferenceScaffold(
            backArrowVisible = backArrowVisible,
            label = label,
            isExpandedScreen = isExpandedScreen,
            containerColor = containerColor,
            actions = actions,
        ) {
            PreferenceLazyColumn(
                contentPadding = it,
                modifier = modifier.then(blurModifier),
                enabled = enabled,
                state = state,
                content = content,
            )
        }
    }
}

// ─── Internal blur container ──────────────────────────────────────────────────

/**
 * Wraps [content] with a blurred wallpaper background when the user has enabled
 * [app.lawnchair.preferences.PreferenceManager.settingsBlurBackground].
 *
 * The [content] lambda receives:
 *  - [Modifier] — pass-through (always [Modifier])
 *  - [Color] — [Color.Transparent] when blur is active, [Color.Unspecified] otherwise.
 *    Pass this to [PreferenceScaffold] as `containerColor` so its View-level
 *    `setBackgroundColor` calls (root, scrollView, appBarLayout, collapsingToolbar)
 *    are all transparent, letting the bitmap layers below show through.
 *
 * The bitmap is cached in [SettingsWallpaperBlurHelper] and passed as the
 * `initialValue` of `produceState`, so navigation between screens is instant —
 * the cached bitmap is already available on the first frame with no flash.
 *
 * When the toggle is turned off, the cache is cleared and memory is freed.
 */
@Composable
private fun SettingsBlurContainer(
    modifier: Modifier = Modifier,
    content: @Composable (innerModifier: Modifier, containerColor: Color) -> Unit,
) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val blurEnabled = prefs.settingsBlurBackground.getAdapter().state.value
    val blurIntensity = prefs.settingsBlurIntensity.getAdapter().state.value.toInt()

    // initialValue is the cached bitmap (non-null on revisited screens) so the
    // first frame renders the wallpaper immediately without waiting for IO.
    val blurredBitmap by produceState(
        initialValue = SettingsWallpaperBlurHelper.getCachedBitmap(blurEnabled, blurIntensity),
        key1 = blurEnabled,
        key2 = blurIntensity,
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

    if (!blurEnabled || blurredBitmap == null) {
        // Blur off or not yet computed — normal opaque surface.
        // Color.Unspecified tells PreferenceScaffold to use its own default.
        content(modifier, Color.Unspecified)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1 — blurred wallpaper
        Image(
            bitmap = blurredBitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Layer 2 — dark scrim (~43% opacity) for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.43f)),
        )
        // Layer 3 — scaffold with all View backgrounds transparent so layers 1+2 show through
        content(Modifier, Color.Transparent)
    }
}
