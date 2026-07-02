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
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.SettingsWallpaperBlurHelper
import com.android.launcher3.R
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private class TitleState(
    var collapsedTitle: String,
    var expandedTitle: String,
    var lastOffset: Int = 0,
    var onExpandedClick: (() -> Unit)? = null,
    var titleAnimator: ValueAnimator? = null,
)

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
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // ── Blur prefs ────────────────────────────────────────────────────────────
    val prefs = preferenceManager()
    val blurEnabled = prefs.settingsBlurBackground.getAdapter().state.value
    val blurIntensity = prefs.settingsBlurIntensity.getAdapter().state.value.toInt()

    // Recomputed whenever the configuration changes (e.g. rotation) — see
    // SettingsWallpaperBlurHelper.screenBounds() / PreferencesDashboard's
    // SearchOverlay for the matching fix on the other call site.
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

    // ── Colors ────────────────────────────────────────────────────────────────
    val surfaceArgb          = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceContainerArgb = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val onSurfaceArgb        = MaterialTheme.colorScheme.onSurface.toArgb()

    // Adaptive scrim: colorSurface at 72% alpha.
    // Dark mode  → surface is near-black  → dims the wallpaper.
    // Light mode → surface is near-white  → brightens / washes the wallpaper.
    // This gives a cohesive tinted look that follows the theme accent automatically
    // (Dynamic Color shifts the surface hue slightly toward the wallpaper palette).
    val scrimColor = ColorUtils.setAlphaComponent(surfaceArgb, (0.72f * 255).toInt())

    // contentScrim shown when CollapsingToolbar is fully collapsed — use
    // surfaceContainer at full opacity so the collapsed title is always readable.
    val collapsedScrimArgb = surfaceContainerArgb

    val parentCompositionContext = rememberCompositionContext()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val themedCtx = ContextThemeWrapper(ctx, MaterialR.style.Theme_Material3_DayNight_NoActionBar)
            val root = LayoutInflater.from(themedCtx)
                .inflate(R.layout.lawnchair_preference_scaffold, null, false)

            val blurBg            = root.findViewById<ImageView>(R.id.preference_blur_bg)
            val blurScrim         = root.findViewById<View>(R.id.preference_blur_scrim)
            val appBarLayout      = root.findViewById<AppBarLayout>(R.id.preference_appbar)
            val collapsingToolbar = root.findViewById<CollapsingToolbarLayout>(R.id.preference_collapsing_toolbar)
            val toolbar           = root.findViewById<MaterialToolbar>(R.id.preference_toolbar)
            val contentFrame      = root.findViewById<FrameLayout>(R.id.preference_content)
            val actionsFrame      = root.findViewById<FrameLayout>(R.id.preference_toolbar_actions)
            val scrollView        = root.findViewById<StretchNestedScrollView>(R.id.preference_scroll_view)

            // Stash views in tags for the update block
            root.tag = arrayOf(blurBg, blurScrim, appBarLayout, collapsingToolbar)

            // Initial state: normal surface backgrounds, blur views hidden
            root.setBackgroundColor(surfaceArgb)
            appBarLayout.setLiftable(false)
            appBarLayout.background      = ColorDrawable(surfaceArgb)
            collapsingToolbar.background = ColorDrawable(surfaceArgb)

            collapsingToolbar.setContentScrimColor(collapsedScrimArgb)
            collapsingToolbar.setStatusBarScrimColor(Color.TRANSPARENT)
            collapsingToolbar.setCollapsedTitleTextColor(onSurfaceArgb)
            collapsingToolbar.setExpandedTitleColor(onSurfaceArgb)

            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
                insets
            }

            val titleState = TitleState(
                collapsedTitle  = label,
                expandedTitle   = expandedLabel,
                onExpandedClick = onExpandedTitleClick,
            )
            collapsingToolbar.tag = titleState
            collapsingToolbar.title = expandedLabel

            collapsingToolbar.setOnClickListener {
                val state = collapsingToolbar.tag as? TitleState ?: return@setOnClickListener
                val threshold = appBarLayout.totalScrollRange * 0.85f
                if (abs(state.lastOffset) < threshold) state.onExpandedClick?.invoke()
            }

            appBarLayout.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { bar, verticalOffset ->
                    val state = collapsingToolbar.tag as? TitleState ?: return@OnOffsetChangedListener
                    state.lastOffset = verticalOffset
                    val threshold = bar.totalScrollRange * 0.85f
                    val isCollapsed = bar.totalScrollRange > 0 && abs(verticalOffset) >= threshold
                    val target = if (isCollapsed) state.collapsedTitle else state.expandedTitle
                    if (collapsingToolbar.title != target) collapsingToolbar.title = target
                },
            )

            if (backArrowVisible) {
                toolbar.setNavigationIcon(R.drawable.ic_back)
                toolbar.setNavigationOnClickListener { backDispatcher?.onBackPressed() }
            } else {
                toolbar.navigationIcon = null
            }

            val actionsComposeView = ComposeView(ctx).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                setContent {
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = MaterialTheme.typography) {
                        Row { actions() }
                    }
                }
            }
            actionsFrame.addView(actionsComposeView)

            val composeView = ComposeView(ctx).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                setContent {
                    CompositionLocalProvider(LocalInsideNestedScrollView provides true) {
                        MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = MaterialTheme.typography) {
                            content(PaddingValues())
                        }
                    }
                }
            }
            contentFrame.addView(composeView)

            val bottomBarFrame = FrameLayout(ctx).apply {
                layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.BOTTOM }
            }
            val bottomBarComposeView = ComposeView(ctx).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                setContent {
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = MaterialTheme.typography) {
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
            val views             = root.tag as? Array<*>
            val blurBg            = views?.getOrNull(0) as? ImageView
            val blurScrim         = views?.getOrNull(1) as? View
            val appBarLayout      = views?.getOrNull(2) as? AppBarLayout
                ?: root.findViewById(R.id.preference_appbar)
            val collapsingToolbar = views?.getOrNull(3) as? CollapsingToolbarLayout
                ?: root.findViewById(R.id.preference_collapsing_toolbar)
            val toolbar           = root.findViewById<MaterialToolbar>(R.id.preference_toolbar)

            val bitmap = blurredBitmap

            if (blurEnabled && bitmap != null && !bitmap.isRecycled) {
                // ── Blur ON ───────────────────────────────────────────────
                blurBg?.setImageDrawable(BitmapDrawable(root.resources, bitmap))
                blurBg?.visibility  = View.VISIBLE

                // Adaptive scrim: surface color at 72% alpha covers the whole
                // screen including the AppBar area since both appBarLayout and
                // collapsingToolbar have null (no-draw) backgrounds.
                blurScrim?.setBackgroundColor(scrimColor)
                blurScrim?.visibility = View.VISIBLE

                // null background = nothing drawn = ImageView + scrim show through.
                // Unlike ColorDrawable(TRANSPARENT), null cannot be overwritten by
                // MaterialShapeDrawable or LiftOnScrollHelper.
                root.background          = null
                appBarLayout?.background      = null
                collapsingToolbar?.background = null

                // When collapsed, contentScrim paints over the toolbar area.
                // Use surfaceContainer at full opacity so the title stays readable.
                collapsingToolbar?.setContentScrimColor(collapsedScrimArgb)
            } else {
                // ── Blur OFF ──────────────────────────────────────────────
                blurBg?.visibility  = View.GONE
                blurBg?.setImageDrawable(null)
                blurScrim?.visibility = View.GONE

                root.setBackgroundColor(surfaceArgb)
                appBarLayout?.background      = ColorDrawable(surfaceArgb)
                collapsingToolbar?.background = ColorDrawable(surfaceArgb)

                collapsingToolbar?.setContentScrimColor(collapsedScrimArgb)
            }

            collapsingToolbar?.setStatusBarScrimColor(Color.TRANSPARENT)
            collapsingToolbar?.setCollapsedTitleTextColor(onSurfaceArgb)
            collapsingToolbar?.setExpandedTitleColor(onSurfaceArgb)

            val state = collapsingToolbar?.tag as? TitleState
            if (state != null) {
                state.collapsedTitle = label
                state.onExpandedClick = onExpandedTitleClick

                val threshold   = (appBarLayout?.totalScrollRange ?: 0) * 0.85f
                val isCollapsed = (appBarLayout?.totalScrollRange ?: 0) > 0 &&
                    abs(state.lastOffset) >= threshold

                if (state.expandedTitle != expandedLabel) {
                    val incomingTitle = expandedLabel
                    val rgbMask = onSurfaceArgb and 0x00FFFFFF
                    state.titleAnimator?.cancel()
                    state.titleAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = 130
                        addUpdateListener { anim ->
                            val alpha = ((anim.animatedValue as Float) * 255).toInt()
                            collapsingToolbar?.setExpandedTitleColor(rgbMask or (alpha shl 24))
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            // onAnimationEnd fires even after cancel() — guard with this flag
                            // so a rapid label change doesn't lock the title to a stale value.
                            private var wasCancelled = false
                            override fun onAnimationCancel(animation: Animator) {
                                wasCancelled = true
                                // Restore full opacity so the title isn't left invisible.
                                collapsingToolbar?.setExpandedTitleColor(onSurfaceArgb)
                            }
                            override fun onAnimationEnd(animation: Animator) {
                                if (wasCancelled) return
                                state.expandedTitle = incomingTitle
                                val visible = if (isCollapsed) label else incomingTitle
                                if (collapsingToolbar?.title != visible) collapsingToolbar?.title = visible
                                state.titleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 130
                                    addUpdateListener { anim ->
                                        val alpha = ((anim.animatedValue as Float) * 255).toInt()
                                        collapsingToolbar?.setExpandedTitleColor(rgbMask or (alpha shl 24))
                                    }
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            collapsingToolbar?.setExpandedTitleColor(onSurfaceArgb)
                                        }
                                    })
                                    start()
                                }
                            }
                        })
                        start()
                    }
                } else {
                    val target = if (isCollapsed) label else expandedLabel
                    if (collapsingToolbar?.title != target) collapsingToolbar?.title = target
                }
            } else {
                collapsingToolbar?.title = label
            }

            toolbar.navigationIcon?.setTint(onSurfaceArgb)
        },
    )
}
