package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.ScrollAnchor
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.rememberPreferenceScrollState
import app.lawnchair.ui.preferences.navigation.DebugMenu
import app.lawnchair.ui.preferences.navigation.ExperimentalFeatures
import app.lawnchair.util.restartLauncher
import com.android.launcher3.R

@Composable
fun ExtrasPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val enableDebug by prefs.enableDebugMenu.observeAsState()
    val scrollState = rememberPreferenceScrollState()
    val context = LocalContext.current

    PreferenceLayout(
        label = stringResource(id = R.string.extras_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            ScrollAnchor(ScrollKeys.EXPERIMENTAL, scrollState) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.experimental_features_label),
                    destination = ExperimentalFeatures,
                )
            }
            if (enableDebug) {
                ScrollAnchor(ScrollKeys.DEBUG_MENU, scrollState) {
                    NavigationActionPreference(
                        label = stringResource(id = R.string.debug_menu_label),
                        destination = DebugMenu,
                    )
                }
            }
            ScrollAnchor(ScrollKeys.RESTART, scrollState) {
                PreferenceTemplate(
                    modifier = Modifier.clickable { restartLauncher(context) },
                    title = { Text(text = stringResource(id = R.string.debug_restart_launcher)) },
                    startWidget = {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                )
            }
            PreferenceTemplate(
                modifier = Modifier.clickable { openAppInfo(context) },
                title = { Text(text = stringResource(id = R.string.app_info_drop_target_label)) },
                startWidget = {
                    Icon(
                        painter = painterResource(R.drawable.ic_about),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
        }
    }
}