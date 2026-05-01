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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
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
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    // Capture colorScheme and typography from the outer LawnchairTheme scope.
    // Reading MaterialTheme.* inside setContent{} reads from the inner ComposeView's
    // composition root which has no LawnchairTheme provider — falling back to default
    // M3 values (system font, default colors).
    // State is updated via SideEffect (non-composable lambda) after capturing the
    // @Composable values into plain vals first, avoiding the compiler error caused by
    // passing a @Composable expression directly to a non-composable delegate setter.
    val currentColorScheme = MaterialTheme.colorScheme
    val currentTypography = MaterialTheme.typography
    val innerColorSchemeState = remember { mutableStateOf(currentColorScheme) }
    val innerTypographyState = remember { mutableStateOf(currentTypography) }
    SideEffect {
        innerColorSchemeState.value = currentColorScheme
        innerTypographyState.value = currentTypography
    }

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

            // ── Dynamic title ─────────────────────────────────────────────
            // The expanded title shows expandedLabel (e.g. a contextual prompt)
            // while the user is scrolled to the top. Once the toolbar collapses
            // it switches to label (e.g. "Settings") so the compact bar always
            // shows the plain screen name. CollapsingToolbarLayout animates the
            // crossfade for free via its normal collapse animation.
            val titleState = TitleState(
                collapsedTitle = label,
                expandedTitle = expandedLabel,
                onExpandedClick = onExpandedTitleClick,
            )
            root.tag = titleState
            collapsingToolbar.title = expandedLabel

            // Single click listener registered once. Reads the latest callback from
            // TitleState (kept up-to-date by the update block) so it never needs
            // re-registration. Only fires when the toolbar is in the expanded zone.
            collapsingToolbar.setOnClickListener {
                val state = root.tag as? TitleState ?: return@setOnClickListener
                val threshold = appBarLayout.totalScrollRange * 0.85f
                if (abs(state.lastOffset) < threshold) {
                    state.onExpandedClick?.invoke()
                }
            }

            appBarLayout.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { appBar, verticalOffset ->
                    val state = root.tag as? TitleState ?: return@OnOffsetChangedListener
                    state.lastOffset = verticalOffset
                    // Switch at 85% of collapse so the string change happens while
                    // CollapsingToolbarLayout's own crossfade already has the expanded
                    // title mostly faded out — the switch is invisible to the user.
                    val threshold = appBar.totalScrollRange * 0.85f
                    val isEffectivelyCollapsed = appBar.totalScrollRange > 0 &&
                        abs(verticalOffset) >= threshold
                    val target = if (isEffectivelyCollapsed) state.collapsedTitle else state.expandedTitle
                    if (collapsingToolbar.title != target) collapsingToolbar.title = target
                },
            )

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
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    MaterialExpressiveTheme(
                        colorScheme = innerColorSchemeState.value,
                        typography = innerTypographyState.value,
                        motionScheme = MotionScheme.expressive(),
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
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        MaterialExpressiveTheme(
                            colorScheme = innerColorSchemeState.value,
                            typography = innerTypographyState.value,
                            motionScheme = MotionScheme.expressive(),
                        ) {
                            content(PaddingValues())
                        }
                    }
                }
            }
            contentFrame.addView(composeView)

            // bottomBar is pinned outside StretchNestedScrollView so it stays
            // fixed at the bottom of the screen rather than scrolling with content.
            // Added as a direct child of root (CoordinatorLayout/FrameLayout) with
            // BOTTOM gravity. scrollView bottom padding is updated after layout so
            // content scrolls fully into view and is never hidden behind the bar.
            val bottomBarFrame = FrameLayout(ctx).apply {
                // root is a CoordinatorLayout — must use its LayoutParams, not FrameLayout's.
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
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    MaterialExpressiveTheme(
                        colorScheme = innerColorSchemeState.value,
                        typography = innerTypographyState.value,
                        motionScheme = MotionScheme.expressive(),
                    ) {
                        bottomBar()
                    }
                }
            }
            bottomBarFrame.addView(bottomBarComposeView)
            (root as CoordinatorLayout).addView(bottomBarFrame)

            // Once bottomBarFrame is laid out and its height known, add that
            // height as extra bottom padding on scrollView. The window-insets
            // listener already covers navigation bar padding; this stacks on top.
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

            // Must mirror every setBackgroundColor call from factory so theme
            // switches (light↔dark) repaint all surfaces. Omitting these was
            // causing root/scrollView to stay at the old theme's surface color.
            root.setBackgroundColor(surfaceColor)
            scrollView.setBackgroundColor(surfaceColor)
            appBarLayout.setBackgroundColor(surfaceColor)
            collapsingToolbar.setContentScrimColor(surfaceContainerColor)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
            collapsingToolbar.setExpandedTitleColor(onSurfaceColor)

            // Refresh stored strings and reapply the correct title for the
            // current scroll position — no listener re-registration needed.
            val state = root.tag as? TitleState
            if (state != null) {
                state.collapsedTitle = label
                state.onExpandedClick = onExpandedTitleClick

                val threshold = appBarLayout.totalScrollRange * 0.85f
                val isEffectivelyCollapsed = appBarLayout.totalScrollRange > 0 &&
                    abs(state.lastOffset) >= threshold

                // Only animate if the expanded label actually changed.
                if (state.expandedTitle != expandedLabel) {
                    val incomingTitle = expandedLabel
                    val rgbMask = onSurfaceColor and 0x00FFFFFF

                    // Cancel any in-progress crossfade before starting a new one.
                    state.titleAnimator?.cancel()

                    // Phase 1 — fade out the current expanded title.
                    state.titleAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = 130
                        addUpdateListener { anim ->
                            val alpha = ((anim.animatedValue as Float) * 255).toInt()
                            collapsingToolbar.setExpandedTitleColor(rgbMask or (alpha shl 24))
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                // Swap the string at the invisible midpoint.
                                state.expandedTitle = incomingTitle
                                val visibleTitle = if (isEffectivelyCollapsed) label else incomingTitle
                                if (collapsingToolbar.title != visibleTitle) {
                                    collapsingToolbar.title = visibleTitle
                                }
                                // Phase 2 — fade the new title back in.
                                state.titleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 130
                                    addUpdateListener { anim ->
                                        val alpha = ((anim.animatedValue as Float) * 255).toInt()
                                        collapsingToolbar.setExpandedTitleColor(rgbMask or (alpha shl 24))
                                    }
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            // Restore the plain color so theme changes still work.
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
                    // Label unchanged — just sync the visible title for scroll position.
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
