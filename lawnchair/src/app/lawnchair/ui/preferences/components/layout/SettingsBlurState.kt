package app.lawnchair.ui.preferences.components.layout

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import app.lawnchair.ui.preferences.SettingsWallpaperBlurHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SettingsBlurState(
    val bitmap: Bitmap?,
    val sizeModifier: Modifier,
)

private data class SettingsBlurViewportKey(
    val width: Int,
    val height: Int,
    val orientation: Int,
    val rotation: Int,
)

/**
 * Loads and keeps a blurred wallpaper bitmap in sync with the settings container's
 * live pixel bounds and screen orientation.
 *
 * On every orientation or size change, any bitmap from the previous orientation is
 * immediately dropped (never stretched/re-cropped) and a fresh crop is computed from
 * the system wallpaper for the new orientation.
 */
@Composable
internal fun rememberSettingsBlurState(
    blurEnabled: Boolean,
    blurIntensity: Int,
): SettingsBlurState {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val fallbackBounds = remember(orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        SettingsWallpaperBlurHelper.screenBoundsForOrientation(context, orientation)
    }
    val targetWidth = if (containerSize.width > 0) containerSize.width else fallbackBounds.width()
    val targetHeight = if (containerSize.height > 0) containerSize.height else fallbackBounds.height()
    val displayRotation = remember(orientation, targetWidth, targetHeight) {
        SettingsWallpaperBlurHelper.displayRotation(context)
    }
    val viewportKey = SettingsBlurViewportKey(
        width = targetWidth,
        height = targetHeight,
        orientation = orientation,
        rotation = displayRotation,
    )

    // Pair the bitmap with the requested viewport key rather than checking exact
    // bitmap pixel dimensions: HokoBlur's sampleFactor downscale/upscale can round
    // the output bitmap by 1–3 px at certain blur intensities (e.g. 75%–100%).
    var loaded by remember {
        val cached = SettingsWallpaperBlurHelper.getCachedBitmap(
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity,
            width = targetWidth,
            height = targetHeight,
            displayRotation = displayRotation,
        )
        mutableStateOf(cached?.let { viewportKey to it })
    }

    LaunchedEffect(blurEnabled, blurIntensity, viewportKey) {
        if (!blurEnabled) {
            loaded = null
            SettingsWallpaperBlurHelper.clearCache()
            return@LaunchedEffect
        }

        val currentKey = loaded?.first
        val viewportChanged = currentKey != null && currentKey != viewportKey
        if (viewportChanged) {
            loaded = null
        }

        val fresh = withContext(Dispatchers.IO) {
            SettingsWallpaperBlurHelper.getBlurredBitmap(
                context = context,
                blurIntensity = blurIntensity,
                targetWidth = viewportKey.width,
                targetHeight = viewportKey.height,
                displayRotation = viewportKey.rotation,
                forceReload = viewportChanged,
            )
        }
        loaded = fresh?.let { viewportKey to it }
    }

    val validBitmap = loaded?.let { (loadedKey, bmp) ->
        val sameOrientation = targetWidth == targetHeight ||
            (bmp.width > bmp.height) == (targetWidth > targetHeight)
        if (blurEnabled && loadedKey == viewportKey && !bmp.isRecycled && sameOrientation) {
            bmp
        } else {
            null
        }
    }

    return SettingsBlurState(
        bitmap = validBitmap,
        sizeModifier = Modifier.onSizeChanged { newSize ->
            if (newSize.width > 0 && newSize.height > 0 && newSize != containerSize) {
                containerSize = newSize
            }
        },
    )
}
