package app.lawnchair.settings.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import com.android.launcher3.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar

class LawnchairSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.lawnchair_settings_activity)

        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val collapsingToolbar = findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        val appBarLayout = findViewById<AppBarLayout>(R.id.appbarlayout)

        // Apply edge-to-edge insets to AppBarLayout (status bar padding)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        collapsingToolbar.title = title

        topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, LawnchairSettingsFragment())
                .commit()
        }
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)?.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)?.title = getString(titleId)
    }
}
