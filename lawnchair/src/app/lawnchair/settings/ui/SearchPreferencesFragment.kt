package app.lawnchair.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.settings.ui.preference.IntSeekBarPreference
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class SearchPreferencesFragment : BaseSettingsFragment() {

    companion object {
        private const val ARG_IS_DOCK = "is_dock"

        fun newInstance(isDock: Boolean = false) = SearchPreferencesFragment().apply {
            arguments = android.os.Bundle().apply { putBoolean(ARG_IS_DOCK, isDock) }
        }
    }

    private val isDock get() = arguments?.getBoolean(ARG_IS_DOCK, false) ?: false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (isDock) {
            setPreferencesFromResource(R.xml.pref_search_dock, rootKey)
            bindDockPreferences()
        } else {
            setPreferencesFromResource(R.xml.pref_search_drawer, rootKey)
            bindDrawerPreferences()
        }
    }

    private fun bindDockPreferences() {
        // Search provider picker
        findPreference<Preference>("search_provider")?.apply {
            val provider = prefs2.hotseatQsbProvider.firstBlocking()
            summary = getString(provider.name)
            setOnPreferenceClickListener {
                navigateTo(SearchProviderPickerFragment(), getString(R.string.search_provider))
                true
            }
        }

        // Style: themed QSB
        findPreference<SwitchPreferenceCompat>("themed_hotseat_qsb")?.apply {
            isChecked = prefs2.themedHotseatQsb.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.themedHotseatQsb.set(newValue as Boolean) }
                true
            }
        }

        // Corner radius
        findPreference<app.lawnchair.settings.ui.preference.FloatSeekBarPreference>("qsb_corner_radius")?.apply {
            value = prefs.hotseatQsbCornerRadius.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.hotseatQsbCornerRadius.set(newValue as Float)
                true
            }
        }

        // Background opacity
        findPreference<IntSeekBarPreference>("qsb_alpha")?.apply {
            min = 0
            max = 100
            value = prefs.hotseatQsbAlpha.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.hotseatQsbAlpha.set(newValue as Int)
                true
            }
        }

        // Stroke width
        findPreference<app.lawnchair.settings.ui.preference.FloatSeekBarPreference>("qsb_stroke_width")?.apply {
            value = prefs.hotseatQsbStrokeWidth.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.hotseatQsbStrokeWidth.set(newValue as Float)
                true
            }
        }
    }

    private fun bindDrawerPreferences() {
        // Show search bar master switch
        findPreference<SwitchPreferenceCompat>("show_app_search_bar")?.apply {
            isChecked = !prefs2.hideAppDrawerSearchBar.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.hideAppDrawerSearchBar.set(!(newValue as Boolean)) }
                updateDrawerSearchVisibility(newValue as Boolean)
                true
            }
        }

        updateDrawerSearchVisibility(!prefs2.hideAppDrawerSearchBar.firstBlocking())

        // Auto show keyboard
        findPreference<SwitchPreferenceCompat>("auto_show_keyboard")?.apply {
            isChecked = prefs2.autoShowKeyboardInDrawer.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.autoShowKeyboardInDrawer.set(newValue as Boolean) }
                true
            }
        }

        // Search algorithm
        findPreference<ListPreference>("search_algorithm")?.apply {
            val current = prefs2.searchAlgorithm.firstBlocking()
            value = current.name
            val algorithms = listOf(
                LawnchairSearchAlgorithm.APP_SEARCH,
                LawnchairSearchAlgorithm.LOCAL_SEARCH,
                LawnchairSearchAlgorithm.ASI_SEARCH
            ).filter {
                it != LawnchairSearchAlgorithm.ASI_SEARCH ||
                    LawnchairSearchAlgorithm.isASISearchEnabled(requireContext())
            }
            entries = algorithms.map { getString(algorithmLabel(it)) }.toTypedArray()
            entryValues = algorithms.map { it.name }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val algo = LawnchairSearchAlgorithm.valueOf(newValue as String)
                lifecycleScope.launch { prefs2.searchAlgorithm.set(algo) }
                true
            }
        }

        // Match dock search bar style
        findPreference<SwitchPreferenceCompat>("match_hotseat_qsb_style")?.apply {
            isChecked = prefs2.matchHotseatQsbStyle.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.matchHotseatQsbStyle.set(newValue as Boolean) }
                true
            }
        }

        // Result toggles
        findPreference<SwitchPreferenceCompat>("search_result_apps")?.apply {
            isChecked = prefs.searchResultApps.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.searchResultApps.set(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("search_result_shortcuts")?.apply {
            isChecked = prefs.searchResultShortcuts.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.searchResultShortcuts.set(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("search_result_people")?.apply {
            val hasPermission = requireContext().checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            isEnabled = hasPermission
            isChecked = prefs.searchResultPeople.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.searchResultPeople.set(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("search_result_settings")?.apply {
            isChecked = prefs.searchResultSettingsEntry.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.searchResultSettingsEntry.set(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("search_result_calculator")?.apply {
            isChecked = prefs.searchResultCalculator.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.searchResultCalculator.set(newValue as Boolean)
                true
            }
        }

        // File search
        findPreference<Preference>("search_result_files")?.setOnPreferenceClickListener {
            navigateTo(FileSearchSettingsFragment(), getString(R.string.search_pref_result_files_title))
            true
        }

        // Web suggestions
        findPreference<SwitchPreferenceCompat>("search_result_web")?.apply {
            isChecked = prefs.searchResultStartPageSuggestion.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.searchResultStartPageSuggestion.set(newValue as Boolean)
                true
            }
        }

        // History
        findPreference<SwitchPreferenceCompat>("search_result_history")?.apply {
            isChecked = prefs.searchResulRecentSuggestion.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.searchResulRecentSuggestion.set(newValue as Boolean)
                true
            }
        }

        // Max result counts
        bindMaxResultCount("max_app_count", prefs2.maxAppSearchResultCount)
        bindMaxResultCount("max_people_count", prefs2.maxPeopleResultCount)
        bindMaxResultCount("max_file_count", prefs2.maxFileResultCount)
        bindMaxResultCount("max_settings_count", prefs2.maxSettingsEntryResultCount)
        bindMaxResultCount("max_history_count", prefs2.maxRecentResultCount)
    }

    private fun <T : com.patrykmichalik.opto.domain.Preference<Int, Int, androidx.datastore.preferences.core.Preferences.Key<Int>>> bindMaxResultCount(
        key: String,
        pref: T
    ) {
        findPreference<IntSeekBarPreference>(key)?.apply {
            min = 2
            max = 10
            value = pref.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { pref.set(newValue as Int) }
                true
            }
        }
    }

    private fun updateDrawerSearchVisibility(searchEnabled: Boolean) {
        listOf(
            "auto_show_keyboard", "search_algorithm", "match_hotseat_qsb_style",
            "pref_category_search_results", "search_result_apps", "search_result_shortcuts",
            "search_result_people", "search_result_settings", "search_result_calculator",
            "search_result_files", "search_result_web", "search_result_history",
            "pref_category_result_counts", "max_app_count", "max_people_count",
            "max_file_count", "max_settings_count", "max_history_count"
        ).forEach { key ->
            findPreference<Preference>(key)?.isVisible = searchEnabled
        }
    }

    private fun algorithmLabel(algo: LawnchairSearchAlgorithm): Int = when (algo) {
        LawnchairSearchAlgorithm.APP_SEARCH -> R.string.search_algorithm_app_search
        LawnchairSearchAlgorithm.LOCAL_SEARCH -> R.string.search_algorithm_global_search_on_device
        LawnchairSearchAlgorithm.ASI_SEARCH -> R.string.search_algorithm_global_search_via_asi
        else -> R.string.search_algorithm_app_search
    }
}