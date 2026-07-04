package app.lawnchair.ui.preferences.components.search

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.engine.provider.web.CustomWebSearchProvider
import app.lawnchair.ui.preferences.LocalNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import app.lawnchair.ui.preferences.components.layout.PreferenceScrollState
import app.lawnchair.ui.preferences.components.layout.ScrollAnchor
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.rememberPreferenceScrollState
import app.lawnchair.ui.preferences.components.HiddenAppsInSearchPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupScope
import app.lawnchair.ui.preferences.navigation.SearchProviderPreference
import app.lawnchair.util.FileAccessManager
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun DrawerSearchPreference(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current

    val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()

    val scrollState = rememberPreferenceScrollState()
    MainSwitchPreference(
        adapter = showDrawerSearchBar,
        label = stringResource(id = R.string.show_app_search_bar),
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(R.string.general_label)) {
            Item {
                ScrollAnchor(ScrollKeys.DS_SHOW_SEARCH_BAR, scrollState) {
                    HiddenAppsInSearchPreference()
                }
            }
            Item {
                ScrollAnchor(ScrollKeys.DS_AUTO_KEYBOARD, scrollState) {
                SwitchPreference(
                    adapter = prefs2.autoShowKeyboardInDrawer.getAdapter(),
                    label = stringResource(id = R.string.pref_search_auto_show_keyboard),
                )
                }
            }
            Item {
                ScrollAnchor(ScrollKeys.DS_ALGORITHM, scrollState) {
                SearchProvider(
                    context = context,
                )
                }
            }
            Item {
                ScrollAnchor(ScrollKeys.DS_MATCH_QSB, scrollState) {
                SwitchPreference(
                    label = stringResource(R.string.allapps_match_qsb_style_label),
                    description = stringResource(R.string.allapps_match_qsb_style_description),
                    adapter = prefs2.matchHotseatQsbStyle.getAdapter(),
                )
                }
            }
        }

        val searchAlgorithm = preferenceManager2().searchAlgorithm.getAdapter().state.value
        val navController = LocalNavController.current
        PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
            if (searchAlgorithm != LawnchairSearchAlgorithm.ASI_SEARCH) {
                val canDisable = searchAlgorithm != LawnchairSearchAlgorithm.APP_SEARCH
                val adapter = prefs.searchResultApps.getAdapter()

                Item {
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
            }
            when (searchAlgorithm) {
                LawnchairSearchAlgorithm.LOCAL_SEARCH -> {
                    LocalSearchSettings(
                        prefs = prefs,
                        prefs2 = prefs2,
                        context = context,
                        scrollState = scrollState,
                    )
                }

                LawnchairSearchAlgorithm.ASI_SEARCH -> {
                    ASISearchSettings(prefs, scrollState = scrollState)
                }
            }
        }
    }
}

@Composable
private fun PreferenceGroupScope.ASISearchSettings(
    prefs: PreferenceManager,
    scrollState: PreferenceScrollState,
) {
    Item {
        SwitchPreference(
            adapter = prefs.searchResultShortcuts.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_shortcuts_title),
        )
    }
    Item {
        ScrollAnchor(ScrollKeys.DS_PEOPLE, scrollState) {
        SwitchPreference(
            adapter = prefs.searchResultPeople.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_people_title),
        )
        }
    }
    Item {
        SwitchPreference(
            adapter = prefs.searchResultPixelTips.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_tips_title),
        )
    }
    Item {
        ScrollAnchor(ScrollKeys.DS_SETTINGS, scrollState) {
        SwitchPreference(
            adapter = prefs.searchResultSettings.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_settings_title),
        )
        }
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
private fun PreferenceGroupScope.LocalSearchSettings(
    prefs: PreferenceManager,
    prefs2: PreferenceManager2,
    context: Context,
    scrollState: PreferenceScrollState,
) {
    val navController = LocalNavController.current
    val webSuggestionProvider =
        stringResource(prefs2.webSuggestionProvider.getAdapter().state.value.label)

    Item {
        ScrollAnchor(ScrollKeys.DS_WEB, scrollState) {
        SearchProviderPreferenceItem(
            adapter = prefs.searchResultStartPageSuggestion.getAdapter(),
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
    }
    Item {
        ScrollAnchor(ScrollKeys.DS_PEOPLE, scrollState) {
        SearchProviderPreferenceItem(
            adapter = prefs.searchResultPeople.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_people_title),
            description = stringResource(id = R.string.search_pref_result_contacts_description),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.CONTACTS))
            },
            enabled = rememberPermissionState(android.Manifest.permission.READ_CONTACTS).status.isGranted,
        )
        }
    }
    Item {
        ScrollAnchor(ScrollKeys.DS_FILES, scrollState) {
        SearchProviderPreferenceItem(
            adapter = prefs.searchResultFilesToggle.getAdapter(),
            label = stringResource(R.string.search_pref_result_files_title),
            description = stringResource(R.string.search_pref_result_files_description),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.FILES))
            },
            enabled = remember { FileAccessManager.getInstance(context) }.hasAnyPermission.collectAsStateWithLifecycle().value,
        )
        }
    }
    Item {
        ScrollAnchor(ScrollKeys.DS_SETTINGS, scrollState) {
        SearchProviderPreferenceItem(
            adapter = prefs.searchResultSettingsEntry.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_settings_title),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.SETTINGS))
            },
        )
        }
    }
    Item {
        ScrollAnchor(ScrollKeys.DS_HISTORY, scrollState) {
        SearchProviderPreferenceItem(
            adapter = prefs.searchResulRecentSuggestion.getAdapter(),
            label = stringResource(id = R.string.search_pref_result_history_title),
            onClick = {
                navController.navigate(SearchProviderPreference(SearchProviderId.HISTORY))
            },
        )
        }
    }
    Item {
        ScrollAnchor(ScrollKeys.DS_CALCULATOR, scrollState) {
        SwitchPreference(
            adapter = prefs.searchResultCalculator.getAdapter(),
            label = stringResource(R.string.all_apps_search_result_calculator),
        )
        }
    }
}