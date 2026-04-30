/*
 * Copyright 2024, Lawnchair
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

package app.lawnchair.ui.preferences.components

import android.R as AndroidR
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.util.LocalBottomSheetHandler
import app.lawnchair.views.overlay.AppOpenAnimationType
import app.lawnchair.views.overlay.FullScreenOverlayMode
import kotlinx.coroutines.launch

// ── App-close overlay options (GNC path) ─────────────────────────────────────
val overlayOptions = listOf(
    FullScreenOverlayMode.NONE,
    FullScreenOverlayMode.SUCK_IN,
    FullScreenOverlayMode.FADE_IN,
)

/**
 * Preference item for the GNC / app-close overlay animation.
 * Controls what happens when the user gesture-navigates back from an app
 * and the launcher receives a GestureNavContract.
 */
@Composable
fun OverlayHandlerPreference(
    adapter: PreferenceAdapter<FullScreenOverlayMode>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val currentConfig = adapter.state.value

    fun onSelect(option: FullScreenOverlayMode) {
        scope.launch { adapter.onChange(option) }
    }

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { Text(text = stringResource(currentConfig.labelRes)) },
        modifier = modifier.clickable {
            bottomSheetHandler.show {
                ModalBottomSheetContent(
                    title = { Text(label) },
                    buttons = {
                        OutlinedButton(onClick = { bottomSheetHandler.hide() }) {
                            Text(text = stringResource(id = AndroidR.string.cancel))
                        }
                    },
                ) {
                    LazyColumn {
                        itemsIndexed(overlayOptions) { index, option ->
                            if (index > 0) {
                                PreferenceDivider(startIndent = 40.dp)
                            }
                            val selected = currentConfig == option
                            PreferenceTemplate(
                                title = { Text(text = stringResource(option.labelRes)) },
                                modifier = Modifier.clickable {
                                    bottomSheetHandler.hide()
                                    onSelect(option)
                                },
                                startWidget = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

// ── App-open animation options ────────────────────────────────────────────────

/**
 * All app-open animation types exposed to the user.
 * Backported from Lawnchair 2 (ch.deletescape.lawnchair.animations.AnimationType).
 */
val appOpenAnimationOptions = listOf(
    AppOpenAnimationType.DEFAULT,
    AppOpenAnimationType.PIE,
    AppOpenAnimationType.REVEAL,
    AppOpenAnimationType.SLIDE_UP,
    AppOpenAnimationType.SCALE_UP,
    AppOpenAnimationType.BLINK,
    AppOpenAnimationType.FADE,
)

/**
 * Preference item for the app-open (launch) animation type.
 *
 * Each mode maps to a different [ActivityOptions] strategy in [LawnchairLauncher]:
 *  - DEFAULT   → system clip-reveal from icon
 *  - PIE       → Android 9 Pie-style scale+fade (launcher exits by zooming out, app zooms in)
 *  - REVEAL    → circular clip-reveal expanding from icon bounds
 *  - SLIDE_UP  → app slides in from bottom (task_open_enter anim)
 *  - SCALE_UP  → app scales up from icon position
 *  - BLINK     → quick blink flash before app appears
 *  - FADE      → simple cross-fade
 */
@Composable
fun AppOpenAnimationPreference(
    adapter: PreferenceAdapter<AppOpenAnimationType>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val currentConfig = adapter.state.value

    fun onSelect(option: AppOpenAnimationType) {
        scope.launch { adapter.onChange(option) }
    }

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { Text(text = stringResource(currentConfig.labelRes)) },
        modifier = modifier.clickable {
            bottomSheetHandler.show {
                ModalBottomSheetContent(
                    title = { Text(label) },
                    buttons = {
                        OutlinedButton(onClick = { bottomSheetHandler.hide() }) {
                            Text(text = stringResource(id = AndroidR.string.cancel))
                        }
                    },
                ) {
                    LazyColumn {
                        itemsIndexed(appOpenAnimationOptions) { index, option ->
                            if (index > 0) {
                                PreferenceDivider(startIndent = 40.dp)
                            }
                            val selected = currentConfig == option
                            PreferenceTemplate(
                                title = { Text(text = stringResource(option.labelRes)) },
                                modifier = Modifier.clickable {
                                    bottomSheetHandler.hide()
                                    onSelect(option)
                                },
                                startWidget = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}
