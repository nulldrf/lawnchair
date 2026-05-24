package app.lawnchair.allapps

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
 * Computes and caches a HokoBlur-blurred wallpaper bitmap used as the
 * app-drawer background on phones (non-sheet layout).
 *
 * All methods are annotated with [@JvmStatic] so
 * [ActivityAllAppsContainerView] (Java) can call them as static methods:
 * `DrawerWallpaperBlurHelper.getBlurredBitmap(context, intensity)`.
 *
 * Thread-safety: [getBlurredBitmap] is safe to call from any thread.
 * Because bitmap computation is heavy, callers must always invoke it from a
 * background executor (e.g. `Executors.UI_HELPER_EXECUTOR`).
 *
 * Cache invalidation: call [clearCache] before changing blur intensity so the
 * next call to [getBlurredBitmap] recomputes with the new radius.
 */
object DrawerWallpaperBlurHelper {

    @Volatile private var cachedBitmap: Bitmap? = null
    @Volatile private var cachedIntensity: Int = -1

    /**
     * Returns the in-memory cached bitmap synchronously if it exists and
     * [blurIntensity] matches the cached value; otherwise returns null.
     *
     * Safe to call on the UI thread — no disk or IPC access.
     */
    @JvmStatic
    fun getCachedBitmap(blurEnabled: Boolean, blurIntensity: Int): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        return if (blurIntensity == cachedIntensity && bmp != null && !bmp.isRecycled) bmp else null
    }

    /**
     * Returns a HokoBlur-blurred copy of the current wallpaper, caching the
     * result.  Returns null if the wallpaper cannot be accessed or blurring
     * fails.
     *
     * **Always call from a background thread** — reads the wallpaper drawable
     * and performs native-code blur, both of which can be slow.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
        // Fast path: return cached result when intensity matches.
        getCachedBitmap(blurEnabled = true, blurIntensity)?.let { return it }

        val wallpaperDrawable = runCatching {
            android.app.WallpaperManager.getInstance(context).drawable
        }.getOrNull() ?: return null

        val bounds = screenBounds(context)
        val w = bounds.width().takeIf { it > 0 } ?: return null
        val h = bounds.height().takeIf { it > 0 } ?: return null

        // Draw the wallpaper onto a full-screen bitmap.
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(src).also { canvas ->
            wallpaperDrawable.setBounds(0, 0, w, h)
            wallpaperDrawable.draw(canvas)
        }

        // Map the 10..150 intensity range onto HokoBlur's 1..25 radius plus a
        // sample-factor so that high intensity values still blur aggressively.
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
        // HokoBlur may return the same object when forceCopy = false.
        if (blurred !== src) src.recycle()

        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        return blurred
    }

    /**
     * Clears the cached bitmap reference.  Call this before changing the blur
     * intensity preference so the next [getBlurredBitmap] recomputes with the
     * new radius.
     *
     * The bitmap itself is NOT recycled here — existing [android.widget.ImageView]
     * references are still valid until the GC collects the old bitmap.
     */
    @JvmStatic
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
