package app.lawnchair.settings.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.LawnchairApp
import app.lawnchair.settings.ui.preference.FloatSeekBarPreference
import app.lawnchair.settings.ui.preference.IntSeekBarPreference
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class QuickstepPreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_quickstep, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        bindGeneralSection()
        bindQuickActionsSection()
        bindCornerRadiusSection()
        bindTaskbarSection()
    }

    private fun bindGeneralSection() {
        // Warning if recents not enabled
        findPreference<Preference>("quickswitch_warning")?.apply {
            isVisible = !LawnchairApp.isRecentsEnabled
        }

        val translucentBg = prefs.recentsTranslucentBackground.get()
        findPreference<SwitchPreferenceCompat>("translucent_background")?.apply {
            isChecked = translucentBg
            setOnPreferenceChangeListener { _, newValue ->
                prefs.recentsTranslucentBackground.set(newValue as Boolean)
                findPreference<FloatSeekBarPreference>("translucent_bg_alpha")?.isVisible = newValue as Boolean
                true
            }
        }

        findPreference<FloatSeekBarPreference>("translucent_bg_alpha")?.apply {
            isVisible = translucentBg
            value = prefs.recentsTranslucentBackgroundAlpha.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.recentsTranslucentBackgroundAlpha.set(newValue as Float)
                true
            }
        }
    }

    private fun bindQuickActionsSection() {
        // Quick actions reordering
        findPreference<Preference>("quick_actions")?.setOnPreferenceClickListener {
            navigateTo(QuickActionsReorderFragment(), getString(R.string.recents_actions_label))
            true
        }
    }

    private fun bindCornerRadiusSection() {
        val overridePref = findPreference<SwitchPreferenceCompat>("override_window_corner_radius")
        overridePref?.apply {
            isChecked = prefs.overrideWindowCornerRadius.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.overrideWindowCornerRadius.set(newValue as Boolean)
                findPreference<IntSeekBarPreference>("window_corner_radius")?.isVisible = newValue as Boolean
                true
            }
        }

        findPreference<IntSeekBarPreference>("window_corner_radius")?.apply {
            isVisible = prefs.overrideWindowCornerRadius.get()
            min = 70
            max = 150
            value = prefs.windowCornerRadius.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.windowCornerRadius.set(newValue as Int)
                true
            }
        }
    }

    private fun bindTaskbarSection() {
        findPreference<SwitchPreferenceCompat>("enable_taskbar")?.apply {
            isVisible = Utilities.ATLEAST_S_V2
            isChecked = prefs2.enableTaskbarOnPhone.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.enableTaskbarOnPhone.set(newValue as Boolean) }
                true
            }
        }
    }
}