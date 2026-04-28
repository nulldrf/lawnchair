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
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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

    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    // Capture the parent CompositionContext so both inner ComposeViews can be
    // linked to it via setParentCompositionContext(). Without this, each ComposeView
    // schedules its initial composition independently on the next Recomposer frame
    // (Frame N+1), causing content to be absent during the enter animation.
    // With the parent context, their initial compositions are enqueued in the same
    // Recomposer pipeline as the navigation transition — Frame N — so content is
    // ready before the first draw pass.
    val parentCompositionContext = rememberCompositionContext()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
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
            val actionsFrame = root.findViewById<FrameLayout>(R.id.preference_toolbar_actions)
            val scrollView = root.findViewById<StretchNestedScrollView>(R.id.preference_scroll_view)

            // Bottom inset for nav bar
            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
                insets
            }

            // Set the root background immediately. The inner ComposeView defers its
            // first composition to the frame after window attachment (Frame N+1), so
            // on first navigation the content area is empty during the enter animation
            // (Frame N). Without this, Compose's AnimatedContent animates a transparent
            // rectangle, making the transition look instant/broken. Setting surfaceColor
            // here ensures Frame N shows the correct opaque surface rather than blank.
            root.setBackgroundColor(surfaceColor)
            scrollView.setBackgroundColor(surfaceColor)

            // Apply Lawnchair dynamic colors
            appBarLayout.setBackgroundColor(surfaceColor)
            collapsingToolbar.setContentScrimColor(surfaceContainerColor)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)
            toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            collapsingToolbar.title = label

            // Back button
            if (backArrowVisible) {
                toolbar.setNavigationIcon(R.drawable.ic_back)
                toolbar.navigationIcon?.setTint(onSurfaceColor)
                toolbar.setNavigationOnClickListener {
                    backDispatcher?.onBackPressed()
                }
            } else {
                toolbar.navigationIcon = null
            }

            // Toolbar actions — setParentCompositionContext links this ComposeView to the
            // parent Recomposer so its initial composition is batched with Frame N.
            val actionsComposeView = ComposeView(ctx).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL,
                )
                setContent {
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme,
                        typography = MaterialTheme.typography,
                    ) {
                        Row { actions() }
                    }
                }
            }
            actionsFrame.addView(actionsComposeView)

            // Start invisible — content will fade in after its first composition
            // completes (see DisposableEffect inside setContent below). This prevents
            // the navigation enter animation from playing over a blank content area
            // on heavy screens where layout takes more than one frame to settle.
            contentFrame.alpha = 0f

            // Main content — setParentCompositionContext is the key fix for the missing
            // enter animation on first navigation. It enqueues the initial composition in
            // the same Recomposer frame as the parent (Frame N) so content is rendered
            // before the first draw pass, rather than deferring to Frame N+1.
            val composeView = ComposeView(ctx).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                setContent {
                    // Fade the content frame in after the first composition so the
                    // navigation transition never plays over an empty layout.
                    // 150ms is short enough to feel instant on simple screens and
                    // smooth enough to mask the layout delay on heavy ones.
                    DisposableEffect(Unit) {
                        contentFrame.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                        onDispose { }
                    }
                    CompositionLocalProvider(
                        LocalInsideNestedScrollView provides true,
                    ) {
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme,
                            typography = MaterialTheme.typography,
                        ) {
                            content(PaddingValues())
                        }
                    }
                }
            }
            contentFrame.addView(composeView)

            root
        },
        update = { root ->
            val collapsingToolbar = root.findViewById<CollapsingToolbarLayout>(R.id.preference_collapsing_toolbar)
            val appBarLayout = root.findViewById<AppBarLayout>(R.id.preference_appbar)
            val toolbar = root.findViewById<MaterialToolbar>(R.id.preference_toolbar)
            val scrollView = root.findViewById<StretchNestedScrollView>(R.id.preference_scroll_view)

            // Must mirror every setBackgroundColor call from factory so theme
            // switches (light↔dark) repaint all surfaces. Omitting these was
            // causing root/scrollView to stay at the old theme's surface color.
            root.setBackgroundColor(surfaceColor)
            scrollView.setBackgroundColor(surfaceColor)
            appBarLayout.setBackgroundColor(surfaceColor)
            collapsingToolbar.setContentScrimColor(surfaceContainerColor)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)
            collapsingToolbar.title = label
            toolbar.navigationIcon?.setTint(onSurfaceColor)
        },
    )
}
