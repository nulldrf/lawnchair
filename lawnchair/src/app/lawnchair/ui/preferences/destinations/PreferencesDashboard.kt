package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.AnnouncementPreference
import app.lawnchair.ui.preferences.components.controls.PreferenceCategory
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.data.liveinfo.SyncLiveInformation
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.navigation.About
import app.lawnchair.ui.preferences.navigation.AppDrawer
import app.lawnchair.ui.preferences.navigation.BackupAndRestore
import app.lawnchair.ui.preferences.navigation.CreateBackup
import app.lawnchair.ui.preferences.navigation.Dock
import app.lawnchair.ui.preferences.navigation.ExperimentalFeatures
import app.lawnchair.ui.preferences.navigation.Extras
import app.lawnchair.ui.preferences.navigation.Folders
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.ui.preferences.navigation.Gestures
import app.lawnchair.ui.preferences.navigation.HomeScreen
import app.lawnchair.ui.preferences.navigation.PreferenceRootRoute
import app.lawnchair.ui.preferences.navigation.Quickstep
import app.lawnchair.ui.preferences.navigation.Search
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking

@Composable
fun PreferencesDashboard(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SyncLiveInformation()
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    // ── Announcement state ────────────────────────────────────────────────
    val liveInformationManager = liveInformationManager()
    val enabled by liveInformationManager.enabled.asState()
    val showAnnouncements by liveInformationManager.showAnnouncements.asState()
    val dismissedAnnouncementIds by liveInformationManager.dismissedAnnouncementIds.asState()
    val liveInformation by liveInformationManager.liveInformation.asState()

    val activeAnnouncements = remember(liveInformation, dismissedAnnouncementIds) {
        liveInformation.announcements.filter {
            it.shouldBeVisible && it.id !in dismissedAnnouncementIds
        }
    }
    val announcementShowing = enabled && showAnnouncements && activeAnnouncements.isNotEmpty()
    val isNotDefaultLauncher = !context.isDefaultLauncher()

    // ── Dynamic toolbar title ─────────────────────────────────────────────
    // When an announcement is visible AND Lawnchair is not yet the default
    // launcher, we repurpose the large expanded title to show the set-default
    // prompt — saving screen space by removing that card.
    // Once the announcement is dismissed the prompt reappears as a card and
    // the title reverts to the regular "Settings" label.
    val settingsLabel = stringResource(id = R.string.settings)
    val setDefaultLabel = stringResource(id = R.string.set_default_launcher_short)
    val expandedLabel = if (announcementShowing && isNotDefaultLauncher) setDefaultLabel else settingsLabel

    // ── About description ─────────────────────────────────────────────────
    val aboutDescription = if (prefs.hideVersionInfo.get()) {
        prefs.pseudonymVersion.get()
    } else {
        "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}"
    }

    // The expanded title is tappable only while it shows the set-default prompt
    // (i.e. an announcement card is occupying the card slot). Once the announcement
    // is dismissed the prompt moves to a card and the title becomes plain "Settings".
    val onExpandedTitleClick: (() -> Unit)? = if (announcementShowing && isNotDefaultLauncher) {
        {
            Intent(Settings.ACTION_HOME_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
            (context as? Activity)?.finish()
        }
    } else null

    // ── Debug badge dialog ────────────────────────────────────────────────
    val isDebugBuild = BuildConfig.APPLICATION_ID.contains("nightly") || BuildConfig.DEBUG
    var showDebugDialog by remember { mutableStateOf(false) }

    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("Debug Build") },
            text = {
                Text("You are using a development build, which may contain bugs and broken features. Use at your own risk!")
            },
            confirmButton = {
                TextButton(onClick = { showDebugDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    PreferenceLayout(
        label = settingsLabel,
        expandedLabel = expandedLabel,
        onExpandedTitleClick = onExpandedTitleClick,
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        backArrowVisible = false,
        actions = {
            if (isDebugBuild) {
                DebugBadge(onClick = { showDebugDialog = true })
            }
        },
    ) {
        // ── Announcement card ─────────────────────────────────────────────
        // Wrapped in AnimatedVisibility so that when live-info is reset in the
        // debug menu the card block expands/fades in smoothly, coordinated with
        // the set-default card shrinking out at the same time.
        AnimatedVisibility(
            visible = announcementShowing,
            enter = expandVertically(animationSpec = tween(350)) + fadeIn(animationSpec = tween(350)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)),
        ) {
            AnnouncementPreference()
        }

        // AnimatedVisibility gives the set-default card a smooth slide-from-bottom
        // + fade entrance when the announcement is dismissed. On the way out
        // (announcement returning) it shrinks vertically to mirror the announcement's
        // expand, so both cards feel like a coordinated swap.
        AnimatedVisibility(
            visible = isNotDefaultLauncher && !announcementShowing,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 350),
                initialOffsetY = { fullHeight -> fullHeight },
            ) + fadeIn(animationSpec = tween(durationMillis = 350)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)),
        ) {
            PreferencesSetDefaultLauncherCard()
        }

        // ── Dev / debug warnings ──────────────────────────────────────────
        // Debug warning is now shown as a badge in the toolbar — see DebugBadge
        // and the AlertDialog above. Nothing to render here.

        val deckLayout = prefs2.deckLayout.getAdapter()
        PreferenceGroup {
            Item {
                PreferenceCategory(
                    label = stringResource(R.string.general_label),
                    description = stringResource(R.string.general_description),
                    iconResource = R.drawable.ic_general,
                    onNavigate = { onNavigate(General) },
                    isSelected = currentRoute is General,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(R.string.home_screen_label),
                    description = stringResource(R.string.home_screen_description),
                    iconResource = R.drawable.ic_home_screen,
                    onNavigate = { onNavigate(HomeScreen) },
                    isSelected = currentRoute is HomeScreen,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            val isSmartspaceEnabled = prefs2.enableSmartspace.firstBlocking()
            Item {
                PreferenceCategory(
                    label = stringResource(id = R.string.smartspace_widget),
                    description = stringResource(R.string.smartspace_widget_description),
                    iconResource = if (isSmartspaceEnabled) R.drawable.ic_smartspace else R.drawable.ic_smartspace_off,
                    onNavigate = { onNavigate(Smartspace) },
                    isSelected = currentRoute is Smartspace,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(R.string.dock_label),
                    description = stringResource(R.string.dock_description),
                    iconResource = R.drawable.ic_dock,
                    onNavigate = { onNavigate(Dock) },
                    isSelected = currentRoute is Dock,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item(
                key = "app_drawer",
                visible = !deckLayout.state.value,
            ) {
                PreferenceCategory(
                    label = stringResource(R.string.app_drawer_label),
                    description = stringResource(R.string.app_drawer_description),
                    iconResource = R.drawable.ic_apps,
                    onNavigate = { onNavigate(AppDrawer) },
                    isSelected = currentRoute is AppDrawer,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(R.string.search_bar_label),
                    description = stringResource(R.string.drawer_search_description),
                    iconResource = R.drawable.ic_search,
                    onNavigate = { onNavigate(Search()) },
                    isSelected = currentRoute is Search,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(R.string.folders_label),
                    description = stringResource(R.string.folders_description),
                    iconResource = R.drawable.ic_folder,
                    onNavigate = { onNavigate(Folders) },
                    isSelected = currentRoute is Folders,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(id = R.string.gestures_label),
                    description = stringResource(R.string.gestures_description),
                    iconResource = R.drawable.ic_gestures,
                    onNavigate = { onNavigate(Gestures) },
                    isSelected = currentRoute is Gestures,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item(
                "quickstep",
                LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG,
            ) {
                PreferenceCategory(
                    label = stringResource(id = R.string.quickstep_label),
                    description = stringResource(id = R.string.quickstep_description),
                    iconResource = R.drawable.ic_quickstep,
                    onNavigate = { onNavigate(Quickstep) },
                    isSelected = currentRoute is Quickstep,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(R.string.backup_and_restore_label),
                    description = stringResource(R.string.backup_and_restore_description),
                    iconResource = R.drawable.backup_restore,
                    onNavigate = { onNavigate(BackupAndRestore) },
                    isSelected = currentRoute is BackupAndRestore,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(R.string.extras_label),
                    description = stringResource(R.string.extras_description),
                    iconResource = R.drawable.ic_extras,
                    onNavigate = { onNavigate(Extras) },
                    isSelected = currentRoute is Extras,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }

            Item {
                PreferenceCategory(
                    label = stringResource(R.string.about_label),
                    description = aboutDescription,
                    iconResource = R.drawable.ic_about,
                    onNavigate = { onNavigate(About) },
                    isSelected = currentRoute is About,
                    isFirst = it.isFirst,
                    isLast = it.isLast,
                )
            }
        }
    }
}

/**
 * A small pill-shaped badge rendered in the toolbar actions area.
 * Tapping it shows the debug warning dialog. Only shown in debug/nightly builds.
 */
@Composable
private fun DebugBadge(
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
        ),
    ) {
        Text(
            text = "debug",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        )
    }
}

/**
 * Styled to match the announcement card (primary colour, same corner radius).
 * Shown when Lawnchair is not the default launcher and there is no active
 * announcement occupying that slot.
 */
@Composable
fun PreferencesSetDefaultLauncherCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        PreferenceTemplate(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    Intent(Settings.ACTION_HOME_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .let { context.startActivity(it) }
                    (context as? Activity)?.finish()
                },
            title = {},
            description = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.set_default_launcher_tip),
                    color = MaterialTheme.colorScheme.background,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.background,
                    contentDescription = null,
                )

            },
        )
    }
}

fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, LawnchairLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}
