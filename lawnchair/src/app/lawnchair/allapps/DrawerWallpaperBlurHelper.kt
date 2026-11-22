package app.lawnchair.allapps

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.hoko.blur.HokoBlur
import java.io.FileDescriptor
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
 * On orientation changes (e.g. portrait -> landscape), this helper never reuses
 * the previous orientation's bitmap and prefers the uncropped system wallpaper
 * when the system's cropped file was pre-cropped to a different orientation.
 */
object DrawerWallpaperBlurHelper {

    private const val TAG = "DrawerWallpaperBlur"
    private const val DEFAULT_Y_OFFSET = 0.5f

    @Volatile private var cachedBitmap: Bitmap? = null

    @Volatile private var cachedIntensity: Int = -1

    @Volatile private var cachedWidth: Int = -1

    @Volatile private var cachedHeight: Int = -1

    @Volatile private var cachedXOffsetKey: Int = -1

    @Volatile private var cachedRotation: Int = -1

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
        return getCachedBitmap(
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity,
            width = width,
            height = height,
            xOffset = xOffset,
            displayRotation = cachedRotation,
        )
    }

    @JvmStatic
    fun getCachedBitmap(
        blurEnabled: Boolean,
        blurIntensity: Int,
        width: Int,
        height: Int,
        xOffset: Float,
        displayRotation: Int,
    ): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        val xKey = quantizeOffset(xOffset)
        return if (
            blurIntensity == cachedIntensity &&
            width == cachedWidth &&
            height == cachedHeight &&
            xKey == cachedXOffsetKey &&
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
     * Convenience overload using current window bounds and centered horizontal offset.
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
            xOffset = 0.5f,
            displayRotation = displayRotation(context),
            forceReload = false,
        )
    }

    /**
     * Backwards-compatible overload using the current display rotation.
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
        return getBlurredBitmap(
            context = context,
            blurIntensity = blurIntensity,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            xOffset = xOffset,
            displayRotation = displayRotation(context),
            forceReload = false,
        )
    }

    /**
     * Returns a HokoBlur-blurred copy of the visible portion of the current wallpaper
     * for the given viewport ([targetWidth] × [targetHeight]), display rotation
     * ([displayRotation]), and horizontal wallpaper scroll offset ([xOffset] in `[0, 1]`),
     * or null if the wallpaper cannot be retrieved (e.g. live wallpaper) or blurring fails.
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
        displayRotation: Int,
        forceReload: Boolean,
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
        val clampedX = xOffset.coerceIn(0f, 1f)
        val xKey = quantizeOffset(clampedX)

        // Fast path: return cached result only when wallpaper ID, intensity, size,
        // orientation/rotation, and offset all match and reload was not forced.
        val existing = cachedBitmap
        if (
            !forceReload &&
            wallpaperId == cachedWallpaperId &&
            blurIntensity == cachedIntensity &&
            w == cachedWidth &&
            h == cachedHeight &&
            displayRotation == cachedRotation &&
            xKey == cachedXOffsetKey &&
            existing != null &&
            !existing.isRecycled
        ) {
            return existing
        }

        val src = loadCroppedWallpaperBitmap(
            wm = wm,
            targetW = w,
            targetH = h,
            xOffset = clampedX,
            yOffset = DEFAULT_Y_OFFSET,
            tag = TAG,
        ) ?: return null

        val blurred = blurCroppedBitmap(context, src, blurIntensity, TAG) ?: return null

        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        cachedWidth = w
        cachedHeight = h
        cachedXOffsetKey = xKey
        cachedRotation = displayRotation
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
        cachedRotation = -1
        cachedWallpaperId = -1
    }

    /**
     * Loads a fresh crop of the system wallpaper sized to [targetW] × [targetH].
     *
     * On Android, `getWallpaperFile(FLAG_SYSTEM)` defaults to `getCropped = true`,
     * which on phones often returns a portrait-cropped bitmap even when the device is
     * rotated to landscape. When the target viewport is landscape (or when the system's
     * cropped file does not match the target orientation), we first try to read the
     * original uncropped system wallpaper (`getCropped = false`) so the new orientation
     * gets a fresh crop from the full wallpaper set by the system instead of re-cropping
     * the portrait slice.
     */
    @SuppressLint("MissingPermission")
    internal fun loadCroppedWallpaperBitmap(
        wm: WallpaperManager,
        targetW: Int,
        targetH: Int,
        xOffset: Float,
        yOffset: Float,
        tag: String,
    ): Bitmap? {
        val isLandscapeTarget = targetW > targetH

        // Attempt 1: In landscape, prefer the original uncropped wallpaper file so we take
        // a genuine landscape crop instead of zooming into a portrait-cropped wallpaper.
        if (isLandscapeTarget) {
            decodeWallpaperFileCropped(
                wm = wm,
                getCropped = false,
                targetW = targetW,
                targetH = targetH,
                xOffset = xOffset,
                yOffset = yOffset,
                tag = tag,
            )?.let { return it }
        }

        // Attempt 2: Public getWallpaperFile(FLAG_SYSTEM) (getCropped = true).
        // In portrait, this preserves the user's exact portrait framing from the system picker;
        // if it turns out to be landscape-cropped while we are in portrait, try the original file.
        val croppedFileBitmap = runCatching {
            wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                val rawDims = peekBounds(pfd.fileDescriptor)
                if (
                    !isLandscapeTarget &&
                    rawDims != null &&
                    rawDims.first > rawDims.second &&
                    rawDims.second < targetH
                ) {
                    decodeWallpaperFileCropped(
                        wm = wm,
                        getCropped = false,
                        targetW = targetW,
                        targetH = targetH,
                        xOffset = xOffset,
                        yOffset = yOffset,
                        tag = tag,
                    )?.let { return it }
                }
                decodeFileDescriptorCropped(
                    fd = pfd.fileDescriptor,
                    targetW = targetW,
                    targetH = targetH,
                    xOffset = xOffset,
                    yOffset = yOffset,
                    tag = tag,
                    sourceLabel = "getWallpaperFile(cropped)",
                )
            }
        }.getOrElse { e ->
            Log.e(tag, "getWallpaperFile() threw: $e")
            null
        }
        if (croppedFileBitmap != null) return croppedFileBitmap

        // Attempt 3: WallpaperManager.drawable fallback. Clear WallpaperManager's in-process
        // drawable cache first so a previous orientation's bitmap is never reused.
        return runCatching {
            runCatching { wm.forgetLoadedWallpaper() }
            val drawable = wm.drawable ?: run {
                Log.e(tag, "getDrawable() null — likely live wallpaper")
                return null
            }
            Log.d(tag, "wallpaper via getDrawable() — cropping to ${targetW}x$targetH (xOffset=$xOffset)")
            val bm = (drawable as? BitmapDrawable)?.bitmap
            if (bm != null && !bm.isRecycled) {
                cropBitmapToViewport(bm, targetW, targetH, xOffset, yOffset, recycleSource = false)
            } else {
                renderDrawableCropped(drawable, targetW, targetH, xOffset, yOffset)
            }
        }.getOrElse { e ->
            Log.e(tag, "getDrawable() threw: $e")
            null
        }
    }

    internal fun blurCroppedBitmap(
        context: Context,
        src: Bitmap,
        blurIntensity: Int,
        tag: String,
    ): Bitmap? {
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
            Log.e(tag, "HokoBlur threw: $e")
            null
        }

        if (blurred == null) {
            Log.e(tag, "HokoBlur returned null")
            src.recycle()
            return null
        }
        if (blurred !== src) src.recycle()

        Log.d(tag, "blur done — ${blurred.width}x${blurred.height} intensity=$blurIntensity")
        return blurred
    }

    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    private fun openWallpaperFile(
        wm: WallpaperManager,
        getCropped: Boolean,
        tag: String,
    ): ParcelFileDescriptor? {
        if (getCropped) {
            return wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
        }
        return runCatching {
            val method = WallpaperManager::class.java.getMethod(
                "getWallpaperFile",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            method.invoke(wm, WallpaperManager.FLAG_SYSTEM, false) as? ParcelFileDescriptor
        }.getOrElse { e ->
            Log.d(tag, "getWallpaperFile(FLAG_SYSTEM, false) unavailable: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun decodeWallpaperFileCropped(
        wm: WallpaperManager,
        getCropped: Boolean,
        targetW: Int,
        targetH: Int,
        xOffset: Float,
        yOffset: Float,
        tag: String,
    ): Bitmap? {
        return runCatching {
            openWallpaperFile(wm, getCropped, tag)?.use { pfd ->
                decodeFileDescriptorCropped(
                    fd = pfd.fileDescriptor,
                    targetW = targetW,
                    targetH = targetH,
                    xOffset = xOffset,
                    yOffset = yOffset,
                    tag = tag,
                    sourceLabel = if (getCropped) "getWallpaperFile(cropped)" else "getWallpaperFile(orig)",
                )
            }
        }.getOrElse { e ->
            Log.e(tag, "decodeWallpaperFileCropped(getCropped=$getCropped) threw: $e")
            null
        }
    }

    private fun peekBounds(fd: FileDescriptor): Pair<Int, Int>? {
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(fd, null, opts)
            Os.lseek(fd, 0L, OsConstants.SEEK_SET)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                opts.outWidth to opts.outHeight
            } else {
                null
            }
        }.getOrNull()
    }

    private fun decodeFileDescriptorCropped(
        fd: FileDescriptor,
        targetW: Int,
        targetH: Int,
        xOffset: Float,
        yOffset: Float,
        tag: String,
        sourceLabel: String,
    ): Bitmap? {
        val opts = BitmapFactory.Options()
        val dims = peekBounds(fd)
        if (dims != null) {
            val (rawW, rawH) = dims
            val scale = max(targetW.toFloat() / rawW, targetH.toFloat() / rawH)
            val cropW = (targetW / scale).roundToInt().coerceIn(1, rawW)
            val cropH = (targetH / scale).roundToInt().coerceIn(1, rawH)
            var sampleSize = 1
            while (
                cropW / (sampleSize * 2) >= targetW &&
                cropH / (sampleSize * 2) >= targetH
            ) {
                sampleSize *= 2
            }
            opts.inSampleSize = sampleSize
        }

        val raw = BitmapFactory.decodeFileDescriptor(fd, null, opts)
        if (raw == null) {
            Log.e(tag, "decodeFileDescriptor($sourceLabel) returned null")
            return null
        }
        Log.d(
            tag,
            "wallpaper via $sourceLabel: ${raw.width}x${raw.height} → ${targetW}x$targetH (xOffset=$xOffset)",
        )
        return cropBitmapToViewport(raw, targetW, targetH, xOffset, yOffset, recycleSource = true)
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

    /**
     * Returns window bounds aligned with [orientation] so transient pre-rotation
     * `WindowMetrics` reads during `onConfigurationChanged` never return inverted dimensions.
     */
    @JvmStatic
    fun screenBoundsForOrientation(context: Context, orientation: Int): Rect {
        val bounds = screenBounds(context)
        val w = bounds.width()
        val h = bounds.height()
        return when {
            orientation == Configuration.ORIENTATION_LANDSCAPE && w < h -> Rect(0, 0, h, w)
            orientation == Configuration.ORIENTATION_PORTRAIT && w > h -> Rect(0, 0, h, w)
            else -> Rect(0, 0, w, h)
        }
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun displayRotation(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val rotation = runCatching { context.display?.rotation }.getOrNull()
            if (rotation != null) return rotation
        }
        return runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.rotation
        }.getOrDefault(Surface.ROTATION_0)
    }

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
