package app.lawnchair.settings.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.hotseat.DisabledHotseat
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.settings.ui.preference.FloatSeekBarPreference
import app.lawnchair.settings.ui.preference.IntSeekBarPreference
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class DockPreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_dock, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        val isHotseatEnabled = prefs2.isHotseatEnabled.firstBlocking()

        // Master switch: show dock
        findPreference<SwitchPreferenceCompat>("show_hotseat")?.apply {
            isChecked = isHotseatEnabled
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.isHotseatEnabled.set(newValue as Boolean) }
                val enabled = newValue as Boolean
                updateDockPrefsVisibility(enabled)
                true
            }
        }

        updateDockPrefsVisibility(isHotseatEnabled)
        bindHotseatModeSection()
        bindStyleSection()
        bindGridSection()
        bindIconsSection()
    }

    private fun updateDockPrefsVisibility(enabled: Boolean) {
        val dockPrefKeys = listOf(
            "hotseat_mode", "search_bar_settings_dock",
            "hotseat_background", "hotseat_bg_settings",
            "pref_category_dock_grid", "dock_icons",
            "hotseat_bottom_space", "page_indicator_height",
            "pref_category_icons_dock", "show_labels_dock"
        )
        dockPrefKeys.forEach { key ->
            findPreference<Preference>(key)?.isVisible = enabled
        }
    }

    private fun bindHotseatModeSection() {
        findPreference<Preference>("hotseat_mode")?.apply {
            val currentMode = prefs2.hotseatMode.firstBlocking()
            summary = getString(currentMode.nameResourceId)
            setOnPreferenceClickListener {
                navigateTo(HotseatModePickerFragment(), getString(R.string.hotseat_mode_label))
                true
            }
        }

        findPreference<Preference>("search_bar_settings_dock")?.apply {
            val hotseatMode = prefs2.hotseatMode.firstBlocking()
            isVisible = hotseatMode != DisabledHotseat
            setOnPreferenceClickListener {
                navigateTo(SearchPreferencesFragment.newInstance(isDock = true), getString(R.string.search_bar_settings))
                true
            }
        }
    }

    private fun bindStyleSection() {
        val hasBg = prefs.hotseatBG.get()

        findPreference<SwitchPreferenceCompat>("hotseat_background")?.apply {
            isChecked = hasBg
            setOnPreferenceChangeListener { _, newValue ->
                prefs.hotseatBG.set(newValue as Boolean)
                findPreference<Preference>("hotseat_bg_settings")?.isVisible = newValue as Boolean
                true
            }
        }

        findPreference<Preference>("hotseat_bg_settings")?.apply {
            isVisible = hasBg
            setOnPreferenceClickListener {
                navigateTo(HotseatBackgroundSettingsFragment(), getString(R.string.hotseat_background))
                true
            }
        }
    }

    private fun bindGridSection() {
        findPreference<IntSeekBarPreference>("dock_icons")?.apply {
            min = 3
            max = 10
            value = prefs.hotseatColumns.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.hotseatColumns.set(newValue as Int)
                true
            }
        }

        findPreference<FloatSeekBarPreference>("hotseat_bottom_space")?.apply {
            value = prefs2.hotseatBottomFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.hotseatBottomFactor.set(newValue as Float) }
                true
            }
        }

        findPreference<FloatSeekBarPreference>("page_indicator_height")?.apply {
            value = prefs2.pageIndicatorHeightFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.pageIndicatorHeightFactor.set(newValue as Float) }
                true
            }
        }
    }

    private fun bindIconsSection() {
        findPreference<SwitchPreferenceCompat>("show_labels_dock")?.apply {
            isChecked = prefs2.enableLabelInDock.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.enableLabelInDock.set(newValue as Boolean) }
                true
            }
        }
    }
}