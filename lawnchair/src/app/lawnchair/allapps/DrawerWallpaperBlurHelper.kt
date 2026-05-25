package app.lawnchair.allapps

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.hoko.blur.HokoBlur
import kotlin.math.min

/**
 * Computes and caches a HokoBlur-blurred wallpaper bitmap used as the
 * app-drawer background on phones (non-sheet layout).
 *
 * Wallpaper access is attempted via two methods in order:
 *  1. [WallpaperManager.getDrawable] — fast cached path.
 *  2. [WallpaperManager.getWallpaperFile] — raw-file fallback, works
 *     on devices where the drawable cache has expired or the wallpaper
 *     manager returns null from [getDrawable] for non-live wallpapers.
 *
 * Neither method can retrieve a live-wallpaper frame; on such devices
 * the method returns null and no blur is shown.
 *
 * All [@JvmStatic] methods are callable from Java as static members:
 * `DrawerWallpaperBlurHelper.getBlurredBitmap(context, intensity)`.
 */
object DrawerWallpaperBlurHelper {

    private const val TAG = "DrawerWallpaperBlur"

    @Volatile private var cachedBitmap: Bitmap? = null
    @Volatile private var cachedIntensity: Int = -1

    @JvmStatic
    fun getCachedBitmap(blurEnabled: Boolean, blurIntensity: Int): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        return if (blurIntensity == cachedIntensity && bmp != null && !bmp.isRecycled) bmp else null
    }

    /**
     * Returns a HokoBlur-blurred copy of the current wallpaper, or null if
     * the wallpaper cannot be retrieved (e.g. live wallpaper) or blurring fails.
     *
     * Safe to call from any thread. Heavy — always call from a background thread.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
        // Fast path: return cached result when intensity matches.
        getCachedBitmap(blurEnabled = true, blurIntensity)?.let { return it }

        val bounds = screenBounds(context)
        val w = bounds.width().takeIf { it > 0 } ?: run {
            Log.e(TAG, "screenBounds returned zero width — aborting")
            return null
        }
        val h = bounds.height().takeIf { it > 0 } ?: run {
            Log.e(TAG, "screenBounds returned zero height — aborting")
            return null
        }

        val wm = WallpaperManager.getInstance(context)

        // ── Attempt 1: getDrawable() ──────────────────────────────────────────
        // Fast cached path. Returns null for live wallpapers and when the bitmap
        // cache has been evicted (e.g. shortly after device boot).
        val src: Bitmap = runCatching {
            val drawable = wm.drawable
            if (drawable != null) {
                Log.d(TAG, "wallpaper obtained via getDrawable()")
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(Canvas(bmp))
                }
            } else {
                Log.d(TAG, "getDrawable() returned null — trying getWallpaperFile()")
                null
            }
        }.getOrElse { e ->
            Log.e(TAG, "getDrawable() threw: $e")
            null
        }
            ?: runCatching {
                // ── Attempt 2: getWallpaperFile() ────────────────────────────────
                // Reads the raw wallpaper file. Works on devices where getDrawable()
                // returns null but the wallpaper is a static image on disk.
                // Returns null for live wallpapers.
                val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                if (pfd == null) {
                    Log.e(TAG, "getWallpaperFile() returned null — likely live wallpaper, cannot blur")
                    return null
                }
                pfd.use { fd ->
                    val raw = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
                    if (raw == null) {
                        Log.e(TAG, "BitmapFactory.decodeFileDescriptor() returned null")
                        return null
                    }
                    Log.d(TAG, "wallpaper obtained via getWallpaperFile() — size ${raw.width}x${raw.height}")
                    // Scale to screen size if the raw file dimensions differ.
                    if (raw.width == w && raw.height == h) {
                        raw
                    } else {
                        Bitmap.createScaledBitmap(raw, w, h, true)
                            .also { scaled -> if (scaled !== raw) raw.recycle() }
                    }
                }
            }.getOrElse { e ->
                Log.e(TAG, "getWallpaperFile() threw: $e")
                null
            }
            ?: return null  // Both attempts failed.

        // ── Apply HokoBlur ────────────────────────────────────────────────────
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
        }.getOrElse { e ->
            Log.e(TAG, "HokoBlur threw: $e")
            null
        }

        if (blurred == null) {
            Log.e(TAG, "HokoBlur returned null bitmap")
            src.recycle()
            return null
        }
        if (blurred !== src) src.recycle()

        Log.d(TAG, "blur computed successfully — ${blurred.width}x${blurred.height} intensity=$blurIntensity")
        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        return blurred
    }

    /**
     * Blurs [src] with HokoBlur and returns the result.
     *
     * Unlike [getBlurredBitmap] this method does **not** access [WallpaperManager];
     * it is used as a permission-free fallback when the caller supplies a bitmap
     * obtained via [android.view.PixelCopy] on the launcher window.
     *
     * Returns null if HokoBlur fails for any reason.
     */
    @JvmStatic
    fun blurBitmap(context: Context, src: Bitmap, blurIntensity: Int): Bitmap? {
        val clamped = blurIntensity.coerceIn(10, 150)
        val hokoRadius = min(clamped, 25)
        val sampleFactor = (clamped / 25f).coerceAtLeast(1f)
        return runCatching<Bitmap?> {
            HokoBlur.with(context)
                .scheme(HokoBlur.SCHEME_NATIVE)
                .mode(HokoBlur.MODE_STACK)
                .radius(hokoRadius)
                .sampleFactor(sampleFactor)
                .forceCopy(false)
                .processor()
                .blur(src)
        }.getOrElse { e ->
            Log.e(TAG, "blurBitmap threw: $e")
            null
        }
    }

    /** Drops the cached blurred bitmap, forcing a recompute on the next call. */
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
