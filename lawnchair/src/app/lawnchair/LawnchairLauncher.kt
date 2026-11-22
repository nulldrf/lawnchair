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
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Pair
import android.view.Display
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.window.SplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairApp.Companion.showQuickstepWarningIfNecessary
import app.lawnchair.animations.LawnchairAppTransitionController
import app.lawnchair.compat.LawnchairQuickstepCompat
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.service.WallpaperService
import app.lawnchair.gestures.GestureController
import app.lawnchair.gestures.VerticalSwipeTouchController
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.ui.LawnchairShortcutActivity
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.preferences2.setCached
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.LawnchairShortcut
import app.lawnchair.util.getThemedIconPacksInstalled
import app.lawnchair.util.unsafeLazy
import app.lawnchair.views.LawnchairFloatingSurfaceView
import app.lawnchair.views.createContainedLoadingIndicator
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.GestureNavContract
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.PredictedContainerInfo
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
import com.android.launcher3.util.IntSet
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LawnchairLauncher : QuickstepLauncher() {

    private val defaultOverlay by unsafeLazy { OverlayCallbackImpl(this) }
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

    val gestureController by unsafeLazy { GestureController(this) }
    val appTransitionController by unsafeLazy { LawnchairAppTransitionController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        layoutInflater.factory2 = LawnchairLayoutFactory(this)
        super.onCreate(savedInstanceState)

        colorScheme = themeProvider.colorScheme

        preferenceManager2.launcherTheme.get().distinctUntilChanged().drop(1).onEach {
            updateTheme()
        }.launchIn(scope = lifecycleScope)
        preferenceManager2.feedProvider.get().distinctUntilChanged().drop(1).onEach {
            defaultOverlay.reconnect()
        }.launchIn(scope = lifecycleScope)
        preferenceManager2.enableFeed.get().distinctUntilChanged().onEach { enable ->
            defaultOverlay.setEnableFeed(enable)
        }.launchIn(scope = lifecycleScope)
        launcher.stateManager.addStateListener(clearSearchStateListener)

        if (preferenceManager2.autoLaunchRoot.firstCached()) {
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

        preferenceManager2.statusBarClock.get().distinctUntilChanged().onEach {
            with(launcher.stateManager) {
                if (it) {
                    addStateListener(statusBarClockListener)
                } else {
                    removeStateListener(statusBarClockListener)
                    LawnchairApp.instance.restoreClockInStatusBar()
                }
            }
        }.launchIn(scope = lifecycleScope)
        preferenceManager2.rememberPosition.get().distinctUntilChanged().onEach {
            with(launcher.stateManager) {
                if (it) addStateListener(rememberPositionStateListener)
                else removeStateListener(rememberPositionStateListener)
            }
        }.launchIn(scope = lifecycleScope)

        preferenceManager2.overrideWindowCornerRadius.onEach(launchIn = lifecycleScope) {
            QuickStepContract.sHasCustomCornerRadius = it
        }
        preferenceManager2.windowCornerRadius.onEach(launchIn = lifecycleScope) {
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

        lifecycleScope.launch {
            LauncherOptionsPopup.restoreMissingPopupOptions(launcher)
            LauncherOptionsPopup.migrateLegacyPreferences(launcher)
        }

        if (preferenceManager2.themedIcons.firstCached() &&
            packageManager.getThemedIconPacksInstalled(this).isEmpty()
        ) {
            preferenceManager2.themedIcons.setCached(false)
        }

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

    override fun getAllAppsItemLongClickListener(): View.OnLongClickListener {
        return View.OnLongClickListener { view ->
            if (view is FolderIcon && view.mInfo.id != ItemInfo.NO_ID) {
                LawnchairShortcut.showAppDrawerFolderPopup(this, view)
            } else {
                super.getAllAppsItemLongClickListener().onLongClick(view)
            }
        }
    }

    override fun getSupportedShortcuts(container: Int): Stream<SystemShortcut.Factory<*>> = Stream.concat(
        super.getSupportedShortcuts(container),
        Stream.concat(
            Stream.of(LawnchairShortcut.UNINSTALL, LawnchairShortcut.CUSTOMIZE, LawnchairShortcut.OPEN_IN_STORE),
            Stream.of(LawnchairShortcut.PAUSE_APPS),
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

    override fun onStateBack() {
        val searchInput = mAppsView?.searchUiManager?.editText
        val isSearching = mAppsView?.isSearching == true || searchInput?.hasFocus() == true
        if (isSearching) {
            mAppsView?.searchUiManager?.resetSearch()
            allAppsController.animateAllAppsToNoScale()
        } else {
            super.onStateBack()
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
        if (!LawnchairApp.isRecentsEnabled && preferenceManager2.enableGnc.firstCached()) {
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

    override fun getActivityLaunchOptions(v: View?, item: ItemInfo?): ActivityOptionsWrapper {
        val customOptions = appTransitionController.getActivityLaunchOptions(v)
        if (customOptions != null) {
            Utilities.allowBGLaunch(customOptions)
            customOptions.launchDisplayId =
                if (v?.display != null) v.display.displayId else Display.DEFAULT_DISPLAY
            val callbacks = RunnableList()
            addEventCallback(EVENT_RESUMED, callbacks::executeAllAndDestroy)
            return ActivityOptionsWrapper(customOptions, callbacks)
        }
        return runCatching {
            super.getActivityLaunchOptions(v, item)
        }.getOrElse {
            getActivityLaunchOptionsDefault(v)
        }
    }

    override fun startActivitySafely(v: View?, intent: Intent, item: ItemInfo?): RunnableList? {
        val wasResumed = hasBeenResumed()
        val callbacks = super.startActivitySafely(v, intent, item)
        if (callbacks != null && wasResumed) {
            appTransitionController.playLaunchAnimation(v, intent)
        }
        return callbacks
    }

    override fun onStart() {
        super.onStart()
        appTransitionController.overrideResumeAnimation()
    }

    private fun getActivityLaunchOptionsDefault(v: View?): ActivityOptionsWrapper {
        var left = 0
        var top = 0
        var width = v?.measuredWidth ?: 0
        var height = v?.measuredHeight ?: 0
        if (v is BubbleTextView) {
            // Launch from center of icon, not entire view
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
            if (v != null) {
                ActivityOptions.makeClipRevealAnimation(v, left, top, width, height)
            } else {
                ActivityOptions.makeBasic()
            },
        )
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId =
            if (v?.display != null) v.display.displayId else Display.DEFAULT_DISPLAY
        val callbacks = RunnableList()
        addEventCallback(EVENT_RESUMED, callbacks::executeAllAndDestroy)
        return ActivityOptionsWrapper(options, callbacks)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        restartIfPending()
        refreshPredictionContainersFromModel()

        // Re-check system accent on every resume. On OEM devices (e.g. Samsung) the
        // OVERLAY_CHANGED broadcast is never fired for accent changes, so ThemeProvider
        // never learns the palette changed while Lawnchair was in the background.
        // reseedSystemAccentIfChanged() only triggers a recreate if the color actually
        // differs, so this is a no-op on normal resumes.
        themeProvider.reseedSystemAccentIfChanged()

        // If an icon pack / themed icons switch happened while in Settings (or is still
        // binding on return home), show the loading overlay immediately without a fade-in
        // delay so old/half-bound icons never flash through. Dismiss only after workspace
        // binding completes (plus a brief settle + minimum visible duration) or safety timeout.
        if (iconPackSwitchPending) {
            showIconPackSwitchOverlay(immediate = true)
            scheduleIconPackOverlaySafetyTimeout()
            if (!iconPackReloadInProgress) {
                scheduleIconPackOverlayDismiss()
            }
        }

        dragLayer.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                private var handled = false
                override fun onDraw() {
                    if (handled) return
                    handled = true
                    dragLayer.post {
                        dragLayer.viewTreeObserver.removeOnDrawListener(this)
                        // Drop stuck All Apps RenderEffect on icons after returning home.
                        depthController.clearStuckBlurOnResumeIfHome()
                    }
                }
            },
        )
    }

    override fun finishBindingItems(pagesBoundFirst: IntSet?) {
        super.finishBindingItems(pagesBoundFirst)
        if (!iconPackReloadInProgress && !iconPackSwitchPending) return
        iconPackReloadInProgress = false
        // Only start the dismiss countdown if the launcher is currently visible with the
        // overlay up. If binding finished while the user is still in Settings, keep
        // iconPackSwitchPending = true so onResume() displays the brief loading animation
        // when returning home (Lawnchair 9 style).
        if (hasBeenResumed() && iconPackOverlay != null) {
            scheduleIconPackOverlayDismiss()
        }
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
        dragLayer.removeCallbacks(iconPackSafetyRunnable)
        iconPackOverlay?.let { overlay ->
            overlay.animate().cancel()
            dragLayer.removeView(overlay)
            iconPackOverlay = null
        }
    }

    override fun onStateSetEnd(state: LauncherState) {
        super.onStateSetEnd(state)
        refreshPredictionContainersFromModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        SmartspacerClient.close()
        // Belt-and-suspenders: cancel pending recreate and remove any overlay re-added
        // between onStop() and onDestroy(). Primary cleanup happens in onStop() above.
        dragLayer.removeCallbacks(recreateDebounceRunnable)
        dragLayer.removeCallbacks(iconPackIdleRunnable)
        dragLayer.removeCallbacks(iconPackSafetyRunnable)
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
    private var iconPackOverlayShownAt = 0L
    private val iconPackIdleRunnable = Runnable { dismissIconPackSwitchOverlay() }
    private val iconPackSafetyRunnable = Runnable {
        iconPackReloadInProgress = false
        dismissIconPackSwitchOverlay()
    }

    /**
     * Shows the icon pack loading overlay only when the launcher is actively resumed and
     * visible. Safe to invoke from any thread (dispatches to the main thread). When the
     * launcher is paused (e.g. user is in Settings), this is a no-op and [onResume] will
     * display the overlay on return home.
     */
    fun showIconPackSwitchOverlayIfVisible() {
        Executors.MAIN_EXECUTOR.execute {
            if (isDestroyed || !hasBeenResumed() || !dragLayer.isAttachedToWindow) return@execute
            showIconPackSwitchOverlay(immediate = false)
            scheduleIconPackOverlaySafetyTimeout()
        }
    }

    fun showIconPackSwitchOverlay(immediate: Boolean = false) {
        // Guard: dragLayer must be attached before we can add views.
        // If the launcher is paused/stopped, skip — onResume() will show it later.
        if (!dragLayer.isAttachedToWindow) return

        dragLayer.removeCallbacks(iconPackIdleRunnable)
        iconPackOverlayShownAt = SystemClock.uptimeMillis()

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

        val indicator = createContainedLoadingIndicator(this, containerSizePx = indicatorSize)

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.argb(160, 0, 0, 0))
            isClickable = true
            isFocusable = true
        }
        scrim.addView(indicator, FrameLayout.LayoutParams(indicatorSize, indicatorSize).apply {
            gravity = android.view.Gravity.CENTER
        })
        dragLayer.addView(scrim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        if (immediate) {
            scrim.alpha = 1f
        } else {
            scrim.alpha = 0f
            scrim.animate().alpha(1f).setDuration(150).start()
        }
        iconPackOverlay = scrim
    }

    private fun scheduleIconPackOverlaySafetyTimeout() {
        dragLayer.removeCallbacks(iconPackSafetyRunnable)
        dragLayer.postDelayed(iconPackSafetyRunnable, MAX_OVERLAY_TIMEOUT_MS)
    }

    private fun scheduleIconPackOverlayDismiss() {
        if (iconPackOverlay == null && !iconPackSwitchPending) return
        val elapsed = SystemClock.uptimeMillis() - iconPackOverlayShownAt
        val minRemaining = (MIN_OVERLAY_VISIBLE_MS - elapsed).coerceAtLeast(0L)
        val delay = maxOf(SETTLE_AFTER_BIND_MS, minRemaining)
        dragLayer.removeCallbacks(iconPackIdleRunnable)
        dragLayer.postDelayed(iconPackIdleRunnable, delay)
    }

    fun dismissIconPackSwitchOverlay() {
        dragLayer.removeCallbacks(iconPackIdleRunnable)
        dragLayer.removeCallbacks(iconPackSafetyRunnable)
        // Only clear the static companion flag if this activity is still alive.
        // If this is called from a stale runnable that fired after onDestroy()
        // (e.g. the activity was recreated by a theme change), clearing the flag
        // would prevent the new activity from seeing iconPackSwitchPending=true
        // in onResume() and skipping its overlay.
        if (!isDestroyed) {
            iconPackSwitchPending = false
            iconPackReloadInProgress = false
        }
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

    private fun refreshPredictionContainersFromModel() {
        LauncherAppState.getInstance(this).model.loadAsync { dataModel ->
            if (dataModel == null || isDestroyed) return@loadAsync

            val predictedContainers = synchronized(dataModel) {
                listOf(
                    dataModel.itemsIdMap[CONTAINER_ALL_APPS_PREDICTION] as? PredictedContainerInfo,
                    dataModel.itemsIdMap[CONTAINER_HOTSEAT_PREDICTION] as? PredictedContainerInfo,
                    dataModel.itemsIdMap[CONTAINER_WIDGETS_PREDICTION] as? PredictedContainerInfo,
                ).filterNotNull()
            }

            Executors.MAIN_EXECUTOR.execute {
                if (isDestroyed) return@execute
                predictedContainers.forEach(::bindPredictedContainerInfo)
            }
        }
    }

    /**
     * Reloads app icons if there is an active icon pack & [PreferenceManager2.alwaysReloadIcons] is enabled.
     */
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
        private const val MIN_OVERLAY_VISIBLE_MS = 450L
        private const val SETTLE_AFTER_BIND_MS = 200L
        private const val MAX_OVERLAY_TIMEOUT_MS = 4_000L

        var sRestartFlags = 0

        @Volatile var iconPackSwitchPending = false

        @Volatile var iconPackReloadInProgress = false

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
