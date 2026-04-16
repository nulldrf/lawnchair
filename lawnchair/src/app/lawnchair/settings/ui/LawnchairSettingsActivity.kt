package app.lawnchair.settings.ui

import android.os.Bundle
import com.android.launcher3.R
import com.android.launcher3.settings.SettingsActivity
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar

class LawnchairSettingsActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable the native ActionBar title and home button entirely
        actionBar?.setDisplayShowTitleEnabled(false)
        actionBar?.setDisplayHomeAsUpEnabled(false)
        actionBar?.setHomeButtonEnabled(false)

        val toolbar = findViewById<MaterialToolbar>(R.id.action_bar)
        val collapsingToolbar = findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)

        val showBack = intent.hasExtra(EXTRA_FRAGMENT_ROOT_KEY)
            || intent.hasExtra(EXTRA_FRAGMENT_ARGS)
            || intent.hasExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY)

        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        } else {
            toolbar.navigationIcon = null
        }

        collapsingToolbar.title = title
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
