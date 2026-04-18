package app.lawnchair.ui.preferences.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Holds callbacks that allow PreferenceScaffold (deep in Compose tree)
 * to control the View-based CollapsingToolbarLayout in PreferenceActivity.
 */
data class CollapsingToolbarCallbacks(
    val setTitle: (String) -> Unit,
    val setBackArrowVisible: (Boolean) -> Unit,
    val setActions: (@Composable () -> Unit) -> Unit,
)

val LocalCollapsingToolbar = staticCompositionLocalOf<CollapsingToolbarCallbacks?> { null }
