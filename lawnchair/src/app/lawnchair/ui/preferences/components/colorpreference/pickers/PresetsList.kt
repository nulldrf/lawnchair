package app.lawnchair.ui.preferences.components.colorpreference.pickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.asState
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

private const val MIN_SATURATION = 0.10f
private const val PALETTE_MAX_COUNT = 32
const val MAX_SWATCHES = 8
private const val DEDUPE_DISTANCE = 48.0

/**
 * Wallpaper colour grid for the Presets page.
 *
 * Reads [preferenceManager2.colorStyle] reactively so every swatch re-renders
 * with the correct Monet style whenever the user changes it — without touching
 * the Custom page's grid at all.
 *
 * Slot layout (up to [MAX_SWATCHES] total):
 *  [0]    SystemAccent
 *  [1..N] WallpaperDerived colours (up to 6, or 7 if no Default)
 *  [last] Default — only when [includeDefault] is true
 */
@Composable
fun WallpaperColorGrid(
    appliedColor: ColorOption,
    onApplyOption: (ColorOption) -> Unit,
    includeDefault: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Read the active colour style reactively — recomposition happens
    // automatically when the user picks a different style.
    val currentColorStyle = preferenceManager2().colorStyle.asState().value

    var extractedEntries by remember {
        mutableStateOf<List<ColorPreferenceEntry<ColorOption>>>(emptyList())
    }

    LaunchedEffect(Unit) {
        extractedEntries = withContext(Dispatchers.IO) {
            buildPresetEntries(context, includeDefault)
        }
    }

    if (extractedEntries.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.presets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    SwatchGrid(
        entries = extractedEntries,
        onSwatchClick = { option -> onApplyOption(option) },
        isSwatchSelected = { option ->
            when {
                option is ColorOption.SystemAccent ->
                    appliedColor is ColorOption.SystemAccent

                option is ColorOption.Default ->
                    appliedColor is ColorOption.Default

                option is ColorOption.WallpaperDerived &&
                    appliedColor is ColorOption.WallpaperDerived ->
                    // Match only on chosen color — fingerprint may differ
                    // between the live entry and the stored preference when
                    // the list was extracted after a wallpaper change.
                    option.color == appliedColor.color

                // Legacy: WallpaperPrimary → highlight first wallpaper swatch.
                option is ColorOption.WallpaperDerived &&
                    appliedColor is ColorOption.WallpaperPrimary ->
                    extractedEntries
                        .firstOrNull { it.value is ColorOption.WallpaperDerived }
                        ?.value == option

                else -> false
            }
        },
        modifier = modifier,
        contentModifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        // Pass the active style so presets swatches reflect e.g. Monochromatic.
        // The banner still shows the raw dot colour, not the processed palette.
        colorStyle = currentColorStyle,
    )
}

// ---------------------------------------------------------------------------
// Entry building
// ---------------------------------------------------------------------------

private fun buildPresetEntries(
    context: Context,
    includeDefault: Boolean,
): List<ColorPreferenceEntry<ColorOption>> {
    val entries = mutableListOf<ColorPreferenceEntry<ColorOption>>()

    // Slot 0: System accent
    entries += ColorOption.SystemAccent.colorPreferenceEntry

    // Slots 1..N: Wallpaper-derived colours
    val wallpaperSlots = MAX_SWATCHES - 1 - (if (includeDefault) 1 else 0)
    val wallpaperColors = extractWallpaperColors(context, wallpaperSlots)

    // The first extracted color is always the wallpaper primary — store it as
    // the fingerprint in every WallpaperDerived entry so ThemeProvider can
    // detect a wallpaper change by comparing against this value.
    val wallpaperPrimary = wallpaperColors.firstOrNull() ?: 0

    wallpaperColors.forEach { colorInt ->
        val option = ColorOption.WallpaperDerived(
            color = colorInt,
            wallpaperPrimary = wallpaperPrimary,
        )
        entries += ColorPreferenceEntry<ColorOption>(
            value = option,
            label = { stringResource(R.string.wallpaper) },
            lightColor = { colorInt },
            darkColor = { colorInt },
        )
    }

    // Last slot: Default (Managed by Lawnchair)
    if (includeDefault) {
        entries += ColorOption.Default.colorPreferenceEntry
    }

    return entries
}

// ---------------------------------------------------------------------------
// Extraction
// ---------------------------------------------------------------------------

private fun extractWallpaperColors(context: Context, maxCount: Int): List<Int> {
    val wm = android.app.WallpaperManager.getInstance(context)
    val colorInts = mutableListOf<Int>()

    if (Utilities.ATLEAST_O_MR1) {
        runCatching {
            val wc = wm.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            if (wc != null) {
                colorInts.add(wc.primaryColor.toArgb())
                wc.secondaryColor?.toArgb()?.let { colorInts.addIfDistinct(it) }
                wc.tertiaryColor?.toArgb()?.let { colorInts.addIfDistinct(it) }
            }
        }
    } else {
        WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors
            ?.primaryColor?.let { colorInts.add(it) }
    }

    if (colorInts.size < maxCount) {
        runCatching {
            val bitmap = getBitmapFromWallpaper(wm)
            if (bitmap != null) {
                val hsv = FloatArray(3)
                Palette.Builder(bitmap)
                    .maximumColorCount(PALETTE_MAX_COUNT)
                    .resizeBitmapArea(20_000)
                    .generate()
                    .swatches
                    .sortedByDescending { it.population }
                    .forEach { swatch ->
                        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                        if (hsv[1] >= MIN_SATURATION) colorInts.addIfDistinct(swatch.rgb)
                    }
            }
        }
    }

    return colorInts.distinct().take(maxCount)
}

private fun getBitmapFromWallpaper(wm: android.app.WallpaperManager): Bitmap? {
    val drawable = runCatching { wm.drawable }.getOrNull()
        ?: runCatching { wm.peekDrawable() }.getOrNull()
        ?: return null
    return when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        else -> runCatching {
            val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(720) ?: 360
            val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(1280) ?: 640
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp
        }.getOrNull()
    }
}

private fun MutableList<Int>.addIfDistinct(color: Int) {
    if (none { existing -> rgbDistance(existing, color) < DEDUPE_DISTANCE }) add(color)
}

private fun rgbDistance(c1: Int, c2: Int): Double {
    val dr = (android.graphics.Color.red(c1) - android.graphics.Color.red(c2)).toDouble()
    val dg = (android.graphics.Color.green(c1) - android.graphics.Color.green(c2)).toDouble()
    val db = (android.graphics.Color.blue(c1) - android.graphics.Color.blue(c2)).toDouble()
    return sqrt(dr.pow(2) + dg.pow(2) + db.pow(2))
}
