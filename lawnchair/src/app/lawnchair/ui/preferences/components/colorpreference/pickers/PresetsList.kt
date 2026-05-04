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

// Minimum HSV saturation (0–1) a Palette swatch must have to be kept.
private const val MIN_SATURATION = 0.10f

// Maximum color buckets requested from the Palette API.
private const val PALETTE_MAX_COUNT = 32

// Maximum swatches shown when the wallpaper is colorful.
const val MAX_SWATCHES = 8

// Minimum Euclidean RGB distance before two colours are treated as duplicates.
private const val DEDUPE_DISTANCE = 48.0

/**
 * Wallpaper colour grid for the Presets page.
 *
 * Every non-Default swatch tap stores [ColorOption.WallpaperPrimary] in the
 * preference so the description label always reads "Wallpaper".  The extracted
 * colours are only used for rendering: the most-dominant swatch is highlighted
 * whenever [ColorOption.WallpaperPrimary] is the applied preference.
 *
 * When [includeDefault] is true (used by preferences that support
 * "Managed by Lawnchair"), the last slot is always [ColorOption.Default].
 */
@Composable
fun WallpaperColorGrid(
    appliedColor: ColorOption,
    onApplyWallpaper: () -> Unit,
    onApplyDefault: () -> Unit,
    includeDefault: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var extractedEntries by remember {
        mutableStateOf<List<ColorPreferenceEntry<ColorOption>>>(emptyList())
    }

    LaunchedEffect(Unit) {
        extractedEntries = withContext(Dispatchers.IO) {
            extractWallpaperSwatches(context, includeDefault)
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
                text = stringResource(id = R.string.wallpaper),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    // The first entry (most-dominant colour) is the visual proxy for
    // WallpaperPrimary selection state.
    val firstEntry = extractedEntries.firstOrNull()

    SwatchGrid(
        entries = extractedEntries,
        onSwatchClick = { option ->
            if (option is ColorOption.Default) onApplyDefault() else onApplyWallpaper()
        },
        isSwatchSelected = { option ->
            when {
                // Default swatch selected when Default is the applied colour.
                option is ColorOption.Default ->
                    appliedColor is ColorOption.Default

                // All other swatches: highlight only the first (most-dominant)
                // one when WallpaperPrimary is the active preference — every
                // swatch tap resolves to WallpaperPrimary anyway.
                appliedColor is ColorOption.WallpaperPrimary ->
                    option == firstEntry?.value

                else -> false
            }
        },
        modifier = modifier,
        contentModifier = Modifier.padding(
            horizontal = 16.dp,
            vertical = 16.dp,
        ),
    )
}

// ---------------------------------------------------------------------------
// Extraction logic
// ---------------------------------------------------------------------------

/**
 * Returns up to [MAX_SWATCHES] dominant colours from the current home-screen
 * wallpaper, sorted by dominance.
 *
 * Strategy:
 *  1. On Android 8.1+ use [android.app.WallpaperManager.getWallpaperColors]
 *     for primary / secondary / tertiary — no bitmap or extra permissions.
 *  2. Supplement by running [Palette] on the wallpaper bitmap for additional
 *     colours up to the target count.
 *  3. Deduplicate using Euclidean RGB distance.
 *  4. If [includeDefault] is true, cap extracted slots at MAX_SWATCHES − 1
 *     and append [ColorOption.Default] as the final entry.
 */
private fun extractWallpaperSwatches(
    context: Context,
    includeDefault: Boolean,
): List<ColorPreferenceEntry<ColorOption>> {
    val wm = android.app.WallpaperManager.getInstance(context)
    val colorInts = mutableListOf<Int>()

    // ── Step 1: WallpaperColors API (Android 8.1+, permission-safe) ─────────
    if (Utilities.ATLEAST_O_MR1) {
        runCatching {
            val colors = wm.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            if (colors != null) {
                colorInts.add(colors.primaryColor.toArgb())
                colors.secondaryColor?.toArgb()?.let { colorInts.addIfDistinct(it) }
                colors.tertiaryColor?.toArgb()?.let { colorInts.addIfDistinct(it) }
            }
        }
    } else {
        // Pre-8.1: fall back to the single primary colour stored by Lawnchair.
        WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors
            ?.primaryColor
            ?.let { colorInts.add(it) }
    }

    // ── Step 2: Palette API on the wallpaper bitmap ──────────────────────────
    val targetCount = if (includeDefault) MAX_SWATCHES - 1 else MAX_SWATCHES
    if (colorInts.size < targetCount) {
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
                        if (hsv[1] >= MIN_SATURATION) {
                            colorInts.addIfDistinct(swatch.rgb)
                        }
                    }
            }
        }
    }

    if (colorInts.isEmpty()) return emptyList()

    // ── Step 3: Build entries ────────────────────────────────────────────────
    // Each entry keeps its own distinct CustomColor value so SwatchGrid can
    // tell swatches apart visually. The click handler in WallpaperColorGrid
    // always applies WallpaperPrimary, keeping the preference label as "Wallpaper".
    val distinct = colorInts.distinct().take(targetCount)
    val entries: MutableList<ColorPreferenceEntry<ColorOption>> = distinct.map { colorInt ->
        ColorPreferenceEntry<ColorOption>(
            value = ColorOption.CustomColor(colorInt),
            label = { stringResource(R.string.wallpaper) },
            lightColor = { colorInt },
            darkColor = { colorInt },
        )
    }.toMutableList()

    // ── Step 4: Append Default if requested ─────────────────────────────────
    if (includeDefault) {
        entries += ColorOption.Default.colorPreferenceEntry
    }

    return entries
}

/**
 * Attempts to obtain a [Bitmap] from the system wallpaper manager drawable.
 * Handles [BitmapDrawable] directly and draws any other drawable type onto
 * an offscreen [Bitmap] so Palette can still analyse it.
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

/** Adds [color] only if it is perceptually distinct from all existing entries. */
private fun MutableList<Int>.addIfDistinct(color: Int) {
    if (none { existing -> rgbDistance(existing, color) < DEDUPE_DISTANCE }) {
        add(color)
    }
}

/** Euclidean distance in RGB space. */
private fun rgbDistance(c1: Int, c2: Int): Double {
    val dr = (android.graphics.Color.red(c1) - android.graphics.Color.red(c2)).toDouble()
    val dg = (android.graphics.Color.green(c1) - android.graphics.Color.green(c2)).toDouble()
    val db = (android.graphics.Color.blue(c1) - android.graphics.Color.blue(c2)).toDouble()
    return sqrt(dr.pow(2) + dg.pow(2) + db.pow(2))
}
