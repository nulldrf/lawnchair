package app.lawnchair.smartspace.provider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.setCached
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.getApkVersionComparison
import com.android.launcher3.LauncherPrefs.Companion.getPrefs
import com.android.launcher3.R
import com.android.launcher3.util.OnboardingPrefs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

class OnboardingProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_onboarding,
        { smartspaceOnboarding },
    ) {

    companion object {
        private val HOME_BOUNCE_KEY = OnboardingPrefs.HOME_BOUNCE_SEEN.sharedPrefKey
        private const val REQUEST_CODE_SETTINGS = 1
    }

    private val prefs = getPrefs(context)
    private val prefs2 = PreferenceManager2.getInstance(context)

    private val lawnSettingsIntent: Intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
        .setPackage(context.packageName)

    private val lawnSettingsPendingIntent: PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_SETTINGS,
        lawnSettingsIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** No-op. */
    private val lawnOnboardingPendingIntent: PendingIntent = lawnSettingsPendingIntent

    private val homeBounceSeenFlow = callbackFlow {
        val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (sharedPreferences == prefs && key == HOME_BOUNCE_KEY) {
                trySend(prefs.getBoolean(HOME_BOUNCE_KEY, false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        trySend(prefs.getBoolean(HOME_BOUNCE_KEY, false))
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
    }

    override val internalTargets = combine(
        homeBounceSeenFlow,
        prefs2.hasOpenedSettings.get(),
        prefs2.lawnchairMajorVersion.get(),
    ) { homeBounceSeen, hasOpenedSettings, majorVersion ->
        listOfNotNull(getSmartspaceTarget(homeBounceSeen, hasOpenedSettings, majorVersion))
    }

    private fun getSmartspaceTarget(
        hasSeenHomeBounce: Boolean,
        hasSeenSettings: Boolean,
        storedMajorVersion: Int,
    ): SmartspaceTarget? {
        val currentMajor = context.getApkVersionComparison().first[0]
        return when {
            !hasSeenHomeBounce -> {
                SmartspaceTarget(
                    id = "onboarding-swipe",
                    headerAction = SmartspaceAction(
                        id = "onboarding-swipe-action",
                        icon = null,
                        title = context.getString(R.string.onboarding_welcome),
                        subtitle = context.getString(R.string.onboarding_swipe_up),
                        pendingIntent = null,
                    ),
                    score = SmartspaceScores.SCORE_ONBOARDING,
                    featureType = SmartspaceTarget.FeatureType.FEATURE_ONBOARDING,
                )
            }

            !hasSeenSettings -> {
                SmartspaceTarget(
                    id = "onboarding-settings",
                    headerAction = SmartspaceAction(
                        id = "onboarding-settings-action",
                        icon = Icon.createWithResource(context, R.drawable.ic_lightbulb),
                        title = context.getString(R.string.onboarding_open_settings_title),
                        subtitle = context.getString(R.string.onboarding_open_settings_subtitle),
                        pendingIntent = lawnSettingsPendingIntent,
                    ),
                    score = SmartspaceScores.SCORE_ONBOARDING,
                    featureType = SmartspaceTarget.FeatureType.FEATURE_ONBOARDING,
                )
            }

            currentMajor > storedMajorVersion -> {
                if (true) return null // pE-TODO(FeatureFlags): upgraded onboarding
                prefs2.lawnchairMajorVersion.setCached(currentMajor)
                SmartspaceTarget(
                    id = "onboarding-upgrade",
                    headerAction = SmartspaceAction(
                        id = "onboarding-upgrade-action",
                        icon = Icon.createWithResource(context, R.drawable.ic_lightbulb),
                        title = context.getString(R.string.onboarding_major_upgrade_title),
                        subtitle = context.getString(R.string.onboarding_major_upgrade_subtitle),
                        pendingIntent = lawnOnboardingPendingIntent,
                    ),
                    score = SmartspaceScores.SCORE_ONBOARDING,
                    featureType = SmartspaceTarget.FeatureType.FEATURE_ONBOARDING,
                )
            }

            else -> null
        }
    }
}
