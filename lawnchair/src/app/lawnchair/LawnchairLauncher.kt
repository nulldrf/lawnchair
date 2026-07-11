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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Pair
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
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
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
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
     * Tracks the last animation type used to launch an app so [onStart] can
     * apply the matching return transition.
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
            packageManager.getThemedIconPacksInstalled(this).isEmpty()) {
            prefs.themedIcons.set(newValue = false)
        }

        colorScheme = themeProvider.colorScheme
        showQuickstepWarningIfNecessary()
        reloadIconsIfNeeded()
        AppDatabase.INSTANCE.get(this).checkpointSync()

        // Inject our custom QuickstepTransitionManager so that app→home
        // (closing) animations respect the user's chosen animation type.
        // Done after super.onCreate() because QuickstepLauncher constructs
        // its transition manager during setupViews() which runs inside super.
        injectLawnchairTransitionManager()
    }

    /**
     * Replaces QuickstepLauncher's default [QuickstepTransitionManager] with
     * [LawnchairQuickstepTransitionManager] via reflection.
     *
     * We walk the class hierarchy looking for the field that holds the
     * QuickstepTransitionManager instance, trying the most common names.
     * If the field is found and the current value is a plain
     * QuickstepTransitionManager (not already our subclass), we:
     *   1. Unregister the old manager's remote animations/transitions
     *   2. Set our custom manager
     *   3. Register our custom manager's remote animations/transitions
     *
     * If reflection fails (field renamed upstream) we log a warning and
     * fall back gracefully — opening animations still work via
     * [startActivitySafely]; only closing will use the system default.
     */
    private fun injectLawnchairTransitionManager() {
        // LauncherBackAnimationController (instantiated inside
        // QuickstepTransitionManager's constructor) references
        // com.android.internal.policy.SystemBarUtils which was added in
        // Android 12 (API 31). Instantiating our subclass on API 30 or below
        // throws NoClassDefFoundError before we even reach the field swap.
        // Also skip when Quickstep/Recents is disabled — the transition manager
        // injection is only meaningful when Quickstep intercepts close transitions.
        if (!Utilities.ATLEAST_S || !LawnchairApp.isRecentsEnabled) return

        try {
            val customManager = LawnchairQuickstepTransitionManager(this)
            val fieldNames = listOf(
                "mAppTransitionManager",
                "appTransitionManager",
                "mTransitionManager",
                "transitionManager",
            )
            var injected = false
            var clazz: Class<*>? = this::class.java
            outer@ while (clazz != null) {
                for (name in fieldNames) {
                    try {
                        val field = clazz.getDeclaredField(name)
                        field.isAccessible = true
                        val current = field.get(this)
                        if (current is com.android.launcher3.QuickstepTransitionManager &&
                            current !is LawnchairQuickstepTransitionManager
                        ) {
                            // Both unregisterRemoteAnimations (private) and
                            // unregisterRemoteTransitions (protected) are not
                            // directly callable here — use reflection for both.
                            listOf(
                                "unregisterRemoteAnimations",
                                "unregisterRemoteTransitions",
                            ).forEach { methodName ->
                                runCatching {
                                    var c: Class<*>? = current::class.java
                                    while (c != null) {
                                        try {
                                            val m = c.getDeclaredMethod(methodName)
                                            m.isAccessible = true
                                            m.invoke(current)
                                            break
                                        } catch (_: NoSuchMethodException) {
                                            c = c.superclass
                                        }
                                    }
                                }
                            }
                            field.set(this, customManager)
                            runCatching { customManager.registerRemoteAnimations() }
                            runCatching { customManager.registerRemoteTransitions() }
                            injected = true
                            break@outer
                        }
                    } catch (_: NoSuchFieldException) {
                        // Try next field name
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "LawnchairLauncher",
                            "injectLawnchairTransitionManager: failed via $name: $e",
                        )
                    }
                }
                clazz = clazz.superclass
            }
            if (!injected) {
                android.util.Log.w(
                    "LawnchairLauncher",
                    "injectLawnchairTransitionManager: field not found — " +
                        "closing animations will use system default",
                )
            }
        } catch (t: Throwable) {
            // Catches NoClassDefFoundError and any other Error/Exception that
            // may arise from constructing QuickstepTransitionManager on
            // unexpected OS configurations.
            android.util.Log.w(
                "LawnchairLauncher",
                "injectLawnchairTransitionManager: aborted — $t",
            )
        }
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
            Stream.of(LawnchairShortcut.UNINSTALL, LawnchairShortcut.CUSTOMIZE, LawnchairShortcut.OPEN_IN_STORE),
            if (LawnchairApp.isRecentsEnabled) Stream.of(LawnchairShortcut.PAUSE_APPS) else Stream.empty(),
        ),
    )

    /**
     * Debounce runnable for [recreate] calls triggered by [updateTheme].
     *
     * [prefs.launcherTheme] can emit multiple rapid changes from a single icon pack
     * switch — the theme provider recalculates once for icon pack color extraction,
     * then again for wallpaper-based adjustment. Each emission calls [updateTheme],
     * and if both land before the Android framework sets [isFinishing] to true after
     * the first [recreate], two new launcher instances are created back-to-back.
     * Over 3–4 icon pack changes this accumulates to 8+ activities.
     *
     * By posting [recreate] on a short delay and cancelling any pending post, all
     * theme-change events within [RECREATE_DEBOUNCE_MS] collapse into one recreate.
     */
    private val recreateDebounceRunnable = Runnable {
        if (!isFinishing && !isDestroyed && themeProvider.colorScheme != colorScheme) {
            recreate()
        }
    }

    fun updateTheme() {
        if (isFinishing || isDestroyed) return
        if (themeProvider.colorScheme != colorScheme) {
            // Debounce: cancel any pending recreate and schedule a fresh one.
            // Multiple rapid theme emissions (e.g. from a single icon pack change)
            // collapse into one recreate rather than stacking instances.
            dragLayer.removeCallbacks(recreateDebounceRunnable)
            dragLayer.postDelayed(recreateDebounceRunnable, RECREATE_DEBOUNCE_MS)
        } else {
            dragLayer.removeCallbacks(recreateDebounceRunnable)
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
        val showWallpaperCarousel = "+carousel" in preferenceManager2.launcherPopupOrder.firstCached()
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

    // ═══════════════════════════════════════════════════════════════════════
    // App-open animation — backported from Lawnchair 2 + fixed for modern Android
    //
    // HOW PIE ICON-TO-CENTER WORKS IN OLD LAWNCHAIR 2:
    //   1. getActivityLaunchOptions returns makeCustomAnimation(dummy_enter, dummy_exit)
    //      to suppress the system window animation.
    //   2. startActivitySafely (after super) calls playLaunchAnimation which:
    //      a. Creates a SplashLayout (fake app window matching app's theme colors)
    //         and adds it to dragLayer.parent
    //      b. playIconAnimators creates a FloatingIconView at the icon's screen
    //         position — this is the "icon copy" that flies around
    //      c. getLauncherContentAnimator(isAppOpening=true) does dragLayer scale
    //         1.0→1.5 + alpha 1→0 with pivot at the icon center
    //      d. getOpeningWindowAnimators tracks the FloatingIconView's position
    //         and positions the SplashView beneath it, scaling from icon size
    //         to full screen
    //
    // OUR APPROACH (verified frame-by-frame against the old Lawnchair 2 recording):
    //   1. Capture a Bitmap of the icon from the BubbleTextView.
    //   2. Add a floating View copy at the icon's rootView-relative position
    //      (mFloatingView equivalent) that flies to screen centre and fades
    //      out quickly — a decoy, mostly invisible once the splash takes over.
    //   3. Add a splashView that grows via a DIRECT, INDEPENDENT lerp of its
    //      own scaleX/scaleY/translation from the icon's exact rect to the
    //      full-screen rect (pivotX=0, pivotY=0 so growth is corner-anchored,
    //      not centered) — this is intentionally NOT derived by tracking the
    //      floating icon's live position, since that view flies toward screen
    //      centre and would pull the splash off a true full-screen fill.
    //   4. The splash's corner radius starts at the CURRENT icon shape's
    //      windowTransitionRadius (per-user icon-shape preference) and lerps
    //      to 0 (square) over the same progress — reproducing the "rounded
    //      icon shape morphing into a square" reveal seen on frames ~100-113
    //      of the reference recording.
    //   5. dragLayer (launcher content) fades alpha 1→0 ONLY — no scale/zoom.
    //      Confirmed against the reference recording: the home screen dims in
    //      place, it never zooms.
    //   6. onStart() applies the return overridePendingTransition (works on Android 11,
    //      no-op on Android 14+Quickstep where remote anim intercepts)
    //
    // ANDROID 14 + QUICKSTEP CLOSING LIMITATION:
    //   QuickstepTransitionManager registers a RemoteAnimationRunner for
    //   ACTIVITY_CLOSE transitions. overridePendingTransition() is ignored.
    //   Fixing this requires hooking into QuickstepTransitionManager's
    //   createLauncherResumeAnimation() — out of scope here.
    // ═══════════════════════════════════════════════════════════════════════

    override fun getActivityLaunchOptions(v: View?, item: ItemInfo?): ActivityOptionsWrapper {
        val animationType = preferenceManager2.appOpenAnimation.firstCached()
        lastAppOpenAnimationType = animationType

        return when (animationType) {
            AppOpenAnimationType.DEFAULT -> getActivityLaunchOptionsDefault(v)

            // PIE, SLIDE_UP, BLINK, FADE: suppress system/Quickstep window animation
            // with dummy anims. Real animation runs on dragLayer in startActivitySafely.
            AppOpenAnimationType.PIE,
            AppOpenAnimationType.SLIDE_UP,
            AppOpenAnimationType.BLINK,
            AppOpenAnimationType.FADE,
            -> {
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

            else -> getActivityLaunchOptionsDefault(v)
        }
    }

    /**
     * Called after every successful app launch from the launcher.
     * Triggers the View-layer exit animation for PIE/SLIDE_UP/BLINK/FADE.
     *
     * This runs AFTER super() starts the activity — so the window transition
     * has already begun. LAYER_TYPE_HARDWARE keeps the dragLayer composited
     * on top of the window layer so our animation is visible regardless of
     * what Quickstep does to the underlying windows.
     *
     * Mirrors old LawnchairLauncher.startActivitySafely() calling
     * animationType.playLaunchAnimation() after super() returns true.
     */
    override fun startActivitySafely(v: View?, intent: Intent, item: ItemInfo?): RunnableList? {
        val callbacks = super.startActivitySafely(v, intent, item)
        if (callbacks != null) {
            when (lastAppOpenAnimationType) {
                AppOpenAnimationType.PIE      -> playPieLaunchAnimation(v, intent)
                AppOpenAnimationType.SLIDE_UP -> playSlideUpLaunchAnimation()
                AppOpenAnimationType.BLINK    -> playBlinkLaunchAnimation()
                AppOpenAnimationType.FADE     -> playFadeLaunchAnimation()
                else                          -> Unit
            }
        }
        return callbacks
    }

    // ── PIE: icon flies from grid position to screen center, launcher zooms out ──

    /**
     * Android Pie-style launcher exit with icon-to-center fly effect.
     *
     * Reconstructs the old LawnchairAppTransitionManagerImpl + AnimationType.PieAnimation
     * behaviour without needing SplashLayout or RemoteAnimationTargetCompat:
     *
     *  1. Capture the icon's bitmap from the BubbleTextView (or any view).
     *  2. Add a floating ImageView copy of the icon to the dragLayer at the
     *     icon's current position.
     *  3. Animate the floating copy translating from the icon position to the
     *     screen centre, scaling up slightly (icon "expands" toward the user).
     *  4. Simultaneously animate dragLayer: scale 1.0→1.5, alpha 1→0, pivot
     *     at the icon centre — so the home screen zooms away from the icon.
     *  5. Reset everything on animation end.
     *
     * Full port of old LawnchairAppTransitionManagerImpl.playIconAnimators() +
     * getLauncherContentAnimator(true).
     */

    // ── Constants from old LawnchairAppTransitionManagerImpl companion object ──
    // App open: home (dragLayer) exit
    private val APP_OPEN_HOME_EXIT_SCALE_TO     = 1.5f
    private val APP_OPEN_HOME_EXIT_SCALE_DUR    = 250L
    private val APP_OPEN_HOME_EXIT_SCALE_INTERP = PathInterpolator(0.2f, 0.5f, 0.2f, 1.0f)
    private val APP_OPEN_HOME_EXIT_ALPHA_TO     = 0.0f
    private val APP_OPEN_HOME_EXIT_ALPHA_DUR    = 250L
    private val APP_OPEN_HOME_EXIT_ALPHA_INTERP = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)

    // From LauncherAppTransitionManagerImpl (mFloatingView icon fly constants)
    private val APP_LAUNCH_DURATION           = 500L
    private val APP_LAUNCH_CURVED_DURATION    = APP_LAUNCH_DURATION / 2  // 250
    private val APP_LAUNCH_DOWN_DUR_SCALE     = 0.8f
    private val APP_LAUNCH_ALPHA_START_DELAY  = 32L
    private val APP_LAUNCH_ALPHA_DURATION     = 50L
    // Grace period + graceful fade-out for splashView teardown (see the
    // "── 6. Cleanup ──" section) — mitigates, but cannot fully eliminate,
    // the handoff-gap race described there.
    private val PIE_SPLASH_GRACE_MS: Long = 120L
    private val PIE_SPLASH_FADEOUT_MS: Long = 120L
    // AGGRESSIVE_EASE = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val AGGRESSIVE_EASE = android.view.animation.PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
    // EXAGGERATED_EASE cubic segments: (0.05,0,0.133,0.08,0.166,0.4) + (0.225,0.94,0.5,1,1,1)
    private val EXAGGERATED_EASE = android.view.animation.PathInterpolator(0.05f, 0.0f, 0.225f, 0.94f)

    /**
     * Android Pie-style opening animation.
     *
     * 1. Resolves icon bounds in dragLayer-coordinate space via
     *    getDescendantRectRelativeToSelf (exactly as old playIconAnimators).
     * 2. Creates a plain View (mFloatingView equivalent) with the icon as background,
     *    added to rootView at the icon's rootView-relative position. This flies to
     *    screen centre and fades out quickly (decoy — see playPieLaunchAnimation).
     * 3. Creates splashView, whose scaleX/scaleY/translation are lerped directly
     *    and independently (pivot 0,0) from the icon's rect to the full-screen
     *    rect over the animation's progress — NOT derived from the floating
     *    view's position, since that view animates toward screen centre.
     * 4. splashView's corner radius starts at the current icon shape's
     *    windowTransitionRadius and lerps to 0 over the same progress.
     * 5. Simultaneously fades dragLayer alpha 1→0 (no scale).
     *
     * dX/dY below are only used to decide [useUpward]; they are not used to
     * position the splash (that would drift it toward centre — see fix notes
     * in playPieLaunchAnimation).
     */
    private fun playPieLaunchAnimation(iconView: View?, launchIntent: Intent? = null) {
        val layer = dragLayer ?: return
        val rootView = layer.parent as? ViewGroup ?: return
        val dp = deviceProfile

        // ── 1. Icon bounds in dragLayer coordinate space ───────────────────
        val rect = android.graphics.Rect()
        val resolvedView: View? = iconView

        if (resolvedView != null) {
            when {
                resolvedView.parent is com.android.launcher3.shortcuts.DeepShortcutView -> {
                    val dsv = resolvedView.parent as com.android.launcher3.shortcuts.DeepShortcutView
                    layer.getDescendantRectRelativeToSelf(dsv.iconView, rect)
                }
                resolvedView is BubbleTextView -> {
                    val pos = android.graphics.Rect()
                    layer.getDescendantRectRelativeToSelf(resolvedView, pos)
                    val iconBounds = android.graphics.Rect()
                    resolvedView.getIconBounds(iconBounds)
                    rect.set(
                        pos.left + iconBounds.left, pos.top  + iconBounds.top,
                        pos.left + iconBounds.right, pos.top + iconBounds.bottom,
                    )
                }
                else -> layer.getDescendantRectRelativeToSelf(resolvedView, rect)
            }
        }

        if (rect.isEmpty) { playSimplePieLaunchAnimation(layer); return }

        // ── 2. Coordinate systems ──────────────────────────────────────────
        val dragLayerLoc = IntArray(2)
        layer.getLocationOnScreen(dragLayerLoc)
        val rootViewLoc = IntArray(2)
        rootView.getLocationOnScreen(rootViewLoc)
        val offsetX = dragLayerLoc[0] - rootViewLoc[0]
        val offsetY = dragLayerLoc[1] - rootViewLoc[1]

        // Icon bounds in rootView coordinate system
        val iconInRoot = android.graphics.Rect(
            rect.left   + offsetX, rect.top    + offsetY,
            rect.right  + offsetX, rect.bottom + offsetY,
        )

        // FIX (top gap on older Android, e.g. Android 11): everything below
        // is positioned in ROOTVIEW's OWN coordinate frame (origin at
        // rootView's top-left), not true screen coordinates. If rootView
        // itself doesn't start at the true top of the display —
        // rootViewLoc[1] > 0, which can legitimately happen depending on how
        // status-bar/edge-to-edge insets are handled on a given Android
        // version — then no scale or translation value computed relative to
        // rootView's own bounds can ever paint above rootView's own top
        // edge, because ViewGroups clip their children to their own bounds
        // by default (clipChildren = true). That's exactly what the
        // screenshot shows: a gap the height of rootView's own top offset,
        // with the wallpaper showing through above it.
        //
        // Fix: measure the actual offset (already have rootViewLoc for
        // free), disable clipChildren on rootView for the duration of this
        // animation so splashView CAN render into that gap via a negative
        // translationY, and extend the target height to cover the true full
        // display height (gap + rootView's own height) instead of just
        // rootView's own height.
        val topGap = rootViewLoc[1].toFloat().coerceAtLeast(0f)
        val rootViewClipChildren = rootView.clipChildren
        if (topGap > 0f) {
            rootView.clipChildren = false
        }

        // ── TEMPORARY DIAGNOSTIC LOGGING ────────────────────────────────────
        // "No changes" after the last fix means topGap is very likely reading
        // as 0 on this device — the fix's guard never activates, which would
        // exactly explain a total no-op. Rather than guess a 4th cause blind,
        // this logs everything needed to actually see what's going on. Search
        // logcat for "PieAnimDebug" right after tapping an icon, and share the
        // output — remove this whole block once we've diagnosed it.
        run {
            val decorView = window?.decorView
            val decorLoc = IntArray(2)
            decorView?.getLocationOnScreen(decorLoc)
            android.util.Log.d(
                "PieAnimDebug",
                "rootViewLoc=(${rootViewLoc[0]}, ${rootViewLoc[1]})  " +
                    "rootView.size=(${rootView.width}, ${rootView.height})  " +
                    "topGap=$topGap  " +
                    "dp.insets=(${dp.insets.left}, ${dp.insets.top}, ${dp.insets.right}, ${dp.insets.bottom})  " +
                    "decorView.loc=(${decorLoc.getOrNull(0)}, ${decorLoc.getOrNull(1)})  " +
                    "decorView.size=(${decorView?.width}, ${decorView?.height})",
            )
        }
        // ── END TEMPORARY DIAGNOSTIC LOGGING ────────────────────────────────

        // screenW/screenH are the SINGLE source of truth for every size AND
        // centering target below (scale-to-fill, crop bounds, endTX/endTY,
        // useUpward's threshold) — they must all agree, or the animation
        // won't land exactly full-screen at t=1 (see reasoning in the
        // corner-radius/crop section further down).
        //
        // FIX (nav bar inset bug, take 2): the previous fix queried insets
        // reactively via ViewCompat.getRootWindowInsets(rootView) at
        // animation-setup time. On Android 11 this still read as off — most
        // likely because getRootWindowInsets() can return null or a stale
        // value depending on exactly when it's queried relative to the last
        // insets dispatch (e.g. mid-tap-gesture), silently falling back to
        // the ?: 0 default and reproducing the original bug with zero actual
        // correction.
        //
        // This version uses DeviceProfile.insets instead — Launcher3's own
        // proactively-maintained Rect, kept in sync via the Activity's own
        // onApplyWindowInsets/updateInsets lifecycle rather than queried
        // ad-hoc, matching how insets are handled elsewhere in this codebase
        // (see the Insettable interface implementations, e.g.
        // LawnchairFloatingSurfaceView.setInsets). This should be more
        // reliable across Android versions since it doesn't depend on
        // dispatch timing at the exact moment of the tap.
        //
        // HONEST CAVEAT: I have not been able to verify, from source alone,
        // whether rootView's own layout already independently excludes
        // system-bar space in some configurations (which would make
        // subtracting dp.insets.bottom on top of that a DOUBLE subtraction,
        // overshooting the other way). If the window now appears to move
        // toward the TOP instead of the bottom, that's the double-subtraction
        // case — let me know and I'll remove the subtraction rather than
        // guess a third variant blind.
        val screenW = rootView.width.toFloat()
        val screenH = (rootView.height - dp.insets.bottom).toFloat() + topGap

        // True-screen-relative Y centre, converted back to rootView-relative
        // coordinates (rootview-relative = true-screen-relative - topGap).
        // Every Y-axis "centre of the screen" target below uses this, not a
        // bare screenH/2 — see the symbolic verification in the commit notes:
        // shifting only this one value is sufficient for the splash's
        // rendered top edge to land at exactly rootview-relative -topGap
        // (== true screen top) at t=1; it propagates correctly through the
        // existing offset math without needing separate patches elsewhere.
        val screenCenterY = screenH / 2f - topGap

        android.util.Log.d(
            "PieAnimDebug",
            "screenW=$screenW  screenH=$screenH  screenCenterY=$screenCenterY  " +
                "iconInRoot.top=${iconInRoot.top}",
        )

        // Translation to bring floating icon centre to screen centre
        val dX = screenW / 2f - iconInRoot.left.toFloat() - rect.width()  / 2f
        val dY = screenCenterY - iconInRoot.top.toFloat()  - rect.height() / 2f

        val useUpward = iconInRoot.top.toFloat() > screenCenterY ||
            kotlin.math.abs(dY) < dp.cellHeightPx.toFloat()

        // ── 3. Floating icon view (mFloatingView equivalent) ───────────────
        // CRITICAL: do NOT use layout() to position the floating view.
        // FrameLayout overrides layout() calls on its children during its own
        // layout pass, resetting left/top back to 0. This causes
        // getLocationOnScreen() to return (0,0), making the splash track to
        // the top-left corner regardless of the icon position.
        //
        // Fix: add with LayoutParams(iconW, iconH) so the view gets left=0,top=0
        // in the parent, then position it with view.x / view.y which set
        // translationX/Y. These persist across layout passes and
        // getLocationOnScreen() correctly includes them.
        val floatStartX = iconInRoot.left.toFloat()
        val floatStartY = iconInRoot.top.toFloat()

        val floatingView = View(this).apply {
            background = if (resolvedView != null)
                captureIconBitmapAsDrawable(resolvedView, rect) else null
        }
        rootView.addView(
            floatingView,
            android.view.ViewGroup.LayoutParams(rect.width(), rect.height()),
        )
        // x/y set translationX/Y (view.left stays 0 after FrameLayout lays out)
        floatingView.x     = floatStartX
        floatingView.y     = floatStartY
        floatingView.pivotX = rect.width()  / 2f
        floatingView.pivotY = rect.height() / 2f

        resolvedView?.let {
            com.android.launcher3.views.FloatingIconViewCompanion.setPropertiesVisible(it, false)
        }

        // ── 4. SplashLayout equivalent ─────────────────────────────────────
        // FAITHFUL PORT of AnimationType_old.kt PieAnimation.getOpeningWindowAnimators
        // (lines 204-275 of the reference source) — this is the ACTUAL algorithm
        // that produced the reference recording, transcribed as directly as
        // possible rather than redesigned:
        //
        //   iconWidth  = bounds.width()  * floatingView.scaleX
        //   iconHeight = bounds.height() * floatingView.scaleY
        //   scale      = min(1, min(iconWidth/windowW, iconHeight/windowH))   -- UNIFORM
        //   offsetX/Y  = (windowSize*scale - iconSize) / 2
        //   transX0/Y0 = floatingView.getLocationOnScreen() - offset
        //
        // Every frame this reads the FLOATING ICON'S OWN LIVE, ANIMATING position
        // and scale (set up in block 5a below — AGGRESSIVE_EASE-driven translate
        // toward screen centre, EXAGGERATED_EASE-driven scale-up) and centres the
        // splash on it. This is precisely what makes the splash visibly travel
        // toward the screen centre WHILE STILL SMALL before flaring out to cover
        // the edges — that behaviour is a direct consequence of reading
        // floatingView's live properties, not something to hand-roll separately.
        // My previous two attempts replaced this with a self-contained lerp that
        // didn't reproduce it; this version restores the exact original mechanism.
        //
        // Crop: starts as a centred SQUARE (screenW x screenW) and reveals to the
        // full rectangle (screenW x screenH) via ViewOutlineProvider — exactly
        // SplashLayout.setCrop()'s behaviour, upgraded from setRect to
        // setRoundRect so it can ALSO carry the corner-radius morph (new,
        // additive — old code had no rounding at all).
        // FIX (part of the "gap at handoff" issue): splashColor was
        // previously resolved from `this` (the LAUNCHER's own theme), never
        // the target app's — meaning even with perfect timing there'd be a
        // colour-mismatch flash the instant the real app's first frame
        // appears underneath. Ported from SplashResolver_old.kt's
        // loadSplash(): resolveActivityInfo → createPackageContext →
        // ContextThemeWrapper(packageContext, theme), which resolves the
        // exact theme the target ACTIVITY will actually render with
        // (respecting per-activity manifest theme overrides). Adapted to
        // pull a solid colorBackground Int (what GradientDrawable.setColor
        // needs) instead of the full windowBackground Drawable old code
        // extracted, since our splash is a plain filled rect, not an
        // arbitrary-background view.
        //
        // Wrapped in runCatching end-to-end: resolveActivityInfo can return
        // null for intents that don't cleanly resolve, and
        // createPackageContext can throw NameNotFoundException (e.g. the
        // package was uninstalled between resolving the Intent and this
        // call). Either way this can only fall back to the PREVIOUS
        // behaviour (launcher's own theme) — it cannot regress below what
        // was already there.
        val splashColor = runCatching {
            val activityInfo = launchIntent?.resolveActivityInfo(packageManager, 0)
            if (activityInfo != null) {
                val themeRes = if (activityInfo.theme != 0) {
                    activityInfo.theme
                } else {
                    activityInfo.applicationInfo.theme
                }
                val packageContext = createPackageContext(activityInfo.packageName, 0)
                val themedContext = android.view.ContextThemeWrapper(packageContext, themeRes)
                val ta = themedContext.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
                val c  = ta.getColor(0, android.graphics.Color.BLACK)
                ta.recycle()
                c
            } else {
                null
            }
        }.getOrNull() ?: run {
            // Fallback: exactly the previous behaviour (launcher's own theme)
            val ta = obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
            val c  = ta.getColor(0, android.graphics.Color.BLACK)
            ta.recycle()
            c
        }
        val splashBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 0f
            setColor(splashColor)
        }
        val initialCropTop = (screenH - screenW) / 2f
        val cropBounds  = floatArrayOf(initialCropTop, initialCropTop + screenW)
        val cropRadius  = floatArrayOf(0f)

        // Starting on-screen corner radius, derived from the CURRENT icon shape
        // (IconShapeManager.getWindowTransitionRadius — a normalised [0,1] hint
        // already defined per built-in shape, e.g. Square=.16f, SharpSquare=0f,
        // Cupertino=.45f, most others default 1f), scaled to the icon's actual
        // half-min-dimension. Lerps to 0 (square) over the same easePercent used
        // for the crop reveal — reproducing the rounded-icon-shape-morphing-to-
        // square look seen on frames ~100-113 of the reference recording. Scale
        // here is UNIFORM (see above), so a single local radius (on-screen
        // radius / scale) is correct — no ellipse/Path complexity needed.
        val shapeTransitionRadius = IconShapeManager.getWindowTransitionRadius(this)
        val startRadiusPx = shapeTransitionRadius *
            kotlin.math.min(rect.width(), rect.height()).toFloat() / 2f

        val splashView = View(this).apply {
            background    = splashBg
            alpha         = 0f
            pivotX        = 0f
            pivotY        = 0f
            clipToOutline = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(
                        0, cropBounds[0].toInt(), view.width, cropBounds[1].toInt(),
                        cropRadius[0].coerceAtLeast(0f),
                    )
                }
            }
        }
        // Native canvas height must be at LEAST the extended screenH (which
        // can now exceed rootView's own original height by topGap) — a View
        // can never render beyond its own allocated size regardless of what
        // the outline/crop specifies, so if this stayed at rootView.height
        // the crop's reveal-to-screenH target would be silently capped
        // short, undoing the top-gap fix above.
        val splashNativeHeight = kotlin.math.max(rootView.height, screenH.toInt())
        rootView.addView(
            splashView,
            android.view.ViewGroup.LayoutParams(rootView.width, splashNativeHeight),
        )
        splashView.layout(0, 0, rootView.width, splashNativeHeight)

        // Initial state: scaled to icon size, translated over the icon —
        // identical formula to the per-frame update at percent=0.
        val initScale = kotlin.math.min(
            rect.width().toFloat()  / screenW,
            rect.height().toFloat() / screenH,
        )
        splashView.scaleX       = initScale
        splashView.scaleY       = initScale
        splashView.translationX = iconInRoot.left.toFloat() - (screenW * initScale - rect.width())  / 2f
        splashView.translationY = iconInRoot.top.toFloat()  - (screenH * initScale - rect.height()) / 2f
        cropRadius[0] = if (initScale > 0f) startRadiusPx / initScale else 0f

        // ── 5. Animators ───────────────────────────────────────────────────
        val anim = AnimatorSet()

        // 5a. Floating icon fly
        val xDur       = if (useUpward) APP_LAUNCH_CURVED_DURATION
                         else (APP_LAUNCH_DOWN_DUR_SCALE * APP_LAUNCH_DURATION).toLong()
        val yDur       = if (useUpward) APP_LAUNCH_DURATION
                         else (APP_LAUNCH_DOWN_DUR_SCALE * APP_LAUNCH_CURVED_DURATION).toLong()
        val maxScale   = kotlin.math.max(screenW / rect.width(), screenH / rect.height())
        val alphaDelay = if (useUpward) APP_LAUNCH_ALPHA_START_DELAY
                         else (APP_LAUNCH_DOWN_DUR_SCALE * APP_LAUNCH_ALPHA_START_DELAY).toLong()
        val alphaDur   = if (useUpward) APP_LAUNCH_ALPHA_DURATION
                         else (APP_LAUNCH_DOWN_DUR_SCALE * APP_LAUNCH_ALPHA_DURATION).toLong()

        // Translation starts from the icon's position (floatStartX/Y) and
        // ends at the screen centre. The 0f→dX approach was wrong because
        // the view's translationX is already floatStartX, not 0.
        // endTY uses screenCenterY (already shifted by -topGap, see its
        // derivation above), not a bare screenH/2 — this is the one value
        // that needs correcting for the top-gap fix; everything downstream
        // (the splash's tracked position) derives from floatingView's live
        // position and inherits the correction automatically.
        val endTX = screenW / 2f - rect.width()  / 2f
        val endTY = screenCenterY - rect.height() / 2f

        anim.playTogether(
            ObjectAnimator.ofFloat(floatingView, View.TRANSLATION_X,
                floatStartX, endTX).apply {
                duration = xDur; interpolator = AGGRESSIVE_EASE
            },
            ObjectAnimator.ofFloat(floatingView, View.TRANSLATION_Y,
                floatStartY, endTY).apply {
                duration = yDur; interpolator = AGGRESSIVE_EASE
            },
            ObjectAnimator.ofFloat(floatingView, View.SCALE_X, 1f, maxScale).apply {
                duration = APP_LAUNCH_DURATION; interpolator = EXAGGERATED_EASE
            },
            ObjectAnimator.ofFloat(floatingView, View.SCALE_Y, 1f, maxScale).apply {
                duration = APP_LAUNCH_DURATION; interpolator = EXAGGERATED_EASE
            },
            ObjectAnimator.ofFloat(floatingView, View.ALPHA, 1f, 0f).apply {
                startDelay = alphaDelay; duration = alphaDur
                interpolator = android.view.animation.LinearInterpolator()
            },
        )

        // 5b. Splash tracks the floating icon's LIVE position/scale each frame —
        // faithful port of AnimationType_old.kt lines 229-271. This is what
        // makes the splash visibly travel with the icon toward screen centre
        // (since floatingView itself is animating there via 5a's AGGRESSIVE_EASE
        // translate) before its own growth (uniform `scale`, tied to
        // floatingView's EXAGGERATED_EASE scale-up) covers the full screen.
        val floatingScreenLoc = IntArray(2)
        anim.playTogether(
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration     = APP_LAUNCH_DURATION
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { va ->
                    val percent     = va.animatedFraction
                    val easePercent = AGGRESSIVE_EASE.getInterpolation(percent)

                    // Read current animated position of the floating icon
                    floatingView.getLocationOnScreen(floatingScreenLoc)
                    val floatX = floatingScreenLoc[0].toFloat() - rootViewLoc[0]
                    val floatY = floatingScreenLoc[1].toFloat() - rootViewLoc[1]

                    // Current icon dimensions after scale animation
                    val iconW = rect.width()  * floatingView.scaleX
                    val iconH = rect.height() * floatingView.scaleY

                    // Scale splash to match icon's current screen size (uniform,
                    // matching the reference algorithm exactly)
                    val scale  = kotlin.math.min(1f,
                        kotlin.math.min(iconW / screenW, iconH / screenH))
                    val scaledW = screenW * scale
                    val scaledH = screenH * scale

                    // Centre splash on the icon
                    val offX = (scaledW - iconW) / 2f
                    val offY = (scaledH - iconH) / 2f
                    splashView.scaleX       = scale
                    splashView.scaleY       = scale
                    splashView.translationX = floatX - offX
                    splashView.translationY = floatY - offY

                    // Alpha 0 → 1 over first ~60 ms (matches old mAlpha FloatProp timing)
                    splashView.alpha = kotlin.math.min(
                        1f, percent * (APP_LAUNCH_DURATION.toFloat() / 60f),
                    )

                    // Crop: centered square → full rectangle (square-to-window morph)
                    val cropH     = screenH * easePercent + screenW * (1f - easePercent)
                    cropBounds[0] = initialCropTop * (1f - easePercent)
                    cropBounds[1] = cropBounds[0] + cropH

                    // Corner radius: current icon shape's radius → 0 (square), lerped
                    // in ON-SCREEN pixels, then converted to the splash view's local
                    // (unscaled, full-screen-sized) coordinate space by dividing by
                    // the CURRENT (uniform) scale — additive on top of the crop,
                    // not present in the original reference algorithm.
                    val curRadiusPx = lerpFloat(startRadiusPx, 0f, easePercent)
                    cropRadius[0] = if (scale > 0f) curRadiusPx / scale else 0f
                    splashView.invalidateOutline()
                }
            },
        )

        // 5c. dragLayer fade: alpha 1 → 0 over APP_LAUNCH_DURATION.
        // Frame-by-frame analysis of old Lawnchair 2 recording confirms:
        //   - dragLayer FADES OUT (alpha only, NO scale/zoom at all)
        //   - Launcher gradually becomes transparent revealing wallpaper
        //   - Scale stays 1.0 throughout the opening animation
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        anim.playTogether(
            ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
                duration     = APP_LAUNCH_DURATION
                interpolator = APP_OPEN_HOME_EXIT_ALPHA_INTERP
            },
        )

        // ── 6. Cleanup ─────────────────────────────────────────────────────
        // FIX (part of the "gap at handoff" issue): previously, splashView
        // (still fully opaque, still covering the entire screen) and
        // floatingView were both removeView()'d, AND layer.alpha was reset
        // to 1.0, all synchronously in the same instant the 500ms animation
        // ended. On a device where the real app's window hasn't actually
        // finished compositing by exactly 500ms (more likely on a slower
        // cold start, e.g. Android 11) — this fake overlay approach has no
        // way to know the real app is actually ready, since that signal
        // requires CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS, which
        // non-system installs don't have — tearing everything down
        // synchronously reveals whatever IS currently on screen at that
        // exact instant, which can be a half-drawn frame or (worse) the
        // launcher's own content popping briefly back into view before the
        // real app's window is actually composited on top of it. That's
        // the visible "gap."
        //
        // Mitigation (this can only reduce the chance of a visible gap, it
        // cannot fully eliminate the underlying race without system
        // permissions we don't have): hold splashView in place, fully
        // opaque, for a short grace period past the main 500ms animation —
        // giving the real app extra time to actually finish compositing
        // underneath — then fade it out gracefully rather than cutting it
        // instantly, so any still-residual mismatch is smoothed over rather
        // than popped.
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // TEMPORARY DIAGNOSTIC: log splashView's ACTUAL final rendered
                // state before any cleanup touches it. My symbolic derivation
                // predicts translationY should be ~0 (true screen top) here
                // when topGap==0 — if the logged value is NOT ~0, the bug is
                // in the runtime values themselves (crop/scale/tracking), not
                // in the coordinate-offset math I already checked. If it IS
                // ~0 here but the screenshot still shows a gap, the bug is
                // downstream of this point entirely (grace period, fade-out,
                // or the crop/outline not actually extending that far).
                android.util.Log.d(
                    "PieAnimDebug",
                    "FINAL splashView: translationX=${splashView.translationX} " +
                        "translationY=${splashView.translationY} " +
                        "scaleX=${splashView.scaleX} scaleY=${splashView.scaleY} " +
                        "alpha=${splashView.alpha} " +
                        "cropBounds=(${cropBounds[0]}, ${cropBounds[1]}) " +
                        "cropRadius=${cropRadius[0]} " +
                        "nativeSize=(${splashView.width}, ${splashView.height})",
                )

                // floatingView is already invisible (alpha faded to 0 well
                // before this point), so removing it immediately has no
                // visual effect either way.
                runCatching { rootView.removeView(floatingView) }
                resolvedView?.let {
                    com.android.launcher3.views.FloatingIconViewCompanion
                        .setPropertiesVisible(it, true)
                }

                // Safe to restore layer's own visibility/layer-type now:
                // splashView is still on top (added to rootView after
                // dragLayer, so it draws over it) and still fully opaque,
                // so this doesn't cause any visible pop on its own.
                layer.alpha = 1.0f
                layer.setLayerType(View.LAYER_TYPE_NONE, null)

                splashView.postDelayed({
                    ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f).apply {
                        duration     = PIE_SPLASH_FADEOUT_MS
                        interpolator = android.view.animation.LinearInterpolator()
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(fadeAnim: Animator) {
                                runCatching { rootView.removeView(splashView) }
                                splashView.setLayerType(View.LAYER_TYPE_NONE, null)
                                // Must restore this — we only disabled it to
                                // let splashView paint into the top-gap area
                                // above rootView's own bounds during this
                                // one animation; leaving it off permanently
                                // would be a real (if usually invisible)
                                // regression to rootView's normal clipping.
                                rootView.clipChildren = rootViewClipChildren
                            }
                        })
                        start()
                    }
                }, PIE_SPLASH_GRACE_MS)
            }
        })

        anim.start()
    }

    private fun lerpInt(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt()

    private fun lerpFloat(from: Float, to: Float, t: Float): Float =
        from + (to - from) * t


    /** Fallback PIE when no icon view is available — scale/fade from screen centre. */
    private fun playSimplePieLaunchAnimation(layer: View) {
        // Fallback: no icon view available. Fade launcher only.
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
            duration     = APP_LAUNCH_DURATION
            interpolator = APP_OPEN_HOME_EXIT_ALPHA_INTERP
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
     * Captures [v]'s icon portion as a [android.graphics.drawable.BitmapDrawable].
     * Used as fallback when DrawableFactory.newIcon() is unavailable.
     */
    private fun captureIconBitmapAsDrawable(
        v: View,
        bounds: android.graphics.Rect,
    ): android.graphics.drawable.Drawable? {
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        return runCatching {
            val bmp = android.graphics.Bitmap.createBitmap(
                bounds.width(), bounds.height(), android.graphics.Bitmap.Config.ARGB_8888,
            )
            val c = android.graphics.Canvas(bmp)
            c.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            v.draw(c)
            android.graphics.drawable.BitmapDrawable(resources, bmp)
        }.getOrNull()
    }

    // ── SLIDE_UP: launcher slides upward out of the way ───────────────────

    /**
     * Slide-up launcher exit.
     * dragLayer translates from Y=0 to Y=-height (slides off the top)
     * while fading out in the second half.
     */
    private fun playSlideUpLaunchAnimation() {
        val layer = dragLayer ?: return
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    layer, View.TRANSLATION_Y, 0f, -layer.height.toFloat(),
                ).apply {
                    duration     = 350L
                    interpolator = PathInterpolator(0.2f, 0.0f, 0.2f, 1.0f)
                },
                ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
                    duration     = 175L
                    startDelay   = 175L
                    interpolator = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
                },
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.translationY = 0f
                    layer.alpha        = 1.0f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    // ── BLINK: three rapid flashes on the dragLayer ───────────────────────

    /**
     * Blink launcher exit — three rapid alpha flashes over 400 ms.
     *
     * Flash timing (each segment ≈ 80 ms):
     *   0–20%  visible → hidden  (flash 1 off)
     *   20–40% hidden  → visible (flash 1 on)
     *   40–60% visible → hidden  (flash 2 off)
     *   60–80% hidden  → visible (flash 2 on)
     *   80–100%visible → hidden  (final off)
     */
    private fun playBlinkLaunchAnimation() {
        val layer = dragLayer ?: return
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                layer.alpha = when {
                    t < 0.20f -> 1.0f
                    t < 0.40f -> 0.0f
                    t < 0.60f -> 1.0f
                    t < 0.80f -> 0.0f
                    else      -> 0.0f
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

    // ── FADE: dragLayer fades to transparent ──────────────────────────────

    /**
     * Fade launcher exit.
     * Fading the launcher (exit side) rather than the app (enter side) means
     * it always works — Android 12+ SplashScreen can replace app-enter anims
     * on cold starts but never replaces launcher-exit View animations.
     */
    private fun playFadeLaunchAnimation() {
        val layer = dragLayer ?: return
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
            duration     = 250L
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

    // ── Return transition ─────────────────────────────────────────────────

    /**
     * Called when the launcher is made visible again (e.g. user pressed Back).
     *
     * [overridePendingTransition] here acts as a secondary fallback for devices
     * where [LawnchairQuickstepTransitionManager] could not be injected (e.g.
     * reflection failed). On Android 11 and without Quickstep this is the
     * primary closing animation path. On Android 14 with Quickstep the
     * transition manager's [createWallpaperOpenAnimations] override handles it.
     *
     * Must be called from [onStart] (not [onResume]) so it is registered before
     * the system commits the window swap.
     */
    override fun onStart() {
        super.onStart()
        applyResumeTransitionForAnimationType(lastAppOpenAnimationType)
    }

    @Suppress("DEPRECATION") // overridePendingTransition still functional on API 34
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

    // ── Helper: resolve icon-level bounds within a view ───────────────────

    /**
     * Returns the pixel bounds of the icon drawable within [v].
     * For [BubbleTextView] this is the icon-only rect (excluding the label).
     * For any other view this is the full view rect.
     * Returns null when [v] is null.
     */
    private fun getIconBoundsForView(v: View?): Rect? {
        if (v == null) return null
        var left = 0
        var top = 0
        var width = v.measuredWidth
        var height = v.measuredHeight
        if (v is BubbleTextView) {
            val icon: Drawable? = v.icon
            if (icon != null) {
                val b = icon.bounds
                left   = (width  - b.width())  / 2
                top    = v.paddingTop
                width  = b.width()
                height = b.height()
            }
        }
        return Rect(left, top, left + width, top + height)
    }

    // ── Default (system clip-reveal from icon) ────────────────────────────

    private fun getActivityLaunchOptionsDefault(v: View?): ActivityOptionsWrapper {
        var left   = 0
        var top    = 0
        var width  = v?.measuredWidth  ?: 0
        var height = v?.measuredHeight ?: 0
        if (v is BubbleTextView) {
            val icon: Drawable? = v.icon
            if (icon != null) {
                val bounds = icon.bounds
                left   = (width  - bounds.width())  / 2
                top    = v.paddingTop
                width  = bounds.width()
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

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        restartIfPending()

        // Re-check system accent on every resume. On OEM devices (e.g. Samsung) the
        // OVERLAY_CHANGED broadcast is never fired for accent changes, so ThemeProvider
        // never learns the palette changed while Lawnchair was in the background.
        // reseedSystemAccentIfChanged() only triggers a recreate if the color actually
        // differs, so this is a no-op on normal resumes.
        themeProvider.reseedSystemAccentIfChanged()

        // If an icon pack switch happened while in background, show overlay now.
        // We also start the idle timer here — the timer from LawnchairIconProvider
        // may have already fired against a null/paused instance and been lost.
        // Starting it fresh in onResume ensures it runs against the live dragLayer.
        if (iconPackSwitchPending) {
            showIconPackSwitchOverlay()
            startIconPackSwitchIdleTimer()
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

    override fun onStop() {
        super.onStop()
        // Cancel any pending debounced recreate — this activity is stopping, so
        // there is no point recreating it. If the theme change is still relevant,
        // the new active instance will receive it via its own subscription.
        dragLayer.removeCallbacks(recreateDebounceRunnable)
        // Force-remove the icon pack switch overlay immediately when stopping.
        //
        // When recreate() replaces this activity, onStop() is called before onDestroy().
        // The overlay is a FrameLayout(this) — it holds a hard reference to this Activity
        // as its Context. If the overlay's fade-out animation is still running (300ms),
        // the animator holds the View alive, which holds the Activity alive, which holds
        // the Window alive — preventing the ViewRootImpl from being released and the EGL
        // surface from being freed. With 3–4 rapid pack changes this stacks to 5+
        // ViewRootImpl instances and 40+ MB of unreleased EGL memory.
        //
        // Since the activity is stopping (window no longer visible), animation is moot.
        // Cancel it and remove the view synchronously.
        // Do NOT clear iconPackSwitchPending here — the companion flag is owned by the
        // active launcher; clearing it from a stale stopping activity would prevent the
        // new launcher from showing its overlay.
        dragLayer.removeCallbacks(iconPackIdleRunnable)
        iconPackOverlay?.let { overlay ->
            overlay.animate().cancel()
            dragLayer.removeView(overlay)
            iconPackOverlay = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SmartspacerClient.close()
        // Belt-and-suspenders: cancel pending recreate and remove any overlay re-added
        // between onStop() and onDestroy(). Primary cleanup happens in onStop() above.
        dragLayer.removeCallbacks(recreateDebounceRunnable)
        dragLayer.removeCallbacks(iconPackIdleRunnable)
        iconPackOverlay?.let { overlay ->
            overlay.animate().cancel()
            dragLayer.removeView(overlay)
            iconPackOverlay = null
        }
    }

    override fun getDefaultOverlay(): LauncherOverlayManager = defaultOverlay

    fun recreateIfNotScheduled() {
        if (sRestartFlags == 0) recreate()
    }

    // ── Icon pack switch loading overlay ──────────────────────────────────

    private var iconPackOverlay: View? = null
    private val iconPackIdleRunnable = Runnable { dismissIconPackSwitchOverlay() }
    private val iconPackIdleDelayMs = 600L

    fun showIconPackSwitchOverlay() {
        // Guard: dragLayer must be attached and visible before we can add views.
        // If the launcher is paused/stopped, skip — onResume() will show it later.
        if (!dragLayer.isAttachedToWindow) return

        val existing = iconPackOverlay
        if (existing != null) {
            // The overlay is already in dragLayer — it may be fully visible or
            // mid-fade-out from a previous dismissIconPackSwitchOverlay() call.
            // Cancel the fade-out and restore full opacity so it stays visible
            // for the new icon pack switch cycle.
            // Without this, a new switch arriving during the 300ms fade-out would
            // see iconPackOverlay == null (already nulled at dismiss start) and add
            // a second scrim on top of the fading one — leaving an orphaned View in
            // dragLayer with no reference to ever dismiss it cleanly.
            existing.animate().cancel()
            existing.alpha = 1f
            return
        }

        val density = resources.displayMetrics.density
        val indicatorSize = (72 * density).toInt()

        // M3 Expressive "Contained loading indicator" via ContextThemeWrapper.
        // Falls back to plain ProgressBar if Material alpha library is unavailable.
        val indicator: View = try {
            val cls = Class.forName(
                "com.google.android.material.loadingindicator.LoadingIndicator")
            val styleId = resources.getIdentifier(
                "Widget_Material3_LoadingIndicator_Contained", "style", packageName)
            val ctx = if (styleId != 0) android.view.ContextThemeWrapper(this, styleId)
                      else this
            cls.getConstructor(android.content.Context::class.java)
                .newInstance(ctx) as View
        } catch (_: Exception) {
            val ta = obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
            val primaryColor = ta.getColor(0, android.graphics.Color.BLUE)
            ta.recycle()
            android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyle).apply {
                isIndeterminate = true
                indeterminateTintList =
                    android.content.res.ColorStateList.valueOf(primaryColor)
            }
        }

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.argb(160, 0, 0, 0))
        }
        scrim.addView(indicator, FrameLayout.LayoutParams(indicatorSize, indicatorSize).apply {
            gravity = android.view.Gravity.CENTER
        })
        scrim.alpha = 0f
        dragLayer.addView(scrim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        scrim.animate().alpha(1f).setDuration(200).start()
        iconPackOverlay = scrim
    }

    /**
     * Starts or resets the 600ms idle timer. When no further call arrives for 600ms,
     * the overlay is dismissed — meaning all visible icon updates have completed.
     * Also clears [iconPackSwitchPending] so that if the launcher is recreated or
     * resumed after dismiss, it does not show a stale overlay.
     */
    fun startIconPackSwitchIdleTimer() {
        // Only run if the overlay is actually showing. If the launcher was in the
        // background when this was called, the overlay may not exist yet — onResume()
        // will show it and call startIconPackSwitchIdleTimer() again.
        if (iconPackOverlay == null && !iconPackSwitchPending) return
        dragLayer.removeCallbacks(iconPackIdleRunnable)
        dragLayer.postDelayed(iconPackIdleRunnable, iconPackIdleDelayMs)
    }

    fun dismissIconPackSwitchOverlay() {
        dragLayer.removeCallbacks(iconPackIdleRunnable)
        // Only clear the static companion flag if this activity is still alive.
        // If this is called from a stale iconPackIdleRunnable that fired after
        // onDestroy() (e.g. the activity was recreated by a theme change while the
        // 600ms timer was running), clearing the flag would prevent the new activity
        // from seeing iconPackSwitchPending=true in onResume() and skipping its overlay.
        if (!isDestroyed) iconPackSwitchPending = false
        val overlay = iconPackOverlay ?: return
        // Do NOT null iconPackOverlay here. Nulling before the animation ends creates
        // a 300ms window where showIconPackSwitchOverlay() sees null and adds a second
        // scrim on top of the fading first one — an orphaned View with no reference.
        // Null it inside withEndAction, guarded by identity to avoid clearing a fresh
        // overlay added by a concurrent showIconPackSwitchOverlay() call.
        overlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                if (iconPackOverlay === overlay) iconPackOverlay = null
                dragLayer.removeView(overlay)
            }
            .start()
    }

    private fun restartIfPending() {
        when {
            sRestartFlags and FLAG_RESTART  != 0 -> lawnchairApp.restart(false)
            sRestartFlags and FLAG_RECREATE != 0 -> {
                sRestartFlags = 0
                // Same guard as updateTheme(): skip if already finishing so rapid
                // preference changes don't stack multiple recreate() calls in flight.
                if (!isFinishing && !isDestroyed) recreate()
            }
        }
    }

    private fun reloadIconsIfNeeded() {
        if (preferenceManager2.alwaysReloadIcons.firstCached()) {
            LauncherAppState.getInstance(this).model.reloadIfActive()
        }
    }

    companion object {
        private const val FLAG_RECREATE = 1 shl 0
        private const val FLAG_RESTART  = 1 shl 1

        /**
         * How long to wait after the last [updateTheme] call before actually calling
         * [recreate]. Theme providers can emit multiple rapid changes for a single
         * icon pack switch; this collapses them into one recreate.
         */
        private const val RECREATE_DEBOUNCE_MS = 150L

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
