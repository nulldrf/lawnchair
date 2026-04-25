package app.lawnchair.settings.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import app.lawnchair.gestures.handlers.SleepMode
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

class GesturePreferencesFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_gestures, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        val gestureMap = mapOf(
            "double_tap" to Triple(
                { prefs2.doubleTapGestureHandler.firstBlocking() },
                getString(R.string.gesture_double_tap),
                "doubleTapGestureHandler"
            ),
            "swipe_up" to Triple(
                { prefs2.swipeUpGestureHandler.firstBlocking() },
                getString(R.string.gesture_swipe_up),
                "swipeUpGestureHandler"
            ),
            "swipe_down" to Triple(
                { prefs2.swipeDownGestureHandler.firstBlocking() },
                getString(R.string.gesture_swipe_down),
                "swipeDownGestureHandler"
            ),
            "two_finger_swipe_up" to Triple(
                { prefs2.twoFingerSwipeUpGestureHandler.firstBlocking() },
                getString(R.string.gesture_two_finger_swipe_up),
                "twoFingerSwipeUpGestureHandler"
            ),
            "two_finger_swipe_down" to Triple(
                { prefs2.twoFingerSwipeDownGestureHandler.firstBlocking() },
                getString(R.string.gesture_two_finger_swipe_down),
                "twoFingerSwipeDownGestureHandler"
            ),
            "home_press" to Triple(
                { prefs2.homePressGestureHandler.firstBlocking() },
                getString(R.string.gesture_home_tap),
                "homePressGestureHandler"
            ),
            "back_press" to Triple(
                { prefs2.backPressGestureHandler.firstBlocking() },
                getString(R.string.gesture_back_tap),
                "backPressGestureHandler"
            )
        )

        gestureMap.forEach { (key, triple) ->
            val (getter, label, prefKey) = triple
            findPreference<Preference>(key)?.apply {
                summary = getter().getLabel(requireContext())
                setOnPreferenceClickListener {
                    navigateTo(
                        GesturePickerFragment.newInstance(prefKey, label),
                        label
                    )
                    true
                }
            }
        }

        // Sleep mode
        findPreference<ListPreference>("sleep_mode")?.apply {
            val current = prefs2.sleepMode.firstBlocking()
            val entries = SleepMode.entries()
            value = current.name
            this.entries = entries.map { it.getLabel(requireContext()) }.toTypedArray()
            entryValues = entries.map { it.name }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val mode = SleepMode.valueOf(newValue as String)
                lifecycleScope.launch { prefs2.sleepMode.set(mode) }
                true
            }
        }
    }
}