package app.lawnchair.icons

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_PACKAGE_ADDED
import android.content.Intent.ACTION_PACKAGE_CHANGED
import android.content.Intent.ACTION_PACKAGE_REMOVED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.content.Intent.ACTION_TIME_TICK
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageItemInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import android.util.ArrayMap
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toDrawable
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.iconpack.IconPack
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.picker.IconType
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.MultiSafeCloseable
import app.lawnchair.util.isPackageInstalled
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.ClockDrawableWrapper
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.icons.mono.ThemedIconDrawable
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser

@LauncherAppSingleton
class LawnchairIconProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    val themeManager: ThemeManager,
) : LauncherIconProvider(
    context,
    themeManager,
) {
    private val prefs = PreferenceManager.getInstance(context)
    private val themedIconsEnabled get() = prefs.themedIcons.get()

    private val iconPackPref = prefs.iconPackPackage
    private val themedIconSourcePref = prefs.themedIconPackPackage

    private val iconPackProvider = IconPackProvider.INSTANCE.get(context)
    private val overrideRepo = IconOverrideRepository.INSTANCE.get(context)

    private val iconPack
        get() = iconPackProvider.getIconPack(iconPackPref.get())?.apply { loadBlocking() }
    private val themedIconSource
        get() = iconPackProvider.getIconPack(themedIconSourcePref.get())?.apply { loadBlocking() }

    private var themeMapName: String = ""
    private var _themeMap: Map<String, ThemeData>? = null

    val themeMap: Map<String, ThemeData>
        get() {
            // Guard first — return without mutating _themeMap or themeMapName so that
            // re-enabling themed icons later causes a clean rebuild rather than serving
            // whatever stale state was left behind.
            //
            // Previously this set _themeMap = DISABLED_MAP and fell through, which meant
            // the three subsequent if-blocks could still fire and overwrite it — so
            // DISABLED_MAP was silently discarded whenever a themedIconSource was active,
            // effectively ignoring the themedIconsEnabled=false setting.
            if (!themedIconsEnabled) return DISABLED_MAP

            // Evaluate themedIconSource exactly once per getter invocation.
            //
            // The original code accessed themedIconSource up to four times in a single
            // call (two null-guard checks + two packPackageName reads), each of which
            // resolves the icon pack and calls loadBlocking() on it.
            val source = themedIconSource
            val sourceName = source?.packPackageName ?: ""

            // Rebuild the map only when it is uninitialized or the themed icon source
            // package has changed (including when it is removed, i.e. sourceName → "").
            //
            // The original code used four independent if-blocks that could all fire in
            // the same getter call:
            //
            //   Block 1 (_themeMap == null): calls getThemedIconMap() with themeMapName=""
            //            → builds Lawnchair-only map.  Result immediately discarded ↓
            //   Block 2 (themeMapName==""): calls super.getThemedIconMap()
            //            → overwrites block 1 result.  Result immediately discarded ↓
            //   Block 3 (themeMapName != sourceName): updates themeMapName, calls
            //            getThemedIconMap() again with the correct name
            //            → overwrites block 2 result.  This is the only one that matters.
            //
            // Block 2's result was always discarded because block 3 always fired
            // immediately after (themeMapName was still "" when block 3 evaluated it).
            // Blocks 1 and 2 were therefore pure waste on every first access.
            if (_themeMap == null || themeMapName != sourceName) {
                themeMapName = sourceName
                _themeMap = getThemedIconMap()
            }

            return _themeMap!!
        }

    val systemIconState = themeManager.iconState

    /**
     * Overrides mSystemState to include the current icon pack package name.
     *
     * The disk cache key per app is: mSystemState + sourceDir (app APK path).
     * Upstream updateSystemState() builds mSystemState from locale + SDK + theme state.
     * None of those change when the user switches icon packs, so the disk cache key is
     * identical before and after the switch — cached bitmaps are served unchanged.
     *
     * By appending the icon pack package name, the disk cache key changes whenever the
     * icon pack changes, making all existing entries stale and forcing regeneration.
     * This is the same approach used for colorize prefs (via LawnchairThemeManager's
     * colorizeSuffix in toUniqueId()) but applied specifically to icon pack changes.
     */
    override fun updateSystemState() {
        super.updateSystemState()
        val iconPack = iconPackPref.get()
        if (iconPack.isNotEmpty()) {
            mSystemState += ",pack=$iconPack"
        }
    }

    // -----------------------------------------------------------------------
    // Icon cache flush on adaptive icon pref changes.
    //
    // When "Smart icon backgrounds" or "Recolor white adaptive backgrounds"
    // change, LawnchairThemeManager.verifyIconState() detects the state change
    // (because the colorize suffix is baked into the iconMask key) and fires
    // onThemeChanged(). We hook into that event here to:
    //   1. Call updateSystemState() so mSystemState changes → disk cache stale.
    //   2. Clear the memory bitmap cache.
    //   3. Clear the LauncherIcons factory pool so the next factory obtained
    //      reads the new pref values instead of using cached factory state.
    //
    // The launcher's own onThemeChanged() downstream handler then refreshes all
    // visible icon views, which re-load from memory cache (now empty), fall
    // through to disk cache (now stale), and regenerate bitmaps with the new
    // background logic. This is the same fast path used by shape changes.
    //
    // We use model.reloadIfActive() for icon pack changes (see IconPackChangeReceiver)
    // because it refreshes all surfaces including the app drawer and icon pack picker,
    // even when the launcher activity is in the background. This works correctly now
    // because updateSystemState() includes the icon pack name, making the disk cache
    // stale so reloadIfActive() regenerates icons rather than serving old cached bitmaps.
    // -----------------------------------------------------------------------

    init {
        // Clear caches on any theme change (shape, themed icons, colorize prefs).
        // updateSystemState() makes disk cache stale; clearMemoryCache() + clearPool()
        // force new bitmaps to be generated with current settings.
        // Recreate is NOT triggered here — shape changes handle their own recreate via
        // LawnchairThemeManager, and colorize changes trigger recreate directly from
        // LawnchairThemeManager.colorizePrefsListener to avoid double-recreates.
        themeManager.addChangeListener {
            Executors.MODEL_EXECUTOR.execute {
                updateSystemState()
                val appState = LauncherAppState.getInstance(context)
                appState.iconCache.clearMemoryCache()
                LauncherIcons.clearPool(context)
            }
        }

        // Flush caches when "Apply smart backgrounds to icon pack" changes.
        // Not routed through ThemeManager since it doesn't affect shape/theme state.
        context.prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "pref_colorizeIconPackBackground") {
                Executors.MODEL_EXECUTOR.execute {
                    updateSystemState()
                    val appState = LauncherAppState.getInstance(context)
                    appState.iconCache.clearMemoryCache()
                    LauncherIcons.clearPool(context)
                    appState.model.reloadIfActive()
                }
            }
        }
    }

    private fun resolveIconEntry(componentName: ComponentName, user: UserHandle): IconEntry? {
        val componentKey = ComponentKey(componentName, user)
        // first look for user-overridden icon
        val overrideItem = overrideRepo.overridesMap[componentKey]
        if (overrideItem != null) {
            return overrideItem.toIconEntry()
        }

        val iconPack = iconPack ?: return null
        // then look for dynamic calendar
        val calendarEntry = iconPack.getCalendar(componentName)
        if (calendarEntry != null) {
            return calendarEntry
        }
        // finally, look for normal icon
        return iconPack.getIcon(componentName)
    }

    /**
     * Resolves the launch component for icon-pack lookup.
     *
     * Avoid [android.content.pm.PackageManager.getLaunchIntentForPackage], which only sees the
     * current user — work/private profile apps would otherwise fall back to the system icon.
     * Prefer the real component from [ComponentInfo], then [LauncherApps] for the app's user.
     */
    private fun resolveComponentName(
        info: PackageItemInfo,
        appInfo: ApplicationInfo,
        user: UserHandle,
    ): ComponentName? {
        if (info is ComponentInfo && !info.name.isNullOrEmpty()) {
            return ComponentName(info.packageName ?: appInfo.packageName, info.name)
        }
        return resolveLaunchComponent(appInfo.packageName, user)
    }

    private fun resolveLaunchComponent(packageName: String, user: UserHandle): ComponentName? {
        return try {
            val launcherApps: LauncherApps = context.requireSystemService()
            launcherApps.getActivityList(packageName, user).firstOrNull()?.componentName
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve launch component for $packageName user=$user", t)
            null
        }
    }

    override fun getIcon(
        info: PackageItemInfo,
        appInfo: ApplicationInfo,
        iconDpi: Int,
    ): Drawable {
        val packageName = appInfo.packageName
        val user = UserHandle.getUserHandleForUid(appInfo.uid)
        val componentName = resolveComponentName(info, appInfo, user)

        var iconEntry: IconEntry? = null
        if (componentName != null) {
            iconEntry = resolveIconEntry(componentName, user)
        }

        var iconPackEntry = iconEntry

        val themeData = getThemeDataForPackage(packageName)

        var themedIcon: Drawable? = null

        val themedColors = ThemedIconDrawable.getColors(context)

        if (iconEntry != null) {
            val clock = iconPackProvider.getClockMetadata(iconEntry)

            if (iconEntry.type == IconType.Calendar) {
                iconPackEntry = iconEntry.resolveDynamicCalendar(getDay())
            }

            when {
                !themedIconsEnabled -> {
                    // theming is disabled, don't populate theme data
                    themedIcon = null
                }

                clock != null -> {
                    // the icon supports dynamic clock, use dynamic themed clock
                    themedIcon =
                        ClockDrawableWrapper.forPackage(mContext, mClock.packageName, iconDpi)
                            .getMonochrome()
                }

                packageName == mClock.packageName -> {
                    // is clock app but icon might not be adaptive, fallback to static themed clock
                    val clockThemedData =
                        ThemeData(context.resources, R.drawable.themed_icon_static_clock)
                    themedIcon = CustomAdaptiveIconDrawable(
                        themedColors[0].toDrawable(),
                        clockThemedData.loadPaddedDrawable().apply { setTint(themedColors[1]) },
                    )
                }

                packageName == mCalendar.packageName -> {
                    // calendar app, apply the dynamic calendar icon
                    themedIcon = loadCalendarDrawable(iconDpi, themeData)
                }

                else -> {
                    // regular icon
                    themedIcon = if (themeData != null) {
                        CustomAdaptiveIconDrawable(
                            themedColors[0].toDrawable(),
                            themeData.loadPaddedDrawable().apply { setTint(themedColors[1]) },
                        )
                    } else {
                        null
                    }
                }
            }
        }

        val iconPackIcon = iconPackEntry?.let { iconPackProvider.getDrawable(it, iconDpi, user) }

        return themedIcon ?: iconPackIcon ?: super.getIcon(info, appInfo, iconDpi)
    }

    override fun getStateForApp(info: ApplicationInfo?): String {
        val base = super.getStateForApp(info)
        val overrideState = if (info != null) {
            val user = UserHandle.getUserHandleForUid(info.uid)
            overrideRepo.getPackageOverrideState(info.packageName, user)
        } else {
            ""
        }
        return "$base|lc:" +
            "ip=${iconPackPref.get()}," +
            "tip=${themedIconSourcePref.get()}," +
            "ti=${prefs.themedIcons.get()}," +
            "dti=${prefs.drawerThemedIcons.get()}," +
            "fm=${prefs.forceIconMonochrome.get()}," +
            "tb=${prefs.tintIconPackBackgrounds.get()}," +
            "ov=$overrideState"
    }

    override fun getThemeDataForPackage(packageName: String?): ThemeData? {
        return themeMap[packageName]
    }

    override fun getThemedIconMap(): MutableMap<String, ThemeData> {
        val themedIconMap = ArrayMap<String, ThemeData>()

        fun ArrayMap<String, ThemeData>.updateFromResources(
            resources: Resources,
            packageName: String,
        ) {
            try {
                @SuppressLint("DiscouragedApi")
                val xmlId = resources.getIdentifier("grayscale_icon_map", "xml", packageName)
                if (xmlId != 0) {
                    val parser = resources.getXml(xmlId)
                    val depth = parser.depth
                    var type: Int
                    while (
                        (
                            parser.next()
                                .also { type = it } != XmlPullParser.END_TAG || parser.depth > depth
                            ) &&
                        type != XmlPullParser.END_DOCUMENT
                    ) {
                        if (type != XmlPullParser.START_TAG) continue
                        if (TAG_ICON == parser.name) {
                            val pkg = parser.getAttributeValue(null, ATTR_PACKAGE)
                            val iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0)
                            if (iconId != 0 && pkg.isNotEmpty()) {
                                this[pkg] = ThemeData(resources, iconId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to parse icon map.", e)
            }
        }

        // first, get Lawnchair's internal grayscale icon map
        themedIconMap.updateFromResources(
            resources = context.resources,
            packageName = context.packageName,
        )

        if (context.packageManager.isPackageInstalled(packageName = themeMapName)) {
            // get the grayscale icon map of the supported icon pack
            themedIconMap.updateFromResources(
                resources = context.packageManager.getResourcesForApplication(themeMapName),
                packageName = themeMapName,
            )
        }

        return themedIconMap
    }

    override fun registerIconChangeListener(
        callback: IconChangeListener,
        handler: Handler,
    ): SafeCloseable {
        return MultiSafeCloseable().apply {
            add(super.registerIconChangeListener(callback, handler))
            add(IconPackChangeReceiver(context, handler, callback))
            add(LawniconsChangeReceiver(context, handler))
        }
    }

    private inner class IconPackChangeReceiver(
        private val context: Context,
        private val handler: Handler,
        private val callback: IconChangeListener,
    ) : SafeCloseable {

        private var calendarAndClockChangeReceiver: CalendarAndClockChangeReceiver? = null
            set(value) {
                field?.close()
                field = value
            }
        private var iconState = themeManager.iconState
        private val iconPackPref = PreferenceManager.getInstance(context).iconPackPackage
        private val themedIconPackPref = PreferenceManager.getInstance(context).themedIconPackPackage

        private val subscription = iconPackPref.subscribeChanges {
            val newState = themeManager.iconState
            if (iconState != newState) iconState = newState
            recreateCalendarAndClockChangeReceiver()

            // Show overlay — dismissed when icon updates stop arriving (idle for 500ms).
            // Do NOT call recreateIfNotScheduled() — it creates a new launcher that takes
            // 3s to start independently of reloadIfActive(), causing the overlay to dismiss
            // while the new instance is still loading. reloadIfActive() updates icons in-place.
            LawnchairLauncher.iconPackSwitchPending = true
            LawnchairLauncher.instance?.showIconPackSwitchOverlay()

            Executors.MODEL_EXECUTOR.execute {
                updateSystemState()
                val appState = LauncherAppState.getInstance(context)
                appState.iconCache.clearMemoryCache()
                // Release all cached per-app Resources objects before the reload begins.
                // Without this, the old pack's Resources stay alive while the new pack's
                // Resources are loaded, doubling the .apk mmap footprint during the
                // transition window (visible as a spike in Assets count and .apk mmap).
                ThemedIconCompat.clearResourcesCache()
                LauncherIcons.clearPool(context)
                appState.model.reloadIfActive()
                // Signal idle timer to start. If the launcher is active (foreground),
                // this starts the 600ms countdown. If it's paused (user in Settings),
                // the timer call is a no-op — onResume() will start it when the user
                // returns, guaranteeing the overlay stays until icons are fully updated.
                Executors.MAIN_EXECUTOR.execute {
                    LawnchairLauncher.instance?.startIconPackSwitchIdleTimer()
                        ?: run {
                            // Launcher is paused — leave iconPackSwitchPending=true so
                            // onResume() shows the overlay and starts the timer fresh.
                        }
                }
            }
        }
        private val themedIconSubscription = themedIconPackPref.subscribeChanges {
            val newState = themeManager.iconState
            if (iconState != newState) iconState = newState
            recreateCalendarAndClockChangeReceiver()
            LawnchairLauncher.iconPackSwitchPending = true
            LawnchairLauncher.instance?.showIconPackSwitchOverlay()
            Executors.MODEL_EXECUTOR.execute {
                updateSystemState()
                val appState = LauncherAppState.getInstance(context)
                appState.iconCache.clearMemoryCache()
                ThemedIconCompat.clearResourcesCache()
                LauncherIcons.clearPool(context)
                appState.model.reloadIfActive()
                Executors.MAIN_EXECUTOR.execute {
                    LawnchairLauncher.instance?.startIconPackSwitchIdleTimer()
                }
            }
        }

        init {
            recreateCalendarAndClockChangeReceiver()
        }

        private fun recreateCalendarAndClockChangeReceiver() {
            val iconPack = IconPackProvider.INSTANCE.get(context).getIconPack(iconPackPref.get())
            calendarAndClockChangeReceiver = if (iconPack != null) {
                CalendarAndClockChangeReceiver(context, handler, iconPack, callback)
            } else {
                null
            }
        }

        override fun close() {
            calendarAndClockChangeReceiver = null
            subscription.close()
            themedIconSubscription.close()
        }
    }

    private class CalendarAndClockChangeReceiver(
        private val context: Context,
        handler: Handler,
        private val iconPack: IconPack,
        private val callback: IconChangeListener,
    ) : BroadcastReceiver(),
        SafeCloseable {

        init {
            val filter = IntentFilter(ACTION_TIMEZONE_CHANGED)
            filter.addAction(ACTION_TIME_TICK)
            filter.addAction(ACTION_TIME_CHANGED)
            filter.addAction(ACTION_DATE_CHANGED)
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TIMEZONE_CHANGED, ACTION_TIME_CHANGED, ACTION_TIME_TICK -> {
                    context.getSystemService<UserManager>()?.userProfiles?.forEach { user ->
                        iconPack.getClocks().forEach { componentName ->
                            callback.onAppIconChanged(
                                componentName.packageName,
                                user,
                            )
                        }
                    }
                }

                ACTION_DATE_CHANGED -> {
                    context.getSystemService<UserManager>()?.userProfiles?.forEach { user ->
                        iconPack.getCalendars().forEach { componentName ->
                            callback.onAppIconChanged(componentName.packageName, user)
                        }
                    }
                }
            }
        }

        override fun close() {
            context.unregisterReceiver(this)
        }
    }

    private inner class LawniconsChangeReceiver(
        private val context: Context,
        handler: Handler,
    ) : BroadcastReceiver(),
        SafeCloseable {

        init {
            val filter = IntentFilter(ACTION_PACKAGE_ADDED)
            filter.addAction(ACTION_PACKAGE_CHANGED)
            filter.addAction(ACTION_PACKAGE_REMOVED)
            filter.addDataScheme("package")
            filter.addDataSchemeSpecificPart(themeMapName, 0)
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            updateSystemState()
        }

        override fun close() {
            context.unregisterReceiver(this)
        }
    }

    companion object {
        const val TAG = "LawnchairIconProvider"
    }
}
