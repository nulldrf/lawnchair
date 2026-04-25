package app.lawnchair.settings.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.settings.ui.preference.FloatSeekBarPreference
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.ui.preferences.components.ThemeChoice
import app.lawnchair.ui.preferences.destinations.ThemedIconsState
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.notification.NotificationListener
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class GeneralPreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_general, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        bindRotation()
        bindAutoUpdater()
        bindFontSection()
        bindIconsSection()
        bindColorsSection()
        bindNotificationDotsSection()
    }

    private fun bindRotation() {
        val pref = findPreference<SwitchPreferenceCompat>("allow_rotation") ?: return
        pref.isChecked = prefs.allowRotation.get()
        pref.setOnPreferenceChangeListener { _, newValue ->
            prefs.allowRotation.set(newValue as Boolean)
            true
        }
    }

    private fun bindAutoUpdater() {
        val pref = findPreference<SwitchPreferenceCompat>("auto_updater_nightly") ?: return
        pref.isVisible = BuildConfig.APPLICATION_ID.contains("nightly")
        pref.isChecked = prefs2.autoUpdaterNightly.firstBlocking()
        pref.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch { prefs2.autoUpdaterNightly.set(newValue as Boolean) }
            true
        }
    }

    private fun bindFontSection() {
        val fontEnabled = prefs2.enableFontSelection.firstBlocking()
        findPreference<Preference>("pref_category_fonts")?.isVisible = fontEnabled
        findPreference<Preference>("font_workspace")?.apply {
            isVisible = fontEnabled
            summary = prefs.fontWorkspace.get().fullDisplayName
            setOnPreferenceClickListener {
                navigateTo(
                    FontSelectionFragment.newInstance(prefs.fontWorkspace.key),
                    getString(R.string.fontWorkspace)
                )
                true
            }
        }
        findPreference<Preference>("font_heading")?.apply {
            isVisible = fontEnabled
            summary = prefs.fontHeading.get().fullDisplayName
            setOnPreferenceClickListener {
                navigateTo(
                    FontSelectionFragment.newInstance(prefs.fontHeading.key),
                    getString(R.string.fontHeading)
                )
                true
            }
        }
        findPreference<Preference>("font_heading_medium")?.apply {
            isVisible = fontEnabled
            summary = prefs.fontHeadingMedium.get().fullDisplayName
            setOnPreferenceClickListener {
                navigateTo(
                    FontSelectionFragment.newInstance(prefs.fontHeadingMedium.key),
                    getString(R.string.fontHeadingMedium)
                )
                true
            }
        }
        findPreference<Preference>("font_body")?.apply {
            isVisible = fontEnabled
            summary = prefs.fontBody.get().fullDisplayName
            setOnPreferenceClickListener {
                navigateTo(
                    FontSelectionFragment.newInstance(prefs.fontBody.key),
                    getString(R.string.fontBody)
                )
                true
            }
        }
        findPreference<Preference>("font_body_medium")?.apply {
            isVisible = fontEnabled
            summary = prefs.fontBodyMedium.get().fullDisplayName
            setOnPreferenceClickListener {
                navigateTo(
                    FontSelectionFragment.newInstance(prefs.fontBodyMedium.key),
                    getString(R.string.fontBodyMedium)
                )
                true
            }
        }
    }

    private fun bindIconsSection() {
        // Icon style navigation
        findPreference<Preference>("icon_style")?.setOnPreferenceClickListener {
            navigateTo(IconPackPreferencesFragment(), getString(R.string.icon_style_label))
            true
        }

        // Transparent icon background (only visible when themed icons are on)
        val themedIconsOn = prefs.themedIcons.get()
        findPreference<SwitchPreferenceCompat>("transparent_icon_background")?.apply {
            isVisible = themedIconsOn
            isChecked = prefs.transparentIconBackground.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.transparentIconBackground.set(newValue as Boolean)
                true
            }
        }

        // Icon shape navigation
        findPreference<Preference>("icon_shape")?.apply {
            val iconShapeEntries = listOf(
                "system", "circle", "squircle", "rounded_square", "square"
            )
            summary = prefs2.iconShape.firstBlocking().toString()
            setOnPreferenceClickListener {
                navigateTo(IconShapePreferencesFragment(), getString(R.string.icon_shape_label))
                true
            }
        }

        // Auto adaptive icons
        val wrapAdaptivePref = findPreference<SwitchPreferenceCompat>("auto_adaptive_icons")
        wrapAdaptivePref?.isChecked = prefs.wrapAdaptiveIcons.get()
        wrapAdaptivePref?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            prefs.wrapAdaptiveIcons.set(enabled)
            findPreference<Preference>("pref_category_adaptive_desc")?.isVisible = enabled
            findPreference<FloatSeekBarPreference>("bg_lightness")?.isVisible = enabled
            findPreference<SwitchPreferenceCompat>("colorized_backgrounds")?.isVisible = enabled
            updateColorizedDependants()
            true
        }

        // Shadow behind icons
        findPreference<SwitchPreferenceCompat>("shadow_bg_icons")?.apply {
            isChecked = prefs.shadowBGIcons.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.shadowBGIcons.set(newValue as Boolean)
                true
            }
        }

        // Background lightness slider
        findPreference<FloatSeekBarPreference>("bg_lightness")?.apply {
            isVisible = prefs.wrapAdaptiveIcons.get()
            value = prefs.coloredBackgroundLightness.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.coloredBackgroundLightness.set(newValue as Float)
                true
            }
        }

        // Colorized backgrounds
        findPreference<SwitchPreferenceCompat>("colorized_backgrounds")?.apply {
            isVisible = prefs.wrapAdaptiveIcons.get()
            isChecked = prefs.colorizedBackgrounds.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.colorizedBackgrounds.set(newValue as Boolean)
                updateColorizedDependants()
                true
            }
        }

        // Treat white adaptive icons
        findPreference<SwitchPreferenceCompat>("treat_white_adaptive_icons")?.apply {
            isVisible = prefs.wrapAdaptiveIcons.get() && prefs.colorizedBackgrounds.get()
            isChecked = prefs.treatWhiteAdaptiveIcons.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.treatWhiteAdaptiveIcons.set(newValue as Boolean)
                true
            }
        }
    }

    private fun updateColorizedDependants() {
        val bothEnabled = prefs.wrapAdaptiveIcons.get() && prefs.colorizedBackgrounds.get()
        findPreference<SwitchPreferenceCompat>("treat_white_adaptive_icons")?.isVisible = bothEnabled
    }

    private fun bindColorsSection() {
        // Theme preference
        findPreference<ListPreference>("theme")?.apply {
            value = prefs.launcherTheme.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.launcherTheme.set(newValue as String)
                true
            }
            entries = arrayOf(
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system_default)
            )
            entryValues = arrayOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.SYSTEM)
        }

        // Accent color
        findPreference<Preference>("accent_color")?.apply {
            setOnPreferenceClickListener {
                navigateTo(
                    ColorSelectionFragment.newInstance("accent_color"),
                    getString(R.string.accent_color)
                )
                true
            }
        }

        // Color style
        findPreference<ListPreference>("color_style")?.apply {
            val currentStyle = prefs2.colorStyle.firstBlocking()
            value = currentStyle.name
            val styles = ColorStyle.values()
            entries = styles.map { getString(it.nameResourceId) }.toTypedArray()
            entryValues = styles.map { it.name }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val selected = ColorStyle.valueOf(newValue as String)
                lifecycleScope.launch { prefs2.colorStyle.set(selected) }
                true
            }
        }
    }

    private fun bindNotificationDotsSection() {
        // Notification dots - navigates to system settings
        findPreference<Preference>("notification_dots")?.setOnPreferenceClickListener {
            val intent = Intent("android.settings.NOTIFICATION_SETTINGS")
            startActivity(intent)
            true
        }

        // Notification dot color
        findPreference<Preference>("notification_dot_color")?.apply {
            setOnPreferenceClickListener {
                navigateTo(
                    ColorSelectionFragment.newInstance("notification_dot_color"),
                    getString(R.string.notification_dots_color)
                )
                true
            }
        }

        // Show notification count
        findPreference<SwitchPreferenceCompat>("show_notification_count")?.apply {
            isChecked = prefs2.showNotificationCount.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showNotificationCount.set(newValue as Boolean) }
                findPreference<Preference>("notification_dot_text_color")?.isVisible = newValue as Boolean
                true
            }
        }

        // Notification dot text color
        findPreference<Preference>("notification_dot_text_color")?.apply {
            isVisible = prefs2.showNotificationCount.firstBlocking()
            setOnPreferenceClickListener {
                navigateTo(
                    ColorSelectionFragment.newInstance("notification_dot_text_color"),
                    getString(R.string.notification_dots_text_color)
                )
                true
            }
        }
    }
}