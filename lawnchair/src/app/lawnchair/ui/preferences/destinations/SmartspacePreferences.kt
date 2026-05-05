package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import app.lawnchair.smartspace.provider.weather.WeatherIconProvider
import app.lawnchair.smartspace.provider.weather.WeatherProvider
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.launcher3.R
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants

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

    PreferenceLayout(
        label = stringResource(id = R.string.smartspace_widget),
        backArrowVisible = !LocalIsExpandedScreen.current && !fromWidget,
        modifier = modifier,
    ) {
        if (fromWidget) {
            SmartspacePreview()
            LawnchairSmartspaceSettings(smartspaceProvider)
        } else {
            MainSwitchPreference(
                adapter = smartspaceAdapter,
                label = stringResource(R.string.smartspace_widget_toggle_label),
                description = stringResource(id = R.string.smartspace_widget_toggle_description).takeIf { modeIsLawnchair },
            ) {
                if (modeIsLawnchair) {
                    SmartspacePreview()
                }
                PreferenceGroup {
                    Item {
                        SmartspaceProviderPreference(adapter = smartspaceModeAdapter)
                    }
                }
                Crossfade(
                    targetState = selectedMode,
                    label = "Smartspace setting transition",
                ) { targetState ->
                    when (targetState) {
                        LawnchairSmartspace -> LawnchairSmartspaceSettings(smartspaceProvider)
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
) {
    Column(modifier = modifier) {
        // Custom weather section replaces the auto-generated toggle for WeatherDataProvider
        SmartspaceWeatherSettings()

        PreferenceGroup(
            heading = stringResource(id = R.string.what_to_show),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            smartspaceProvider.dataSources
                .asSequence()
                .filter { it.isAvailable }
                .filter { it !is WeatherDataProvider } // handled by SmartspaceWeatherSettings
                .forEach {
                    key(it.providerName) {
                        Item { _ ->
                            SwitchPreference(
                                adapter = it.enabledPref.getAdapter(),
                                label = stringResource(id = it.providerName),
                            )
                        }
                    }
                }
        }
        SmartspaceDateAndTimePreferences()
    }
}

@Composable
private fun SmartspaceWeatherSettings(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = preferenceManager2()

    val weatherProviderAdapter = prefs.smartspaceWeatherProvider.getAdapter()
    val selectedProvider = weatherProviderAdapter.state.value
    val iconPackAdapter = prefs.smartspaceWeatherIconPack.getAdapter()

    val weatherProviderEntries = remember {
        WeatherProvider.entries.map { provider ->
            ListPreferenceEntry(provider) { stringResource(id = provider.nameResId) }
        }
    }

    val iconPackEntries = remember {
        val installed = WeatherIconProvider.getInstalledPacks(context)
        listOf(ListPreferenceEntry("") { stringResource(R.string.smartspace_weather_icon_pack_none) }) +
            installed.map { (pkg, label) -> ListPreferenceEntry(pkg) { label } }
    }

    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_weather),
        modifier = modifier.padding(top = 8.dp),
    ) {
        Item {
            ListPreference(
                adapter = weatherProviderAdapter,
                entries = weatherProviderEntries,
                label = stringResource(id = R.string.smartspace_weather_source),
            )
        }

        // API key — only visible when PirateWeather is selected
        Item(visible = selectedProvider == WeatherProvider.PIRATE_WEATHER) {
            PirateWeatherApiKeyPreference(adapter = prefs.pirateWeatherApiKey.getAdapter())
        }

        // Icon pack — only visible when a real source is selected
        Item(visible = selectedProvider != WeatherProvider.NONE) {
            ListPreference(
                adapter = iconPackAdapter,
                entries = iconPackEntries,
                label = stringResource(id = R.string.smartspace_weather_icon_pack),
            )
        }
    }
}

@Composable
private fun PirateWeatherApiKeyPreference(
    adapter: PreferenceAdapter<String>,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf(adapter.state.value) }

    val currentKey = adapter.state.value
    val subtitle = if (currentKey.isBlank()) {
        stringResource(R.string.smartspace_pirate_weather_api_key_not_set)
    } else {
        "*".repeat(minOf(currentKey.length, 32))
    }

    // ClickablePreference with the masked key shown underneath via a wrapping Column
    Column(modifier = modifier) {
        ClickablePreference(
            label = "${stringResource(R.string.smartspace_pirate_weather_api_key)} — $subtitle",
            onClick = {
                inputValue = adapter.state.value
                showDialog = true
            },
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.smartspace_pirate_weather_api_key)) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(stringResource(R.string.smartspace_pirate_weather_api_key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    adapter.onChange(inputValue.trim())
                    showDialog = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun SmartspaceProviderPreference(
    adapter: PreferenceAdapter<SmartspaceMode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entries = remember {
        SmartspaceMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }.toList()
    }
    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_mode_label),
        modifier = modifier,
    )
}

@Composable
fun SmartspacePreview(
    modifier: Modifier = Modifier,
) {
    val themeRes = if (isSelectedThemeDark) R.style.AppTheme_Dark else R.style.AppTheme_DarkText
    val context = LocalContext.current
    val themedContext = remember(themeRes) { ContextThemeWrapper(context, themeRes) }

    PreferenceGroup(
        heading = stringResource(id = R.string.preview_label),
        modifier = modifier,
    ) {
        Item {
            CompositionLocalProvider(LocalContext provides themedContext) {
                AndroidView(
                    factory = {
                        val view = SmartspaceViewContainer(it, previewMode = true)
                        val height = it.resources
                            .getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
                        view.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, height)
                        view
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
            }
            LaunchedEffect(key1 = null) {
                SmartspaceProvider.INSTANCE.get(context).startSetup(context as Activity)
            }
        }
    }
}

@Composable
fun SmartspaceDateAndTimePreferences(
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()
    val calendarAdapter = preferenceManager2.smartspaceCalendar.getAdapter()
    val showDateAdapter = preferenceManager2.smartspaceShowDate.getAdapter()
    val showTimeAdapter = preferenceManager2.smartspaceShowTime.getAdapter()
    val calendarHasMinimumContent = !showDateAdapter.state.value || !showTimeAdapter.state.value
    val calendar = calendarAdapter.state.value

    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_date_and_time),
        modifier = modifier.padding(top = 8.dp),
    ) {
        val supportCustomizationFormat = calendar.formatCustomizationSupport
        Item(key = "smartspace_date", visible = supportCustomizationFormat) {
            SwitchPreference(
                adapter = showDateAdapter,
                label = stringResource(id = R.string.smartspace_date),
                enabled = if (showDateAdapter.state.value) !calendarHasMinimumContent else true,
            )
        }
        Item("smartspace_calendar", supportCustomizationFormat && showDateAdapter.state.value) {
            SmartspaceCalendarPreference()
        }
        Item("smartspace_time", supportCustomizationFormat) {
            SwitchPreference(
                adapter = showTimeAdapter,
                label = stringResource(id = R.string.smartspace_time),
                enabled = if (showTimeAdapter.state.value) !calendarHasMinimumContent else true,
            )
        }
        Item("smartspace_time_format", supportCustomizationFormat && showTimeAdapter.state.value) {
            SmartspaceTimeFormatPreference()
        }
    }
}

