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
    // OUR SIMPLIFIED APPROACH (no SplashLayout needed, same visual effect):
    //   1. Capture a Bitmap of the icon from the BubbleTextView
    //   2. Add a floating ImageView copy at the icon's dragLayer-relative position
    //   3. Animate: floating copy translates from icon position → screen center
    //               + dragLayer scales 1.0→1.5 with pivot at icon center
    //               + dragLayer alpha 1→0
    //               + floating copy alpha 0→1→0 (appears, then fades as launcher fades)
    //   4. onStart() applies the return overridePendingTransition (works on Android 11,
    //      no-op on Android 14+Quickstep where remote anim intercepts)
    //
    // ANDROID 14 + QUICKSTEP CLOSING LIMITATION:
    //   QuickstepTransitionManager registers a RemoteAnimationRunner for
    //   ACTIVITY_CLOSE transitions. overridePendingTransition() is ignored.
    //   Fixing this requires hooking into QuickstepTransitionManager's
    //   createLauncherResumeAnimation() — out of scope here.
    // ═══════════════════════════════════════════════════════════════════════

    override fun getActivityLaunchOptions(v: View?, item: ItemInfo?): ActivityOptionsWrapper {
        val animationType = preferenceManager2.appOpenAnimation.firstBlocking()
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
                AppOpenAnimationType.PIE      -> playPieLaunchAnimation(v)
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
     * Constants match the old companion object in LawnchairAppTransitionManagerImpl:
     *   APP_OPEN_HOME_EXIT_SCALE_FROM/TO = 1.0/1.5, DURATION = 250 ms
     *   APP_OPEN_HOME_EXIT_ALPHA_FROM/TO = 1.0/0.0, DURATION = 250 ms
     */
    private fun playPieLaunchAnimation(iconView: View?) {
        val layer = dragLayer ?: return

        // ── Step 1: Resolve icon screen position ───────────────────────────
        val iconBounds = getIconBoundsForView(iconView)
        val iconScreenLoc = IntArray(2)
        iconView?.getLocationOnScreen(iconScreenLoc)

        // dragLayer-relative coordinates for the icon
        val layerScreenLoc = IntArray(2)
        layer.getLocationOnScreen(layerScreenLoc)

        val iconRelLeft: Float
        val iconRelTop: Float
        val iconW: Float
        val iconH: Float

        if (iconView != null && iconBounds != null) {
            iconRelLeft = (iconScreenLoc[0] + iconBounds.left - layerScreenLoc[0]).toFloat()
            iconRelTop  = (iconScreenLoc[1] + iconBounds.top  - layerScreenLoc[1]).toFloat()
            iconW = iconBounds.width().toFloat()
            iconH = iconBounds.height().toFloat()
        } else {
            // No icon available — fall back to simple scale/alpha from centre.
            layer.pivotX = layer.width / 2f
            layer.pivotY = layer.height / 2f
            playSimplePieLaunchAnimation(layer)
            return
        }

        val iconCentreX = iconRelLeft + iconW / 2f
        val iconCentreY = iconRelTop  + iconH / 2f

        // ── Step 2: Capture icon bitmap ────────────────────────────────────
        val iconBitmap = captureIconBitmap(iconView, iconBounds)

        // ── Step 3: Create floating icon ImageView on dragLayer.PARENT ────
        //
        // CRITICAL FIX: add the floating icon to dragLayer.parent, NOT to
        // dragLayer itself. dragLayer is the view being scaled 1.0→1.5 in the
        // exit animation. If the floating ImageView is a child of dragLayer, it
        // is transformed along with dragLayer — so instead of flying to screen
        // centre it appears to slide toward the edge (inheriting the scale pivot
        // displacement). Adding it to the parent puts it in a coordinate space
        // that is unaffected by the dragLayer scale, matching the old
        // LawnchairAppTransitionManagerImpl behaviour where the FloatingIconView
        // was added directly to the drag layer's parent container.
        val iconParent = layer.parent as? ViewGroup
        val floatingIcon: ImageView? = if (iconBitmap != null && iconParent != null) {
            ImageView(this).apply {
                setImageBitmap(iconBitmap)
                scaleType = ImageView.ScaleType.FIT_XY
            }
        } else null

        // Position is relative to iconParent (same coordinate space as dragLayer
        // since dragLayer fills iconParent, but without being subject to dragLayer's
        // own scale transform).
        val floatingLp = FrameLayout.LayoutParams(iconW.toInt(), iconH.toInt()).apply {
            leftMargin = iconRelLeft.toInt()
            topMargin  = iconRelTop.toInt()
        }

        if (floatingIcon != null) {
            iconParent?.addView(floatingIcon, floatingLp)
        }

        // ── Step 4: Set dragLayer pivot to icon centre ─────────────────────
        layer.pivotX = iconCentreX
        layer.pivotY = iconCentreY
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val screenCentreX = layer.width  / 2f
        val screenCentreY = layer.height / 2f

        // Translation needed to bring the floating icon's centre to screen centre
        val floatTargetTX = screenCentreX - iconCentreX
        val floatTargetTY = screenCentreY - iconCentreY

        // ── Step 5: Build animators ────────────────────────────────────────
        val scaleInterp = PathInterpolator(0.2f, 0.5f, 0.2f, 1.0f)
        val alphaInterp = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
        val duration    = 350L  // slightly longer than original 250 ms so the fly is visible

        val animators = mutableListOf<Animator>()

        // dragLayer scale: 1.0 → 1.5
        animators += ObjectAnimator.ofFloat(layer, View.SCALE_X, 1.0f, 1.5f).apply {
            this.duration = duration; interpolator = scaleInterp
        }
        animators += ObjectAnimator.ofFloat(layer, View.SCALE_Y, 1.0f, 1.5f).apply {
            this.duration = duration; interpolator = scaleInterp
        }
        // dragLayer alpha: 1 → 0
        animators += ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
            this.duration = duration; interpolator = alphaInterp
        }

        if (floatingIcon != null) {
            // Floating icon translate to screen centre
            animators += ObjectAnimator.ofFloat(
                floatingIcon, View.TRANSLATION_X, 0f, floatTargetTX,
            ).apply { this.duration = duration; interpolator = scaleInterp }
            animators += ObjectAnimator.ofFloat(
                floatingIcon, View.TRANSLATION_Y, 0f, floatTargetTY,
            ).apply { this.duration = duration; interpolator = scaleInterp }
            // Scale up slightly (the icon "grows" toward the user as it flies)
            animators += ObjectAnimator.ofFloat(
                floatingIcon, View.SCALE_X, 1.0f, 1.8f,
            ).apply { this.duration = duration; interpolator = scaleInterp }
            animators += ObjectAnimator.ofFloat(
                floatingIcon, View.SCALE_Y, 1.0f, 1.8f,
            ).apply { this.duration = duration; interpolator = scaleInterp }
            // Floating icon fades out in the second half of the animation
            animators += ObjectAnimator.ofFloat(
                floatingIcon, View.ALPHA, 1.0f, 0.0f,
            ).apply {
                this.duration = duration / 2
                startDelay     = duration / 2
                interpolator   = alphaInterp
            }
        }

        // ── Step 6: Play ───────────────────────────────────────────────────
        AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (floatingIcon != null) {
                        iconParent?.removeView(floatingIcon)
                        iconBitmap?.recycle()
                    }
                    layer.scaleX = 1.0f
                    layer.scaleY = 1.0f
                    layer.alpha  = 1.0f
                    layer.pivotX = layer.width  / 2f
                    layer.pivotY = layer.height / 2f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    /**
     * Simple Pie exit when no icon view is available (e.g. launched from
     * a shortcut or notification). Zooms out from screen centre.
     */
    private fun playSimplePieLaunchAnimation(layer: View) {
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val scaleInterp = PathInterpolator(0.2f, 0.5f, 0.2f, 1.0f)
        val alphaInterp = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(layer, View.SCALE_X, 1.0f, 1.5f).apply {
                    duration = 250L; interpolator = scaleInterp
                },
                ObjectAnimator.ofFloat(layer, View.SCALE_Y, 1.0f, 1.5f).apply {
                    duration = 250L; interpolator = scaleInterp
                },
                ObjectAnimator.ofFloat(layer, View.ALPHA, 1.0f, 0.0f).apply {
                    duration = 250L; interpolator = alphaInterp
                },
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.scaleX = 1.0f
                    layer.scaleY = 1.0f
                    layer.alpha  = 1.0f
                    layer.pivotX = layer.width  / 2f
                    layer.pivotY = layer.height / 2f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    /**
     * Captures a [Bitmap] of just the icon portion of [v].
     *
     * For a [BubbleTextView] we crop to [iconBounds] so we get only the
     * icon drawable, not the label. For other views we capture the whole view.
     * Returns null if the bitmap would be zero-sized or drawing fails.
     */
    private fun captureIconBitmap(v: View, iconBounds: Rect): Bitmap? {
        if (iconBounds.width() <= 0 || iconBounds.height() <= 0) return null
        return try {
            val bmp = Bitmap.createBitmap(
                iconBounds.width(), iconBounds.height(), Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            // Translate so the icon's top-left aligns with the canvas origin
            canvas.translate(-iconBounds.left.toFloat(), -iconBounds.top.toFloat())
            v.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
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

    // ── Icon pack switch loading overlay ──────────────────────────────────

    private var iconPackOverlay: View? = null

    fun showIconPackSwitchOverlay() {
        if (iconPackOverlay != null) return
        val density = resources.displayMetrics.density
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
        val primaryColor = ta.getColor(0, android.graphics.Color.BLUE)
        ta.recycle()
        val containerSize = (72 * density).toInt()
        val spinnerSize   = (40 * density).toInt()
        val containerBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.argb(
                30,
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor),
            ))
        }
        val container = FrameLayout(this).apply { background = containerBg }
        val spinner = android.widget.ProgressBar(
            this, null, android.R.attr.progressBarStyle,
        ).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        }
        container.addView(
            spinner,
            FrameLayout.LayoutParams(spinnerSize, spinnerSize).apply {
                gravity = android.view.Gravity.CENTER
            },
        )
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.argb(160, 0, 0, 0))
        }
        scrim.addView(
            container,
            FrameLayout.LayoutParams(containerSize, containerSize).apply {
                gravity = android.view.Gravity.CENTER
            },
        )
        scrim.alpha = 0f
        dragLayer.addView(
            scrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
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
            sRestartFlags and FLAG_RESTART  != 0 -> lawnchairApp.restart(false)
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
        private const val FLAG_RESTART  = 1 shl 1

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
