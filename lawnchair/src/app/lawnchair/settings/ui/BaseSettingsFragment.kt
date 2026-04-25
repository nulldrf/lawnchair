package app.lawnchair.settings.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

abstract class BaseSettingsFragment : PreferenceFragmentCompat() {

    protected val prefs: PreferenceManager by lazy {
        PreferenceManager.getInstance(requireContext())
    }

    protected val prefs2: PreferenceManager2 by lazy {
        PreferenceManager2.getInstance(requireContext())
    }

    protected fun navigateTo(fragment: BaseSettingsFragment, title: String) {
        (activity as? LawnchairSettingsActivity)?.navigateTo(fragment, title)
    }

    protected fun navigateTo(fragment: Fragment, title: String) {
        (activity as? LawnchairSettingsActivity)?.navigateTo(fragment, title)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.isNestedScrollingEnabled = true
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                systemBars.bottom
            )
            insets
        }
    }

    // Helper: bind a SwitchPreference to a prefs2 boolean DataStore preference
    protected fun <T> bindSwitch(
        key: String,
        getter: () -> Boolean,
        setter: suspend (Boolean) -> Unit,
        visibilityDepends: (() -> Boolean)? = null
    ) {
        val pref = findPreference<SwitchPreferenceCompat>(key) ?: return
        pref.isChecked = getter()
        visibilityDepends?.let { pref.isVisible = it() }
        pref.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch { setter(newValue as Boolean) }
            true
        }
    }

    // Helper: update a preference summary
    protected fun updateSummary(key: String, summary: String?) {
        findPreference<Preference>(key)?.summary = summary
    }

    // Helper: set preference visibility
    protected fun setVisible(key: String, visible: Boolean) {
        findPreference<Preference>(key)?.isVisible = visible
    }
}