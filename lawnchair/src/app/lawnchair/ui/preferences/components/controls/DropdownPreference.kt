package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

// ── Popup content — top-level function, zero scope inheritance ────────────────
@Composable
private fun <T> DropdownPopupContent(
    entries: List<Pair<T, String>>,
    currentValue: T,
    onSelect: (T) -> Unit,
) {
    var animVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animVisible = true }

    AnimatedVisibility(
        visible = animVisible,
        enter = fadeIn(tween(100)) + scaleIn(
            animationSpec = tween(150),
            transformOrigin = TransformOrigin(1f, 0f),
            initialScale = 0.95f,
        ),
        exit = fadeOut(tween(80)) + scaleOut(
            animationSpec = tween(120),
            transformOrigin = TransformOrigin(1f, 0f),
            targetScale = 0.95f,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 240.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), clip = false),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                entries.forEach { (value, entryLabel) ->
                    val isSelected = value == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            )
                            .clickable { onSelect(value) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = entryLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ── Public preference row ─────────────────────────────────────────────────────
@Composable
fun <T> DropdownPreference(
    label: String,
    entries: List<Pair<T, String>>,
    currentValue: T,
    onValueChange: (T) -> Unit,
    description: String? = null,
) {
    var popupVisible by remember { mutableStateOf(false) }
    val currentLabel = entries.firstOrNull { it.first == currentValue }?.second ?: ""

    // Capture locals outside the Popup so its separate composition tree
    // inherits the app font and layout settings.
    val localTextStyle = LocalTextStyle.current
    val localContentColor = LocalContentColor.current
    val localDensity = LocalDensity.current
    val localLayoutDirection = LocalLayoutDirection.current
    val localFontFamilyResolver = LocalFontFamilyResolver.current

    // PreferenceTemplate is what every other preference row uses — it sets
    // LocalTextStyle to typography.titleMedium (the app font / Google Sans)
    // for the title slot and typography.bodyMedium for the description slot.
    // Using it here makes Color spec look identical to Color style and every
    // other row on the screen.
    PreferenceTemplate(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { popupVisible = true },
        title = {
            Text(text = label)
        },
        description = {
            if (description != null) Text(text = description)
        },
        endWidget = {
            // Popup anchors to this Box — top-end of the value label
            Box(contentAlignment = Alignment.TopEnd) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (popupVisible) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        onDismissRequest = { popupVisible = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        CompositionLocalProvider(
                            LocalTextStyle provides localTextStyle,
                            LocalContentColor provides localContentColor,
                            LocalDensity provides localDensity,
                            LocalLayoutDirection provides localLayoutDirection,
                            LocalFontFamilyResolver provides localFontFamilyResolver,
                        ) {
                            DropdownPopupContent(
                                entries = entries,
                                currentValue = currentValue,
                                onSelect = { value ->
                                    popupVisible = false
                                    onValueChange(value)
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}
