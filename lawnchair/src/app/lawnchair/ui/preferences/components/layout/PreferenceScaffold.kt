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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import com.android.launcher3.R
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.abs

/**
 * Holds the two title strings, the most-recent AppBarLayout scroll offset,
 * and an optional click handler for the expanded title area.
 * Regular class (not data class) because functions don't have structural equality.
 * Stored as [android.view.View.tag] on the scaffold root view.
 */
private class TitleState(
    var collapsedTitle: String,
    var expandedTitle: String,
    var lastOffset: Int = 0,
    /** Invoked when the user taps the CollapsingToolbar while expanded. Null = not tappable. */
    var onExpandedClick: (() -> Unit)? = null,
    /** Running crossfade animator for the expanded title — cancelled on each new change. */
    var titleAnimator: ValueAnimator? = null,
)

@Composable
fun PreferenceScaffold(
    label: String,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Text shown in the *expanded* toolbar when scrolled to the top.
     * Defaults to [label] so all existing callers are unaffected.
     */
    expandedLabel: String = label,
    /**
     * Optional click handler for the expanded toolbar title area.
     * Pass a non-null lambda to make the expanded title tappable (e.g. to open
     * a system settings screen). Pass null (the default) to disable tapping.
     * The click is suppressed automatically when the toolbar is collapsed.
     */
    onExpandedTitleClick: (() -> Unit)? = null,
    backArrowVisible: Boolean = true,
    /**
     * Background color applied to the root view, scroll view, and app bar.
     *
     * Defaults to [MaterialTheme.colorScheme.surface] (the normal opaque surface).
     * Pass [Color.Transparent] when a blurred wallpaper is rendered behind this
     * scaffold in Compose — all View-level backgrounds will be set to transparent
     * so the bitmap layer underneath shows through.
     *
     * When transparent, the collapsing toolbar content scrim is replaced with a
     * semi-transparent dark scrim so the collapsed toolbar title remains legible.
     */
    containerColor: Color = MaterialTheme.colorScheme.surface,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val containerArgb = containerColor.toArgb()
    // When containerColor is transparent we want the toolbar scrim to be a dark
    // translucent overlay so the collapsed title stays legible over the wallpaper.
    // When opaque, use the normal surfaceContainer token.
    val isTransparent = containerColor == Color.Transparent
    val surfaceContainerColor = if (isTransparent) {
        android.graphics.Color.argb(204, 0, 0, 0) // ~80% black
    } else {
        MaterialTheme.colorScheme.surfaceContainer.toArgb()
    }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

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

            // Apply containerColor to all View surfaces. When transparent, every
            // layer shows nothing — the Compose bitmap layer underneath shows through.
            root.setBackgroundColor(containerArgb)
            scrollView.setBackgroundColor(containerArgb)
            appBarLayout.setBackgroundColor(containerArgb)
            collapsingToolbar.setContentScrimColor(surfaceContainerColor)
            collapsingToolbar.setStatusBarScrimColor(surfaceContainerColor)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)
            toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // ── Dynamic title ─────────────────────────────────────────────
            val titleState = TitleState(
                collapsedTitle = label,
                expandedTitle = expandedLabel,
                onExpandedClick = onExpandedTitleClick,
            )
            root.tag = titleState
            collapsingToolbar.title = expandedLabel

            collapsingToolbar.setOnClickListener {
                val state = root.tag as? TitleState ?: return@setOnClickListener
                val threshold = appBarLayout.totalScrollRange * 0.85f
                if (abs(state.lastOffset) < threshold) {
                    state.onExpandedClick?.invoke()
                }
            }

            appBarLayout.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { bar, verticalOffset ->
                    val state = root.tag as? TitleState ?: return@OnOffsetChangedListener
                    state.lastOffset = verticalOffset
                    val threshold = bar.totalScrollRange * 0.85f
                    val isEffectivelyCollapsed = bar.totalScrollRange > 0 &&
                        abs(verticalOffset) >= threshold
                    val target = if (isEffectivelyCollapsed) state.collapsedTitle else state.expandedTitle
                    if (collapsingToolbar.title != target) collapsingToolbar.title = target
                },
            )

            if (backArrowVisible) {
                toolbar.setNavigationIcon(R.drawable.ic_back)
                toolbar.setNavigationOnClickListener {
                    backDispatcher?.onBackPressed()
                }
            } else {
                toolbar.navigationIcon = null
            }

            // Actions ComposeView
            val actionsComposeView = ComposeView(ctx).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
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

            // bottomBar is pinned outside StretchNestedScrollView so it stays
            // fixed at the bottom of the screen rather than scrolling with content.
            val bottomBarFrame = FrameLayout(ctx).apply {
                layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.BOTTOM }
            }
            val bottomBarComposeView = ComposeView(ctx).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                setContent {
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme,
                        typography = MaterialTheme.typography,
                    ) {
                        bottomBar()
                    }
                }
            }
            bottomBarFrame.addView(bottomBarComposeView)
            (root as CoordinatorLayout).addView(bottomBarFrame)

            bottomBarFrame.doOnLayout { bar ->
                scrollView.updatePadding(bottom = scrollView.paddingBottom + bar.height)
            }

            root
        },
        update = { root ->
            val collapsingToolbar = root.findViewById<CollapsingToolbarLayout>(R.id.preference_collapsing_toolbar)
            val appBarLayout = root.findViewById<AppBarLayout>(R.id.preference_appbar)
            val toolbar = root.findViewById<MaterialToolbar>(R.id.preference_toolbar)
            val scrollView = root.findViewById<StretchNestedScrollView>(R.id.preference_scroll_view)

            // Mirror every setBackgroundColor from factory so theme switches
            // (light↔dark) and blur toggle changes repaint all surfaces correctly.
            root.setBackgroundColor(containerArgb)
            scrollView.setBackgroundColor(containerArgb)
            appBarLayout.setBackgroundColor(containerArgb)
            collapsingToolbar.setContentScrimColor(surfaceContainerColor)
            collapsingToolbar.setStatusBarScrimColor(surfaceContainerColor)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)

            val state = root.tag as? TitleState
            if (state != null) {
                state.collapsedTitle = label
                state.onExpandedClick = onExpandedTitleClick

                val threshold = appBarLayout.totalScrollRange * 0.85f
                val isEffectivelyCollapsed = appBarLayout.totalScrollRange > 0 &&
                    abs(state.lastOffset) >= threshold

                if (state.expandedTitle != expandedLabel) {
                    val incomingTitle = expandedLabel
                    val rgbMask = onSurfaceColor and 0x00FFFFFF

                    state.titleAnimator?.cancel()

                    state.titleAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = 130
                        addUpdateListener { anim ->
                            val alpha = ((anim.animatedValue as Float) * 255).toInt()
                            collapsingToolbar.setExpandedTitleColor(rgbMask or (alpha shl 24))
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                state.expandedTitle = incomingTitle
                                val visibleTitle = if (isEffectivelyCollapsed) label else incomingTitle
                                if (collapsingToolbar.title != visibleTitle) {
                                    collapsingToolbar.title = visibleTitle
                                }
                                state.titleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 130
                                    addUpdateListener { anim ->
                                        val alpha = ((anim.animatedValue as Float) * 255).toInt()
                                        collapsingToolbar.setExpandedTitleColor(rgbMask or (alpha shl 24))
                                    }
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)
                                        }
                                    })
                                    start()
                                }
                            }
                        })
                        start()
                    }
                } else {
                    val target = if (isEffectivelyCollapsed) label else expandedLabel
                    if (collapsingToolbar.title != target) collapsingToolbar.title = target
                }
            } else {
                collapsingToolbar.title = label
            }

            toolbar.navigationIcon?.setTint(onSurfaceColor)
        },
    )
}
