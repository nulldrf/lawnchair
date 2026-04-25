package app.lawnchair.settings.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.settings.ui.preference.FloatSeekBarPreference
import app.lawnchair.settings.ui.preference.IntSeekBarPreference
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class FolderPreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_folders, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        // Folder color
        findPreference<Preference>("folder_color")?.setOnPreferenceClickListener {
            navigateTo(
                ColorSelectionFragment.newInstance("folder_color"),
                getString(R.string.folder_preview_bg_color_label)
            )
            true
        }

        // Folder preview background opacity
        findPreference<FloatSeekBarPreference>("folder_preview_bg_opacity")?.apply {
            value = prefs2.folderPreviewBackgroundOpacity.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.folderPreviewBackgroundOpacity.set(newValue as Float) }
                true
            }
        }

        // Folder background opacity
        findPreference<FloatSeekBarPreference>("folder_bg_opacity")?.apply {
            value = prefs2.folderBackgroundOpacity.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.folderBackgroundOpacity.set(newValue as Float) }
                true
            }
        }

        // Max folder columns
        findPreference<IntSeekBarPreference>("max_folder_columns")?.apply {
            min = 2
            max = 5
            value = prefs2.folderColumns.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.folderColumns.set(newValue as Int) }
                true
            }
        }

        // Max folder rows
        findPreference<IntSeekBarPreference>("max_folder_rows")?.apply {
            min = 2
            max = 5
            value = prefs.folderRows.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.folderRows.set(newValue as Int)
                true
            }
        }

        // Show labels in folders
        val showLabelsPref = findPreference<SwitchPreferenceCompat>("show_labels_folder")
        showLabelsPref?.apply {
            isChecked = prefs2.showIconLabelsOnHomeScreenFolder.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showIconLabelsOnHomeScreenFolder.set(newValue as Boolean) }
                findPreference<FloatSeekBarPreference>("folder_icon_label_size_factor")?.isVisible = newValue as Boolean
                true
            }
        }

        // Folder icon label size factor
        findPreference<FloatSeekBarPreference>("folder_icon_label_size_factor")?.apply {
            isVisible = prefs2.showIconLabelsOnHomeScreenFolder.firstBlocking()
            value = prefs2.homeIconLabelFolderSizeFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.homeIconLabelFolderSizeFactor.set(newValue as Float) }
                true
            }
        }
    }
}