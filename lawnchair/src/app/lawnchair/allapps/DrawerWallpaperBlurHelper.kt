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
 * app-drawer background.
 *
 * ## Recompute triggers
 * The bitmap is recomputed only when something actually changes:
 *  - [getBlurredBitmap] is called with a different [blurIntensity]
 *  - Screen dimensions change (rotation) — the cache key includes width/height
 *  - [clearCache] is called explicitly (from [PreferenceManager2] when
 *    [drawerBlurIntensity] changes, or from [ActivityAllAppsContainerView]
 *    when the device rotates via [onDeviceProfileChanged])
 *
 * ## Why not PixelCopy / per-frame capture?
 * PixelCopy forces a GPU surface readback every vsync, which causes frame
 * tears and visual glitches in the app drawer. A wallpaper bitmap only needs
 * to be re-read when the wallpaper itself changes or the screen rotates —
 * neither of which happens while the drawer is being scrolled or animated.
 *
 * ## Wallpaper source priority
 *  1. [WallpaperManager.getWallpaperFile] — reads the raw file directly,
 *     bypassing WallpaperManager's internal drawable cache (keyed by wallpaper
 *     ID, not orientation) which returns stale pre-rotation pixels after rotate.
 *  2. [WallpaperManager.getDrawable] — fallback for OEM ROMs where
 *     [getWallpaperFile] returns null.
 *
 * Live wallpapers return null from both calls; no blur is shown for them.
 *
 * ## Cache key
 * `(blurIntensity, screenWidth, screenHeight)` — a rotation that swaps w/h
 * is automatically a cache miss and forces a correct-sized recompute.
 */
object DrawerWallpaperBlurHelper {

    private const val TAG = "DrawerWallpaperBlur"

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

        // Fast path: return cached result when intensity and dimensions all match.
        getCachedBitmap(blurEnabled = true, blurIntensity, w, h)?.let { return it }

        val wm = WallpaperManager.getInstance(context)

        // Attempt 1: getWallpaperFile() — bypasses WallpaperManager's own
        // drawable cache so we always get pixels at the correct orientation.
        val src: Bitmap = runCatching {
            val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
            if (pfd == null) {
                Log.d(TAG, "getWallpaperFile() null — trying getDrawable()")
                null
            } else {
                pfd.use { fd ->
                    val raw = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
                    if (raw == null) {
                        Log.e(TAG, "decodeFileDescriptor() returned null")
                        null
                    } else {
                        Log.d(TAG, "wallpaper via getWallpaperFile(): ${raw.width}x${raw.height} → ${w}x${h}")
                        if (raw.width == w && raw.height == h) raw
                        else Bitmap.createScaledBitmap(raw, w, h, true)
                            .also { scaled -> if (scaled !== raw) raw.recycle() }
                    }
                }
            }
        }.getOrElse { e -> Log.e(TAG, "getWallpaperFile() threw: $e"); null }

            // Attempt 2: getDrawable() fallback.
            ?: runCatching {
                val drawable = wm.drawable ?: run {
                    Log.e(TAG, "getDrawable() null — likely live wallpaper")
                    return null
                }
                Log.d(TAG, "wallpaper via getDrawable() — rendering to ${w}x${h}")
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(Canvas(bmp))
                }
            }.getOrElse { e -> Log.e(TAG, "getDrawable() threw: $e"); null }

            ?: return null

        // Apply HokoBlur on the source bitmap.
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
        }.getOrElse { e -> Log.e(TAG, "HokoBlur threw: $e"); null }

        if (blurred == null) {
            Log.e(TAG, "HokoBlur returned null")
            src.recycle()
            return null
        }
        if (blurred !== src) src.recycle()

        Log.d(TAG, "blur done — ${blurred.width}x${blurred.height} intensity=$blurIntensity")

        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        cachedWidth = w
        cachedHeight = h
        return blurred
    }

    /**
     * Drops the cached bitmap, forcing a recompute on the next [getBlurredBitmap] call.
     * Called when blur intensity changes or screen rotates.
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
