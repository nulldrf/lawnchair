package app.lawnchair.ui.preferences.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation destination for the full-screen colour-style picker.
 *
 * Implements [PreferenceRoute] so it can be passed directly to
 * [NavigationActionPreference]'s `destination` parameter.
 *
 * [showLegacyKdrag] is forwarded from [GeneralPreferences]: it is `true`
 * only when the active accent source is wallpaper-derived, mirroring the
 * gate that the old bottom-sheet used.
 *
 */
@Serializable
data class GeneralColorStyle(
    val showLegacyKdrag: Boolean = false,
) : PreferenceRoute
