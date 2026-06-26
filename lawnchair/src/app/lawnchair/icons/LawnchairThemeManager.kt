package app.lawnchair.icons

import android.content.Context
import android.util.Log
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.PathShapeDelegate
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

@LauncherAppSingleton
class LawnchairThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    private val prefs: LauncherPrefs,
    private val iconControllerFactory: IconControllerFactory,
    private val lifecycle: DaggerSingletonTracker,
    private val prefs2: PreferenceManager2,
    private val prefs1: PreferenceManager,
) : ThemeManager(
    context,
    uiExecutor,
    prefs,
    iconControllerFactory,
    lifecycle,
) {
    // -----------------------------------------------------------------------
    // Prefs whose values are folded into the IconState cache key.
    //
    // Upstream prefs (wrapAdaptiveIcons … forceIconMonochrome) and the mod's
    // two colorize prefs are all tracked here so that any change triggers
    // verifyIconState() → onThemeChanged() → full icon reload via the same
    // fast path used by shape changes.
    //
    // colorizedBackgrounds and treatWhiteAdaptiveIcons are mod additions:
    //   - "Smart icon backgrounds" (pref_colorizedLegacyTreatment)
    //   - "Recolor white adaptive backgrounds" (pref_enableWhiteOnlyTreatment)
    // -----------------------------------------------------------------------
    private val statePrefs1 = listOf(
        prefs1.wrapAdaptiveIcons,
        prefs1.transparentIconBackground,
        prefs1.shadowBGIcons,
        prefs1.coloredBackgroundLightness,
        prefs1.forceIconMonochrome,
        prefs1.colorizedBackgrounds,
        prefs1.treatWhiteAdaptiveIcons,
    )

    private val prefListener = PreferenceChangeListener {
        uiExecutor.execute { verifyIconState() }
    }

    override var iconState = parseIconStateV2(null)

    init {
        val scope = MainScope() + CoroutineName("LawnchairThemeManager")
        merge(
            prefs2.iconShape.get(),
            prefs2.customIconShape.get(),
            prefs2.folderShape.get(),
            prefs2.customFolderShape.get(),
        ).onEach { verifyIconState() }
            .launchIn(scope)

        statePrefs1.forEach { it.addListener(prefListener) }

        lifecycle.addCloseable {
            scope.cancel()
            statePrefs1.forEach { it.removeListener(prefListener) }
        }
    }

    override fun verifyIconState() {
        val newState = parseIconStateV2(iconState)
        if (newState == iconState) return
        iconState = newState

        listeners.forEach { it.onThemeChanged() }
    }

    private fun prefs1State(): String = statePrefs1.joinToString(",") { it.get().toString() }

    private fun parseIconStateV2(oldState: IconState?): IconState {
        val currentAppShape: IconShape = try {
            prefs2.iconShape.firstCached()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.Circle
        }

        val currentFolderShape: IconShape = try {
            prefs2.folderShape.firstCached()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting folder shape", e)
            IconShape.Circle
        }

        // All tracked prefs (including the mod's colorize prefs) are serialised into the
        // shape key so that toggling any of them produces a different IconState and triggers
        // onThemeChanged() → full icon reload, with no manual SharedPreferences listener needed.
        val currentPrefs1State = prefs1State()
        val appShapeKey    = currentAppShape.getHashString()    + currentPrefs1State
        val folderShapeKey = currentFolderShape.getHashString() + currentPrefs1State
        val combinedKey = "$appShapeKey:$folderShapeKey"

        val appShape =
            if (oldState != null && (oldState.iconShape as? PathShapeDelegate)?.iconShape == currentAppShape) {
                oldState.iconShape
            } else {
                PathShapeDelegate(currentAppShape)
            }

        val folderShape =
            if (oldState != null && (oldState.folderShape as? PathShapeDelegate)?.iconShape == currentFolderShape) {
                oldState.folderShape
            } else {
                PathShapeDelegate(currentFolderShape)
            }

        return IconState(
            iconMask = combinedKey,
            folderRadius = 1f,
            shapeRadius = 1f,
            themeController = iconControllerFactory.createThemeController(),
            iconShape = appShape,
            folderShape = folderShape,
        )
    }
}

private const val TAG = "LawnchairThemeManager"