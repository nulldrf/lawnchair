package app.lawnchair.smartspace.provider

import android.app.Activity
import android.content.Context
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.util.dropWhileBusy
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

@LauncherAppSingleton
class SmartspaceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    val dataSources = listOf(
        WeatherDataProvider(context),
        SmartspaceWidgetReader(context),
        BatteryStatusProvider(context),
        TorchProvider(context),
        NowPlayingProvider(context),
        OnboardingProvider(context),
        PersonalityProvider(context),
    )

    private val state = dataSources
        .map { it.targets }
        .reduce { acc, flow -> flow.combine(acc) { a, b -> a + b } }
        .map { combined ->
            combined.copy(targets = normalizeWeatherTargets(combined.targets))
        }
        .shareIn(
            MainScope(),
            SharingStarted.WhileSubscribed(),
            replay = 1,
        )
    val targets = state
        .map {
            if (it.requiresSetup.isNotEmpty()) {
                listOf(setupTarget) + it.targets
            } else {
                it.targets
            }
        }
    val previewTargets = state
        .map { it.targets }

    private val setupTarget = SmartspaceTarget(
        id = "smartspaceSetup",
        headerAction = SmartspaceAction(
            id = "smartspaceSetupAction",
            title = context.getString(R.string.smartspace_requires_setup),
            intent = PreferenceActivity.createIntent(context, Smartspace),
        ),
        score = 999f,
        featureType = SmartspaceTarget.FeatureType.FEATURE_TIPS,
    )

    private val dateOnlyFallbackTarget = SmartspaceTarget(
        id = "dateFallback",
        score = SmartspaceScores.SCORE_WEATHER,
        featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
    )

    /**
     * Guarantees At a Glance always contains exactly one FEATURE_WEATHER (date/weather)
     * card so inbuilt Google Smartspace and custom WeatherDataProvider can never produce
     * duplicate date/weather cards, while still keeping a date-only card if both are off.
     */
    private fun normalizeWeatherTargets(targets: List<SmartspaceTarget>): List<SmartspaceTarget> {
        val (weatherTargets, otherTargets) = targets.partition {
            it.featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER
        }
        val primaryWeather = weatherTargets.firstOrNull { it.headerAction != null }
            ?: weatherTargets.firstOrNull()
            ?: dateOnlyFallbackTarget
        return listOf(primaryWeather) + otherTargets
    }

    suspend fun startSetup(activity: Activity) {
        state
            .map { it.requiresSetup }
            .dropWhileBusy()
            .collect { sources ->
                sources.forEach {
                    it.startSetup(activity)
                    it.onSetupDone()
                }
            }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getSmartspaceProvider)
    }
}
