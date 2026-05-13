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
import androidx.compose.material3.MaterialTheme
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
 * When the user has enabled the settings background blur in GeneralPreferences, every
 * preference screen will show the blurred wallpaper behind its content.  The blur is
 * rendered entirely in Compose — toggling the preference recomposes immediately without
 * requiring an activity recreate.
 *
 * @param label the text shown in the *collapsed* toolbar and used as the default
 *   expanded title (e.g. "Settings")
 * @param expandedLabel the text shown in the *expanded* toolbar when scrolled to the
 *   top.  Defaults to [label] so existing callers need no changes.  Pass a different
 *   string to repurpose the generous space the large expanded title provides —
 *   for example a contextual prompt that replaces the plain screen title while the
 *   user hasn't scrolled yet.
 * @param onExpandedTitleClick optional click handler for the expanded title area.
 *   Pass a lambda to make the large title tappable; pass null (default) to disable.
 *   The tap is automatically suppressed once the toolbar collapses.
 * @param backArrowVisible whether to show the back arrow or not
 * @param verticalArrangement the vertical arrangement of the layout's children
 * @param horizontalAlignment the horizontal alignment of the layout's children
 * @param scrollState the [ScrollState] to use to allow vertical overflow
 * @param actions what content to show at the top-right of the layout
 * @param bottomBar what content to show at the bottom of the layout
 * @param content the actual content
 * @see [PreferenceLayoutLazyColumn]
 *
 * TODO: use DSL to represent all preferences
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
    SettingsBlurContainer(modifier = modifier) { blurModifier ->
        PreferenceScaffold(
            modifier = blurModifier,
            backArrowVisible = backArrowVisible,
            label = label,
            expandedLabel = expandedLabel,
            onExpandedTitleClick = onExpandedTitleClick,
            isExpandedScreen = isExpandedScreen,
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
 *
 * TODO: use DSL to represent all preferences
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
    SettingsBlurContainer { blurModifier ->
        PreferenceScaffold(
            backArrowVisible = backArrowVisible,
            label = label,
            isExpandedScreen = isExpandedScreen,
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
 * Wraps [content] with a blurred wallpaper background when the user has
 * enabled [app.lawnchair.preferences.PreferenceManager.settingsBlurBackground].
 *
 * When blur is enabled:
 *  - The wallpaper is captured and blurred on [Dispatchers.IO] via [SettingsWallpaperBlurHelper].
 *  - The blurred bitmap is drawn full-screen behind [content].
 *  - A 43 % black scrim is composited on top for contrast.
 *  - [MaterialTheme.colorScheme.background] is locally overridden to [Color.Transparent]
 *    so that the scaffold surface does not paint over the wallpaper layer.
 *
 * Toggling the preference causes an immediate recomposition — no recreate needed.
 */
@Composable
private fun SettingsBlurContainer(
    modifier: Modifier = Modifier,
    content: @Composable (innerModifier: Modifier) -> Unit,
) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val blurEnabled = prefs.settingsBlurBackground.getAdapter().state.value
    val blurIntensity = prefs.settingsBlurIntensity.getAdapter().state.value.toInt()

    // Re-runs whenever blurEnabled or blurIntensity changes.
    val blurredBitmap by produceState(
        initialValue = null as android.graphics.Bitmap?,
        key1 = blurEnabled,
        key2 = blurIntensity,
    ) {
        value = if (blurEnabled) {
            withContext(Dispatchers.IO) {
                SettingsWallpaperBlurHelper.getBlurredBitmap(context, blurIntensity)
            }
        } else {
            null
        }
    }

    if (!blurEnabled || blurredBitmap == null) {
        // Blur off or bitmap not ready yet — render content with default theming.
        content(modifier)
        return
    }

    // Override the Material3 background token to transparent so the Scaffold
    // surface does not paint an opaque colour over the wallpaper layer.
    val transparentScheme = MaterialTheme.colorScheme.copy(background = Color.Transparent)

    MaterialTheme(colorScheme = transparentScheme) {
        Box(modifier = modifier.fillMaxSize()) {
            // Layer 1 — blurred wallpaper
            Image(
                bitmap = blurredBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Layer 2 — dark scrim (~43 % opacity) for legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.43f)),
            )
            // Layer 3 — actual preference content (transparent scaffold surface)
            content(Modifier)
        }
    }
}
