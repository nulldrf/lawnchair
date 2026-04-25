package app.lawnchair.settings.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import app.lawnchair.backup.LawnchairBackup
import com.android.launcher3.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreFragment : BaseSettingsFragment() {

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        navigateTo(
            RestoreBackupFragment.newInstance(uri.toString()),
            getString(R.string.restore_backup)
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_backup_restore, rootKey)
        bindAllPreferences()
    }

    private fun bindAllPreferences() {
        findPreference<Preference>("create_backup")?.setOnPreferenceClickListener {
            navigateTo(CreateBackupFragment(), getString(R.string.create_backup))
            true
        }

        findPreference<Preference>("restore_backup")?.setOnPreferenceClickListener {
            pickBackupFile.launch(arrayOf("*/*"))
            true
        }
    }
}