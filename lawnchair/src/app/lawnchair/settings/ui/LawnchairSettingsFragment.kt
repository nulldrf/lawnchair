package app.lawnchair.settings.ui

import android.os.Bundle
import android.view.View
import com.android.launcher3.settings.SettingsActivity.LauncherSettingsFragment

class LawnchairSettingsFragment : LauncherSettingsFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.isNestedScrollingEnabled = true
    }
}