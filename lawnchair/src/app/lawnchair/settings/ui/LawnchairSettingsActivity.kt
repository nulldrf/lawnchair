package app.lawnchair.settings.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.android.launcher3.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar

class LawnchairSettingsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_FRAGMENT_CLASS = "extra_fragment_class"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_FRAGMENT_ARGS = "extra_fragment_args"

        fun start(context: Context) {
            context.startActivity(Intent(context, LawnchairSettingsActivity::class.java))
        }

        fun start(context: Context, fragmentClass: Class<out Fragment>, title: String) {
            context.startActivity(
                Intent(context, LawnchairSettingsActivity::class.java).apply {
                    putExtra(EXTRA_FRAGMENT_CLASS, fragmentClass.name)
                    putExtra(EXTRA_TITLE, title)
                }
            )
        }

        fun createIntent(context: Context, fragmentClass: Class<out Fragment>, title: String): Intent {
            return Intent(context, LawnchairSettingsActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_CLASS, fragmentClass.name)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }

    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBarLayout: AppBarLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lawnchair_settings_activity)

        toolbar = findViewById(R.id.topAppBar)
        collapsingToolbar = findViewById(R.id.collapsingtoolbar)
        appBarLayout = findViewById(R.id.appbarlayout)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val count = supportFragmentManager.backStackEntryCount
            if (count > 0) {
                val entry = supportFragmentManager.getBackStackEntryAt(count - 1)
                collapsingToolbar.title = entry.name ?: getString(R.string.settings)
                toolbar.setNavigationIcon(R.drawable.ic_back)
                toolbar.navigationIcon?.setTint(
                    getColor(com.google.android.material.R.color.material_on_surface_emphasis_high_type)
                )
            } else {
                collapsingToolbar.title = getString(R.string.settings)
                toolbar.navigationIcon = null
            }
        }

        // Apply window insets to root view
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBarLayout.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        if (savedInstanceState == null) {
            val fragmentClassName = intent.getStringExtra(EXTRA_FRAGMENT_CLASS)
            val title = intent.getStringExtra(EXTRA_TITLE)

            val fragment: Fragment = if (fragmentClassName != null) {
                runCatching {
                    Class.forName(fragmentClassName).getDeclaredConstructor().newInstance() as Fragment
                }.getOrDefault(LawnchairSettingsFragment())
            } else {
                LawnchairSettingsFragment()
            }

            collapsingToolbar.title = title ?: getString(R.string.settings)

            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.settings_container, fragment)
            }
        }
    }

    fun navigateTo(fragment: Fragment, title: String) {
        collapsingToolbar.title = title
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            replace(R.id.settings_container, fragment)
            addToBackStack(title)
        }
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.navigationIcon?.setTint(
            getColor(com.google.android.material.R.color.material_on_surface_emphasis_high_type)
        )
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        if (::collapsingToolbar.isInitialized) {
            collapsingToolbar.title = title
        }
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        if (::collapsingToolbar.isInitialized) {
            collapsingToolbar.title = getString(titleId)
        }
    }
}