package app.lawnchair.icons

import android.content.Context
import android.util.Log
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.PathShapeDelegate
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.ThemeProvider
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.icons.mono.ThemedIconDrawable
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.LooperExecutor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    private val themeProvider: ThemeProvider,
) : ThemeManager(
    context,
    uiExecutor,
    prefs,
    iconControllerFactory,
    lifecycle,
) {
    private val statePrefs = listOf(
        prefs2.wrapAdaptiveIcons,
        prefs2.transparentIconBackground,
        prefs2.shadowBGIcons,
        prefs2.coloredBackgroundLightness,
        prefs2.forceIconMonochrome,
        prefs2.launcherTheme,
        prefs2.colorizedBackgrounds,
        prefs2.treatWhiteAdaptiveIcons,
    )

    override var iconState = parseIconStateV2(null)

    init {
        val scope = MainScope() + CoroutineName("LawnchairThemeManager")
        merge(
            prefs2.iconShape.get().distinctUntilChanged().drop(1),
            prefs2.customIconShape.get().distinctUntilChanged().drop(1),
            prefs2.folderShape.get().distinctUntilChanged().drop(1),
            prefs2.customFolderShape.get().distinctUntilChanged().drop(1),
            prefs2.wrapAdaptiveIcons.get().distinctUntilChanged().drop(1),
            prefs2.transparentIconBackground.get().distinctUntilChanged().drop(1),
            prefs2.shadowBGIcons.get().distinctUntilChanged().drop(1),
            prefs2.coloredBackgroundLightness.get().distinctUntilChanged().drop(1),
            prefs2.forceIconMonochrome.get().distinctUntilChanged().drop(1),
            prefs2.launcherTheme.get().distinctUntilChanged().drop(1),
            prefs2.colorizedBackgrounds.get().distinctUntilChanged().drop(1),
            prefs2.treatWhiteAdaptiveIcons.get().distinctUntilChanged().drop(1),
        ).onEach { verifyIconState() }
            .launchIn(scope)

        val colorSchemeChangeListener = object : ThemeProvider.ColorSchemeChangeListener {
            @Volatile
            private var isDestroyed = false

            override fun onColorSchemeChanged() {
                synchronized(this) {
                    if (isDestroyed) return
                    uiExecutor.execute {
                        synchronized(this) {
                            if (isDestroyed) return@execute
                            verifyIconState()
                        }
                    }
                }
            }

            fun destroy() {
                synchronized(this) {
                    isDestroyed = true
                    themeProvider.removeListener(this)
                }
            }
        }
        themeProvider.addListener(colorSchemeChangeListener)

        val displayController = DisplayController.INSTANCE.get(context)
        val displayInfoChangeListener = DisplayController.DisplayInfoChangeListener { _, _, flags ->
            if (flags and DisplayController.CHANGE_NIGHT_MODE != 0) {
                uiExecutor.execute { verifyIconState() }
            }
        }
        displayController.addChangeListener(displayInfoChangeListener)

        lifecycle.addCloseable {
            colorSchemeChangeListener.destroy()
            scope.cancel()
            displayController.removeChangeListener(displayInfoChangeListener)
        }
    }

    override fun verifyIconState() {
        val newState = parseIconStateV2(iconState)
        if (newState == iconState) return
        iconState = newState

        listeners.forEach { it.onThemeChanged() }
    }

    private fun statePrefsKey(): String = statePrefs.joinToString(",") { it.firstCached().toString() }

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

        val currentPrefsState = statePrefsKey()
        val appShapeKey = currentAppShape.getHashString() + currentPrefsState
        val folderShapeKey = currentFolderShape.getHashString() + currentPrefsState
        val themeController = iconControllerFactory.createThemeController()?.let {
            if (prefs2.forceIconMonochrome.firstCached()) {
                FORCED_MONO_THEME_CONTROLLER
            } else {
                MONO_THEME_CONTROLLER
            }
        }
        val themedColorsKey =
            if (themeController != null) {
                ThemedIconDrawable.getColors(context).joinToString(",")
            } else {
                ""
            }
        val combinedKey = "$appShapeKey:$folderShapeKey:$themedColorsKey"

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
            themeController = themeController,
            iconShape = appShape,
            folderShape = folderShape,
        )
    }
}

// Reuse controllers to allow the equality check in verifyIconState.
private val MONO_THEME_CONTROLLER = MonoIconThemeController()
private val FORCED_MONO_THEME_CONTROLLER = MonoIconThemeController(shouldForceThemeIcon = true)
private const val TAG = "LawnchairThemeManager"
