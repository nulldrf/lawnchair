package app.lawnchair.settings.ui

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import app.lawnchair.ui.preferences.destinations.openAppInfo
import app.lawnchair.util.restartLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

class ExtrasPreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_extras, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        findPreference<Preference>("experimental_features")?.setOnPreferenceClickListener {
            navigateTo(ExperimentalFeaturesFragment(), getString(R.string.experimental_features_label))
            true
        }

        findPreference<Preference>("debug_menu")?.apply {
            isVisible = prefs.enableDebugMenu.get()
            setOnPreferenceClickListener {
                navigateTo(DebugMenuFragment(), getString(R.string.debug_menu_label))
                true
            }
        }

        findPreference<Preference>("restart_launcher")?.setOnPreferenceClickListener {
            restartLauncher(requireContext())
            true
        }

        findPreference<Preference>("app_info")?.setOnPreferenceClickListener {
            openAppInfo(requireContext())
            true
        }
    }
}