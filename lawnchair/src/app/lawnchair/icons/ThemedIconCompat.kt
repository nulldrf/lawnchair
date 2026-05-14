package app.lawnchair.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

object ThemedIconCompat {
    const val TAG = "ThemedIconCompat"

    /**
     * Bounded cache of [Resources] objects keyed by package name.
     *
     * [PackageManager.getResourcesForApplication] memory-maps the target APK into the
     * process address space. Previously this was called on every [getThemedIcon] invocation
     * with no caching, so loading the home screen or opening All Apps would fire it for
     * every single installed app, creating one APK mmap entry per app per load — none of
     * which were explicitly released.
     *
     * The cache is sized at 100 entries. A [Resources] object itself is lightweight (the
     * heavy part is the underlying [android.content.res.AssetManager] mmap, which is shared
     * by the OS when the same APK is already mapped). Capping at 100 covers typical home
     * screen + first page of All Apps without allowing unbounded growth on devices with
     * very large app counts.
     */
    private val resourcesCache = LruCache<String, Resources>(100)

    /**
     * Evicts all cached [Resources] entries, releasing references to the underlying
     * [android.content.res.AssetManager] objects and making the APK mmaps they hold
     * eligible for GC and OS reclamation.
     *
     * Call this:
     * - During an icon pack change (before reloading icons), so old-pack Resources
     *   don't co-exist with new-pack Resources and double the .apk mmap footprint.
     * - In [android.app.Application.onTrimMemory] at TRIM_MEMORY_MODERATE or higher,
     *   so the OS can reclaim the mapped APK pages under memory pressure.
     */
    fun clearResourcesCache() {
        resourcesCache.evictAll()
    }

    fun getThemedIcon(
        context: Context,
        componentName: ComponentName,
    ): Drawable? {
        val activityInfo = resolveActivityInfo(context, componentName) ?: return null
        return getMonochromeIconResource(context, activityInfo)
    }

    private fun resolveActivityInfo(context: Context, componentName: ComponentName): ActivityInfo? {
        return try {
            context.packageManager.getActivityInfo(componentName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Returns a [Resources] instance for [packageName], pulling from [resourcesCache] if
     * available or loading and caching it on first access.
     *
     * Returns null if the package is not found (e.g. it was uninstalled between the time
     * [resolveActivityInfo] resolved it and now). A failed lookup is not stored in the
     * cache so the next call will retry — which allows recovery without restarting.
     */
    private fun getOrCacheResources(pm: PackageManager, packageName: String): Resources? {
        resourcesCache.get(packageName)?.let { return it }
        return try {
            pm.getResourcesForApplication(packageName).also { resourcesCache.put(packageName, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found when loading resources: $packageName", e)
            null
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getMonochromeIconResource(context: Context, activityInfo: ActivityInfo): Drawable? {
        val iconResource = activityInfo.applicationInfo.icon

        val resources = getOrCacheResources(context.packageManager, activityInfo.packageName)
            ?: return null

        var xmlParser: XmlResourceParser? = null
        try {
            xmlParser = resources.getXml(iconResource)
            if (!xmlParser.skipToNextTag()) return null

            if (xmlParser.name != "adaptive-icon") {
                return null
            }

            while (xmlParser.skipToNextTag()) {
                if (xmlParser.name == "monochrome") {
                    val drawable = xmlParser.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android",
                        "drawable",
                        0,
                    )
                    if (drawable == 0) return null

                    return resources.getDrawable(drawable, null)
                }
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, e.toString())
            return null
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            return null
        } catch (e: XmlPullParserException) {
            Log.e(TAG, e.toString())
            return null
        } finally {
            xmlParser?.close()
        }

        return null
    }
}

fun XmlPullParser.skipToNextTag(): Boolean {
    while (next() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) return true
    }
    return false
}
