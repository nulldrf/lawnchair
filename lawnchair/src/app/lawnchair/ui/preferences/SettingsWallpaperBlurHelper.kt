package app.lawnchair.ui.preferences

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import app.lawnchair.allapps.DrawerWallpaperBlurHelper

/**
 * Computes and caches a blurred wallpaper bitmap for settings screens,
 * keyed by wallpaper ID, blur intensity, viewport dimensions, and display rotation.
 *
 * Uses the same orientation-aware cropping as [DrawerWallpaperBlurHelper] so every
 * orientation change takes a fresh crop from the system wallpaper rather than reusing
 * the previous orientation's bitmap.
 */
object SettingsWallpaperBlurHelper {

    private const val TAG = "SettingsWallpaperBlur"
    private const val CENTER_OFFSET = 0.5f

    @Volatile private var cachedBitmap: Bitmap? = null

    @Volatile private var cachedIntensity: Int = -1

    @Volatile private var cachedWidth: Int = -1

    @Volatile private var cachedHeight: Int = -1

    @Volatile private var cachedRotation: Int = -1

    @Volatile private var cachedWallpaperId: Int = -1

    @JvmStatic
    fun getCachedBitmap(blurEnabled: Boolean, blurIntensity: Int, width: Int, height: Int): Bitmap? {
        return getCachedBitmap(
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity,
            width = width,
            height = height,
            displayRotation = cachedRotation,
        )
    }

    @JvmStatic
    fun getCachedBitmap(
        blurEnabled: Boolean,
        blurIntensity: Int,
        width: Int,
        height: Int,
        displayRotation: Int,
    ): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        return if (
            blurIntensity == cachedIntensity &&
            width == cachedWidth &&
            height == cachedHeight &&
            displayRotation == cachedRotation &&
            bmp != null &&
            !bmp.isRecycled
        ) {
            bmp
        } else {
            null
        }
    }

    /**
     * Convenience overload using current orientation-aligned screen bounds.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
        val bounds = screenBoundsForOrientation(context, context.resources.configuration.orientation)
        return getBlurredBitmap(
            context = context,
            blurIntensity = blurIntensity,
            targetWidth = bounds.width(),
            targetHeight = bounds.height(),
            displayRotation = displayRotation(context),
            forceReload = false,
        )
    }

    /**
     * Returns a blurred copy of the visible center-cropped wallpaper for the given
     * viewport ([targetWidth] × [targetHeight]) and [displayRotation], or null for
     * live wallpapers or failures. Call from a background thread.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(
        context: Context,
        blurIntensity: Int,
        targetWidth: Int,
        targetHeight: Int,
        displayRotation: Int,
        forceReload: Boolean = false,
    ): Bitmap? {
        val fallbackBounds = if (targetWidth <= 0 || targetHeight <= 0) {
            screenBoundsForOrientation(context, context.resources.configuration.orientation)
        } else {
            null
        }
        val w = if (targetWidth > 0) targetWidth else (fallbackBounds?.width() ?: 0)
        val h = if (targetHeight > 0) targetHeight else (fallbackBounds?.height() ?: 0)
        if (w <= 0 || h <= 0) {
            Log.e(TAG, "Invalid blur viewport ${w}x$h — aborting")
            return null
        }

        val wm = WallpaperManager.getInstance(context)
        val wallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val existing = cachedBitmap
        if (
            !forceReload &&
            wallpaperId == cachedWallpaperId &&
            blurIntensity == cachedIntensity &&
            w == cachedWidth &&
            h == cachedHeight &&
            displayRotation == cachedRotation &&
            existing != null &&
            !existing.isRecycled
        ) {
            return existing
        }

        val src = DrawerWallpaperBlurHelper.loadCroppedWallpaperBitmap(
            wm = wm,
            targetW = w,
            targetH = h,
            xOffset = CENTER_OFFSET,
            yOffset = CENTER_OFFSET,
            tag = TAG,
        ) ?: return null

        val blurred = DrawerWallpaperBlurHelper.blurCroppedBitmap(
            context = context,
            src = src,
            blurIntensity = blurIntensity,
            tag = TAG,
        ) ?: return null

        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        cachedWidth = w
        cachedHeight = h
        cachedRotation = displayRotation
        cachedWallpaperId = wallpaperId
        return blurred
    }

    /** Clears the cached bitmap so the next call recomputes it. */
    @JvmStatic
    fun clearCache() {
        cachedBitmap = null
        cachedIntensity = -1
        cachedWidth = -1
        cachedHeight = -1
        cachedRotation = -1
        cachedWallpaperId = -1
    }

    /** Returns the current window bounds so callers can key cache lookups on screen size. */
    @JvmStatic
    fun screenBounds(context: Context): Rect = DrawerWallpaperBlurHelper.screenBounds(context)

    /** Returns window bounds aligned to [orientation]. */
    @JvmStatic
    fun screenBoundsForOrientation(context: Context, orientation: Int): Rect = DrawerWallpaperBlurHelper.screenBoundsForOrientation(context, orientation)

    /** Returns the current display rotation. */
    @JvmStatic
    fun displayRotation(context: Context): Int = DrawerWallpaperBlurHelper.displayRotation(context)
}
