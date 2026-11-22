package app.lawnchair.allapps

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
 * Computes and caches a HokoBlur-blurred wallpaper bitmap used as the
 * app-drawer background.
 *
 * Instead of scaling/squishing the entire raw wallpaper image into the screen
 * bounds, this helper crops the exact visible portion of the wallpaper using
 * Android's `CENTER_CROP` + `(xOffset, yOffset)` viewport mapping so portrait,
 * landscape, foldable, tablet, and scrolling wallpapers match what is actually
 * behind the app drawer with zero aspect-ratio distortion.
 *
 * ## Recompute triggers
 * The bitmap is recomputed only when something actually changes:
 *  - [blurIntensity] changes
 *  - Target viewport dimensions `(width, height)` change (rotation / resize)
 *  - Workspace wallpaper horizontal offset [xOffset] changes
 *  - System wallpaper changes ([WallpaperManager.getWallpaperId])
 *  - [clearCache] is called explicitly
 */
object DrawerWallpaperBlurHelper {

    private const val TAG = "DrawerWallpaperBlur"
    private const val DEFAULT_Y_OFFSET = 0.5f

    @Volatile private var cachedBitmap: Bitmap? = null

    @Volatile private var cachedIntensity: Int = -1

    @Volatile private var cachedWidth: Int = -1

    @Volatile private var cachedHeight: Int = -1

    @Volatile private var cachedXOffsetKey: Int = -1

    @Volatile private var cachedWallpaperId: Int = -1

    @JvmStatic
    fun getCachedBitmap(blurEnabled: Boolean, blurIntensity: Int, width: Int, height: Int): Bitmap? {
        return getCachedBitmap(blurEnabled, blurIntensity, width, height, xOffset = 0.5f)
    }

    @JvmStatic
    fun getCachedBitmap(
        blurEnabled: Boolean,
        blurIntensity: Int,
        width: Int,
        height: Int,
        xOffset: Float,
    ): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        val xKey = quantizeOffset(xOffset)
        return if (
            blurIntensity == cachedIntensity &&
            width == cachedWidth &&
            height == cachedHeight &&
            xKey == cachedXOffsetKey &&
            bmp != null &&
            !bmp.isRecycled
        ) {
            bmp
        } else {
            null
        }
    }

    /**
     * Convenience overload using current window bounds and centered horizontal offset.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
        val bounds = screenBounds(context)
        return getBlurredBitmap(context, blurIntensity, bounds.width(), bounds.height(), 0.5f)
    }

    /**
     * Returns a HokoBlur-blurred copy of the visible portion of the current wallpaper
     * for the given viewport ([targetWidth] × [targetHeight]) and horizontal wallpaper
     * scroll offset ([xOffset] in `[0, 1]`), or null if the wallpaper cannot be retrieved
     * (e.g. live wallpaper) or blurring fails.
     *
     * Safe to call from any thread. Heavy — always call from a background thread.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(
        context: Context,
        blurIntensity: Int,
        targetWidth: Int,
        targetHeight: Int,
        xOffset: Float,
    ): Bitmap? {
        val fallbackBounds = if (targetWidth <= 0 || targetHeight <= 0) screenBounds(context) else null
        val w = if (targetWidth > 0) targetWidth else (fallbackBounds?.width() ?: 0)
        val h = if (targetHeight > 0) targetHeight else (fallbackBounds?.height() ?: 0)
        if (w <= 0 || h <= 0) {
            Log.e(TAG, "Invalid blur viewport ${w}x$h — aborting")
            return null
        }

        val wm = WallpaperManager.getInstance(context)
        val wallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val clampedX = xOffset.coerceIn(0f, 1f)
        val xKey = quantizeOffset(clampedX)

        // Fast path: return cached result when wallpaper ID, intensity, size, and offset match.
        val existing = cachedBitmap
        if (
            wallpaperId == cachedWallpaperId &&
            blurIntensity == cachedIntensity &&
            w == cachedWidth &&
            h == cachedHeight &&
            xKey == cachedXOffsetKey &&
            existing != null &&
            !existing.isRecycled
        ) {
            return existing
        }

        // Attempt 1: getWallpaperFile() — bypasses WallpaperManager's own
        // drawable cache so we always get raw pixels and crop to the active viewport.
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
                        Log.d(TAG, "wallpaper via getWallpaperFile(): ${raw.width}x${raw.height} → ${w}x$h (xOffset=$clampedX)")
                        cropBitmapToViewport(raw, w, h, clampedX, DEFAULT_Y_OFFSET, recycleSource = true)
                    }
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "getWallpaperFile() threw: $e")
            null
        }

            // Attempt 2: getDrawable() fallback.
            ?: runCatching {
                val drawable = wm.drawable ?: run {
                    Log.e(TAG, "getDrawable() null — likely live wallpaper")
                    return null
                }
                Log.d(TAG, "wallpaper via getDrawable() — cropping to ${w}x$h (xOffset=$clampedX)")
                val bm = (drawable as? BitmapDrawable)?.bitmap
                if (bm != null && !bm.isRecycled) {
                    cropBitmapToViewport(bm, w, h, clampedX, DEFAULT_Y_OFFSET, recycleSource = false)
                } else {
                    renderDrawableCropped(drawable, w, h, clampedX, DEFAULT_Y_OFFSET)
                }
            }.getOrElse { e ->
                Log.e(TAG, "getDrawable() threw: $e")
                null
            }

            ?: return null

        // Apply HokoBlur on the cropped viewport bitmap.
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
        cachedXOffsetKey = xKey
        cachedWallpaperId = wallpaperId
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
        cachedXOffsetKey = -1
        cachedWallpaperId = -1
    }

    /**
     * Crops and scales [raw] to [targetW] × [targetH] using aspect-preserving `CENTER_CROP`
     * positioned by ([xOffset], [yOffset]), matching Android's static wallpaper viewport.
     */
    private fun cropBitmapToViewport(
        raw: Bitmap,
        targetW: Int,
        targetH: Int,
        xOffset: Float,
        yOffset: Float,
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
        val cropLeft = ((rawW - cropW) * xOffset).roundToInt().coerceIn(0, rawW - cropW)
        val cropTop = ((rawH - cropH) * yOffset).roundToInt().coerceIn(0, rawH - cropH)

        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val srcRect = Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH)
        val dstRect = Rect(0, 0, targetW, targetH)
        canvas.drawBitmap(raw, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
        if (recycleSource) {
            raw.recycle()
        }
        return out
    }

    private fun renderDrawableCropped(
        drawable: android.graphics.drawable.Drawable,
        targetW: Int,
        targetH: Int,
        xOffset: Float,
        yOffset: Float,
    ): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val dw = drawable.intrinsicWidth
        val dh = drawable.intrinsicHeight
        if (dw > 0 && dh > 0) {
            val scale = max(targetW.toFloat() / dw, targetH.toFloat() / dh)
            val scaledW = (dw * scale).roundToInt()
            val scaledH = (dh * scale).roundToInt()
            val left = -((scaledW - targetW) * xOffset).roundToInt()
            val top = -((scaledH - targetH) * yOffset).roundToInt()
            drawable.setBounds(left, top, left + scaledW, top + scaledH)
        } else {
            drawable.setBounds(0, 0, targetW, targetH)
        }
        drawable.draw(canvas)
        return out
    }

    private fun quantizeOffset(offset: Float): Int = (offset.coerceIn(0f, 1f) * 100f).roundToInt()

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
