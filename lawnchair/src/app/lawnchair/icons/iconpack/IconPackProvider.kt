package app.lawnchair.icons.iconpack

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import app.lawnchair.icons.ClockMetadata
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.shouldTransparentBGIcons
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.ClockDrawableWrapper
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject

@LauncherAppSingleton
class IconPackProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    /**
     * LRU cache of loaded [IconPack] instances, bounded to [MAX_CACHED_ICON_PACKS].
     *
     * Previously an unbounded [mutableMapOf], this map accumulated one [CustomIconPack]
     * per icon pack the user ever loaded — and each [CustomIconPack] holds [packResources]
     * ([android.content.res.Resources]), which memory-maps the pack's APK into the process.
     * A comprehensive icon pack APK can be 30–80 MB. A user who browses and tries five packs
     * therefore has five APKs simultaneously mapped, accounting for 150–400 MB of .apk mmap
     * that never releases until the process dies — the primary driver of hard-to-reproduce
     * 500 MB+ memory spikes.
     *
     * With [LinkedHashMap] in access-order mode, the two most recently accessed packs are
     * always retained. Two slots cover all normal usage:
     *  - Slot 1: the currently selected icon pack ([iconPackPref])
     *  - Slot 2: the themed icon source ([themedIconSourcePref]), which can differ from slot 1
     *
     * When a third pack is accessed (e.g. the user browses the picker), the least-recently-
     * used entry is evicted. The evicted [CustomIconPack] loses its only strong reference
     * from this map; once GC runs, [packResources] is released and the APK pages are
     * unmapped by the OS.
     *
     * A [null] value indicates a pack that failed to load ([PackageManager.NameNotFoundException]);
     * it is cached to prevent repeated failed construction attempts and is subject to the same
     * LRU eviction as a successful entry.
     */
    private val iconPacks: MutableMap<String, IconPack?> = object : LinkedHashMap<String, IconPack?>(
        MAX_CACHED_ICON_PACKS + 1,
        0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, IconPack?>,
        ): Boolean = size > MAX_CACHED_ICON_PACKS
    }

    fun getIconPackOrSystem(packageName: String): IconPack? {
        if (packageName.isEmpty()) return SystemIconPack(context, packageName)
        return getIconPack(packageName)
    }

    fun getIconPack(packageName: String): IconPack? {
        if (packageName.isEmpty()) {
            return null
        }
        return iconPacks.getOrPut(packageName) {
            try {
                CustomIconPack(context, packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    fun getClockMetadata(iconEntry: IconEntry): ClockMetadata? {
        val iconPack = getIconPackOrSystem(iconEntry.packPackageName) ?: return null
        return iconPack.getClock(iconEntry)
    }

    fun getDrawable(iconEntry: IconEntry, iconDpi: Int, user: UserHandle): Drawable? {
        val iconPack = getIconPackOrSystem(iconEntry.packPackageName) ?: return null
        iconPack.loadBlocking()
        val drawable = iconPack.getIcon(iconEntry, iconDpi) ?: return null
        val clockMetadata =
            if (user == Process.myUserHandle()) iconPack.getClock(iconEntry) else null
        try {
            if (clockMetadata != null) {
                val clockDrawable: ClockDrawableWrapper =
                    ClockDrawableWrapper.forMeta(Build.VERSION.SDK_INT, clockMetadata) {
                        drawable
                    }

                return if (context.shouldTransparentBGIcons()) {
                    clockDrawable.foreground
                } else {
                    CustomAdaptiveIconDrawable(
                        clockDrawable.background,
                        clockDrawable.foreground,
                    )
                }
            }
        } catch (t: Throwable) {
            // Ignore
        }

        return drawable
    }

    /**
     * Clears all cached [IconPack] instances, releasing their [packResources] references
     * and making the underlying APK memory mappings eligible for GC and OS reclamation.
     *
     * Called by the Dagger component on launcher shutdown. Previously a [TODO] stub,
     * meaning APK pages were never explicitly released at shutdown.
     */
    override fun close() {
        iconPacks.clear()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconPackProvider)

        /**
         * Maximum number of [IconPack] instances kept in the LRU cache.
         *
         * Two slots cover the two packs that [LawnchairIconProvider] accesses simultaneously:
         * the active icon pack ([iconPackPref]) and the themed icon source
         * ([themedIconSourcePref]). Packs beyond this count are evicted and their APK
         * mappings freed, preventing the 150–400 MB .apk mmap accumulation seen when a
         * user browses multiple icon packs in a single session.
         */
        private const val MAX_CACHED_ICON_PACKS = 2
    }
}
