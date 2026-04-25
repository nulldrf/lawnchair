package app.lawnchair.settings.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.settings.ui.preference.FloatSeekBarPreference
import app.lawnchair.settings.ui.preference.IntSeekBarPreference
import app.lawnchair.util.isGestureNavContractCompatible
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class ExperimentalFeaturesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_experimental, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        bindWorkspaceSection()
        bindInternalSection()
    }

    private fun bindWorkspaceSection() {
        // Folder icon shape customization
        val enableFolderShapePref = findPreference<SwitchPreferenceCompat>("enable_folder_icon_shape")
        enableFolderShapePref?.apply {
            isChecked = prefs2.enableFolderIconShapeCustomization.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.enableFolderIconShapeCustomization.set(newValue as Boolean) }
                if (!(newValue as Boolean)) {
                    lifecycleScope.launch { prefs2.folderShape.set(prefs2.folderShape.defaultValue) }
                }
                findPreference<Preference>("folder_shape_picker")?.isVisible = newValue
                true
            }
        }

        findPreference<Preference>("folder_shape_picker")?.apply {
            isVisible = prefs2.enableFolderIconShapeCustomization.firstBlocking()
            setOnPreferenceClickListener {
                navigateTo(IconShapePreferencesFragment.newInstance(isFolder = true), getString(R.string.folder_shape_label))
                true
            }
        }

        // Font selection toggle
        findPreference<SwitchPreferenceCompat>("enable_font_selection")?.apply {
            isChecked = prefs2.enableFontSelection.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.enableFontSelection.set(newValue as Boolean) }
                true
            }
        }

        // Increase max grid size
        findPreference<SwitchPreferenceCompat>("workspace_increase_max_grid_size")?.apply {
            isChecked = prefs.workspaceIncreaseMaxGridSize.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.workspaceIncreaseMaxGridSize.set(newValue as Boolean)
                true
            }
        }

        // Icon swipe gestures
        findPreference<SwitchPreferenceCompat>("icon_swipe_gestures")?.apply {
            isChecked = prefs2.iconSwipeGestures.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.iconSwipeGestures.set(newValue as Boolean) }
                true
            }
        }

        // Show deck layout option
        findPreference<SwitchPreferenceCompat>("show_deck_layout")?.apply {
            isChecked = prefs2.showDeckLayout.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showDeckLayout.set(newValue as Boolean) }
                true
            }
        }

        // Wallpaper blur
        findPreference<SwitchPreferenceCompat>("enable_wallpaper_blur")?.apply {
            isChecked = prefs.enableWallpaperBlur.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.enableWallpaperBlur.set(newValue as Boolean)
                val enabled = newValue as Boolean
                findPreference<IntSeekBarPreference>("wallpaper_blur_amount")?.isVisible = enabled
                findPreference<FloatSeekBarPreference>("wallpaper_blur_factor")?.isVisible = enabled
                true
            }
        }

        val blurEnabled = prefs.enableWallpaperBlur.get()

        findPreference<IntSeekBarPreference>("wallpaper_blur_amount")?.apply {
            isVisible = blurEnabled
            min = 0
            max = 100
            value = prefs.wallpaperBlur.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.wallpaperBlur.set(newValue as Int)
                true
            }
        }

        findPreference<FloatSeekBarPreference>("wallpaper_blur_factor")?.apply {
            isVisible = blurEnabled
            value = prefs.wallpaperBlurFactorThreshold.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.wallpaperBlurFactorThreshold.set(newValue as Float)
                true
            }
        }
    }

    private fun bindInternalSection() {
        // Always reload icons
        val alwaysReloadPref = findPreference<SwitchPreferenceCompat>("always_reload_icons")
        alwaysReloadPref?.apply {
            isChecked = prefs2.alwaysReloadIcons.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.alwaysReloadIcons.set(newValue as Boolean) }
                findPreference<Preference>("always_reload_icons_warning")?.isVisible = newValue as Boolean
                true
            }
        }

        findPreference<Preference>("always_reload_icons_warning")?.apply {
            isVisible = prefs2.alwaysReloadIcons.firstBlocking()
        }

        // GestureNavContract
        findPreference<SwitchPreferenceCompat>("enable_gnc")?.apply {
            isEnabled = Utilities.ATLEAST_Q
            isChecked = prefs.enableGnc.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.enableGnc.set(newValue as Boolean)
                val enabled = newValue as Boolean
                findPreference<Preference>("gnc_warning")?.isVisible = enabled && !isGestureNavContractCompatible
                true
            }
        }

        findPreference<Preference>("gnc_warning")?.apply {
            isVisible = prefs.enableGnc.get() && !isGestureNavContractCompatible
        }
    }
}