@Composable
fun SmartspaceTimeFormatPreference(modifier: Modifier = Modifier) {
    val entries = remember {
        SmartspaceTimeFormat.values().map { format ->
            ListPreferenceEntry(format) { stringResource(id = format.nameResourceId) }
        }
    }
    ListPreference(
        adapter = preferenceManager2().smartspaceTimeFormat.getAdapter(),
        entries = entries,
        label = stringResource(id = R.string.smartspace_time_format),
        modifier = modifier,
    )
}

@Composable
fun SmartspaceCalendarPreference(modifier: Modifier = Modifier) {
    val entries = remember {
        SmartspaceCalendar.values().map { calendar ->
            ListPreferenceEntry(calendar) { stringResource(id = calendar.nameResourceId) }
        }
    }
    ListPreference(
        adapter = preferenceManager2().smartspaceCalendar.getAdapter(),
        entries = entries,
        label = stringResource(id = R.string.smartspace_calendar),
        modifier = modifier,
    )
}

@Composable
fun SmartspacerSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    Column(modifier) {
        PreferenceGroup(heading = stringResource(id = R.string.smartspacer_settings)) {
            Item {
                SliderPreference(
                    label = stringResource(R.string.maximum_number_of_targets),
                    adapter = prefs2.smartspacerMaxCount.getAdapter(),
                    valueRange = 5..15,
                    step = 1,
                )
            }
            Item {
                ClickablePreference(label = stringResource(R.string.open_smartspacer_settings)) {
                    val intent = context.packageManager.getLaunchIntentForPackage(
                        SmartspacerConstants.SMARTSPACER_PACKAGE_NAME,
                    )
                    context.startActivity(intent)
                }
            }
        }
    }
}
