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
 * Rendering is handled entirely in Compose inside [PreferenceLayout], so there
 * is no window-background manipulation here.
 * Call [getBlurredBitmap] from a
 * coroutine on [kotlinx.coroutines.Dispatchers.IO] — HokoBlur's native call
 * is CPU-heavy.
 *
 * Returns null if the wallpaper is unavailable or if blurring fails.
 */
object SettingsWallpaperBlurHelper {

    /**
     * @param blurIntensity  User-facing intensity in [10, 150].
     *
     * HokoBlur's radius is capped at 25 internally, so we cover the full
     * slider range by also scaling the downsample factor:
     *
     * radius       = min(intensity, 25)        → 10 … 25
     * sampleFactor = max(1f, intensity / 25f)  → 1x … 6x
     *
     * Intensity 10  → subtle frost.
     * Intensity 150 → heavy fog (max radius + 6× downsample).
     */
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
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

        // Added explicit type <Bitmap?> to fix type inference issue
        val blurred: Bitmap? = runCatching<Bitmap?> {
            HokoBlur.with(context)
                .scheme(HokoBlur.SCHEME_NATIVE) // Native C++ implementation
                .mode(HokoBlur.MODE_STACK)      // Stack ≈ Gaussian quality, better perf
                .radius(hokoRadius)
                .sampleFactor(sampleFactor)
                .forceCopy(false)               // needUpscale(true) removed for v1.5.5 compatibility
                .processor()                    // build the processor first
                .blur(src)                      // then blur
        }.getOrNull()

        if (blurred == null) {
            src.recycle()
            return null
        }
        
        // HokoBlur may mutate src in-place when forceCopy=false.
        // Only recycle src when a distinct bitmap was returned.
        if (blurred !== src) src.recycle()
        return blurred
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
