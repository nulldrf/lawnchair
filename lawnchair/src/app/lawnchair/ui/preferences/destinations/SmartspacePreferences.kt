/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.smartspace.SmartspaceViewContainer
import app.lawnchair.smartspace.model.LawnchairSmartspace
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceMode
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.smartspace.model.Smartspacer
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.smartspace.provider.WeatherDataProvider
import app.lawnchair.smartspace.provider.weather.TemperatureUnit
import app.lawnchair.smartspace.provider.weather.WeatherIconProvider
import app.lawnchair.smartspace.provider.weather.WeatherProvider
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceScrollState
import app.lawnchair.ui.preferences.components.layout.ScrollAnchor
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.rememberPreferenceScrollState
import app.lawnchair.ui.theme.isSelectedThemeDark
import app.lawnchair.ui.theme.preferenceGroupColor
import com.android.launcher3.R
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants
import kotlinx.coroutines.launch

private const val ICON_PACK_REPO_URL =
    "https://github.com/breezy-weather/breezy-weather-icon-packs/blob/main/README.md"

// Maps a data source's providerName resource id to its stable ScrollKey, so
// the dashboard search overlay can auto-scroll/highlight the exact switch —
// even though the "what to show" list is otherwise a fully dynamic loop over
// smartspaceProvider.dataSources with no per-item structure of its own.
private fun scrollKeyForProvider(providerNameRes: Int): String? = when (providerNameRes) {
    R.string.smartspace_battery_status -> ScrollKeys.SS_BATTERY_STATUS
    R.string.smartspace_torch -> ScrollKeys.SS_FLASHLIGHT
    R.string.smartspace_now_playing -> ScrollKeys.SS_NOW_PLAYING
    R.string.smartspace_onboarding -> ScrollKeys.SS_ONBOARDING
    R.string.smartspace_personality -> ScrollKeys.SS_PERSONALITY
    else -> null
}

