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

import android.animation.AnimatorSet
import android.app.Activity
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
                -> {
                    LawnchairApp.instance.restoreClockInStatusBar()
                }
                else -> {
                    workspace.updateStatusbarClock()
                }
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {}
    }
    private val clearSearchStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState == LauncherState.NORMAL && mAppsView != null && mAppsView.isSearching) {
                mAppsView?.post {
                    mAppsView.reset(false, true)
                }
            }
        }
    }

    private lateinit var colorScheme: ColorScheme
    private var hasBackGesture = false

    /**
     * Tracks the last-used app-open animation type so [onResume] can apply the
     * correct paired return transition (PIE/BLINK/FADE each have a matching
     * "close" animation).
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
                } catch (_: RootNotAvailableException) {
                }
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

        if (
            prefs.themedIcons.get() &&
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
                    this,
                    false,
                    AbstractFloatingView.TYPE_ICON_SURFACE,
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
                this,
                getPopupTarget(x, y),
                OptionsPopupView.getOptions(this),
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
        val popup = activityContext.layoutInflater.inflate(layout, activityContext.dragLayer, false) as OptionsPopupView<T>
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
                this, 0, 0,
                Executors.MAIN_EXECUTOR.handler, null,
            ) {
                callbacks.executeAllAndDestroy()
            }
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
    // App-open animation (backported from Lawnchair 2 + fixed for modern Android)
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // KEY DESIGN PRINCIPLE (learned from Android internals):
    //   makeCustomAnimation(context, enterRes, exitRes) operates at the WINDOW
    //   COMPOSITOR layer — the window manager applies the animations to the
    //   entire launcher window (exit) and the entire app window (enter) at the
    //   SurfaceFlinger level, BEFORE Compose / View drawing happens.
    //
    //   This means:
    //     1. Driving a View-layer ObjectAnimator on dragLayer simultaneously with
    //        a window-layer animation causes them to FIGHT — the window layer
    //        wins and the View-layer animator is invisible or causes flicker.
    //        → REMOVED schedulePieLauncherExitAnimation() and scheduleBlinkLauncherExitAnimation()
    //
    //     2. The LAUNCHER EXIT (2nd parameter) always works on all Android versions.
    //
    //     3. The APP ENTER (1st parameter) may be replaced by the system SplashScreen
    //        on Android 12+ cold starts. On warm/hot starts it always shows.
    //
    //   Therefore all animations are expressed as pure XML window transitions.
    //
    // Mapping from old ch.deletescape.lawnchair.animations.AnimationType:
    //   DEFAULT  → system clip-reveal (passthrough to super)
    //   PIE      → XML: launcher scales-up+fades (exit) / app scales-in (enter)
    //   REVEAL   → ActivityOptions.makeClipRevealAnimation from icon bounds
    //   SLIDE_UP → XML: task_open_enter (fixed interpolator refs) / no_anim exit
    //   SCALE_UP → ActivityOptions.makeScaleUpAnimation from icon bounds
    //   BLINK    → XML: blink_open_enter / blink_open_exit (launcher flashes)
    //   FADE     → XML: no_anim enter / fade_out_launcher exit (always visible)
    // ═══════════════════════════════════════════════════════════════════════════

    override fun getActivityLaunchOptions(v: View?, item: ItemInfo?): ActivityOptionsWrapper {
        val animationType = preferenceManager2.appOpenAnimation.firstBlocking()
        lastAppOpenAnimationType = animationType

        return when (animationType) {
            AppOpenAnimationType.DEFAULT -> getActivityLaunchOptionsDefault(v)

            AppOpenAnimationType.PIE -> {
                // ── Pie (Android 9 style) ────────────────────────────────────
                // EXIT  (launcher): pie_like_open_exit.xml
                //         Launcher scales from 1.0→1.5 and fades from 1→0.
                //         This is the hallmark Pie "home screen zooms away" feel.
                // ENTER (app):      pie_like_open_enter.xml
                //         App scales from 0.3→1.0 and fades from 0→1.
                //         May be replaced by SplashScreen on Android 12+ cold start.
                //
                // REMOVED: schedulePieLauncherExitAnimation() — a View-layer
                // ObjectAnimator on dragLayer is invisible when the window layer
                // is already running makeCustomAnimation, causing broken animation
                // on all Android versions.
                val options = ActivityOptions.makeCustomAnimation(
                    this,
                    R.anim.pie_like_open_enter,
                    R.anim.pie_like_open_exit,
                )
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }

            AppOpenAnimationType.REVEAL -> {
                // ── Clip Reveal ──────────────────────────────────────────────
                // Circular clip-reveal expanding from the tapped icon bounds.
                // makeClipRevealAnimation operates at the window compositor level
                // so it works on all Android versions including 14.
                val bounds = getIconBoundsForView(v)
                val options = if (bounds != null && v != null) {
                    ActivityOptions.makeClipRevealAnimation(
                        v,
                        bounds.left,
                        bounds.top,
                        bounds.width(),
                        bounds.height(),
                    )
                } else {
                    // Fallback when icon bounds can't be resolved (e.g. launched
                    // from a shortcut without a visible icon).
                    ActivityOptions.makeBasic()
                }
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }

            AppOpenAnimationType.SLIDE_UP -> {
                // ── Slide Up ─────────────────────────────────────────────────
                // App slides in from below the screen.
                // EXIT (launcher): no_anim — stays put while app slides up over it.
                // ENTER (app):     task_open_enter.xml
                //
                // FIX: task_open_enter.xml previously referenced
                // @interpolator/decelerate_quart and @interpolator/decelerate_quint
                // without the @android: prefix. Those names don't exist in the
                // app's own resource table (animationlib ships only emphasized_*
                // and standard_*). Added decelerate_quart.xml and decelerate_quint.xml
                // to res/interpolator/ so the references resolve correctly.
                val options = ActivityOptions.makeCustomAnimation(
                    this,
                    R.anim.task_open_enter,
                    R.anim.no_anim,
                )
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }

            AppOpenAnimationType.SCALE_UP -> {
                // ── Scale Up ─────────────────────────────────────────────────
                // System makeScaleUpAnimation — operates at compositor level,
                // reliable on all Android versions.
                val bounds = getIconBoundsForView(v)
                val options = if (bounds != null && v != null) {
                    ActivityOptions.makeScaleUpAnimation(
                        v,
                        bounds.left,
                        bounds.top,
                        bounds.width(),
                        bounds.height(),
                    )
                } else {
                    ActivityOptions.makeBasic()
                }
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }

            AppOpenAnimationType.BLINK -> {
                // ── Blink ────────────────────────────────────────────────────
                // EXIT  (launcher): blink_open_exit.xml — three rapid alpha flashes.
                // ENTER (app):      blink_open_enter.xml — stays invisible, then snaps.
                //
                // REMOVED: scheduleBlinkLauncherExitAnimation() — a View-layer
                // alpha ValueAnimator on dragLayer conflicts with the window-layer
                // blink_open_exit XML, causing double-flash and visual glitches.
                // The XML alone produces the correct blink effect.
                val options = ActivityOptions.makeCustomAnimation(
                    this,
                    R.anim.blink_open_enter,
                    R.anim.blink_open_exit,
                )
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }

            AppOpenAnimationType.FADE -> {
                // ── Fade ─────────────────────────────────────────────────────
                // EXIT  (launcher): fade_out_launcher.xml — launcher fades to 0.
                // ENTER (app):      no_anim — let system/splash handle app appearance.
                //
                // FIX: The previous implementation put the fade on the APP ENTER
                // side (fade_in_short.xml / no_anim_short exit). Android 12+
                // SplashScreen overrides app-enter animations on cold starts, making
                // FADE indistinguishable from DEFAULT.  Putting the fade on the
                // LAUNCHER EXIT side bypasses SplashScreen entirely — the window
                // manager always applies the launcher exit animation.
                val options = ActivityOptions.makeCustomAnimation(
                    this,
                    R.anim.no_anim,
                    R.anim.fade_out_launcher,
                )
                Utilities.allowBGLaunch(options)
                ActivityOptionsWrapper(options, RunnableList())
            }
        }
    }

    /**
     * Resolves the pixel-space icon bounds from a [BubbleTextView]'s drawable
     * (or the full view rect for any other view type).
     *
     * Matches getBounds() from the old Lawnchair 2 AnimationType base class.
     * Returns null when [v] is null so callers can fall back gracefully.
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

    // ═══════════════════════════════════════════════════════════════════════════
    // onResume — apply paired return transition
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // When the user presses Back from an app and the launcher resumes, the system
    // needs to know what animation to use for the return journey.
    // overridePendingTransition() (deprecated in API 34 but still functional)
    // sets the enter/exit pair for the NEXT activity transition, i.e. the one
    // triggered by the Back press that surfaces the launcher.
    //
    // Mirrors AnimationType.overrideResumeAnimation() from the old codebase:
    //   PIE   → pie_like_close_enter / pie_like_close_exit
    //   BLINK → blink_close_enter   / blink_close_exit
    //   FADE  → no_anim_short       / fade_out_short
    //   others → no override (system handles the return)
    // ═══════════════════════════════════════════════════════════════════════════

    override fun onResume() {
        super.onResume()
        restartIfPending()

        applyResumeTransitionForAnimationType(lastAppOpenAnimationType)

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

    /**
     * Applies [Activity.overridePendingTransition] with the correct pair of
     * enter/exit XML animations that mirror the opening animation used to
     * launch the app.
     *
     * Called from [onResume] so the transition is set before the system
     * commits the window swap.
     */
    @Suppress("DEPRECATION") // overridePendingTransition still works on API 34
    private fun applyResumeTransitionForAnimationType(type: AppOpenAnimationType) {
        when (type) {
            AppOpenAnimationType.PIE -> overridePendingTransition(
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
            // DEFAULT, REVEAL, SLIDE_UP, SCALE_UP — system handles the return.
            else -> Unit
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Default launch options (clip-reveal from icon, used for DEFAULT mode and
    // as fallback when we can't resolve ActivityOptions from the user's choice)
    // ═══════════════════════════════════════════════════════════════════════════

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

    override fun onDestroy() {
        super.onDestroy()
        SmartspacerClient.close()
    }

    override fun getDefaultOverlay(): LauncherOverlayManager = defaultOverlay

    fun recreateIfNotScheduled() {
        if (sRestartFlags == 0) recreate()
    }

    // ── Icon pack switch loading overlay ────────────────────────────────────

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
        val spinner = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyle).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        }
        container.addView(spinner, android.widget.FrameLayout.LayoutParams(spinnerSize, spinnerSize).apply {
            gravity = android.view.Gravity.CENTER
        })
        val scrim = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.argb(160, 0, 0, 0))
        }
        scrim.addView(container, android.widget.FrameLayout.LayoutParams(containerSize, containerSize).apply {
            gravity = android.view.Gravity.CENTER
        })
        scrim.alpha = 0f
        dragLayer.addView(scrim, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        ))
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
