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

    var bitmap by remember {
        mutableStateOf(
            SettingsWallpaperBlurHelper.getCachedBitmap(
                blurEnabled = blurEnabled,
                blurIntensity = blurIntensity,
                width = targetWidth,
                height = targetHeight,
                displayRotation = displayRotation,
            ),
        )
    }
    var lastOrientationKey by remember { mutableStateOf(orientation to displayRotation) }

    LaunchedEffect(blurEnabled, blurIntensity, targetWidth, targetHeight, orientation, displayRotation) {
        if (!blurEnabled) {
            bitmap = null
            SettingsWallpaperBlurHelper.clearCache()
            return@LaunchedEffect
        }

        val orientationChanged = lastOrientationKey != (orientation to displayRotation)
        lastOrientationKey = orientation to displayRotation

        val current = bitmap
        val sizeMismatch = current != null && (current.width != targetWidth || current.height != targetHeight)
        if (orientationChanged || sizeMismatch) {
            bitmap = null
        }

        bitmap = withContext(Dispatchers.IO) {
            SettingsWallpaperBlurHelper.getBlurredBitmap(
                context = context,
                blurIntensity = blurIntensity,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                displayRotation = displayRotation,
                forceReload = orientationChanged || sizeMismatch,
            )
        }
    }

    val validBitmap = bitmap?.takeIf {
        blurEnabled &&
            !it.isRecycled &&
            it.width == targetWidth &&
            it.height == targetHeight
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
