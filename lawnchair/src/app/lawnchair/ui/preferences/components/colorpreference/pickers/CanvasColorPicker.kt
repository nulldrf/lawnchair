package app.lawnchair.ui.preferences.components.colorpreference.pickers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.launcher3.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CanvasColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor by remember {
        derivedStateOf {
            android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        }
    }

    var hexText by remember { mutableStateOf(String.format("%06X", 0xFFFFFF and initialColor)) }
    var isEditingHex by remember { mutableStateOf(false) }

    LaunchedEffect(hue, saturation, brightness) {
        if (!isEditingHex) {
            hexText = String.format("%06X", 0xFFFFFF and currentColor)
        }
    }

    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.custom),
                    style = MaterialTheme.typography.titleLarge,
                )

                // 2D canvas + vertical hue strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SaturationBrightnessCanvas(
                        hue = hue,
                        saturation = saturation,
                        brightness = brightness,
                        onSaturationBrightnessChange = { s, b ->
                            saturation = s
                            brightness = b
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    HueStrip(
                        hue = hue,
                        onHueChange = { hue = it },
                        modifier = Modifier
                            .width(32.dp)
                            .fillMaxHeight(),
                    )
                }

                // Before → After circles + hex input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(initialColor)),
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(currentColor)),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    HexInputRow(
                        hexText = hexText,
                        onHexTextChange = { newVal ->
                            val clean = newVal
                                .uppercase()
                                .filter { it.isLetterOrDigit() }
                                .take(6)
                            hexText = clean
                            isEditingHex = true
                            if (clean.length == 6) {
                                runCatching {
                                    val parsed = android.graphics.Color.parseColor("#$clean")
                                    val h = FloatArray(3)
                                    android.graphics.Color.colorToHSV(parsed, h)
                                    hue = h[0]
                                    saturation = h[1]
                                    brightness = h[2]
                                }
                                isEditingHex = false
                            }
                        },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("hex_color", "#$hexText"),
                            )
                        },
                        onPaste = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            val text = clipboard.primaryClip
                                ?.getItemAt(0)
                                ?.text
                                ?.toString()
                                ?: return@HexInputRow
                            val clean = text
                                .removePrefix("#")
                                .uppercase()
                                .filter { it.isLetterOrDigit() }
                                .take(6)
                            if (clean.length == 6) {
                                runCatching {
                                    val parsed = android.graphics.Color.parseColor("#$clean")
                                    val h = FloatArray(3)
                                    android.graphics.Color.colorToHSV(parsed, h)
                                    hue = h[0]
                                    saturation = h[1]
                                    brightness = h[2]
                                    hexText = clean
                                }
                            }
                        },
                    )
                }

                Button(
                    onClick = {
                        onColorSelected(currentColor)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = R.string.action_apply))
                }
            }
        }
    }
}

@Composable
private fun HexInputRow(
    hexText: String,
    onHexTextChange: (String) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = hexText,
            onValueChange = onHexTextChange,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            singleLine = true,
            modifier = Modifier.width(52.dp),
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(onClick = onPaste, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SaturationBrightnessCanvas(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onSaturationBrightnessChange: (saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val pureHueColor = Color.hsv(hue, 1f, 1f)

    fun updateFromOffset(offset: Offset) {
        if (canvasSize == Size.Zero) return
        val s = (offset.x / canvasSize.width).coerceIn(0f, 1f)
        val b = 1f - (offset.y / canvasSize.height).coerceIn(0f, 1f)
        onSaturationBrightnessChange(s, b)
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> updateFromOffset(change.position) }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> updateFromOffset(offset) }
            },
    ) {
        canvasSize = size

        // White (left) → pure hue (right)
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, pureHueColor)))
        // Transparent (top) → Black (bottom)
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val handleX = saturation * size.width
        val handleY = (1f - brightness) * size.height

        // Outer ring (white border)
        drawCircle(
            color = Color.White,
            radius = 11.dp.toPx(),
            center = Offset(handleX, handleY),
            style = Stroke(width = 2.dp.toPx()),
        )
        // Inner filled circle (current color)
        drawCircle(
            color = Color.hsv(hue, saturation, brightness),
            radius = 9.dp.toPx(),
            center = Offset(handleX, handleY),
        )
    }
}

@Composable
private fun HueStrip(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stripHeight by remember { mutableFloatStateOf(0f) }
    val hueColors = remember {
        (0..360 step 6).map { deg ->
            Color.hsv(deg.toFloat().coerceAtMost(359f), 1f, 1f)
        }
    }

    fun updateHue(y: Float) {
        if (stripHeight == 0f) return
        onHueChange(((y / stripHeight) * 360f).coerceIn(0f, 360f))
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> updateHue(change.position.y) }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> updateHue(offset.y) }
            },
    ) {
        stripHeight = size.height
        drawRect(brush = Brush.verticalGradient(hueColors))

        val handleH = 8.dp.toPx()
        val handleY = ((hue / 360f) * size.height - handleH / 2f)
            .coerceIn(0f, size.height - handleH)

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, handleY),
            size = Size(size.width, handleH),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}
