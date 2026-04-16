package app.lawnchair.settings.ui

import android.os.Bundle
import com.android.launcher3.R
import com.android.launcher3.settings.SettingsActivity
import com.google.android.material.appbar.CollapsingToolbarLayout

class LawnchairSettingsActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // super handles setContentView + setActionBar

        // Just hide the native ActionBar's own title — CollapsingToolbarLayout shows it instead
        getActionBar()?.setDisplayShowTitleEnabled(false)

        // Sync initial title to the collapsing toolbar
        findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.title = title
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.title = getString(titleId)
    }
}