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

@Composable
fun <T> DropdownPreference(
    label: String,
    entries: List<Pair<T, String>>,
    currentValue: T,
    onValueChange: (T) -> Unit,
    description: String? = null,
) {
    // popupVisible controls whether the Popup node exists in the tree.
    // animVisible drives the AnimatedVisibility inside — starts false so
    // the enter animation always plays when the popup first appears.
    var popupVisible by remember { mutableStateOf(false) }
    var animVisible by remember { mutableStateOf(false) }

    val currentLabel = entries.firstOrNull { it.first == currentValue }?.second ?: ""

    // Snapshot the composition locals here — outside the Popup — so they can
    // be forwarded into the Popup's separate composition context. This is what
    // makes the font match the rest of the settings screen.
    val localTextStyle = LocalTextStyle.current
    val localContentColor = LocalContentColor.current
    val localDensity = LocalDensity.current
    val localLayoutDirection = LocalLayoutDirection.current
    val localFontFamilyResolver = LocalFontFamilyResolver.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                popupVisible = true
                // animVisible is set to true by LaunchedEffect after the
                // Popup is in the tree, guaranteeing the enter animation fires.
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(contentAlignment = Alignment.TopEnd) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (popupVisible) {
                // Once the Popup is in the tree, trigger the enter animation
                // on the very next frame so visible goes false→true.
                LaunchedEffect(Unit) { animVisible = true }

                Popup(
                    alignment = Alignment.TopEnd,
                    onDismissRequest = {
                        animVisible = false
                        // Delay removing the Popup until the exit animation
                        // finishes. The AnimatedVisibility exit is ~140ms so
                        // we keep popupVisible=true during that window via
                        // the onAnimationEnd callback below in AnimatedVisibility.
                    },
                    properties = PopupProperties(focusable = true),
                ) {
                    // Forward the parent composition locals into the Popup so
                    // fonts, density, and layout direction all match the host.
                    CompositionLocalProvider(
                        LocalTextStyle provides localTextStyle,
                        LocalContentColor provides localContentColor,
                        LocalDensity provides localDensity,
                        LocalLayoutDirection provides localLayoutDirection,
                        LocalFontFamilyResolver provides localFontFamilyResolver,
                    ) {
                        AnimatedVisibility(
                            visible = animVisible,
                            enter = scaleIn(
                                animationSpec = tween(durationMillis = 200),
                                transformOrigin = TransformOrigin(1f, 0f),
                                initialScale = 0.85f,
                            ) + fadeIn(tween(durationMillis = 170)),
                            exit = scaleOut(
                                animationSpec = tween(durationMillis = 150),
                                transformOrigin = TransformOrigin(1f, 0f),
                                targetScale = 0.85f,
                            ) + fadeOut(tween(durationMillis = 130)),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 240.dp)
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        clip = false,
                                    ),
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
                                        val pillBg = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        }
                                        val textColor = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(pillBg)
                                                .clickable {
                                                    animVisible = false
                                                    onValueChange(value)
                                                    // Remove popup after exit animation
                                                    popupVisible = false
                                                }
                                                .padding(
                                                    // Unselected items: no leading icon space,
                                                    // just a bit more start padding to align
                                                    // visually with the selected item's text.
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
                                                Spacer(modifier = Modifier.width(12.dp))
                                            }
                                            Text(
                                                text = entryLabel,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) {
                                                    FontWeight.Medium
                                                } else {
                                                    FontWeight.Normal
                                                },
                                                color = textColor,
                                                fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
