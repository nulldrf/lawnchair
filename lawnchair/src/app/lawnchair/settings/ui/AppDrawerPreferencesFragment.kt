package app.lawnchair.settings.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.settings.ui.preference.FloatSeekBarPreference
import app.lawnchair.settings.ui.preference.IntSeekBarPreference
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch
import com.android.launcher3.R

class AppDrawerPreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_app_drawer, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        bindLayoutSection()
        bindGeneralSection()
        bindStyleSection()
        bindGridSection()
        bindIconsSection()
        bindAdvancedSection()
    }

    private fun bindLayoutSection() {
        // Drawer layout: list vs caddy
        findPreference<SwitchPreferenceCompat>("drawer_list")?.apply {
            isChecked = prefs.drawerList.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.drawerList.set(newValue as Boolean)
                findPreference<Preference>("app_drawer_folder")?.isVisible = newValue as Boolean
                true
            }
        }

        findPreference<Preference>("app_drawer_folder")?.apply {
            isVisible = prefs.drawerList.get()
            setOnPreferenceClickListener {
                navigateTo(AppDrawerFoldersFragment(), getString(R.string.app_drawer_folder))
                true
            }
        }
    }

    private fun bindGeneralSection() {
        val hiddenApps = prefs2.hiddenApps.firstBlocking()

        findPreference<Preference>("hidden_apps")?.apply {
            summary = resources.getQuantityString(
                R.plurals.apps_count, hiddenApps.size, hiddenApps.size
            )
            setOnPreferenceClickListener {
                navigateTo(HiddenAppsFragment(), getString(R.string.hidden_apps_label))
                true
            }
        }

        findPreference<Preference>("drawer_search_settings")?.setOnPreferenceClickListener {
            navigateTo(SearchPreferencesFragment(), getString(R.string.search_bar_settings))
            true
        }

        findPreference<Preference>("suggestions")?.setOnPreferenceClickListener {
            navigateTo(SuggestionsFragment(), getString(R.string.suggestion_pref_screen_title))
            true
        }

        findPreference<SwitchPreferenceCompat>("app_drawer_haptic_feedback")?.apply {
            isChecked = prefs2.appDrawerHapticFeedback.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.appDrawerHapticFeedback.set(newValue as Boolean) }
                true
            }
        }
    }

    private fun bindStyleSection() {
        findPreference<Preference>("app_drawer_bg_color")?.apply {
            setOnPreferenceClickListener {
                navigateTo(
                    ColorSelectionFragment.newInstance("app_drawer_bg_color"),
                    getString(R.string.app_drawer_bg_color_label)
                )
                true
            }
        }

        findPreference<FloatSeekBarPreference>("drawer_opacity")?.apply {
            value = prefs.drawerOpacity.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.drawerOpacity.set(newValue as Float)
                true
            }
        }

        findPreference<Preference>("work_profile_tab_bg_color")?.apply {
            setOnPreferenceClickListener {
                navigateTo(
                    ColorSelectionFragment.newInstance("work_profile_tab_bg_color"),
                    getString(R.string.work_profile_tab_background_label)
                )
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("work_profile_tab_container_bg")?.apply {
            isChecked = prefs2.workProfileTabContainerBackground.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.workProfileTabContainerBackground.set(newValue as Boolean) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("app_drawer_search_bar_bg")?.apply {
            isChecked = prefs2.appDrawerSearchBarBackground.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.appDrawerSearchBarBackground.set(newValue as Boolean) }
                true
            }
        }
    }

    private fun bindGridSection() {
        findPreference<IntSeekBarPreference>("drawer_columns")?.apply {
            min = 3
            max = 10
            value = prefs2.drawerColumns.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.drawerColumns.set(newValue as Int) }
                true
            }
        }

        findPreference<FloatSeekBarPreference>("drawer_cell_height_factor")?.apply {
            value = prefs2.drawerCellHeightFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.drawerCellHeightFactor.set(newValue as Float) }
                true
            }
        }

        findPreference<FloatSeekBarPreference>("drawer_left_right_margin_factor")?.apply {
            value = prefs2.drawerLeftRightMarginFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.drawerLeftRightMarginFactor.set(newValue as Float) }
                true
            }
        }
    }

    private fun bindIconsSection() {
        findPreference<FloatSeekBarPreference>("drawer_icon_size_factor")?.apply {
            value = prefs2.drawerIconSizeFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.drawerIconSizeFactor.set(newValue as Float) }
                true
            }
        }

        val showLabelsPref = findPreference<SwitchPreferenceCompat>("show_icon_labels_drawer")
        showLabelsPref?.apply {
            isChecked = prefs2.showIconLabelsInDrawer.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showIconLabelsInDrawer.set(newValue as Boolean) }
                val show = newValue as Boolean
                findPreference<FloatSeekBarPreference>("drawer_icon_label_size_factor")?.isVisible = show
                findPreference<SwitchPreferenceCompat>("two_line_all_apps")?.isVisible = show
                true
            }
        }

        val labelsEnabled = prefs2.showIconLabelsInDrawer.firstBlocking()

        findPreference<FloatSeekBarPreference>("drawer_icon_label_size_factor")?.apply {
            isVisible = labelsEnabled
            value = prefs2.drawerIconLabelSizeFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.drawerIconLabelSizeFactor.set(newValue as Float) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("two_line_all_apps")?.apply {
            isVisible = labelsEnabled
            isChecked = prefs2.twoLineAllApps.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.twoLineAllApps.set(newValue as Boolean) }
                true
            }
        }
    }

    private fun bindAdvancedSection() {
        findPreference<SwitchPreferenceCompat>("remember_position")?.apply {
            isChecked = prefs2.rememberPosition.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.rememberPosition.set(newValue as Boolean) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("show_scrollbar")?.apply {
            isChecked = prefs2.showScrollbar.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showScrollbar.set(newValue as Boolean) }
                true
            }
        }
    }
}