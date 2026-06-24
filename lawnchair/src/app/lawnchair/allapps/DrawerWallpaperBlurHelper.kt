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
 *  1. [WallpaperManager.getWallpaperFile] — raw-file path. Reads directly
 *     from disk so it is always up-to-date after rotation, unlike
 *     [WallpaperManager.getDrawable] whose internal bitmap cache can hold a
 *     stale (pre-rotation) frame.
 *  2. [WallpaperManager.getDrawable] — fast cached fallback for devices that
 *     do not expose a file descriptor (some OEM ROMs return null from
 *     [getWallpaperFile]).
 *
 * Neither method can retrieve a live-wallpaper frame; on such devices
 * the method returns null and no blur is shown.
 *
 * Cache key: (intensity, screenWidth, screenHeight).  Changing any of the
 * three — including a screen rotation that swaps width and height — forces a
 * full recompute, which is exactly what we need so the blurred bitmap always
 * matches the current display orientation.
 *
 * All [@JvmStatic] methods are callable from Java:
 *   `DrawerWallpaperBlurHelper.getBlurredBitmap(context, intensity)`
 */
object DrawerWallpaperBlurHelper {

    private const val TAG = "DrawerWallpaperBlur"

    // LC-Note: cache key is (intensity, width, height) so a rotation that
    // swaps w/h automatically invalidates the cache and forces a recompute
    // with the correct screen dimensions, preventing the "squished wallpaper"
    // artifact that occurred when the cached portrait bitmap was reused in
    // landscape (or vice-versa).
    @Volatile private var cachedBitmap: Bitmap? = null
    @Volatile private var cachedIntensity: Int = -1
    @Volatile private var cachedWidth: Int = -1
    @Volatile private var cachedHeight: Int = -1

    @JvmStatic
    fun getCachedBitmap(blurEnabled: Boolean, blurIntensity: Int, width: Int, height: Int): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        return if (
            blurIntensity == cachedIntensity &&
            width == cachedWidth &&
            height == cachedHeight &&
            bmp != null &&
            !bmp.isRecycled
        ) bmp else null
    }

    /**
     * Returns a HokoBlur-blurred copy of the current wallpaper sized to the
     * current screen bounds, or null if the wallpaper cannot be retrieved
     * (e.g. live wallpaper) or blurring fails.
     *
     * Safe to call from any thread. Heavy — always call from a background thread.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
        val bounds = screenBounds(context)
        val w = bounds.width().takeIf { it > 0 } ?: run {
            Log.e(TAG, "screenBounds returned zero width — aborting")
            return null
        }
        val h = bounds.height().takeIf { it > 0 } ?: run {
            Log.e(TAG, "screenBounds returned zero height — aborting")
            return null
        }

        // LC-Note: fast path now also checks width/height so a rotation
        // (which swaps w and h) correctly bypasses the stale cached bitmap.
        getCachedBitmap(blurEnabled = true, blurIntensity, w, h)?.let { return it }

        val wm = WallpaperManager.getInstance(context)

        // ── Attempt 1: getWallpaperFile() ────────────────────────────────────
        // LC-Note: this is now the PRIMARY path (previously it was the fallback).
        // WallpaperManager.getDrawable() keeps an internal Bitmap cache that is
        // keyed by the wallpaper ID, NOT by screen orientation.  After a
        // rotation the drawable cache still holds the pre-rotation bitmap, so
        // calling getDrawable() first would return the wrong-sized image and
        // produce the "squished wallpaper" artifact even though the cached
        // bitmap in *our* helper had been cleared.  Reading the raw file via
        // getWallpaperFile() bypasses that internal cache entirely and always
        // gives us fresh pixels that we then scale to the current screen bounds.
        val src: Bitmap = runCatching {
            val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
            if (pfd == null) {
                Log.d(TAG, "getWallpaperFile() returned null — trying getDrawable()")
                null
            } else {
                pfd.use { fd ->
                    val raw = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
                    if (raw == null) {
                        Log.e(TAG, "BitmapFactory.decodeFileDescriptor() returned null")
                        null
                    } else {
                        Log.d(TAG, "wallpaper obtained via getWallpaperFile() — size ${raw.width}x${raw.height}, target ${w}x${h}")
                        // Always scale to current screen bounds so the bitmap
                        // fills the drawer exactly regardless of orientation.
                        if (raw.width == w && raw.height == h) {
                            raw
                        } else {
                            Bitmap.createScaledBitmap(raw, w, h, true)
                                .also { scaled -> if (scaled !== raw) raw.recycle() }
                        }
                    }
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "getWallpaperFile() threw: $e")
            null
        }
            // ── Attempt 2: getDrawable() ──────────────────────────────────────
            // Fallback for devices where getWallpaperFile() is unavailable or
            // returns null (some OEM ROMs, live wallpapers return null here too).
            ?: runCatching {
                val drawable = wm.drawable
                if (drawable == null) {
                    Log.e(TAG, "getDrawable() returned null — likely live wallpaper, cannot blur")
                    return null
                }
                Log.d(TAG, "wallpaper obtained via getDrawable() — rendering to ${w}x${h}")
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(Canvas(bmp))
                }
            }.getOrElse { e ->
                Log.e(TAG, "getDrawable() threw: $e")
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

        // LC-Note: store width/height in the cache key so any future call with
        // different dimensions (e.g. after the next rotation) is treated as a
        // cache miss and triggers a fresh recompute.
        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        cachedWidth = w
        cachedHeight = h
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

    /**
     * Drops the cached blurred bitmap, forcing a recompute on the next call.
     * Called from [PreferenceManager2] when [drawerBlurIntensity] changes and
     * from [ActivityAllAppsContainerView.onDeviceProfileChanged] on rotation.
     */
    @JvmStatic
    fun clearCache() {
        cachedBitmap = null
        cachedIntensity = -1
        cachedWidth = -1
        cachedHeight = -1
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
