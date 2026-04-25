package app.lawnchair.settings.ui

import android.app.Activity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.settings.ui.preference.IntSeekBarPreference
import app.lawnchair.smartspace.model.LawnchairSmartspace
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceMode
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.smartspace.model.Smartspacer
import app.lawnchair.smartspace.provider.SmartspaceProvider
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class SmartspacePreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_smartspace, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        bindMasterSwitch()
        bindProviderSelection()
        bindDataSources()
        bindDateTimePreferences()
        bindSmartspacerSection()
    }

    private fun bindMasterSwitch() {
        val enableSmartspace = prefs2.enableSmartspace.firstBlocking()

        findPreference<SwitchPreferenceCompat>("enable_smartspace")?.apply {
            isChecked = enableSmartspace
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.enableSmartspace.set(newValue as Boolean) }
                updateSectionsVisibility()
                true
            }
        }

        updateSectionsVisibility()
    }

    private fun updateSectionsVisibility() {
        val enabled = prefs2.enableSmartspace.firstBlocking()
        val mode = prefs2.smartspaceMode.firstBlocking()
        val isLawnchair = mode == LawnchairSmartspace
        val isSmartspacer = mode == Smartspacer

        findPreference<Preference>("smartspace_mode")?.isVisible = enabled
        findPreference<Preference>("pref_category_what_to_show")?.isVisible = enabled && isLawnchair
        findPreference<Preference>("pref_category_date_time")?.isVisible = enabled && isLawnchair
        findPreference<Preference>("pref_category_smartspacer")?.isVisible = enabled && isSmartspacer
    }

    private fun bindProviderSelection() {
        findPreference<ListPreference>("smartspace_mode")?.apply {
            val currentMode = prefs2.smartspaceMode.firstBlocking()
            value = currentMode.name
            val modes = SmartspaceMode.values()
            entries = modes.map { getString(it.nameResourceId) }.toTypedArray()
            entryValues = modes.map { it.name }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val mode = SmartspaceMode.valueOf(newValue as String)
                lifecycleScope.launch { prefs2.smartspaceMode.set(mode) }
                updateSectionsVisibility()
                true
            }
        }
    }

    private fun bindDataSources() {
        val smartspaceProvider = SmartspaceProvider.INSTANCE.get(requireContext())
        smartspaceProvider.dataSources
            .filter { it.isAvailable }
            .forEach { source ->
                findPreference<SwitchPreferenceCompat>("smartspace_source_${source.providerName}")?.apply {
                    isChecked = source.enabledPref.firstBlocking()
                    setOnPreferenceChangeListener { _, newValue ->
                        lifecycleScope.launch { source.enabledPref.set(newValue as Boolean) }
                        true
                    }
                }
            }
    }

    private fun bindDateTimePreferences() {
        val calendar = prefs2.smartspaceCalendar.firstBlocking()
        val showDate = prefs2.smartspaceShowDate.firstBlocking()
        val showTime = prefs2.smartspaceShowTime.firstBlocking()
        val supportsCustom = calendar.formatCustomizationSupport

        findPreference<SwitchPreferenceCompat>("smartspace_show_date")?.apply {
            isVisible = supportsCustom
            isChecked = showDate
            isEnabled = !(showDate && !showTime)
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.smartspaceShowDate.set(newValue as Boolean) }
                updateDateTimeEnabled()
                true
            }
        }

        findPreference<ListPreference>("smartspace_calendar")?.apply {
            isVisible = supportsCustom && showDate
            val calendars = SmartspaceCalendar.values()
            value = calendar.name
            entries = calendars.map { getString(it.nameResourceId) }.toTypedArray()
            entryValues = calendars.map { it.name }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val cal = SmartspaceCalendar.valueOf(newValue as String)
                lifecycleScope.launch { prefs2.smartspaceCalendar.set(cal) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("smartspace_show_time")?.apply {
            isVisible = supportsCustom
            isChecked = showTime
            isEnabled = !(showTime && !showDate)
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.smartspaceShowTime.set(newValue as Boolean) }
                updateDateTimeEnabled()
                true
            }
        }

        findPreference<ListPreference>("smartspace_time_format")?.apply {
            isVisible = supportsCustom && showTime
            val formats = SmartspaceTimeFormat.values()
            value = prefs2.smartspaceTimeFormat.firstBlocking().name
            entries = formats.map { getString(it.nameResourceId) }.toTypedArray()
            entryValues = formats.map { it.name }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val format = SmartspaceTimeFormat.valueOf(newValue as String)
                lifecycleScope.launch { prefs2.smartspaceTimeFormat.set(format) }
                true
            }
        }
    }

    private fun updateDateTimeEnabled() {
        val showDate = prefs2.smartspaceShowDate.firstBlocking()
        val showTime = prefs2.smartspaceShowTime.firstBlocking()
        findPreference<SwitchPreferenceCompat>("smartspace_show_date")?.isEnabled = !(showDate && !showTime)
        findPreference<SwitchPreferenceCompat>("smartspace_show_time")?.isEnabled = !(showTime && !showDate)
        findPreference<Preference>("smartspace_calendar")?.isVisible = showDate
        findPreference<Preference>("smartspace_time_format")?.isVisible = showTime
    }

    private fun bindSmartspacerSection() {
        findPreference<IntSeekBarPreference>("smartspacer_max_count")?.apply {
            min = 5
            max = 15
            value = prefs2.smartspacerMaxCount.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.smartspacerMaxCount.set(newValue as Int) }
                true
            }
        }

        findPreference<Preference>("open_smartspacer_settings")?.setOnPreferenceClickListener {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(
                com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants.SMARTSPACER_PACKAGE_NAME
            )
            intent?.let { startActivity(it) }
            true
        }
    }
}