package app.lawnchair.ui.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.hoko.blur.HokoBlur
import kotlin.math.min

/**
 * Pure utility — captures the current wallpaper and returns a blurred [Bitmap].
 *
 * Results are cached by intensity so that navigating between preference screens
 * returns the bitmap synchronously (no IO, no flash). The cache holds a single
 * entry; changing intensity or toggling blur off invalidates it.
 *
 * Call [getCachedBitmap] to get the current cached value synchronously (use as
 * the `initialValue` of `produceState` so the first frame already has the bitmap).
 * Call [getBlurredBitmap] on [kotlinx.coroutines.Dispatchers.IO] to compute or
 * return the cached bitmap for a given intensity.
 */
object SettingsWallpaperBlurHelper {

    @Volatile private var cachedBitmap: Bitmap? = null
    @Volatile private var cachedIntensity: Int = -1

    /**
     * Returns the cached bitmap synchronously if [blurEnabled] is true and
     * [blurIntensity] matches the last computed intensity, otherwise null.
     *
     * Use this as the `initialValue` in `produceState` so screens that are
     * revisited render the bitmap on the very first frame with no flicker.
     */
    fun getCachedBitmap(blurEnabled: Boolean, blurIntensity: Int): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        return if (blurIntensity == cachedIntensity && bmp != null && !bmp.isRecycled) bmp else null
    }

    /**
     * Returns a blurred bitmap for the wallpaper at [blurIntensity].
     *
     * Returns the cache immediately when intensity is unchanged. Recomputes
     * (on the calling thread — run on [kotlinx.coroutines.Dispatchers.IO])
     * when intensity differs or the cache is empty.
     *
     * Returns null if the wallpaper is unavailable or blurring fails.
     *
     * @param blurIntensity  User-facing intensity in [10, 150].
     *
     *   HokoBlur's radius is capped at 25 internally, so we cover the full
     *   slider range by also scaling the downsample factor:
     *
     *     radius       = min(intensity, 25)        → 10 … 25
     *     sampleFactor = max(1f, intensity / 25f)  → 1x … 6x
     *
     *   Intensity 10  → subtle frost.
     *   Intensity 150 → heavy fog (max radius + 6× downsample).
     */
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
        // Return cache immediately if nothing has changed.
        getCachedBitmap(blurEnabled = true, blurIntensity)?.let { return it }

        val wallpaperDrawable = runCatching {
            android.app.WallpaperManager.getInstance(context).drawable
        }.getOrNull() ?: return null

        val bounds = screenBounds(context)
        val w = bounds.width().takeIf { it > 0 } ?: return null
        val h = bounds.height().takeIf { it > 0 } ?: return null

        // Rasterise the wallpaper at full screen resolution.
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(src).also { canvas ->
            wallpaperDrawable.setBounds(0, 0, w, h)
            wallpaperDrawable.draw(canvas)
        }

        val clamped = blurIntensity.coerceIn(10, 150)
        val hokoRadius = min(clamped, 25)
        val sampleFactor = (clamped / 25f).coerceAtLeast(1f)

        val blurred: Bitmap? = runCatching<Bitmap?> {
            HokoBlur.with(context)
                .scheme(HokoBlur.SCHEME_NATIVE)
                .mode(HokoBlur.MODE_STACK)
                .radius(hokoRadius)
                .sampleFactor(sampleFactor)
                .forceCopy(false)
                .processor()
                .blur(src)
        }.getOrNull()

        if (blurred == null) {
            src.recycle()
            return null
        }
        // HokoBlur may mutate src in-place when forceCopy=false.
        if (blurred !== src) src.recycle()

        // Update cache. Don't explicitly recycle the old bitmap here — Compose's
        // ImageBitmap may still be referencing it for one more frame.
        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        return blurred
    }

    /** Clears the bitmap cache. Called when blur is toggled off to free memory. */
    fun clearCache() {
        cachedBitmap = null
        cachedIntensity = -1
    }

    @Suppress("DEPRECATION")
    private fun screenBounds(context: Context): Rect {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
    }
}
