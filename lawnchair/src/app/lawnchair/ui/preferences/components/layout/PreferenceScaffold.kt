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

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.android.launcher3.R
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar

@Composable
fun PreferenceScaffold(
    label: String,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier,
    backArrowVisible: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // Read colors from Compose MaterialTheme — these are Lawnchair's dynamic colors
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            // Only use ContextThemeWrapper for inflation — so Material3 attrs resolve
            // We override the actual colors below using Compose MaterialTheme values
            val themedCtx = ContextThemeWrapper(
                ctx,
                MaterialR.style.Theme_Material3_DayNight_NoActionBar,
            )

            val root = LayoutInflater.from(themedCtx)
                .inflate(R.layout.lawnchair_preference_scaffold, null, false)

            val appBarLayout = root.findViewById<AppBarLayout>(R.id.preference_appbar)
            val collapsingToolbar = root.findViewById<CollapsingToolbarLayout>(R.id.preference_collapsing_toolbar)
            val toolbar = root.findViewById<MaterialToolbar>(R.id.preference_toolbar)
            val contentFrame = root.findViewById<FrameLayout>(R.id.preference_content)
            val scrollView = root.findViewById<NestedScrollView>(R.id.preference_scroll_view)

            // Apply bottom inset
            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
                insets
            }

            // Apply Lawnchair's dynamic colors to View components
            appBarLayout.setBackgroundColor(surfaceColor)
            collapsingToolbar.setContentScrimColor(surfaceColor)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)
            toolbar.setBackgroundColor(surfaceColor)

            // Title
            collapsingToolbar.title = label

            // Back button with dynamic color
            if (backArrowVisible) {
                toolbar.setNavigationIcon(R.drawable.ic_back)
                toolbar.navigationIcon?.setTint(onSurfaceColor)
                toolbar.setNavigationOnClickListener {
                    backDispatcher?.onBackPressed()
                }
            } else {
                toolbar.navigationIcon = null
            }

            // Embed Compose content using original ctx so all Lawnchair theme/colors apply
            val composeView = ComposeView(ctx).apply {
                setContent {
                    content(PaddingValues())
                }
            }
            contentFrame.addView(composeView)

            root
        },
        update = { root ->
            val collapsingToolbar = root.findViewById<CollapsingToolbarLayout>(R.id.preference_collapsing_toolbar)
            val appBarLayout = root.findViewById<AppBarLayout>(R.id.preference_appbar)
            val toolbar = root.findViewById<MaterialToolbar>(R.id.preference_toolbar)

            // Re-apply dynamic colors on recomposition (e.g. theme change)
            appBarLayout.setBackgroundColor(surfaceColor)
            collapsingToolbar.setContentScrimColor(surfaceColor)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)
            toolbar.setBackgroundColor(surfaceColor)
            collapsingToolbar.title = label

            toolbar.navigationIcon?.setTint(onSurfaceColor)
        },
    )
}
