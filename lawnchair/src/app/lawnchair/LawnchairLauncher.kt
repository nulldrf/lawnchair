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

package app.lawnchair

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Pair
import android.view.Display
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.window.SplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairApp.Companion.showQuickstepWarningIfNecessary
import app.lawnchair.compat.LawnchairQuickstepCompat
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.service.WallpaperService
import app.lawnchair.gestures.GestureController
import app.lawnchair.gestures.VerticalSwipeTouchController
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.ui.LawnchairShortcutActivity
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.LawnchairShortcut
import app.lawnchair.util.getThemedIconPacksInstalled
import app.lawnchair.util.unsafeLazy
import app.lawnchair.views.LawnchairFloatingSurfaceView
import app.lawnchair.views.overlay.AppOpenAnimationType
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.GestureNavContract
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StateManager.StateHandler
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.uioverrides.states.AllAppsState
import com.android.launcher3.uioverrides.states.BackgroundAppState
import com.android.launcher3.uioverrides.states.OverviewState
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW
import com.android.launcher3.util.Themes
import com.android.launcher3.util.TouchController
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.RoundedCornerEnforcement
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.shared.system.QuickStepContract
import com.kieronquinn.app.smartspacer.sdk.client.SmartspacerClient
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.onEach
import dev.kdrag0n.monet.theme.ColorScheme
import java.util.stream.Stream
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LawnchairLauncher : QuickstepLauncher() {

    private val defaultOverlay by unsafeLazy { OverlayCallbackImpl(this) }
    private val prefs by unsafeLazy { PreferenceManager.getInstance(this) }
    private val preferenceManager2 by unsafeLazy { PreferenceManager2.getInstance(this) }
    private val insetsController: WindowInsetsControllerCompat by lazy {
        val window = launcher.window
            ?: throw Exception("WindowInsetsControllerCompat not available.")
        WindowInsetsControllerCompat(window, rootView)
    }
    private val themeProvider by unsafeLazy { ThemeProvider.INSTANCE.get(this) }

    private val noStatusBarStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState is OverviewState) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState !is OverviewState) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    private val rememberPositionStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState is AllAppsState) {
                mAppsView.activeRecyclerView.restoreScrollPosition()
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {}
    }
    private val statusBarClockListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            when (toState) {
                is BackgroundAppState,
                is OverviewState,
                is AllAppsState,
                -> LawnchairApp.instance.restoreClockInStatusBar()
                else -> workspace.updateStatusbarClock()
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {}
    }
    private val clearSearchStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState == LauncherState.NORMAL && mAppsView != null && mAppsView.isSearching) {
                mAppsView?.post { mAppsView.reset(false, true) }
            }
        }
    }

    private lateinit var colorScheme: ColorScheme
    private var hasBackGesture = false

    /**
     * Tracks the last animation type used when launching an app so [onStart]
     * can apply the matching return transition.
     */
    private var lastAppOpenAnimationType: AppOpenAnimationType = AppOpenAnimationType.DEFAULT

    val gestureController by unsafeLazy { GestureController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        layoutInflater.factory2 = LawnchairLayoutFactory(this)
        super.onCreate(savedInstanceState)

        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)
        prefs.feedProvider.subscribeChanges(this, defaultOverlay::reconnect)
        preferenceManager2.enableFeed.get().distinctUntilChanged().onEach { enable ->
            defaultOverlay.setEnableFeed(enable)
        }.launchIn(scope = lifecycleScope)
        launcher.stateManager.addStateListener(clearSearchStateListener)

        if (prefs.autoLaunchRoot.get()) {
            lifecycleScope.launch {
                try {
                    RootHelperManager.INSTANCE.get(this@LawnchairLauncher)
                } catch (_: RootNotAvailableException) { }
            }
        }

        preferenceManager2.showStatusBar.get().distinctUntilChanged().onEach {
            with(insetsController) {
                if (it) show(WindowInsetsCompat.Type.statusBars())
                else hide(WindowInsetsCompat.Type.statusBars())
            }
            with(launcher.stateManager) {
                if (it) removeStateListener(noStatusBarStateListener)
                else addStateListener(noStatusBarStateListener)
            }
        }.launchIn(scope = lifecycleScope)

        preferenceManager2.statusBarClock.get().onEach {
            with(launcher.stateManager) {
                if (it) {
                    addStateListener(statusBarClockListener)
                } else {
                    removeStateListener(statusBarClockListener)
                    LawnchairApp.instance.restoreClockInStatusBar()
                }
            }
        }
        preferenceManager2.rememberPosition.get().onEach {
            with(launcher.stateManager) {
                if (it) addStateListener(rememberPositionStateListener)
                else removeStateListener(rememberPositionStateListener)
            }
        }.launchIn(scope = lifecycleScope)

        prefs.overrideWindowCornerRadius.subscribeValues(this) {
            QuickStepContract.sHasCustomCornerRadius = it
        }
        prefs.windowCornerRadius.subscribeValues(this) {
            QuickStepContract.sCustomCornerRadius = it.toFloat()
        }
        preferenceManager2.roundedWidgets.onEach(launchIn = lifecycleScope) {
            RoundedCornerEnforcement.sRoundedCornerEnabled = it
        }
        val isWorkspaceDarkText = Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText)
        preferenceManager2.darkStatusBar.onEach(launchIn = lifecycleScope) { darkStatusBar ->
            systemUiController?.updateUiState(UI_STATE_BASE_WINDOW, isWorkspaceDarkText || darkStatusBar)
        }
        preferenceManager2.backPressGestureHandler.onEach(launchIn = lifecycleScope) { handler ->
            hasBackGesture = handler !is GestureHandlerConfig.NoOp
        }

        LauncherOptionsPopup.restoreMissingPopupOptions(launcher)
        LauncherOptionsPopup.migrateLegacyPreferences(launcher)

        if (prefs.themedIcons.get() &&
            packageManager.getThemedIconPacksInstalled(this).isEmpty()
        ) {
            prefs.themedIcons.set(newValue = false)
        }

        colorScheme = themeProvider.colorScheme
        showQuickstepWarningIfNecessary()
        reloadIconsIfNeeded()
        AppDatabase.INSTANCE.get(this).checkpointSync()
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent != null && intent.action == LawnchairShortcutActivity.START_ACTION) {
            val handlerString = intent.getStringExtra(LawnchairShortcutActivity.EXTRA_HANDLER)
            val config = handlerString?.let { GestureHandlerConfig.fromString(it) }
            if (config != null && config.isExternallyInvokable()) {
                gestureController.handle(config)
            }
        }
        super.onNewIntent(intent)
    }

    override fun collectStateHandlers(out: MutableList<StateHandler<LauncherState>>) {
        super.collectStateHandlers(out)
        out.add(SearchBarStateHandler(this))
    }

    override fun getSupportedShortcuts(container: Int): Stream<SystemShortcut.Factory<*>> = Stream.concat(
        super.getSupportedShortcuts(container),
        Stream.concat(
            Stream.of(LawnchairShortcut.UNINSTALL, LawnchairShortcut.CUSTOMIZE),
            if (LawnchairApp.isRecentsEnabled) Stream.of(LawnchairShortcut.PAUSE_APPS) else Stream.empty(),
        ),
    )

    fun updateTheme() {
        if (themeProvider.colorScheme != colorScheme) {
            recreate()
        } else {
            mWallpaperThemeManager.updateTheme()
        }
    }

    override fun createTouchControllers(): Array<TouchController> {
        val verticalSwipeController = VerticalSwipeTouchController(this, gestureController)
        return arrayOf<TouchController>(verticalSwipeController) + super.createTouchControllers()
    }

    override fun handleHomeTap() {
        gestureController.onHomePressed()
    }

    override fun registerBackDispatcher() {
        if (LawnchairApp.isAtleastT) {
            super.registerBackDispatcher()
        }
    }

    fun bindItems(items: List<ItemInfo>, forceAnimateIcons: Boolean) {
        val inflatedItems = items.map { i ->
            Pair.create(i, itemInflater?.inflateItem(i, null))
        }.toList()
        bindInflatedItems(inflatedItems, if (forceAnimateIcons) AnimatorSet() else null)
    }

    override fun handleGestureContract(intent: Intent) {
        if (!LawnchairApp.isRecentsEnabled && prefs.enableGnc.get()) {
            val gnc = GestureNavContract.fromIntent(intent)
            if (gnc != null) {
                AbstractFloatingView.closeOpenViews(
                    this, false, AbstractFloatingView.TYPE_ICON_SURFACE,
                )
                LawnchairFloatingSurfaceView.show(this, gnc)
            }
        }
    }

    override fun onUiChangedWhileSleeping() {
        if (Utilities.ATLEAST_S) {
            super.onUiChangedWhileSleeping()
        }
    }

    override fun showDefaultOptions(x: Float, y: Float) {
        val showWallpaperCarousel = "+carousel" in preferenceManager2.launcherPopupOrder.firstBlocking()
        if (showWallpaperCarousel) {
            show<LawnchairLauncher>(
                this, getPopupTarget(x, y), OptionsPopupView.getOptions(this),
            )
        } else {
            super.showDefaultOptions(x, y)
        }
    }

    private fun <T> show(
        activityContext: ActivityContext?,
        targetRect: RectF,
        items: List<OptionItem>,
        shouldAddArrow: Boolean = false,
        width: Int = 0,
    ): OptionsPopupView<T>? where T : Context?, T : ActivityContext? {
        if (activityContext == null) return null
        val isEmpty = WallpaperService.INSTANCE.get(this).getTopWallpapers().isEmpty()
        val layout = if (isEmpty) R.layout.longpress_options_menu else R.layout.wallpaper_options_popup
        val popup = activityContext.layoutInflater.inflate(
            layout, activityContext.dragLayer, false,
        ) as OptionsPopupView<T>
        popup.setTargetRect(targetRect)
        popup.setShouldAddArrow(shouldAddArrow)
        for (item in items) {
            val deepLayout = if (isEmpty) R.layout.system_shortcut else R.layout.wallpaper_options_popup_item
            val view = popup.inflateAndAdd<DeepShortcutView>(deepLayout, popup)
            if (width > 0) view.layoutParams.width = width
            view.iconView.setBackgroundDrawable(item.icon)
            view.bubbleText.text = item.label
            view.setOnClickListener(popup)
            view.setOnLongClickListener(popup)
            popup.mItemMap[view] = item
        }
        popup.show()
        return popup
    }

    fun createAppWidgetHolder(): LauncherWidgetHolder {
        val holder = LauncherWidgetHolder.newInstance(this)
        holder.setAppWidgetRemovedCallback { appWidgetId ->
            workspace.removeWidget(appWidgetId)
        }
        return holder
    }

    override fun makeDefaultActivityOptions(splashScreenStyle: Int): ActivityOptionsWrapper {
        val callbacks = RunnableList()
        val options = if (Utilities.ATLEAST_Q) {
            LawnchairQuickstepCompat.activityOptionsCompat.makeCustomAnimation(
                this, 0, 0, Executors.MAIN_EXECUTOR.handler, null,
            ) { callbacks.executeAllAndDestroy() }
        } else {
            ActivityOptions.makeBasic()
        }
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = splashScreenStyle
        }
        Utilities.allowBGLaunch(options)
        return ActivityOptionsWrapper(options, callbacks)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // App-open animation system — backported from Lawnchair 2
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // ROOT CAUSE ANALYSIS (confirmed by reading old LawnchairAppTransitionManagerImpl):
    //
    // 1. PIE OPENING BROKEN ON ALL ANDROID VERSIONS:
    //    My previous implementation put the dragLayer ObjectAnimator inside
    //    getActivityLaunchOptions(), which runs BEFORE startActivity() is called.
    //    At that point the window hasn't been created yet so the animation fires
    //    and finishes before the transition even begins.
    //    FIX: Override startActivitySafely(). After super() succeeds (activity
    //    actually started), THEN run the dragLayer animation.
    //
    // 2. BLINK / SLIDE_UP / FADE BROKEN ON ANDROID 14:
    //    makeCustomAnimation() is overridden by Quickstep's RemoteAnimationRunner
    //    on Android 12+. QuickstepTransitionManager registers a remote animation
    //    for ACTIVITY_OPEN transitions which completely ignores makeCustomAnimation.
    //    FIX: Use makeCustomAnimation(dummy_enter, dummy_exit) to suppress the
    //    system window animation, then drive the effect on dragLayer via
    //    LAYER_TYPE_HARDWARE — exactly what the old code did via
    //    getLauncherContentAnimator() + SyncRtSurfaceTransactionApplier.
    //    LAYER_TYPE_HARDWARE promotes the View to a GPU texture that composites
    //    on top of the window layer, making it visible regardless of what
    //    Quickstep does to the underlying windows.
    //
    // 3. RETURN TRANSITION (onStart vs onResume):
    //    Old code calls overrideResumeAnimation() from onStart(), not onResume().
    //    onStart() fires earlier in the activity lifecycle and overridePendingTransition
    //    must be called before the system commits the transition, so onStart is correct.
    //
    // STRATEGY PER ANIMATION TYPE:
    //   DEFAULT   → super (system clip-reveal, no interference)
    //   PIE       → dummy window anim + dragLayer scale(1→1.5) + alpha(1→0) via HARDWARE layer
    //   REVEAL    → makeClipRevealAnimation (system, works on all versions)
    //   SLIDE_UP  → dummy window anim + dragLayer translate(0→-height) via HARDWARE layer
    //   SCALE_UP  → makeScaleUpAnimation (system, works on all versions)
    //   BLINK     → dummy window anim + dragLayer alpha blink via HARDWARE layer
    //   FADE      → dummy window anim + dragLayer alpha(1→0) via HARDWARE layer
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the [ActivityOptions] to use for this app launch.
     *
     * For PIE/SLIDE_UP/BLINK/FADE we return dummy window animations to suppress
     * Quickstep's remote animation runner while we drive the real animation on
     * the dragLayer View in [startActivitySafely].
     *
     * For REVEAL and SCALE_UP we use the system APIs directly — these are not
     * overridden by Quickstep and work reliably on all Android versions.
     */
    override fun getActivityLaunchOptions(v: View?, item: ItemInfo?): ActivityOptionsWrapper {
        val animationType = preferenceManager2.appOpenAnimation.firstBlocking()
        lastAppOpenAnimationType = animationType

        return when (animationType) {
            AppOpenAnimationType.DEFAULT -> getActivityLaunchOptionsDefault(v)

            AppOpenAnimationType.PIE,
            AppOpenAnimationType.SLIDE_UP,
            AppOpenAnimationType.BLINK,
            AppOpenAnimationType.FADE,
            -> {
                // Suppress the system/Quickstep window animation with dummy anims.
                // The real animation is driven in startActivitySafely() on the
                // dragLayer View with LAYER_TYPE_HARDWARE.
                val options = ActivityOptions.makeCustomAnimation(
                    this,
                    R.anim.dummy_anim_enter,
                    R.anim.dummy_anim_exit,
                )
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }

            AppOpenAnimationType.REVEAL -> {
                val bounds = getIconBoundsForView(v)
                val options = if (bounds != null && v != null) {
                    ActivityOptions.makeClipRevealAnimation(
                        v, bounds.left, bounds.top, bounds.width(), bounds.height(),
                    )
                } else {
                    ActivityOptions.makeBasic()
                }
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }

            AppOpenAnimationType.SCALE_UP -> {
                val bounds = getIconBoundsForView(v)
                val options = if (bounds != null && v != null) {
                    ActivityOptions.makeScaleUpAnimation(
                        v, bounds.left, bounds.top, bounds.width(), bounds.height(),
                    )
                } else {
                    ActivityOptions.makeBasic()
                }
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }
        }
    }

    /**
     * Called after every successful app launch from the launcher.
     *
     * This is where we trigger the View-layer exit animation for PIE, SLIDE_UP,
     * BLINK, and FADE. Mirroring old LawnchairLauncher.startActivitySafely()
     * which called animationType.playLaunchAnimation() right here.
     *
     * The animation runs on dragLayer with LAYER_TYPE_HARDWARE so it is
     * composited as a GPU texture on top of the window layer — visible regardless
     * of what Quickstep's RemoteAnimationRunner does to the underlying windows.
     */
    override fun startActivitySafely(v: View?, intent: Intent, item: ItemInfo?): Boolean {
        val success = super.startActivitySafely(v, intent, item)
        if (success) {
            val animationType = lastAppOpenAnimationType
            when (animationType) {
                AppOpenAnimationType.PIE    -> playPieLaunchAnimation(v)
                AppOpenAnimationType.SLIDE_UP -> playSlideUpLaunchAnimation()
                AppOpenAnimationType.BLINK  -> playBlinkLaunchAnimation()
                AppOpenAnimationType.FADE   -> playFadeLaunchAnimation()
                else -> Unit
            }
        }
        return success
    }

    // ── Per-animation View-layer implementations ─────────────────────────────

    /**
     * Android Pie-style launcher exit.
     *
     * dragLayer scales from 1.0 → 1.5 (pivot at icon centre, or screen centre
     * if no icon) and fades 1.0 → 0.0 simultaneously over 250 ms.
     * Constants match the old companion object:
     *   APP_OPEN_HOME_EXIT_SCALE_FROM/TO = 1.0/1.5, DURATION = 250
     *   APP_OPEN_HOME_EXIT_ALPHA_FROM/TO = 1.0/0.0, DURATION = 250
     */
    private fun playPieLaunchAnimation(iconView: View?) {
        val layer = dragLayer ?: return

        // Pivot at the icon centre if available, else screen centre.
        val iconBounds = getIconBoundsForView(iconView)
        if (iconBounds != null) {
            // Convert icon bounds to dragLayer-relative coordinates.
            val loc = IntArray(2)
            iconView?.getLocationInWindow(loc)
            val layerLoc = IntArray(2)
            layer.getLocationInWindow(layerLoc)
            layer.pivotX = (loc[0] - layerLoc[0] + iconBounds.exactCenterX())
            layer.pivotY = (loc[1] - layerLoc[1] + iconBounds.exactCenterY())
        } else {
            layer.pivotX = layer.width / 2f
            layer.pivotY = layer.height / 2f
        }

        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val scaleInterp = PathInterpolator(0.2f, 0.5f, 0.2f, 1.0f)
        val alphaInterp = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
        val duration = 250L

        val scaleX = ObjectAnimator.ofFloat(layer, View.SCALE_X, 1.0f, 1.5f).apply {
            this.duration = duration; interpolator = scaleInterp
        }
        val scaleY = ObjectAnimator.ofFloat(layer, View.SCALE_Y, 1.0f, 1.5f).apply {
            this.duration = duration; interpolator = scaleInterp
        }
        val alpha = ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
            this.duration = duration; interpolator = alphaInterp
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.scaleX = 1.0f
                    layer.scaleY = 1.0f
                    layer.alpha = 1.0f
                    layer.pivotX = layer.width / 2f
                    layer.pivotY = layer.height / 2f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    /**
     * Slide-up launcher exit.
     *
     * dragLayer translates upward (Y: 0 → -height) so it appears to slide out
     * of the way while the app comes in. This is visible because LAYER_TYPE_HARDWARE
     * keeps the View composited on top of whatever Quickstep does to the windows.
     */
    private fun playSlideUpLaunchAnimation() {
        val layer = dragLayer ?: return
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val translateY = ObjectAnimator.ofFloat(
            layer, View.TRANSLATION_Y, 0f, -layer.height.toFloat(),
        ).apply {
            duration = 350L
            interpolator = PathInterpolator(0.2f, 0.0f, 0.2f, 1.0f)
        }
        val alpha = ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
            duration = 200L
            startDelay = 150L
            interpolator = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
        }

        AnimatorSet().apply {
            playTogether(translateY, alpha)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.translationY = 0f
                    layer.alpha = 1.0f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    /**
     * Blink launcher exit.
     *
     * Three rapid alpha flashes (on/off/on/off/on/off) over 400 ms.
     * Mirrors blink_open_exit.xml logic but at the View layer so it works on
     * Android 14 where makeCustomAnimation is overridden by Quickstep.
     *
     * Flash timing (each segment = 80 ms):
     *   0–80   ms : visible → hidden  (flash 1 off)
     *   80–160 ms : hidden → visible  (flash 1 on)
     *   160–240ms : visible → hidden  (flash 2 off)
     *   240–320ms : hidden → visible  (flash 2 on)
     *   320–400ms : visible → hidden  (final off)
     */
    private fun playBlinkLaunchAnimation() {
        val layer = dragLayer ?: return
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                layer.alpha = when {
                    t < 0.20f -> 1.0f   // visible
                    t < 0.40f -> 0.0f   // flash 1 off
                    t < 0.60f -> 1.0f   // back on
                    t < 0.80f -> 0.0f   // flash 2 off
                    else      -> 0.0f   // final off
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.alpha = 1.0f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    /**
     * Fade launcher exit.
     *
     * dragLayer fades from 1.0 → 0.0 over 250 ms.
     * Putting the fade on the launcher side (not the app side) means it works on
     * Android 12+ where SplashScreen replaces app-enter animations on cold starts.
     */
    private fun playFadeLaunchAnimation() {
        val layer = dragLayer ?: return
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
            duration = 250L
            interpolator = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.alpha = 1.0f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    // ── Return transition (onStart, matching old LawnchairLauncher.onStart) ──

    /**
     * onStart fires earlier than onResume and is the correct place to call
     * overridePendingTransition so the return-to-launcher animation is set
     * before the system commits the window swap.
     *
     * Mirrors old LawnchairLauncher.onStart() → animationType.overrideResumeAnimation()
     */
    override fun onStart() {
        super.onStart()
        applyResumeTransitionForAnimationType(lastAppOpenAnimationType)
    }

    /**
     * Applies the return-to-launcher window transition matching the animation
     * type used to open the app.
     *
     *   PIE   → pie_like_close_enter / pie_like_close_exit
     *   BLINK → blink_close_enter   / blink_close_exit
     *   FADE  → no_anim_short       / fade_out_short
     *   SLIDE_UP → pie_like_close_enter / pie_like_close_exit (natural slide-back)
     *   Others → no override, system handles return
     */
    @Suppress("DEPRECATION")
    private fun applyResumeTransitionForAnimationType(type: AppOpenAnimationType) {
        when (type) {
            AppOpenAnimationType.PIE,
            AppOpenAnimationType.SLIDE_UP,
            -> overridePendingTransition(
                R.anim.pie_like_close_enter,
                R.anim.pie_like_close_exit,
            )
            AppOpenAnimationType.BLINK -> overridePendingTransition(
                R.anim.blink_close_enter,
                R.anim.blink_close_exit,
            )
            AppOpenAnimationType.FADE -> overridePendingTransition(
                R.anim.no_anim_short,
                R.anim.fade_out_short,
            )
            else -> Unit
        }
    }

    // ── Helper: resolve icon bounds for REVEAL / SCALE_UP ────────────────────

    /**
     * Returns icon-level bounds within the view for [BubbleTextView], or the
     * full view rect for any other view type. Null when [v] is null.
     */
    private fun getIconBoundsForView(v: View?): android.graphics.Rect? {
        if (v == null) return null
        var left = 0
        var top = 0
        var width = v.measuredWidth
        var height = v.measuredHeight
        if (v is BubbleTextView) {
            val icon: Drawable? = v.icon
            if (icon != null) {
                val b = icon.bounds
                left = (width - b.width()) / 2
                top = v.paddingTop
                width = b.width()
                height = b.height()
            }
        }
        return android.graphics.Rect(left, top, left + width, top + height)
    }

    // ── Fallback: DEFAULT clip-reveal from icon ───────────────────────────────

    private fun getActivityLaunchOptionsDefault(v: View?): ActivityOptionsWrapper {
        var left = 0
        var top = 0
        var width = v?.measuredWidth ?: 0
        var height = v?.measuredHeight ?: 0
        if (v is BubbleTextView) {
            val icon: Drawable? = v.icon
            if (icon != null) {
                val bounds = icon.bounds
                left = (width - bounds.width()) / 2
                top = v.paddingTop
                width = bounds.width()
                height = bounds.height()
            }
        }
        val options = Utilities.allowBGLaunch(
            ActivityOptions.makeClipRevealAnimation(v, left, top, width, height),
        )
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId =
            if (v?.display != null) v.display.displayId else Display.DEFAULT_DISPLAY
        return ActivityOptionsWrapper(options, RunnableList())
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        restartIfPending()

        if (iconPackSwitchPending) {
            showIconPackSwitchOverlay()
        }

        dragLayer.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                private var handled = false
                override fun onDraw() {
                    if (handled) return
                    handled = true
                    dragLayer.post {
                        dragLayer.viewTreeObserver.removeOnDrawListener(this)
                    }
                    depthController
                }
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        SmartspacerClient.close()
    }

    override fun getDefaultOverlay(): LauncherOverlayManager = defaultOverlay

    fun recreateIfNotScheduled() {
        if (sRestartFlags == 0) recreate()
    }

    // ── Icon pack switch loading overlay ─────────────────────────────────────

    private var iconPackOverlay: android.view.View? = null

    fun showIconPackSwitchOverlay() {
        if (iconPackOverlay != null) return
        val density = resources.displayMetrics.density
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
        val primaryColor = ta.getColor(0, android.graphics.Color.BLUE)
        ta.recycle()
        val containerSize = (72 * density).toInt()
        val spinnerSize = (40 * density).toInt()
        val containerBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.argb(
                30,
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor),
            ))
        }
        val container = android.widget.FrameLayout(this).apply { background = containerBg }
        val spinner = android.widget.ProgressBar(
            this, null, android.R.attr.progressBarStyle,
        ).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        }
        container.addView(
            spinner,
            android.widget.FrameLayout.LayoutParams(spinnerSize, spinnerSize).apply {
                gravity = android.view.Gravity.CENTER
            },
        )
        val scrim = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.argb(160, 0, 0, 0))
        }
        scrim.addView(
            container,
            android.widget.FrameLayout.LayoutParams(containerSize, containerSize).apply {
                gravity = android.view.Gravity.CENTER
            },
        )
        scrim.alpha = 0f
        dragLayer.addView(
            scrim,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        scrim.animate().alpha(1f).setDuration(200).start()
        iconPackOverlay = scrim
    }

    fun dismissIconPackSwitchOverlay() {
        val overlay = iconPackOverlay ?: return
        iconPackOverlay = null
        overlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { dragLayer.removeView(overlay) }
            .start()
    }

    private fun restartIfPending() {
        when {
            sRestartFlags and FLAG_RESTART != 0 -> lawnchairApp.restart(false)
            sRestartFlags and FLAG_RECREATE != 0 -> {
                sRestartFlags = 0
                recreate()
            }
        }
    }

    private fun reloadIconsIfNeeded() {
        if (preferenceManager2.alwaysReloadIcons.firstBlocking()) {
            LauncherAppState.getInstance(this).model.reloadIfActive()
        }
    }

    companion object {
        private const val FLAG_RECREATE = 1 shl 0
        private const val FLAG_RESTART = 1 shl 1

        var sRestartFlags = 0

        @Volatile var iconPackSwitchPending = false

        val instance get() = LawnchairApp.launcher
    }
}

val Context.launcher: LawnchairLauncher
    get() = BaseActivity.fromContext(this)

val Context.launcherNullable: LawnchairLauncher? get() = try {
    launcher
} catch (_: IllegalArgumentException) {
    null
}
