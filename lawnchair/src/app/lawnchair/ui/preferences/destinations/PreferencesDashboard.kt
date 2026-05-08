package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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

// ── Searchable entry — represents any preference that can appear in search ──
private data class SearchableEntry(
    val label: String,
    val description: String,
    val breadcrumb: String,           // e.g. "General > Colors"
    val iconResource: Int,
    val route: PreferenceRootRoute,
)

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
    val settingsLabel = stringResource(id = R.string.settings)
    val setDefaultLabel = stringResource(id = R.string.set_default_launcher_short)
    val expandedLabel = if (announcementShowing && isNotDefaultLauncher) setDefaultLabel else settingsLabel

    val onExpandedTitleClick: (() -> Unit)? = if (announcementShowing && isNotDefaultLauncher) {
        {
            Intent(Settings.ACTION_HOME_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
            (context as? Activity)?.finish()
        }
    } else null

    // ── About description ─────────────────────────────────────────────────
    val aboutDescription = if (prefs.hideVersionInfo.get()) {
        prefs.pseudonymVersion.get()
    } else {
        "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}"
    }

    // ── Search state ──────────────────────────────────────────────────────
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // ── Resolve all item strings ──────────────────────────────────────────
    val labelGeneral     = stringResource(R.string.general_label)
    val descGeneral      = stringResource(R.string.general_description)
    val labelHomeScreen  = stringResource(R.string.home_screen_label)
    val descHomeScreen   = stringResource(R.string.home_screen_description)
    val labelSmartspace  = stringResource(id = R.string.smartspace_widget)
    val descSmartspace   = stringResource(R.string.smartspace_widget_description)
    val labelDock        = stringResource(R.string.dock_label)
    val descDock         = stringResource(R.string.dock_description)
    val labelAppDrawer   = stringResource(R.string.app_drawer_label)
    val descAppDrawer    = stringResource(R.string.app_drawer_description)
    val labelSearchBar   = stringResource(R.string.search_bar_label)
    val descSearchBar    = stringResource(R.string.drawer_search_description)
    val labelFolders     = stringResource(R.string.folders_label)
    val descFolders      = stringResource(R.string.folders_description)
    val labelGestures    = stringResource(id = R.string.gestures_label)
    val descGestures     = stringResource(R.string.gestures_description)
    val labelQuickstep   = stringResource(id = R.string.quickstep_label)
    val descQuickstep    = stringResource(id = R.string.quickstep_description)
    val labelBackup      = stringResource(R.string.backup_and_restore_label)
    val descBackup       = stringResource(R.string.backup_and_restore_description)
    val labelExtras      = stringResource(R.string.extras_label)
    val descExtras       = stringResource(R.string.extras_description)
    val labelAbout       = stringResource(R.string.about_label)

    val deckLayout = prefs2.deckLayout.getAdapter()
    val isSmartspaceEnabled = prefs2.enableSmartspace.firstBlocking()

    // ── Full searchable entry list ────────────────────────────────────────
    // Each entry carries its top-level route so tapping it navigates directly.
    // Sub-setting labels (from strings.xml) are included as extra search terms
    // via the description field so e.g. "icon pack" finds General.
    val allEntries = remember(labelGeneral, deckLayout.state.value) {
        buildList {
            add(SearchableEntry(labelGeneral, descGeneral + " • icon packs • colors • notification dots • themes • fonts • accent color", "Settings", R.drawable.ic_general, General))
            add(SearchableEntry(labelHomeScreen, descHomeScreen + " • grid • feed • widgets • wallpaper • status bar • infinite scrolling • rotation", "Settings", R.drawable.ic_home_screen, HomeScreen))
            add(SearchableEntry(labelSmartspace, descSmartspace + " • at a glance • weather • battery • clock • date • calendar", "Settings", if (isSmartspaceEnabled) R.drawable.ic_smartspace else R.drawable.ic_smartspace_off, Smartspace))
            add(SearchableEntry(labelDock, descDock + " • hotseat • bottom bar • icon count • search bar", "Settings", R.drawable.ic_dock, Dock))
            if (!deckLayout.state.value) {
                add(SearchableEntry(labelAppDrawer, descAppDrawer + " • hidden apps • columns • scrollbar • bulk loading", "Settings", R.drawable.ic_apps, AppDrawer))
            }
            add(SearchableEntry(labelSearchBar, descSearchBar + " • fuzzy search • suggestions • web search • google • device search", "Settings", R.drawable.ic_search, Search()))
            add(SearchableEntry(labelFolders, descFolders + " • folder rows • folder columns • folder background", "Settings", R.drawable.ic_folder, Folders))
            add(SearchableEntry(labelGestures, descGestures + " • double tap • swipe up • swipe down • back • home • sleep • lock", "Settings", R.drawable.ic_gestures, Gestures))
            if (LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
                add(SearchableEntry(labelQuickstep, descQuickstep + " • recents • clear all • corner radius • taskbar • overview", "Settings", R.drawable.ic_quickstep, Quickstep))
            }
            add(SearchableEntry(labelBackup, descBackup + " • export • import • restore • save layout", "Settings", R.drawable.backup_restore, BackupAndRestore))
            add(SearchableEntry(labelExtras, descExtras + " • experimental • debug • restart • font • deck layout", "Settings", R.drawable.ic_extras, Extras))
            add(SearchableEntry(labelAbout, aboutDescription + " • version • contributors • github • telegram • discord • donate • changelog", "Settings", R.drawable.ic_about, About))
        }
    }

    // ── Debug badge ───────────────────────────────────────────────────────
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

    // ── Search overlay ────────────────────────────────────────────────────
    // When active, a full-screen overlay replaces the normal settings list.
    // BackHandler pops back to the normal view.
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    AnimatedContent(
        targetState = searchActive,
        transitionSpec = {
            (fadeIn(tween(220)) + expandVertically(tween(280))).togetherWith(
                fadeOut(tween(180)) + shrinkVertically(tween(220)),
            )
        },
        label = "search_overlay",
    ) { isSearching ->
        if (isSearching) {
            SearchOverlay(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    searchActive = false
                    searchQuery = ""
                },
                entries = allEntries,
                onNavigate = onNavigate,
            )
        } else {
            // ── Normal settings layout ────────────────────────────────────
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
                // ── Announcement card ─────────────────────────────────────
                AnimatedVisibility(
                    visible = announcementShowing,
                    enter = expandVertically(animationSpec = tween(350)) + fadeIn(animationSpec = tween(350)),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)),
                ) {
                    AnnouncementPreference()
                }

                // ── Set default card ──────────────────────────────────────
                AnimatedVisibility(
                    visible = isNotDefaultLauncher && !announcementShowing,
                    enter = slideInVertically(
                        animationSpec = tween(350),
                        initialOffsetY = { it },
                    ) + fadeIn(tween(350)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
                ) {
                    PreferencesSetDefaultLauncherCard()
                }

                // ── Search bar ────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSearchBar(
                    onActivate = { searchActive = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // ── Debug warning (badge only, no card) ───────────────────
                // Debug warning is shown as a DebugBadge in the toolbar.

                // ── Preference items ──────────────────────────────────────
                PreferenceGroup {
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelGeneral,
                            description = descGeneral,
                            iconResource = R.drawable.ic_general,
                            onNavigate = { onNavigate(General) },
                            isSelected = currentRoute is General,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelHomeScreen,
                            description = descHomeScreen,
                            iconResource = R.drawable.ic_home_screen,
                            onNavigate = { onNavigate(HomeScreen) },
                            isSelected = currentRoute is HomeScreen,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelSmartspace,
                            description = descSmartspace,
                            iconResource = if (isSmartspaceEnabled) R.drawable.ic_smartspace else R.drawable.ic_smartspace_off,
                            onNavigate = { onNavigate(Smartspace) },
                            isSelected = currentRoute is Smartspace,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelDock,
                            description = descDock,
                            iconResource = R.drawable.ic_dock,
                            onNavigate = { onNavigate(Dock) },
                            isSelected = currentRoute is Dock,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(key = "app_drawer", visible = !deckLayout.state.value) {
                        PreferenceCategory(
                            label = labelAppDrawer,
                            description = descAppDrawer,
                            iconResource = R.drawable.ic_apps,
                            onNavigate = { onNavigate(AppDrawer) },
                            isSelected = currentRoute is AppDrawer,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelSearchBar,
                            description = descSearchBar,
                            iconResource = R.drawable.ic_search,
                            onNavigate = { onNavigate(Search()) },
                            isSelected = currentRoute is Search,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelFolders,
                            description = descFolders,
                            iconResource = R.drawable.ic_folder,
                            onNavigate = { onNavigate(Folders) },
                            isSelected = currentRoute is Folders,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelGestures,
                            description = descGestures,
                            iconResource = R.drawable.ic_gestures,
                            onNavigate = { onNavigate(Gestures) },
                            isSelected = currentRoute is Gestures,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(key = "quickstep", visible = LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
                        PreferenceCategory(
                            label = labelQuickstep,
                            description = descQuickstep,
                            iconResource = R.drawable.ic_quickstep,
                            onNavigate = { onNavigate(Quickstep) },
                            isSelected = currentRoute is Quickstep,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelBackup,
                            description = descBackup,
                            iconResource = R.drawable.backup_restore,
                            onNavigate = { onNavigate(BackupAndRestore) },
                            isSelected = currentRoute is BackupAndRestore,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelExtras,
                            description = descExtras,
                            iconResource = R.drawable.ic_extras,
                            onNavigate = { onNavigate(Extras) },
                            isSelected = currentRoute is Extras,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelAbout,
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
    }
}

// ── Search overlay ────────────────────────────────────────────────────────────
// Full-screen composable shown when the search bar is activated.
// Styled after the old Lawnchair search screen.
@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    entries: List<SearchableEntry>,
    onNavigate: (PreferenceRootRoute) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val filtered = remember(query, entries) {
        if (query.isBlank()) entries
        else {
            val q = query.trim().lowercase()
            entries.filter {
                it.label.lowercase().contains(q) ||
                    it.description.lowercase().contains(q)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Search field ──────────────────────────────────────────────
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_settings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                leadingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* keep keyboard open */ }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )

            // ── Results ───────────────────────────────────────────────────
            LazyColumn {
                items(filtered.size) { idx ->
                    val entry = filtered[idx]
                    PreferenceCategory(
                        label = entry.label,
                        description = entry.breadcrumb,
                        iconResource = entry.iconResource,
                        onNavigate = {
                            onClose()
                            onNavigate(entry.route)
                        },
                        isSelected = false,
                        isFirst = idx == 0,
                        isLast = idx == filtered.lastIndex,
                    )
                }
            }
        }
    }
}

// ── Tap-to-activate search bar ────────────────────────────────────────────────
// Not interactive as a text field — tapping it fires onActivate so the
// full-screen SearchOverlay opens with the animated transition.
@Composable
private fun SettingsSearchBar(
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onActivate,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.search_settings),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ── Set default launcher card ─────────────────────────────────────────────────
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

/**
 * A small pill-shaped badge rendered in the toolbar actions area.
 * Styled after LibChecker's "CI" badge — bordered chip, slight primary tint.
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

fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, LawnchairLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}
