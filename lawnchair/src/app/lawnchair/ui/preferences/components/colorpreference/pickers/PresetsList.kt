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
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

// Minimum HSV saturation a Palette swatch must have to be considered colourful.
private const val MIN_SATURATION = 0.10f

// Maximum colour buckets requested from the Palette API.
private const val PALETTE_MAX_COUNT = 32

// Maximum swatches in the grid (including SystemAccent and optionally Default).
const val MAX_SWATCHES = 8

// Minimum Euclidean RGB distance before two colours are treated as duplicates.
private const val DEDUPE_DISTANCE = 48.0

/**
 * Wallpaper colour grid for the Presets page.
 *
 * Layout (up to [MAX_SWATCHES] total):
 *  [0] SystemAccent — always the first slot
 *  [1..N] Wallpaper-extracted colours (up to 6, or 7 if no Default)
 *  [last] Default ("Managed by Lawnchair") — only when [includeDefault] is true
 *
 * All wallpaper-colour taps call [onApplyOption] with [ColorOption.WallpaperPrimary].
 * The last-tapped wallpaper swatch is tracked locally so the user gets clear
 * per-swatch visual feedback even though all wallpaper taps resolve to the same
 * preference value.
 */
@Composable
fun WallpaperColorGrid(
    appliedColor: ColorOption,
    onApplyOption: (ColorOption) -> Unit,
    includeDefault: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var extractedEntries by remember {
        mutableStateOf<List<ColorPreferenceEntry<ColorOption>>>(emptyList())
    }

    // Track which wallpaper swatch the user last tapped for visual feedback.
    var lastTappedWallpaperValue by remember { mutableStateOf<ColorOption?>(null) }

    // Reset tracked swatch when the applied preference moves away from WallpaperPrimary.
    LaunchedEffect(appliedColor) {
        if (appliedColor !is ColorOption.WallpaperPrimary) lastTappedWallpaperValue = null
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

    // First wallpaper-colour entry (used as fallback selection indicator when
    // WallpaperPrimary is active but the user hasn't tapped a swatch yet).
    val firstWallpaperEntry = extractedEntries.firstOrNull { it.value is ColorOption.CustomColor }

    SwatchGrid(
        entries = extractedEntries,
        onSwatchClick = { option ->
            when (option) {
                is ColorOption.SystemAccent -> {
                    lastTappedWallpaperValue = null
                    onApplyOption(ColorOption.SystemAccent)
                }
                is ColorOption.Default -> {
                    lastTappedWallpaperValue = null
                    onApplyOption(ColorOption.Default)
                }
                is ColorOption.CustomColor -> {
                    // All wallpaper swatches resolve to WallpaperPrimary in the
                    // preference, but we track which tile was tapped for feedback.
                    lastTappedWallpaperValue = option
                    onApplyOption(ColorOption.WallpaperPrimary)
                }
                else -> Unit
            }
        },
        isSwatchSelected = { option ->
            when {
                option is ColorOption.SystemAccent ->
                    appliedColor is ColorOption.SystemAccent

                option is ColorOption.Default ->
                    appliedColor is ColorOption.Default

                option is ColorOption.CustomColor &&
                    appliedColor is ColorOption.WallpaperPrimary -> {
                    // Highlight the last tapped swatch, or the first wallpaper
                    // swatch if the preference was already WallpaperPrimary on entry.
                    val target = lastTappedWallpaperValue ?: firstWallpaperEntry?.value
                    option == target
                }

                else -> false
            }
        },
        modifier = modifier,
        contentModifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

// ---------------------------------------------------------------------------
// Entry building
// ---------------------------------------------------------------------------

/**
 * Builds the full preset entry list:
 *  1. [ColorOption.SystemAccent] — always first
 *  2. Wallpaper-extracted colours — up to (MAX_SWATCHES − 1 − if(includeDefault) 1 else 0)
 *  3. [ColorOption.Default] — only when [includeDefault] is true, always last
 */
private fun buildPresetEntries(
    context: Context,
    includeDefault: Boolean,
): List<ColorPreferenceEntry<ColorOption>> {
    val entries = mutableListOf<ColorPreferenceEntry<ColorOption>>()

    // ── Slot 0: System accent ────────────────────────────────────────────────
    entries += ColorOption.SystemAccent.colorPreferenceEntry

    // ── Slots 1..N: Wallpaper colours ────────────────────────────────────────
    val wallpaperSlots = MAX_SWATCHES - 1 - (if (includeDefault) 1 else 0)  // 6 or 7
    val wallpaperColors = extractWallpaperColors(context, wallpaperSlots)
    wallpaperColors.forEach { colorInt ->
        entries += ColorPreferenceEntry<ColorOption>(
            value = ColorOption.CustomColor(colorInt),
            label = { stringResource(R.string.wallpaper) },
            lightColor = { colorInt },
            darkColor = { colorInt },
        )
    }

    // ── Last slot: Default (Managed by Lawnchair) ────────────────────────────
    if (includeDefault) {
        entries += ColorOption.Default.colorPreferenceEntry
    }

    return entries
}

// ---------------------------------------------------------------------------
// Wallpaper colour extraction
// ---------------------------------------------------------------------------

/**
 * Extracts up to [maxCount] dominant colours from the current wallpaper.
 *
 * Strategy:
 *  1. On API 27+: [android.app.WallpaperManager.getWallpaperColors] → primary /
 *     secondary / tertiary (no bitmap permission required).
 *  2. Supplement with [Palette] run on the wallpaper bitmap for additional slots.
 *  3. De-duplicate by Euclidean RGB distance.
 */
private fun extractWallpaperColors(context: Context, maxCount: Int): List<Int> {
    val wm = android.app.WallpaperManager.getInstance(context)
    val colorInts = mutableListOf<Int>()

    // Step 1: WallpaperColors API (API 27+, no permission needed)
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

    // Step 2: Palette API for additional colours
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

/**
 * Attempts to get a [Bitmap] from the wallpaper manager drawable.
 * Works for [BitmapDrawable] and any other drawable type by drawing it
 * onto an offscreen canvas.
 */
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun MutableList<Int>.addIfDistinct(color: Int) {
    if (none { existing -> rgbDistance(existing, color) < DEDUPE_DISTANCE }) add(color)
}

private fun rgbDistance(c1: Int, c2: Int): Double {
    val dr = (android.graphics.Color.red(c1) - android.graphics.Color.red(c2)).toDouble()
    val dg = (android.graphics.Color.green(c1) - android.graphics.Color.green(c2)).toDouble()
    val db = (android.graphics.Color.blue(c1) - android.graphics.Color.blue(c2)).toDouble()
    return sqrt(dr.pow(2) + dg.pow(2) + db.pow(2))
}
