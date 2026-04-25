package app.lawnchair.settings.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import app.lawnchair.LawnchairApp
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking

class LawnchairSettingsFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_dashboard, rootKey)
        setupNavigation()
    }

    private fun setupNavigation() {
        findPreference<Preference>("pref_general")?.setOnPreferenceClickListener {
            navigateTo(GeneralPreferencesFragment(), getString(R.string.general_label))
            true
        }

        findPreference<Preference>("pref_home_screen")?.setOnPreferenceClickListener {
            navigateTo(HomeScreenPreferencesFragment(), getString(R.string.home_screen_label))
            true
        }

        findPreference<Preference>("pref_smartspace")?.setOnPreferenceClickListener {
            navigateTo(SmartspacePreferencesFragment(), getString(R.string.smartspace_widget))
            true
        }

        findPreference<Preference>("pref_dock")?.setOnPreferenceClickListener {
            navigateTo(DockPreferencesFragment(), getString(R.string.dock_label))
            true
        }

        findPreference<Preference>("pref_app_drawer")?.apply {
            val deckLayoutActive = prefs2.deckLayout.firstBlocking()
            isVisible = !deckLayoutActive
            setOnPreferenceClickListener {
                navigateTo(AppDrawerPreferencesFragment(), getString(R.string.app_drawer_label))
                true
            }
        }

        findPreference<Preference>("pref_search")?.setOnPreferenceClickListener {
            navigateTo(SearchPreferencesFragment(), getString(R.string.search_bar_label))
            true
        }

        findPreference<Preference>("pref_folders")?.setOnPreferenceClickListener {
            navigateTo(FolderPreferencesFragment(), getString(R.string.folders_label))
            true
        }

        findPreference<Preference>("pref_gestures")?.setOnPreferenceClickListener {
            navigateTo(GesturePreferencesFragment(), getString(R.string.gestures_label))
            true
        }

        findPreference<Preference>("pref_quickstep")?.apply {
            isVisible = LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG
            setOnPreferenceClickListener {
                navigateTo(QuickstepPreferencesFragment(), getString(R.string.quickstep_label))
                true
            }
        }

        findPreference<Preference>("pref_backup_restore")?.setOnPreferenceClickListener {
            navigateTo(BackupRestoreFragment(), getString(R.string.backup_and_restore_label))
            true
        }

        findPreference<Preference>("pref_extras")?.setOnPreferenceClickListener {
            navigateTo(ExtrasPreferencesFragment(), getString(R.string.extras_label))
            true
        }

        findPreference<Preference>("pref_about")?.apply {
            val versionDisplay = if (prefs.hideVersionInfo.get()) {
                prefs.pseudonymVersion.get()
            } else {
                BuildConfig.VERSION_DISPLAY_NAME
            }
            summary = versionDisplay
            setOnPreferenceClickListener {
                navigateTo(AboutFragment(), getString(R.string.about_label))
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.isNestedScrollingEnabled = true
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }
    }
}