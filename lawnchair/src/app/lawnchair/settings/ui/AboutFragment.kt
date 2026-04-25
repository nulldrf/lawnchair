package app.lawnchair.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import app.lawnchair.ui.preferences.about.AboutViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import kotlinx.coroutines.launch

class AboutFragment : BaseSettingsFragment() {

    private val viewModel by lazy {
        ViewModelProvider(this)[AboutViewModel::class.java]
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_about, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        // Version info
        findPreference<Preference>("version_info")?.apply {
            val version = if (prefs.hideVersionInfo.get()) {
                prefs.pseudonymVersion.get() + " (pseudonym)"
            } else {
                BuildConfig.VERSION_DISPLAY_NAME
            }
            summary = version
            setOnPreferenceClickListener {
                val commitUrl = "https://github.com/LawnchairLauncher/lawnchair/commit/${BuildConfig.COMMIT_HASH}"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(commitUrl)))
                true
            }
        }

        // Update check
        findPreference<Preference>("check_for_updates")?.apply {
            isVisible = BuildConfig.APPLICATION_ID.contains("nightly")
            setOnPreferenceClickListener {
                // Observe update state
                viewModel.uiState.value.updateState.let { state ->
                    summary = state.toString()
                }
                true
            }
        }

        // Links
        mapOf(
            "link_news" to "https://t.me/lawnchairci",
            "link_support" to "https://lawnchair.app/support",
            "link_github" to "https://github.com/LawnchairLauncher/lawnchair",
            "link_translate" to "https://lawnchair.crowdin.com/lawnchair",
            "link_donate" to "https://opencollective.com/lawnchair",
            "link_telegram" to "https://t.me/lccommunity",
            "link_discord" to "https://discord.com/invite/3x8qNWxgGZ",
            "link_twitter" to "https://x.com/lawnchairapp"
        ).forEach { (key, url) ->
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            }
        }

        // Acknowledgements
        findPreference<Preference>("acknowledgements")?.setOnPreferenceClickListener {
            navigateTo(AcknowledgementsFragment(), getString(R.string.acknowledgements))
            true
        }

        // Privacy policy
        findPreference<Preference>("privacy_policy")?.setOnPreferenceClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://lawnchair.app/privacy_policy"))
            )
            true
        }
    }
}