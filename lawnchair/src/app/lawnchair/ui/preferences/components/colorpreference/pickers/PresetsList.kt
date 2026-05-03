package app.lawnchair.ui.preferences.components.colorpreference.pickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Minimum HSV saturation (0–1) for a swatch to be considered "colorful".
// Swatches below this threshold are treated as near-monochrome.
private const val MIN_SATURATION = 0.12f

// Maximum distinct colors to extract via Palette before filtering.
private const val PALETTE_MAX_COUNT = 24

// Maximum swatches shown in the grid when the wallpaper is colorful.
private const val MAX_SWATCHES = 8

// Minimum swatches always shown regardless of saturation filtering.
private const val MIN_SWATCHES = 2

@Composable
fun WallpaperColorGrid(
    onSwatchClick: (ColorOption) -> Unit,
    isSwatchSelected: (ColorOption) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var extractedEntries by remember {
        mutableStateOf<List<ColorPreferenceEntry<ColorOption>>>(emptyList())
    }

    LaunchedEffect(Unit) {
        extractedEntries = withContext(Dispatchers.IO) {
            extractWallpaperSwatches(context)
        }
    }

    if (extractedEntries.isEmpty()) {
        // Show a loading placeholder with the same grid shape
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.wallpaper),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    SwatchGrid(
        entries = extractedEntries,
        onSwatchClick = onSwatchClick,
        isSwatchSelected = isSwatchSelected,
        modifier = modifier,
        contentModifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp,
        ),
    )
}

/**
 * Extracts up to [MAX_SWATCHES] dominant colors from the current home-screen
 * wallpaper, sorted by population (most dominant first).
 *
 * Strategy:
 *  1. Get wallpaper bitmap via [WallpaperManagerCompat].
 *  2. Run [Palette] with [PALETTE_MAX_COUNT] buckets.
 *  3. Sort by population descending.
 *  4. Filter out near-monochrome swatches (saturation < [MIN_SATURATION]).
 *  5. If fewer than [MIN_SWATCHES] survive the filter, fall back to the
 *     top-[MIN_SWATCHES] by population regardless of saturation.
 *  6. Cap at [MAX_SWATCHES].
 *  7. Wrap each ARGB int into a [ColorPreferenceEntry] pointing to
 *     [ColorOption.CustomColor] so it integrates with the existing
 *     preference adapter without touching [ColorOption].
 */
private fun extractWallpaperSwatches(context: Context): List<ColorPreferenceEntry<ColorOption>> {
    val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)

    // Obtain the wallpaper bitmap.  On API < 27 the manager's drawable is the
    // only route; on 27+ we can ask WallpaperManager directly.
    val bitmap: Bitmap? = runCatching {
        val drawable = wallpaperManager.wallpaperManager.drawable
        (drawable as? BitmapDrawable)?.bitmap
    }.getOrNull()

    // If we cannot get the bitmap at all, fall back to just the primaryColor.
    if (bitmap == null) {
        val fallback = wallpaperManager.wallpaperColors?.primaryColor
            ?: return emptyList()
        return listOf(colorIntToEntry(fallback))
    }

    // Resize to a small area so Palette runs fast on the UI-blocked IO dispatcher.
    val palette = Palette.Builder(bitmap)
        .maximumColorCount(PALETTE_MAX_COUNT)
        .resizeBitmapArea(15_000)
        .generate()

    val allSwatches = palette.swatches
        .sortedByDescending { it.population }

    if (allSwatches.isEmpty()) {
        val fallback = wallpaperManager.wallpaperColors?.primaryColor
            ?: return emptyList()
        return listOf(colorIntToEntry(fallback))
    }

    // Filter to colorful swatches only.
    val hsv = FloatArray(3)
    val colorful = allSwatches.filter { swatch ->
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        hsv[1] >= MIN_SATURATION
    }

    // Use colorful list if it has enough entries, otherwise fall back gracefully.
    val candidates = if (colorful.size >= MIN_SWATCHES) colorful else allSwatches

    return candidates
        .take(MAX_SWATCHES)
        .map { colorIntToEntry(it.rgb) }
}

private fun colorIntToEntry(colorInt: Int): ColorPreferenceEntry<ColorOption> {
    val option = ColorOption.CustomColor(colorInt)
    return ColorPreferenceEntry(
        value = option,
        label = { "" },
        lightColor = { colorInt },
        darkColor = { colorInt },
    )
}
