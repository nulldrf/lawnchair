package app.lawnchair.ui.preferences.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.font.FontCache
import app.lawnchair.preferences.getAdapter
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.GeneralFontSelection
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.patrykmichalik.opto.core.PreferenceImpl

@Composable
fun FontPreference(
    fontPref: PreferenceImpl<FontCache.Font, String>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)

    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        description = {
            val font = fontPref.getAdapter().state.value
            Text(
                text = font.fullDisplayName,
                fontFamily = font.composeFontFamily,
            )
        },
        onClick = {
            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
            navController.navigate(route = GeneralFontSelection(fontPref.key.name))
        },
    )
}
