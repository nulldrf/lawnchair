package app.lawnchair.ui.preferences.components.search

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.engine.provider.web.CustomWebSearchProvider
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.HiddenAppsInSearchPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.TwoTargetSwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceScrollState
import app.lawnchair.ui.preferences.components.layout.ScrollAnchor
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.rememberPreferenceScrollState
import app.lawnchair.ui.preferences.navigation.SearchProviderPreference
import app.lawnchair.util.FileAccessManager
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun DrawerSearchPreference(
    modifier: Modifier = Modifier,
    scrollState: PreferenceScrollState = rememberPreferenceScrollState(),
) {
    val prefs2 = preferenceManager2()
    val context = LocalContext.current

    val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()
    val hiddenApps = prefs2.hiddenApps.getAdapter().state.value

    MainSwitchPreference(
        adapter = showDrawerSearchBar,
        label = stringResource(id = R.string.show_app_search_bar),
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(R.string.general_label)) {
            if (hiddenApps.isNotEmpty()) {
                ScrollAnchor(ScrollKeys.DS_HIDDEN_APPS, scrollState) {
                    HiddenAppsInSearchPreference()
                }
            }
            ScrollAnchor(ScrollKeys.DS_AUTO_KEYBOARD, scrollState) {
                SwitchPreference(
                    adapter = prefs2.autoShowKeyboardInDrawer.getAdapter(),
                    label = stringResource(id = R.string.pref_search_auto_show_keyboard),
                )
            }
            ScrollAnchor(ScrollKeys.DS_ALGORITHM, scrollState) {
                SearchProvider(
                    context = context,
                )
            }
            ScrollAnchor(ScrollKeys.DS_MATCH_QSB, scrollState) {
                SwitchPreference(
                    label = stringResource(R.string.allapps_match_qsb_style_label),
                    description = stringResource(R.string.allapps_match_qsb_style_description),
                    adapter = prefs2.matchHotseatQsbStyle.getAdapter(),
                )
            }
        }

        val searchAlgorithm = preferenceManager2().searchAlgorithm.getAdapter().state.value
        val navController = LocalNavController.current
        PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
            if (searchAlgorithm != LawnchairSearchAlgorithm.ASI_SEARCH) {
                val canDisable = searchAlgorithm != LawnchairSearchAlgorithm.APP_SEARCH
                val adapter = prefs2.searchResultApps.getAdapter()

                ScrollAnchor(ScrollKeys.DS_APPS_SHORTCUTS, scrollState) {
                    TwoTargetSwitchPreference(
                        checked = if (canDisable) adapter.state.value else true,
                        onCheckedChange = if (canDisable) adapter::onChange else ({}),
                        enabled = canDisable,
                        label = stringResource(R.string.search_pref_result_apps_and_shortcuts_title),
                        onClick = {
                            navController.navigate(SearchProviderPreference(SearchProviderId.APPS))
                        },
                    )
                }
            }
            when (searchAlgorithm) {
                LawnchairSearchAlgorithm.LOCAL_SEARCH -> {
                    LocalSearchSettings(
                        prefs2 = prefs2,
                        context = context,
                        scrollState = scrollState,
                    )
                }

                LawnchairSearchAlgorithm.ASI_SEARCH -> {
                    ASISearchSettings(prefs2, scrollState)
                }
            }
        }
    }
}

@Composable
private fun ASISearchSettings(
    prefs: PreferenceManager2,
    scrollState: PreferenceScrollState,
) {
    ScrollAnchor(ScrollKeys.DS_SHORTCUTS, scrollState) {
        SwitchPreference(
            adapter = prefs.searchResultShortcuts.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_shortcuts_title),
        )
    }
    ScrollAnchor(ScrollKeys.DS_PEOPLE, scrollState) {
        SwitchPreference(
            adapter = prefs.searchResultPeople.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_people_title),
        )
    }
    ScrollAnchor(ScrollKeys.DS_TIPS, scrollState) {
        SwitchPreference(
            adapter = prefs.searchResultPixelTips.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_tips_title),
        )
    }
    ScrollAnchor(ScrollKeys.DS_SETTINGS, scrollState) {
        SwitchPreference(
            adapter = prefs.searchResultSettings.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_settings_title),
        )
    }
}

