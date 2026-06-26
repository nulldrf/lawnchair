/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.theme.dividerColor
import app.lawnchair.ui.util.preview.PreferenceGroupPreviewContainer
import app.lawnchair.ui.util.preview.PreviewLawnchair
import app.lawnchair.preferences2.preferenceManager2
import com.android.launcher3.util.MSDLPlayerWrapper
import com.patrykmichalik.opto.core.firstBlocking
import com.google.android.msdl.data.model.MSDLToken

@Composable
fun SwitchPreference(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val checked = adapter.state.value
    SwitchPreference(
        checked = checked,
        onCheckedChange = adapter::onChange,
        label = label,
        modifier = modifier,
        description = description,
        onClick = onClick,
        enabled = enabled,
    )
}

/**
 * A Preference that provides a two-state toggleable option.
 */
@Composable
fun SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val prefs2 = preferenceManager2()

    val wrappedOnCheckedChange: (Boolean) -> Unit = { newValue ->
        if (prefs2.hapticFeedback.firstBlocking()) {
            mMSDLPlayerWrapper.playToken(if (newValue) MSDLToken.SWITCH_ON else MSDLToken.SWITCH_OFF)
        }
        onCheckedChange(newValue)
    }

    // Special case for SPEC_2025 schemes - especially "Vibrant"/"Expressive" style 
    val checkedThumbColor = MaterialTheme.colorScheme.onPrimary
    val uncheckedThumbColor = MaterialTheme.colorScheme.outline
    val switchColors = SwitchDefaults.colors(
        checkedIconColor = iconTintForBackground(checkedThumbColor),
        uncheckedIconColor = iconTintForBackground(uncheckedThumbColor),
    )

    PreferenceTemplate(
        modifier = modifier.clickable(
            enabled = enabled,
            indication = ripple(),
            interactionSource = interactionSource,
        ) {
            if (onClick != null) {
                onClick()
            } else {
                wrappedOnCheckedChange(!checked)
            }
        },
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        endWidget = {
            if (onClick != null) {
                Spacer(
                    modifier = Modifier
                        .height(32.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(dividerColor()),
                )
            }
            Switch(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .height(24.dp),
                checked = checked,
                onCheckedChange = wrappedOnCheckedChange,
                enabled = enabled,
                interactionSource = interactionSource,
                colors = switchColors,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
            )
        },
        enabled = enabled,
        applyPaddings = false,
    )
}

private fun iconTintForBackground(background: Color): Color {
    val luma = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luma > 0.5f) Color.Black else Color.White
}

@PreviewLawnchair
@Composable
private fun SwitchPreferencePreview(
    @PreviewParameter(SwitchPreferencePreviewParameterProvider::class) checked: Boolean,
) {
    LawnchairTheme {
        PreferenceGroupPreviewContainer {
            Item {
                SwitchPreference(
                    checked = checked,
                    onCheckedChange = {},
                    label = "Label",
                    description = "Description",
                )
            }
        }
    }
}

private class SwitchPreferencePreviewParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true, false)
}