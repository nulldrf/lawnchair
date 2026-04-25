package app.lawnchair.settings.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.LawnchairApp
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.settings.ui.preference.FloatSeekBarPreference
import app.lawnchair.theme.color.ColorMode
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class HomeScreenPreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_home_screen, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        bindGeneralSection()
        bindFeedSection()
        bindStyleSection()
        bindWallpaperSection()
        bindGridSection()
        bindStatusBarSection()
        bindIconsSection()
        bindWidgetSection()
    }

    private fun bindGeneralSection() {
        // Show deck layout toggle (beta)
        val showDeckLayout = prefs2.showDeckLayout.firstBlocking()
        findPreference<Preference>("home_layout")?.apply {
            isVisible = showDeckLayout
            setOnPreferenceClickListener {
                navigateTo(HomeLayoutPreferencesFragment(), getString(R.string.layout))
                true
            }
        }

        // Add new apps to home screen
        val locked = prefs2.lockHomeScreen.firstBlocking()
        val isDeckLayout = prefs2.deckLayout.firstBlocking()
        findPreference<SwitchPreferenceCompat>("add_icon_to_home")?.apply {
            isEnabled = !locked
            isChecked = (!locked && prefs.addIconToHome.get()) || isDeckLayout
            isVisible = !isDeckLayout
            if (locked) summary = getString(R.string.home_screen_locked)
            setOnPreferenceChangeListener { _, newValue ->
                prefs.addIconToHome.set(newValue as Boolean)
                true
            }
        }

        // Double tap gesture
        findPreference<Preference>("double_tap_gesture")?.apply {
            summary = prefs2.doubleTapGestureHandler.firstBlocking().getLabel(requireContext())
            setOnPreferenceClickListener {
                navigateTo(
                    GesturePickerFragment.newInstance("doubleTapGestureHandler", getString(R.string.gesture_double_tap)),
                    getString(R.string.gesture_double_tap)
                )
                true
            }
        }

        // Infinite scrolling
        findPreference<SwitchPreferenceCompat>("infinite_scrolling")?.apply {
            isChecked = prefs.infiniteScrolling.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.infiniteScrolling.set(newValue as Boolean)
                true
            }
        }

        // Clear home screen
        findPreference<Preference>("clear_home_screen")?.setOnPreferenceClickListener {
            clearHomeScreen()
            true
        }
    }

    private fun bindFeedSection() {
        val feedAvailable = app.lawnchair.nexuslauncher.OverlayCallbackImpl.minusOneAvailable(requireContext())
        val enableFeedPref = findPreference<SwitchPreferenceCompat>("enable_feed")
        enableFeedPref?.apply {
            isEnabled = feedAvailable
            if (!feedAvailable) summary = getString(R.string.minus_one_unavailable)
            isChecked = prefs2.enableFeed.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.enableFeed.set(newValue as Boolean) }
                findPreference<Preference>("feed_provider")?.isVisible = feedAvailable && (newValue as Boolean)
                true
            }
        }

        findPreference<Preference>("feed_provider")?.apply {
            isVisible = feedAvailable && prefs2.enableFeed.firstBlocking()
            setOnPreferenceClickListener {
                navigateTo(FeedProviderFragment(), getString(R.string.feed_provider))
                true
            }
        }
    }

    private fun bindStyleSection() {
        // Text color
        findPreference<ListPreference>("workspace_text_color")?.apply {
            val current = prefs2.workspaceTextColor.firstBlocking()
            value = current.name
            val modes = ColorMode.values()
            entries = modes.map { getString(it.labelRes) }.toTypedArray()
            entryValues = modes.map { it.name }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val mode = ColorMode.valueOf(newValue as String)
                lifecycleScope.launch { prefs2.workspaceTextColor.set(mode) }
                true
            }
        }

        // App closing animation
        findPreference<Preference>("app_closing_animation")?.apply {
            val current = prefs2.closingAppOverlay.firstBlocking()
            summary = getString(current.labelRes)
            setOnPreferenceClickListener {
                navigateTo(OverlayAnimationPickerFragment(), getString(R.string.app_closing_animation))
                true
            }
        }
    }

    private fun bindWallpaperSection() {
        findPreference<SwitchPreferenceCompat>("wallpaper_scrolling")?.apply {
            isChecked = prefs.wallpaperScrolling.get()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.wallpaperScrolling.set(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("wallpaper_depth_effect")?.apply {
            isVisible = Utilities.ATLEAST_R
            isChecked = prefs2.wallpaperDepthEffect.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.wallpaperDepthEffect.set(newValue as Boolean) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("show_top_shadow")?.apply {
            isChecked = prefs2.showTopShadow.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showTopShadow.set(newValue as Boolean) }
                true
            }
        }
    }

    private fun bindGridSection() {
        val columns = prefs.workspaceColumns.get()
        val rows = prefs.workspaceRows.get()

        findPreference<Preference>("home_screen_grid")?.apply {
            summary = getString(R.string.x_by_y, columns, rows)
            setOnPreferenceClickListener {
                navigateTo(HomeScreenGridFragment(), getString(R.string.home_screen_grid))
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("lock_home_screen")?.apply {
            isChecked = prefs2.lockHomeScreen.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.lockHomeScreen.set(newValue as Boolean) }
                val locked = newValue as Boolean
                findPreference<SwitchPreferenceCompat>("add_icon_to_home")?.apply {
                    isEnabled = !locked
                    if (locked) summary = getString(R.string.home_screen_locked)
                    else summary = null
                }
                true
            }
        }

        // Popup menu
        findPreference<Preference>("popup_menu")?.setOnPreferenceClickListener {
            navigateTo(LauncherPopupPreferenceFragment(), getString(R.string.popup_menu))
            true
        }
    }

    private fun bindStatusBarSection() {
        val showStatusBarPref = findPreference<SwitchPreferenceCompat>("show_status_bar")
        showStatusBarPref?.apply {
            isChecked = prefs2.showStatusBar.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showStatusBar.set(newValue as Boolean) }
                val show = newValue as Boolean
                findPreference<SwitchPreferenceCompat>("dark_status_bar")?.isVisible = show
                findPreference<SwitchPreferenceCompat>("status_bar_clock")?.isVisible =
                    show && LawnchairApp.isRecentsEnabled
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("dark_status_bar")?.apply {
            isVisible = prefs2.showStatusBar.firstBlocking()
            isChecked = prefs2.darkStatusBar.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.darkStatusBar.set(newValue as Boolean) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("status_bar_clock")?.apply {
            isVisible = prefs2.showStatusBar.firstBlocking() && LawnchairApp.isRecentsEnabled
            isChecked = prefs2.statusBarClock.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.statusBarClock.set(newValue as Boolean) }
                true
            }
        }
    }

    private fun bindIconsSection() {
        findPreference<FloatSeekBarPreference>("home_icon_size_factor")?.apply {
            value = prefs2.homeIconSizeFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.homeIconSizeFactor.set(newValue as Float) }
                true
            }
        }

        val showLabelsPref = findPreference<SwitchPreferenceCompat>("show_icon_labels_home")
        showLabelsPref?.apply {
            isChecked = prefs2.showIconLabelsOnHomeScreen.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.showIconLabelsOnHomeScreen.set(newValue as Boolean) }
                findPreference<FloatSeekBarPreference>("home_icon_label_size_factor")?.isVisible =
                    newValue as Boolean
                true
            }
        }

        findPreference<FloatSeekBarPreference>("home_icon_label_size_factor")?.apply {
            isVisible = prefs2.showIconLabelsOnHomeScreen.firstBlocking()
            value = prefs2.homeIconLabelSizeFactor.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.homeIconLabelSizeFactor.set(newValue as Float) }
                true
            }
        }
    }

    private fun bindWidgetSection() {
        findPreference<SwitchPreferenceCompat>("rounded_widgets")?.apply {
            isChecked = prefs2.roundedWidgets.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.roundedWidgets.set(newValue as Boolean) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("allow_widget_overlap")?.apply {
            isChecked = prefs2.allowWidgetOverlap.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.allowWidgetOverlap.set(newValue as Boolean) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("widget_unlimited_size")?.apply {
            isChecked = prefs2.widgetUnlimitedSize.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.widgetUnlimitedSize.set(newValue as Boolean) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("force_widget_resize")?.apply {
            isChecked = prefs2.forceWidgetResize.firstBlocking()
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch { prefs2.forceWidgetResize.set(newValue as Boolean) }
                true
            }
        }
    }

    private fun clearHomeScreen() {
        val launcherModel = LauncherAppState.getInstance(requireContext()).model
        val modelWriter = launcherModel.getWriter(
            false,
            com.android.launcher3.celllayout.CellPosMapper.DEFAULT,
            null
        )
        val removed = modelWriter.clearAllHomeScreenViewsByType(
            com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
        )
        if (removed) {
            launcherModel.forceReload()
            Toast.makeText(
                requireContext(),
                R.string.home_screen_all_views_removed_msg,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}