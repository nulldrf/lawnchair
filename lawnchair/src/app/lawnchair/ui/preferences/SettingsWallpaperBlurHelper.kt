package app.lawnchair.ui.preferences

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
 * Computes and caches a blurred wallpaper bitmap for settings screens,
 * keyed by blur intensity and screen dimensions.
 */
object SettingsWallpaperBlurHelper {

    private const val TAG = "SettingsWallpaperBlur"

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
     * Returns a blurred copy of the current wallpaper for the current screen bounds,
     * or null for live wallpapers or failures. Call from a background thread.
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

        getCachedBitmap(blurEnabled = true, blurIntensity, w, h)?.let { return it }

        val wm = WallpaperManager.getInstance(context)

        // Read the raw wallpaper file first to avoid stale cached drawables after rotation.
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

            // Fall back to WallpaperManager.drawable when getWallpaperFile() is unavailable.
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

    /** Clears the cached bitmap so the next call recomputes it. */
    @JvmStatic
    fun clearCache() {
        cachedBitmap = null
        cachedIntensity = -1
        cachedWidth = -1
        cachedHeight = -1
    }

    /** Returns the current window bounds so callers can key cache lookups on screen size. */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun screenBounds(context: Context): Rect {
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
