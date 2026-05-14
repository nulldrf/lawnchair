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
 * Captures the current wallpaper and returns a blurred [Bitmap].
 *
 * Results are cached by intensity so that navigating between preference screens
 * returns the bitmap synchronously on the first frame with no flash.
 */
object SettingsWallpaperBlurHelper {

    @Volatile private var cachedBitmap: Bitmap? = null
    @Volatile private var cachedIntensity: Int = -1

    /**
     * Returns the cached bitmap synchronously if [blurEnabled] is true and
     * [blurIntensity] matches the last computed intensity, otherwise null.
     * Use this as the `initialValue` of `produceState`.
     */
    fun getCachedBitmap(blurEnabled: Boolean, blurIntensity: Int): Bitmap? {
        if (!blurEnabled) return null
        val bmp = cachedBitmap
        return if (blurIntensity == cachedIntensity && bmp != null && !bmp.isRecycled) bmp else null
    }

    /**
     * Returns a blurred bitmap at [blurIntensity]. Serves from cache when
     * intensity is unchanged. Run on [kotlinx.coroutines.Dispatchers.IO].
     */
    @SuppressLint("MissingPermission")
    fun getBlurredBitmap(context: Context, blurIntensity: Int): Bitmap? {
        getCachedBitmap(blurEnabled = true, blurIntensity)?.let { return it }

        val wallpaperDrawable = runCatching {
            android.app.WallpaperManager.getInstance(context).drawable
        }.getOrNull() ?: return null

        val bounds = screenBounds(context)
        val w = bounds.width().takeIf { it > 0 } ?: return null
        val h = bounds.height().takeIf { it > 0 } ?: return null

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
        if (blurred !== src) src.recycle()

        cachedBitmap = blurred
        cachedIntensity = blurIntensity
        return blurred
    }

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