@Composable
fun SmartspacePreferences(
    fromWidget: Boolean,
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()
    val smartspaceProvider = SmartspaceProvider.INSTANCE.get(LocalContext.current)
    val smartspaceAdapter = preferenceManager2.enableSmartspace.getAdapter()
    val smartspaceModeAdapter = preferenceManager2.smartspaceMode.getAdapter()
    val selectedMode = smartspaceModeAdapter.state.value
    val modeIsLawnchair = selectedMode == LawnchairSmartspace

    val scrollState = rememberPreferenceScrollState()
    PreferenceLayout(
        label = stringResource(id = R.string.smartspace_widget),
        backArrowVisible = !LocalIsExpandedScreen.current && !fromWidget,
        modifier = modifier,
    ) {
        if (fromWidget) {
            SmartspacePreview()
            LawnchairSmartspaceSettings(smartspaceProvider, scrollState = scrollState)
        } else {
            MainSwitchPreference(
                adapter = smartspaceAdapter,
                label = stringResource(R.string.smartspace_widget_toggle_label),
                description = stringResource(id = R.string.smartspace_widget_toggle_description).takeIf { modeIsLawnchair },
            ) {
                if (modeIsLawnchair) SmartspacePreview()
                PreferenceGroup {
                    ScrollAnchor(ScrollKeys.SS_MODE, scrollState) {
                        SmartspaceProviderPreference(adapter = smartspaceModeAdapter)
                    }
                }
                Crossfade(targetState = selectedMode, label = "Smartspace setting transition") { targetState ->
                    when (targetState) {
                        LawnchairSmartspace -> LawnchairSmartspaceSettings(smartspaceProvider, scrollState = scrollState)
                        Smartspacer -> SmartspacerSettings()
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun LawnchairSmartspaceSettings(
    smartspaceProvider: SmartspaceProvider,
    modifier: Modifier = Modifier,
    scrollState: PreferenceScrollState = rememberPreferenceScrollState(),
) {
    Column(modifier = modifier) {
        SmartspaceWeatherSettings(smartspaceProvider, scrollState = scrollState)
        PreferenceGroup(
            heading = stringResource(id = R.string.what_to_show),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            smartspaceProvider.dataSources
                .asSequence()
                .filter { it.isAvailable }
                .filter { it !is WeatherDataProvider }
                .forEach { dataSource ->
                    key(dataSource.providerName) {
                        val itemScrollKey = scrollKeyForProvider(dataSource.providerName)
                        if (itemScrollKey != null) {
                            ScrollAnchor(itemScrollKey, scrollState) {
                                SwitchPreference(
                                    adapter = dataSource.enabledPref.getAdapter(),
                                    label = stringResource(id = dataSource.providerName),
                                )
                            }
                        } else {
                            SwitchPreference(
                                adapter = dataSource.enabledPref.getAdapter(),
                                label = stringResource(id = dataSource.providerName),
                            )
                        }
                    }
                }
        }
        SmartspaceDateAndTimePreferences(scrollState = scrollState)
    }
}

@Composable
private fun SmartspaceWeatherSettings(
    smartspaceProvider: SmartspaceProvider,
    modifier: Modifier = Modifier,
    scrollState: PreferenceScrollState = rememberPreferenceScrollState(),
) {
    val context = LocalContext.current
    val prefs = preferenceManager2()
    val scope = rememberCoroutineScope()

    val weatherProviderAdapter = prefs.smartspaceWeatherProvider.getAdapter()
    val selectedProvider = weatherProviderAdapter.state.value
    val hasSource = selectedProvider != WeatherProvider.NONE

    val installedPacks = remember { WeatherIconProvider.getInstalledPacks(context) }

    val weatherProviderEntries = remember {
        WeatherProvider.entries.map { provider ->
            ListPreferenceEntry(provider) { stringResource(id = provider.nameResId) }
        }
    }

    val unitEntries = remember {
        TemperatureUnit.entries.map { unit ->
            ListPreferenceEntry(unit) { stringResource(id = unit.nameResId) }
        }
    }

    val intervalEntries = remember {
        WeatherDataProvider.REFRESH_INTERVAL_OPTIONS.map { minutes ->
            ListPreferenceEntry(minutes) {
                when (minutes) {
                    15L -> stringResource(R.string.smartspace_weather_interval_15)
                    30L -> stringResource(R.string.smartspace_weather_interval_30)
                    60L -> stringResource(R.string.smartspace_weather_interval_60)
                    else -> stringResource(R.string.smartspace_weather_interval_180)
                }
            }
        }
    }

    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_weather),
        modifier = modifier.padding(top = 8.dp),
    ) {
        ScrollAnchor(ScrollKeys.SS_WEATHER_SOURCE, scrollState) {
            ListPreference(
                adapter = weatherProviderAdapter,
                entries = weatherProviderEntries,
                label = stringResource(id = R.string.smartspace_weather_source),
            )
        }

        // API keys — shown per-provider
        ExpandAndShrink(visible = selectedProvider == WeatherProvider.PIRATE_WEATHER) {
            ScrollAnchor(ScrollKeys.SS_API_KEY, scrollState) {
                ApiKeyPreference(
                    adapter = prefs.pirateWeatherApiKey.getAdapter(),
                    label = stringResource(R.string.smartspace_pirate_weather_api_key),
                    hint = stringResource(R.string.smartspace_pirate_weather_api_key_hint),
                )
            }
        }
        ExpandAndShrink(visible = selectedProvider == WeatherProvider.OPEN_WEATHER_MAP) {
            ScrollAnchor(ScrollKeys.SS_API_KEY, scrollState) {
                ApiKeyPreference(
                    adapter = prefs.openWeatherMapApiKey.getAdapter(),
                    label = stringResource(R.string.smartspace_owm_api_key),
                    hint = stringResource(R.string.smartspace_owm_api_key_hint),
                )
            }
        }
        ExpandAndShrink(visible = selectedProvider == WeatherProvider.ACCU_WEATHER) {
            ScrollAnchor(ScrollKeys.SS_API_KEY, scrollState) {
                ApiKeyPreference(
                    adapter = prefs.accuWeatherApiKey.getAdapter(),
                    label = stringResource(R.string.smartspace_accu_api_key),
                    hint = stringResource(R.string.smartspace_accu_api_key_hint),
                )
            }
        }

        // City — shown for all providers when source is active
        ExpandAndShrink(visible = hasSource) {
            ScrollAnchor(ScrollKeys.SS_WEATHER_CITY, scrollState) {
                CityPreference(adapter = prefs.smartspaceWeatherCity.getAdapter())
            }
        }

        // Temperature unit
        ExpandAndShrink(visible = hasSource) {
            ScrollAnchor(ScrollKeys.SS_WEATHER_UNIT, scrollState) {
                ListPreference(
                    adapter = prefs.smartspaceWeatherUnit.getAdapter(),
                    entries = unitEntries,
                    label = stringResource(R.string.smartspace_weather_unit),
                )
            }
        }

        // Icon pack
        ExpandAndShrink(visible = hasSource) {
            ScrollAnchor(ScrollKeys.SS_WEATHER_ICON_PACK, scrollState) {
                IconPackPreference(
                    adapter = prefs.smartspaceWeatherIconPack.getAdapter(),
                    installedPacks = installedPacks,
                )
            }
        }

        // Refresh interval
        ExpandAndShrink(visible = hasSource) {
            ScrollAnchor(ScrollKeys.SS_WEATHER_INTERVAL, scrollState) {
                ListPreference(
                    adapter = prefs.smartspaceWeatherRefreshInterval.getAdapter(),
                    entries = intervalEntries,
                    label = stringResource(id = R.string.smartspace_weather_refresh_interval),
                )
            }
        }

        // Manual refresh
        ExpandAndShrink(visible = hasSource) {
            ScrollAnchor(ScrollKeys.SS_WEATHER_REFRESH_NOW, scrollState) {
                ClickablePreference(
                    label = stringResource(R.string.smartspace_weather_refresh_now),
                    onClick = {
                        scope.launch {
                            smartspaceProvider.dataSources
                                .filterIsInstance<WeatherDataProvider>()
                                .firstOrNull()
                                ?.onSetupDone()
                        }
                    },
                )
            }
        }
    }
}

/** Generic masked API key preference with inline dialog */
@Composable
private fun ApiKeyPreference(
    adapter: PreferenceAdapter<String>,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf(adapter.state.value) }
    val currentKey = adapter.state.value
    val masked = if (currentKey.isBlank()) {
        stringResource(R.string.smartspace_api_key_not_set)
    } else "*".repeat(minOf(currentKey.length, 32))

    Column(modifier = modifier) {
        ClickablePreference(label = "$label — $masked", onClick = { inputValue = adapter.state.value; showDialog = true })
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { adapter.onChange(inputValue.trim()); showDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** City input preference */
@Composable
private fun CityPreference(
    adapter: PreferenceAdapter<String>,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf(adapter.state.value) }
    val currentCity = adapter.state.value
    val subtitle = if (currentCity.isBlank()) {
        stringResource(R.string.smartspace_weather_city_auto)
    } else currentCity

    Column(modifier = modifier) {
        ClickablePreference(
            label = stringResource(R.string.smartspace_weather_city),
            subtitle = subtitle,
            onClick = { inputValue = adapter.state.value; showDialog = true },
        )
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.smartspace_weather_city)) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(stringResource(R.string.smartspace_weather_city_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { adapter.onChange(inputValue.trim()); showDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** Icon pack picker with radio buttons and Get More link */
@Composable
private fun IconPackPreference(
    adapter: PreferenceAdapter<String>,
    installedPacks: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val currentPack = adapter.state.value
    val entries = remember(installedPacks) {
        listOf("" to context.getString(R.string.smartspace_weather_icon_pack_none)) + installedPacks
    }
    val currentLabel = entries.firstOrNull { it.first == currentPack }?.second
        ?: context.getString(R.string.smartspace_weather_icon_pack_none)

    ClickablePreference(
        label = stringResource(R.string.smartspace_weather_icon_pack),
        subtitle = currentLabel,
        modifier = modifier,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.smartspace_weather_icon_pack)) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    entries.forEach { (pkg, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = pkg == currentPack,
                                    role = Role.RadioButton,
                                    onClick = { adapter.onChange(pkg); showDialog = false },
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = pkg == currentPack, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ICON_PACK_REPO_URL))) }) {
                    Text(stringResource(R.string.smartspace_weather_icon_pack_get_more))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
fun SmartspaceProviderPreference(adapter: PreferenceAdapter<SmartspaceMode>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries = remember {
        SmartspaceMode.values().map { mode ->
            ListPreferenceEntry(value = mode, label = { stringResource(id = mode.nameResourceId) }, enabled = mode.isAvailable(context = context))
        }.toList()
    }
    ListPreference(adapter = adapter, entries = entries, label = stringResource(id = R.string.smartspace_mode_label), modifier = modifier)
}

@Composable
fun SmartspacePreview(modifier: Modifier = Modifier) {
    val themeRes = if (isSelectedThemeDark) R.style.AppTheme_Dark else R.style.AppTheme_DarkText
    val context = LocalContext.current
    val themedContext = remember(themeRes) { ContextThemeWrapper(context, themeRes) }
    PreferenceGroup(heading = stringResource(id = R.string.preview_label), modifier = modifier) {
        Surface(
            color = preferenceGroupColor(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalContext provides themedContext) {
                AndroidView(
                    factory = {
                        val view = SmartspaceViewContainer(it, previewMode = true)
                        val height = it.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
                        view.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, height)
                        view
                    },
                    modifier = Modifier.padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                )
            }
        }
        LaunchedEffect(key1 = null) {
            SmartspaceProvider.INSTANCE.get(context).startSetup(context as Activity)
        }
    }
}

@Composable
fun SmartspaceDateAndTimePreferences(
    modifier: Modifier = Modifier,
    scrollState: PreferenceScrollState = rememberPreferenceScrollState(),
) {
    val preferenceManager2 = preferenceManager2()
    val calendarAdapter = preferenceManager2.smartspaceCalendar.getAdapter()
    val showDateAdapter = preferenceManager2.smartspaceShowDate.getAdapter()
    val showTimeAdapter = preferenceManager2.smartspaceShowTime.getAdapter()
    val calendarHasMinimumContent = !showDateAdapter.state.value || !showTimeAdapter.state.value
    val calendar = calendarAdapter.state.value
    val supportCustomizationFormat = calendar.formatCustomizationSupport

    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_date_and_time),
        modifier = modifier.padding(top = 8.dp),
    ) {
        ExpandAndShrink(visible = supportCustomizationFormat) {
            ScrollAnchor(ScrollKeys.SS_DATE, scrollState) {
                SwitchPreference(
                    adapter = showDateAdapter,
                    label = stringResource(id = R.string.smartspace_date),
                    enabled = if (showDateAdapter.state.value) !calendarHasMinimumContent else true,
                )
            }
        }
        ExpandAndShrink(visible = supportCustomizationFormat && showDateAdapter.state.value) {
            ScrollAnchor(ScrollKeys.SS_CALENDAR, scrollState) {
                SmartspaceCalendarPreference()
            }
        }
        ExpandAndShrink(visible = supportCustomizationFormat) {
            ScrollAnchor(ScrollKeys.SS_TIME, scrollState) {
                SwitchPreference(
                    adapter = showTimeAdapter,
                    label = stringResource(id = R.string.smartspace_time),
                    enabled = if (showTimeAdapter.state.value) !calendarHasMinimumContent else true,
                )
            }
        }
        ExpandAndShrink(visible = supportCustomizationFormat && showTimeAdapter.state.value) {
            ScrollAnchor(ScrollKeys.SS_TIME_FORMAT, scrollState) {
                SmartspaceTimeFormatPreference()
            }
        }
    }
}

@Composable
fun SmartspaceTimeFormatPreference(modifier: Modifier = Modifier) {
    val entries = remember { SmartspaceTimeFormat.values().map { ListPreferenceEntry(it) { stringResource(id = it.nameResourceId) } } }
    ListPreference(adapter = preferenceManager2().smartspaceTimeFormat.getAdapter(), entries = entries, label = stringResource(id = R.string.smartspace_time_format), modifier = modifier)
}

@Composable
fun SmartspaceCalendarPreference(modifier: Modifier = Modifier) {
    val entries = remember { SmartspaceCalendar.values().map { ListPreferenceEntry(it) { stringResource(id = it.nameResourceId) } } }
    ListPreference(adapter = preferenceManager2().smartspaceCalendar.getAdapter(), entries = entries, label = stringResource(id = R.string.smartspace_calendar), modifier = modifier)
}

@Composable
fun SmartspacerSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    Column(modifier) {
        PreferenceGroup(
            heading = stringResource(id = R.string.smartspacer_settings),
        ) {
            SliderPreference(
                label = stringResource(R.string.maximum_number_of_targets),
                adapter = prefs2.smartspacerMaxCount.getAdapter(),
                valueRange = 5..15,
                step = 1,
            )
            ClickablePreference(label = stringResource(R.string.open_smartspacer_settings)) {
                val intent = context.packageManager.getLaunchIntentForPackage(
                    SmartspacerConstants.SMARTSPACER_PACKAGE_NAME,
                )
                context.startActivity(intent)
            }
        }
    }
}