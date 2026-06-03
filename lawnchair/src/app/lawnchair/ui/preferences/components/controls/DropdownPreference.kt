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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

// ── Popup content — top-level function, no RowScope/ColumnScope in context ───
// This is the only reliable fix for the RowScope.AnimatedVisibility compiler
// error: by being a top-level function, no implicit receiver leaks in.
@Composable
private fun <T> DropdownPopupContent(
    entries: List<Pair<T, String>>,
    currentValue: T,
    fontFamily: FontFamily?,
    onSelect: (T) -> Unit,
) {
    var animVisible by remember { mutableStateOf(false) }
    // LaunchedEffect fires after first composition, flipping visible false→true
    // so AnimatedVisibility always plays the enter transition.
    LaunchedEffect(Unit) { animVisible = true }

    AnimatedVisibility(
        visible = animVisible,
        enter = fadeIn(tween(120)) + scaleIn(
            animationSpec = tween(180),
            transformOrigin = TransformOrigin(1f, 0f),
            initialScale = 0.92f,
        ),
        exit = fadeOut(tween(100)) + scaleOut(
            animationSpec = tween(140),
            transformOrigin = TransformOrigin(1f, 0f),
            targetScale = 0.92f,
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
                    .padding(vertical = 8.dp, horizontal = 8.dp),
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
                            .padding(
                                start = if (isSelected) 12.dp else 46.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = entryLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            fontFamily = fontFamily,
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

    // Read the app's typography font family here, before entering any scope.
    // MaterialTheme.typography is populated by LawnchairTheme so this will be
    // Google Sans (or whatever the app theme sets), not the system font.
    val appFontFamily = MaterialTheme.typography.bodyLarge.fontFamily

    // Capture composition locals before entering Row so they can be forwarded
    // into the Popup's separate composition tree.
    val localTextStyle = LocalTextStyle.current
    val localContentColor = LocalContentColor.current
    val localDensity = LocalDensity.current
    val localLayoutDirection = LocalLayoutDirection.current
    val localFontFamilyResolver = LocalFontFamilyResolver.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { popupVisible = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = appFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = appFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(contentAlignment = Alignment.TopEnd) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = appFontFamily,
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
                            fontFamily = appFontFamily,
                            onSelect = { value ->
                                popupVisible = false
                                onValueChange(value)
                            },
                        )
                    }
                }
            }
        }
    }
}
