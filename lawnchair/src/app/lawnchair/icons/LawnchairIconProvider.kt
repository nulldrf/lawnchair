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
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import android.util.ArrayMap
import android.util.Log
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.iconpack.IconPack
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.picker.IconType
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.util.MultiSafeCloseable
import app.lawnchair.util.isPackageInstalled
import app.lawnchair.util.requireSystemService
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.ClockDrawableWrapper
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SafeCloseable
import com.android.quickstep.RecentsModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.xmlpull.v1.XmlPullParser

@LauncherAppSingleton
class LawnchairIconProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    val themeManager: ThemeManager,
) : LauncherIconProvider(
    context,
    themeManager,
) {
    private val prefs = PreferenceManager2.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val themedIconsEnabled get() = prefs.themedIcons.firstCached()

    private val iconPackPref = prefs.iconPackPackage
    private val themedIconSourcePref = prefs.themedIconPackPackage

    private val iconPackProvider = IconPackProvider.INSTANCE.get(context)
    private val overrideRepo = IconOverrideRepository.INSTANCE.get(context)

    private val iconPack
        get() = iconPackProvider.getIconPack(iconPackPref.firstCached())?.apply { loadBlocking() }
    private val themedIconSource
        get() = iconPackProvider.getIconPack(themedIconSourcePref.firstCached())?.apply { loadBlocking() }

    private val themeMapLock = Any()
    private var themeMapName: String = ""
    private var themeMapEnabled: Boolean? = null
    private var _themeMap: Map<String, ThemeData>? = null

    val themeMap: Map<String, ThemeData>
        get() = synchronized(themeMapLock) {
            val sourceName = themedIconSource?.packPackageName.orEmpty()
            val enabled = themedIconsEnabled
            if (
                _themeMap == null ||
                themeMapName != sourceName ||
                themeMapEnabled != enabled
            ) {
                themeMapName = sourceName
                themeMapEnabled = enabled
                _themeMap = if (enabled) getThemedIconMap() else DISABLED_MAP
            }
            _themeMap!!
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
        val iconPack = iconPackPref.firstCached()
        if (iconPack.isNotEmpty()) {
            mSystemState += ",pack=$iconPack"
        }
        val themedPack = themedIconSourcePref.firstCached()
        if (themedPack.isNotEmpty()) {
            mSystemState += ",themedPack=$themedPack"
        }
        if (themedIconsEnabled) {
            mSystemState += ",themed=1"
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
        prefs.colorizeIconPackBackground.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                Executors.MODEL_EXECUTOR.execute {
                    updateSystemState()
                    val appState = LauncherAppState.getInstance(context)
                    appState.iconCache.clearMemoryCache()
                    LauncherIcons.clearPool(context)
                    appState.model.reloadIfActive()
                }
            }
            .launchIn(scope)
    }

    private fun resolveIconPackEntry(componentName: ComponentName): IconEntry? {
        val iconPack = iconPack ?: return null
        // Look for dynamic calendar first, then regular icon in the active custom icon pack.
        return iconPack.getCalendar(componentName) ?: iconPack.getIcon(componentName)
    }

    /**
     * Resolves launch component and icon entry.
     * This function tries to resolve the real component so that it works for non-default
     * users (like work / private profiles)
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

        // 1. Per-app manual icon overrides always win (never replaced by system/Lawnicons monochrome).
        if (componentName != null) {
            val overrideItem = overrideRepo.overridesMap[ComponentKey(componentName, user)]
            if (overrideItem != null) {
                var overrideEntry = overrideItem.toIconEntry()
                if (overrideEntry.type == IconType.Calendar) {
                    overrideEntry = overrideEntry.resolveDynamicCalendar(getDay())
                }
                iconPackProvider.getDrawable(overrideEntry, iconDpi, user)?.let { return it }
            }
        }

        // 2. Colored base icon: active custom icon pack first, then system app icon.
        //    This always stays intact for unthemed surfaces (e.g. App Drawer when Home-only)
        //    and serves as the fallback on themed surfaces when no monochrome icon exists.
        val iconPackEntry = componentName?.let { resolveIconPackEntry(it) }
        val resolvedPackEntry = if (iconPackEntry?.type == IconType.Calendar) {
            iconPackEntry.resolveDynamicCalendar(getDay())
        } else {
            iconPackEntry
        }
        val iconPackIcon = resolvedPackEntry?.let { iconPackProvider.getDrawable(it, iconDpi, user) }
        val baseIcon = iconPackIcon ?: super.getIcon(info, appInfo, iconDpi)

        if (!themedIconsEnabled) {
            return baseIcon
        }

        // 3. Themed layer (kept separate from the colored base icon so Home-only and Home+Drawer
        //    both work cleanly and predictably):
        //    - If baseIcon already has a monochrome layer, keep it.
        //    - If forceIconMonochrome is ON, leave monochrome null so BaseIconFactory +
        //      FORCED_MONO_THEME_CONTROLLER generate monochrome directly without overwriting baseIcon.
        //    - Otherwise attach resolved monochrome (native <monochrome> or Fallback themed pack /
        //      Lawnicons) to the monochrome layer only.
        if (baseIcon is AdaptiveIconDrawable) {
            val existingMono = when {
                baseIcon is CustomAdaptiveIconDrawable -> baseIcon.monochrome
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> baseIcon.monochrome
                else -> null
            }
            if (existingMono != null || prefs.forceIconMonochrome.firstCached()) {
                return baseIcon
            }
            val mono = resolveMonochromeDrawable(packageName, componentName, resolvedPackEntry, iconDpi)
            return if (mono != null) {
                CustomAdaptiveIconDrawable(baseIcon.background, baseIcon.foreground, mono)
            } else {
                baseIcon
            }
        }

        if (prefs.forceIconMonochrome.firstCached()) {
            return baseIcon
        }

        val mono = resolveMonochromeDrawable(packageName, componentName, resolvedPackEntry, iconDpi)
            ?: return baseIcon
        return if (baseIcon is ExtendedBitmapDrawable) {
            baseIcon.monochromeDrawable = mono
            baseIcon
        } else {
            MonochromeDrawableWrapper(baseIcon, mono)
        }
    }

    private fun resolveMonochromeDrawable(
        packageName: String,
        componentName: ComponentName?,
        iconEntry: IconEntry?,
        iconDpi: Int,
    ): Drawable? {
        val clock = iconEntry?.let { iconPackProvider.getClockMetadata(it) }

        return when {
            clock != null && mClock != null -> {
                ClockDrawableWrapper.forPackage(mContext, mClock.packageName, iconDpi)?.let { wrapper ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wrapper.monochrome else null
                } ?: ThemeData(context.resources, R.drawable.themed_icon_static_clock).loadPaddedDrawable()
            }

            mClock != null && packageName == mClock.packageName -> {
                ThemeData(context.resources, R.drawable.themed_icon_static_clock).loadPaddedDrawable()
            }

            mCalendar != null && packageName == mCalendar.packageName -> {
                val themeData = getThemeDataForPackage(packageName) ?: return null
                val calDrawable = loadCalendarDrawable(iconDpi, themeData)
                when {
                    calDrawable is CustomAdaptiveIconDrawable -> calDrawable.monochrome

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && calDrawable is AdaptiveIconDrawable ->
                        calDrawable.monochrome

                    else -> themeData.loadPaddedDrawable()
                }
            }

            else -> {
                componentName?.let { ThemedIconCompat.getThemedIcon(context, it) }
                    ?: getThemeDataForPackage(packageName)?.loadPaddedDrawable()
            }
        }
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
            "ip=${iconPackPref.firstCached()}," +
            "tip=${themedIconSourcePref.firstCached()}," +
            "ti=${prefs.themedIcons.firstCached()}," +
            "dti=${prefs.drawerThemedIcons.firstCached()}," +
            "fm=${prefs.forceIconMonochrome.firstCached()}," +
            "ov=$overrideState"
    }

    override fun getThemeDataForPackage(packageName: String?): ThemeData? {
        if (!themedIconsEnabled || prefs.forceIconMonochrome.firstCached()) {
            return null
        }
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
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val prefs2 = PreferenceManager2.getInstance(context)
        private val iconPackPref = prefs2.iconPackPackage
        private val themedIconPackPref = prefs2.themedIconPackPackage

        private fun updateIconStateIfNeeded() {
            val newState = themeManager.iconState
            if (iconState != newState) iconState = newState

            // Mark switch pending + in-progress. If the launcher is currently visible, show the
            // loading overlay immediately; if paused in Settings, LawnchairLauncher.onResume()
            // will display it on return home and dismiss only once finishBindingItems() completes.
            LawnchairLauncher.iconPackSwitchPending = true
            LawnchairLauncher.iconPackReloadInProgress = true
            LawnchairLauncher.instance?.showIconPackSwitchOverlayIfVisible()
            RecentsModel.INSTANCE.get(context).onThemeChanged()

            Executors.MODEL_EXECUTOR.execute {
                updateSystemState()
                val appState = LauncherAppState.getInstance(context)
                val idp = InvariantDeviceProfile.INSTANCE.get(context)
                ThemedIconCompat.clearResourcesCache()
                LauncherIcons.clearPool(context)
                appState.iconCache.clearMemoryCache()
                appState.iconCache.updateIconParams(idp.fillResIconDpi, idp.iconBitmapSize)
                appState.model.forceReload()
            }
        }

        init {
            recreateCalendarAndClockChangeReceiver()
            iconPackPref.get()
                .distinctUntilChanged()
                .drop(1)
                .onEach {
                    recreateCalendarAndClockChangeReceiver()
                    updateIconStateIfNeeded()
                }
                .launchIn(receiverScope)
            themedIconPackPref.get()
                .distinctUntilChanged()
                .drop(1)
                .onEach {
                    updateIconStateIfNeeded()
                }
                .launchIn(receiverScope)
        }

        private fun recreateCalendarAndClockChangeReceiver() {
            val iconPack = IconPackProvider.INSTANCE.get(context).getIconPack(iconPackPref.firstCached())
            calendarAndClockChangeReceiver = if (iconPack != null) {
                CalendarAndClockChangeReceiver(context, handler, iconPack, callback)
            } else {
                null
            }
        }

        override fun close() {
            calendarAndClockChangeReceiver = null
            receiverScope.cancel()
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
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart != themedIconSourcePref.firstCached()) return
            synchronized(themeMapLock) {
                _themeMap = null
            }
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
