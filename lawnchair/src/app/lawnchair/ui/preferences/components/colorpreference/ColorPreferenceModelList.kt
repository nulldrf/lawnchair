package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import javax.inject.Inject

@LauncherAppSingleton
class ColorPreferenceModelList @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val models = mutableMapOf<String, ColorPreferenceModel>()

    init {
        val prefs = PreferenceManager2.getInstance(context)

        // ── General / accent ─────────────────────────────────────────────────
        // Accent color — uses the full split-circle Monet swatch design.
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.accentColor,
                labelRes = R.string.accent_color,
                dynamicEntries = dynamicColors,
                useSimpleSwatches = false,
            ),
        )

        // ── Home screen ───────────────────────────────────────────────────────
        // Home screen icon label text colour.
        // Default = honour the Light/Dark/Auto workspaceTextColor setting.
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.workspaceIconTextColor,
                labelRes = R.string.home_screen_icon_text_color,
                dynamicEntries = dynamicColorsWithDefault,
                useSimpleSwatches = true,
            ),
        )

        // ── App drawer ────────────────────────────────────────────────────────
        // Drawer background colour.
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.appDrawerBackgroundColor,
                labelRes = R.string.app_drawer_bg_color_label,
                dynamicEntries = dynamicColorsWithDefault,
                useSimpleSwatches = true,
            ),
        )
        // Drawer icon label text colour.
        // Default = use the luminance-based automatic heuristic.
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.drawerIconTextColor,
                labelRes = R.string.drawer_icon_text_color,
                dynamicEntries = dynamicColorsWithDefault,
                useSimpleSwatches = true,
            ),
        )
        // Work-profile tab background colour.
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.workProfileTabBackgroundColor,
                labelRes = R.string.work_profile_tab_background_label,
                dynamicEntries = dynamicColors,
                useSimpleSwatches = true,
            ),
        )

        // ── Dock / hotseat ────────────────────────────────────────────────────
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.strokeColorStyle,
                labelRes = R.string.qsb_hotseat_stroke_color,
                dynamicEntries = dynamicColors,
                useSimpleSwatches = true,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.hotseatBackgroundColor,
                labelRes = R.string.hotseat_bg_color_label,
                dynamicEntries = dynamicColorsWithDefault,
                useSimpleSwatches = true,
            ),
        )

        // ── Notification dots ─────────────────────────────────────────────────
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.notificationDotColor,
                labelRes = R.string.notification_dots_color,
                dynamicEntries = dynamicColorsWithDefault,
                useSimpleSwatches = true,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.notificationDotTextColor,
                labelRes = R.string.notification_dots_text_color,
                dynamicEntries = dynamicColorsWithDefault,
                useSimpleSwatches = true,
            ),
        )

        // ── Folders ───────────────────────────────────────────────────────────
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.folderColor,
                labelRes = R.string.folder_preview_bg_color_label,
                dynamicEntries = dynamicColorsWithDefault,
                useSimpleSwatches = true,
            ),
        )
    }

    operator fun get(key: String): ColorPreferenceModel = models.getValue(key)

    private fun registerModel(model: ColorPreferenceModel) {
        models[model.prefObject.key.name] = model
    }

    companion object {
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getColorPreferenceModelList)
    }
}