@Composable
private fun SearchProvider(
    context: Context,
) {
    val searchAlgorithmEntries = remember {
        sequenceOf(
            ListPreferenceEntry(LawnchairSearchAlgorithm.APP_SEARCH) { stringResource(R.string.search_algorithm_app_search) },
            ListPreferenceEntry(LawnchairSearchAlgorithm.LOCAL_SEARCH) { stringResource(R.string.search_algorithm_global_search_on_device) },
            ListPreferenceEntry(LawnchairSearchAlgorithm.ASI_SEARCH) { stringResource(R.string.search_algorithm_global_search_via_asi) },
        ).filter {
            when (it.value) {
                LawnchairSearchAlgorithm.ASI_SEARCH -> LawnchairSearchAlgorithm.isASISearchEnabled(
                    context,
                )

                else -> true
            }
        }.toList()
    }

    ListPreference(
        adapter = preferenceManager2().searchAlgorithm.getAdapter(),
        entries = searchAlgorithmEntries,
        label = stringResource(R.string.app_search_algorithm),
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocalSearchSettings(
    prefs2: PreferenceManager2,
    context: Context,
    scrollState: PreferenceScrollState,
) {
    val navController = LocalNavController.current
    val webSuggestionProvider =
        stringResource(prefs2.webSuggestionProvider.getAdapter().state.value.label)

    ScrollAnchor(ScrollKeys.DS_WEB, scrollState) {
        TwoTargetSwitchPreference(
            adapter = prefs2.searchResultStartPageSuggestion.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_web_title),
            description = if (webSuggestionProvider == stringResource(CustomWebSearchProvider.label)) {
                webSuggestionProvider
            } else {
                stringResource(
                    id = R.string.search_pref_result_web_provider_description,
                    webSuggestionProvider,
                )
            },
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.WEB))
            },
        )
    }
    val peopleAdapter = prefs2.searchResultPeople.getAdapter()
    val peopleEnabled = rememberPermissionState(android.Manifest.permission.READ_CONTACTS).status.isGranted
    ScrollAnchor(ScrollKeys.DS_PEOPLE, scrollState) {
        TwoTargetSwitchPreference(
            checked = peopleEnabled && peopleAdapter.state.value,
            onCheckedChange = peopleAdapter::onChange,
            switchEnabled = peopleEnabled,
            label = stringResource(id = R.string.search_pref_result_people_title),
            description = stringResource(id = R.string.search_pref_result_contacts_description),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.CONTACTS))
            },
        )
    }
    val filesAdapter = prefs2.searchResultFilesToggle.getAdapter()
    val filesEnabled = remember { FileAccessManager.getInstance(context) }.hasAnyPermission.collectAsStateWithLifecycle().value
    ScrollAnchor(ScrollKeys.DS_FILES, scrollState) {
        TwoTargetSwitchPreference(
            checked = filesEnabled && filesAdapter.state.value,
            onCheckedChange = filesAdapter::onChange,
            switchEnabled = filesEnabled,
            label = stringResource(R.string.search_pref_result_files_title),
            description = stringResource(R.string.search_pref_result_files_description),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.FILES))
            },
        )
    }
    ScrollAnchor(ScrollKeys.DS_SETTINGS, scrollState) {
        TwoTargetSwitchPreference(
            adapter = prefs2.searchResultSettingsEntry.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_settings_title),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.SETTINGS))
            },
        )
    }
    ScrollAnchor(ScrollKeys.DS_HISTORY, scrollState) {
        TwoTargetSwitchPreference(
            adapter = prefs2.searchResulRecentSuggestion.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_history_title),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.HISTORY))
            },
        )
    }
    ScrollAnchor(ScrollKeys.DS_CALCULATOR, scrollState) {
        SwitchPreference(
            adapter = prefs2.searchResultCalculator.getAdapter(),
            label = stringResource(R.string.all_apps_search_result_calculator),
        )
    }
}
