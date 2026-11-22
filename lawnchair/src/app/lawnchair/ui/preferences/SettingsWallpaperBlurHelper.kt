package app.lawnchair.ui.preferences

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.hoko.blur.HokoBlur
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Computes and caches a blurred wallpaper bitmap for settings screens,
 * keyed by wallpaper ID, blur intensity, and screen dimensions.
 */
object SettingsWallpaperBlurHelper {

    private const val TAG = "SettingsWallpaperBlur"

    @Volatile private var cachedBitmap: Bitmap? = null

    @Volatile private var cachedIntensity: Int = -1

    @Volatile private var cachedWidth: Int = -1

    @Volatile private var cachedHeight: Int = -1

    @Volatile private var cachedWallpaperId: Int = -1

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
        ) {
            bmp
        } else {
            null
        }
    }

    /**
     * Returns a blurred copy of the visible center-cropped wallpaper for the current screen bounds,
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

        val wm = WallpaperManager.getInstance(context)
        val wallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val existing = cachedBitmap
        if (
            wallpaperId == cachedWallpaperId &&
            blurIntensity == cachedIntensity &&
            w == cachedWidth &&
            h == cachedHeight &&
            existing != null &&
            !existing.isRecycled
        ) {
            return existing
        }

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
                        Log.d(TAG, "wallpaper via getWallpaperFile(): ${raw.width}x${raw.height} → ${w}x$h")
                        cropCenterToViewport(raw, w, h, recycleSource = true)
                    }
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "getWallpaperFile() threw: $e")
            null
        }

            // Fall back to WallpaperManager.drawable when getWallpaperFile() is unavailable.
            ?: runCatching {
                val drawable = wm.drawable ?: run {
                    Log.e(TAG, "getDrawable() null — likely live wallpaper")
                    return null
                }
                Log.d(TAG, "wallpaper via getDrawable() — cropping to ${w}x$h")
                val bm = (drawable as? BitmapDrawable)?.bitmap
                if (bm != null && !bm.isRecycled) {
                    cropCenterToViewport(bm, w, h, recycleSource = false)
                } else {
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        val dw = drawable.intrinsicWidth
                        val dh = drawable.intrinsicHeight
                        if (dw > 0 && dh > 0) {
                            val scale = max(w.toFloat() / dw, h.toFloat() / dh)
                            val scaledW = (dw * scale).roundToInt()
                            val scaledH = (dh * scale).roundToInt()
                            val left = -((scaledW - w) / 2f).roundToInt()
                            val top = -((scaledH - h) / 2f).roundToInt()
                            drawable.setBounds(left, top, left + scaledW, top + scaledH)
                        } else {
                            drawable.setBounds(0, 0, w, h)
                        }
                        drawable.draw(Canvas(bmp))
                    }
                }
            }.getOrElse { e ->
                Log.e(TAG, "getDrawable() threw: $e")
                null
            }

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
        }.getOrElse { e ->
            Log.e(TAG, "HokoBlur threw: $e")
            null
        }

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
        cachedWallpaperId = -1
    }

    private fun cropCenterToViewport(
        raw: Bitmap,
        targetW: Int,
        targetH: Int,
        recycleSource: Boolean,
    ): Bitmap {
        val rawW = raw.width
        val rawH = raw.height
        if (rawW == targetW && rawH == targetH && recycleSource && raw.isMutable && raw.config == Bitmap.Config.ARGB_8888) {
            return raw
        }

        val scale = max(targetW.toFloat() / rawW, targetH.toFloat() / rawH)
        val cropW = (targetW / scale).roundToInt().coerceIn(1, rawW)
        val cropH = (targetH / scale).roundToInt().coerceIn(1, rawH)
        val cropLeft = ((rawW - cropW) / 2f).roundToInt().coerceIn(0, rawW - cropW)
        val cropTop = ((rawH - cropH) / 2f).roundToInt().coerceIn(0, rawH - cropH)

        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(
            raw,
            Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH),
            Rect(0, 0, targetW, targetH),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        if (recycleSource) {
            raw.recycle()
        }
        return out
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
